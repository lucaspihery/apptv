package io.github.lucaspihery.apptv.core.data.live

import io.github.lucaspihery.apptv.core.common.IoDispatcher
import io.github.lucaspihery.apptv.core.data.account.AccountError
import io.github.lucaspihery.apptv.core.data.account.AccountType
import io.github.lucaspihery.apptv.core.data.account.DefaultAccountRepository
import io.github.lucaspihery.apptv.core.data.account.SavedAccount
import io.github.lucaspihery.apptv.core.data.core.CoreResult
import io.github.lucaspihery.apptv.core.data.core.IptvCore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface LiveRepository {
    /** Catalogue du compte actif, gardé en mémoire tant que le compte ne change pas. */
    suspend fun catalog(refresh: Boolean = false): CoreResult<LiveCatalog>

    /** URL de lecture (contient souvent des identifiants : ne jamais la journaliser). */
    suspend fun streamUrl(channel: LiveChannel): String?
}

@Singleton
class DefaultLiveRepository @Inject constructor(
    private val accounts: DefaultAccountRepository,
    private val core: IptvCore,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : LiveRepository {

    private val mutex = Mutex()
    private var cached: Pair<SavedAccount, LiveCatalog>? = null

    override suspend fun catalog(refresh: Boolean): CoreResult<LiveCatalog> = withContext(io) {
        mutex.withLock {
            val account = accounts.account.first() ?: return@withLock CoreResult.Err(AccountError.AuthFailed)
            cached?.let { (a, c) -> if (!refresh && a == account) return@withLock CoreResult.Ok(c) }
            val secret = accounts.secret() ?: return@withLock CoreResult.Err(AccountError.Storage)
            val result = when (account.type) {
                AccountType.XTREAM -> core.xtreamLiveCatalog(account.server, account.username.orEmpty(), secret)
                AccountType.M3U -> core.m3uLiveCatalog(secret)
            }
            cached = (result as? CoreResult.Ok)?.let { account to it.value }
            result
        }
    }

    override suspend fun streamUrl(channel: LiveChannel): String? = withContext(io) {
        channel.url?.let { return@withContext it }
        val id = channel.streamId ?: return@withContext null
        val account = accounts.account.first() ?: return@withContext null
        if (account.type != AccountType.XTREAM) return@withContext null
        val secret = accounts.secret() ?: return@withContext null
        (core.xtreamLiveUrl(account.server, account.username.orEmpty(), secret, id, LIVE_FORMAT) as? CoreResult.Ok)?.value
    }

    private companion object {
        /** MPEG-TS : format le plus répandu chez les fournisseurs Xtream. */
        const val LIVE_FORMAT = "ts"
    }
}
