package io.github.lucaspihery.apptv.core.data.live

import io.github.lucaspihery.apptv.core.common.IoDispatcher
import io.github.lucaspihery.apptv.core.data.account.AccountType
import io.github.lucaspihery.apptv.core.data.account.DefaultAccountRepository
import io.github.lucaspihery.apptv.core.data.account.SavedAccount
import io.github.lucaspihery.apptv.core.data.core.CoreResult
import io.github.lucaspihery.apptv.core.data.core.IptvCore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guide des programmes, chaîne par chaîne. Seules les chaînes affichées sont demandées,
 * peu de requêtes à la fois, et le résultat reste en cache jusqu'à la fin du programme en cours.
 * M3U : pas de guide pour l'instant (il faudrait lire le XMLTV en flux).
 */
interface EpgRepository {
    /** Programmes déjà connus pour cette chaîne (sans appel réseau), ou `null`. */
    fun cached(channel: LiveChannel, minCount: Int = SHORT): List<Program>?

    /** Programmes à venir (le premier est en cours). Liste vide si indisponible. */
    suspend fun programs(channel: LiveChannel, minCount: Int = SHORT): List<Program>

    companion object {
        /** Programme en cours + suivant (liste des chaînes, bandeau). */
        const val SHORT = 2

        /** Grille : environ une demi-journée. */
        const val GRID = 16
    }
}

@Singleton
class DefaultEpgRepository @Inject constructor(
    private val accounts: DefaultAccountRepository,
    private val core: IptvCore,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : EpgRepository {

    private class Entry(val programs: List<Program>, val requested: Int, val fetchedAt: Long)

    /** Cache LRU borné (mémoire limitée de la TV). */
    private val cache = object : LinkedHashMap<String, Entry>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > MAX_ENTRIES
    }
    private val requests = Semaphore(MAX_PARALLEL)
    private val credsLock = Mutex()
    private var creds: Pair<SavedAccount, String>? = null

    override fun cached(channel: LiveChannel, minCount: Int): List<Program>? {
        val e = synchronized(cache) { cache[channel.key] } ?: return null
        return if (e.isFresh(minCount, now())) e.programs.dropWhile { it.end <= now() } else null
    }

    override suspend fun programs(channel: LiveChannel, minCount: Int): List<Program> {
        cached(channel, minCount)?.let { return it }
        val streamId = channel.streamId ?: return emptyList()
        return withContext(io) {
            requests.withPermit {
                // Une autre demande a pu remplir le cache pendant l'attente.
                cached(channel, minCount)?.let { return@withPermit it }
                val (account, secret) = credentials() ?: return@withPermit emptyList()
                val limit = maxOf(minCount, EpgRepository.SHORT) + 1
                val result = core.xtreamShortEpg(account.server, account.username.orEmpty(), secret, streamId, limit)
                val list = (result as? CoreResult.Ok)?.value.orEmpty()
                synchronized(cache) { cache[channel.key] = Entry(list, limit, now()) }
                list
            }
        }
    }

    private fun Entry.isFresh(minCount: Int, now: Long): Boolean {
        if (requested < minCount + 1 && programs.size >= requested) return false
        val first = programs.firstOrNull { it.end > now }
        return if (first == null) now - fetchedAt < EMPTY_TTL_S else first.end > now
    }

    private suspend fun credentials(): Pair<SavedAccount, String>? = credsLock.withLock {
        val account = accounts.account.first() ?: return@withLock null
        if (account.type != AccountType.XTREAM) return@withLock null
        creds?.takeIf { it.first == account }?.let { return@withLock it }
        val secret = accounts.secret() ?: return@withLock null
        (account to secret).also {
            creds = it
            synchronized(cache) { cache.clear() }
        }
    }

    private fun now(): Long = System.currentTimeMillis() / 1000

    private companion object {
        const val MAX_PARALLEL = 4
        const val MAX_ENTRIES = 800

        /** Chaîne sans guide : on redemande au bout de 10 minutes. */
        const val EMPTY_TTL_S = 600L
    }
}
