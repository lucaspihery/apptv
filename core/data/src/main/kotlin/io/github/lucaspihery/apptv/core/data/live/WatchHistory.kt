package io.github.lucaspihery.apptv.core.data.live

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.lucaspihery.apptv.core.data.account.AccountRepository
import io.github.lucaspihery.apptv.core.data.account.SavedAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Qualité d'un flux : hauteur de l'image en pixels et images par seconde.
 * [measured] : vue par le lecteur ; sinon déduite du nom de la chaîne (« FHD », « 4K »…).
 */
data class StreamQuality(val height: Int, val fps: Int?, val measured: Boolean) {
    /** Pastille de définition : 4K, QHD, FHD, HD ou SD. `null` si inconnue. */
    val resolutionLabel: String?
        get() = when {
            height <= 0 -> null
            height >= 2000 -> "4K"
            height >= 1400 -> "QHD"
            height >= 1000 -> "FHD"
            height >= 700 -> "HD"
            else -> "SD"
        }

    /** Pastille d'images par seconde (« 50 fps »), `null` si inconnue. */
    val fpsLabel: String?
        get() = fps?.takeIf { it > 0 }?.let { "$it fps" }

    companion object {
        private val FPS_IN_NAME = Regex("""(?<!\d)(24|25|30|48|50|60|100|120)\s*(?:fps|ips|p\b)""", RegexOption.IGNORE_CASE)
        private val TOKENS = Regex("""[^\p{L}\p{N}+]+""")

        /** Qualité annoncée par le nom (« Sport FHD 50FPS », « Film 4K HEVC »), ou `null`. */
        fun fromName(name: String): StreamQuality? {
            val words = name.uppercase().split(TOKENS).toSet()
            val height = when {
                words.any { it == "4K" || it == "UHD" || it == "2160P" || it == "2160" } -> 2160
                words.any { it == "QHD" || it == "1440P" } -> 1440
                words.any { it == "FHD" || it == "1080P" || it == "1080" || it == "1080I" } -> 1080
                words.any { it == "HD" || it == "720P" || it == "720" || it == "HD+" } -> 720
                words.any { it == "SD" || it == "576P" || it == "480P" } -> 576
                else -> 0
            }
            val fps = FPS_IN_NAME.find(name)?.groupValues?.get(1)?.toIntOrNull()
            return if (height == 0 && fps == null) null else StreamQuality(height, fps, measured = false)
        }

        /** Arrondit une cadence mesurée à la valeur standard la plus proche (23,976 → 24, 59,94 → 60). */
        fun snapFps(raw: Float): Int? {
            if (raw.isNaN() || raw < 10f || raw > 130f) return null
            val standard = intArrayOf(24, 25, 30, 48, 50, 60, 100, 120)
            val best = standard.minBy { abs(it - raw) }
            return if (abs(best - raw) <= 1.5f) best else raw.roundToInt()
        }

        internal fun encode(map: Map<String, StreamQuality>): String =
            map.entries.joinToString("\n") { (k, q) -> "${k.clean()}\t${q.height}\t${q.fps ?: 0}" }

        internal fun decode(text: String?): Map<String, StreamQuality> {
            if (text.isNullOrEmpty()) return emptyMap()
            val out = LinkedHashMap<String, StreamQuality>()
            for (line in text.lineSequence()) {
                val parts = line.split('\t')
                if (parts.size != 3) continue
                val h = parts[1].toIntOrNull() ?: continue
                val f = parts[2].toIntOrNull()?.takeIf { it > 0 }
                out[parts[0]] = StreamQuality(h, f, measured = true)
            }
            return out
        }
    }
}

/** Les clés sont stockées une par ligne : pas de retour à la ligne ni de tabulation dedans. */
private fun String.clean(): String = replace('\n', ' ').replace('\t', ' ')

/**
 * Historique des chaînes regardées et qualité vue par le lecteur, par compte.
 * L'historique ne garde que les clés ([LiveChannel.key]), jamais d'URL.
 */
interface WatchHistoryRepository {
    /** Clés des chaînes regardées, de la plus récente à la plus ancienne. */
    val history: Flow<List<String>>

    /** Dernière qualité mesurée par chaîne. */
    val qualities: Flow<Map<String, StreamQuality>>

    suspend fun recordWatched(channel: LiveChannel)

    suspend fun recordQuality(channel: LiveChannel, quality: StreamQuality)

    suspend fun clearHistory()
}

@Singleton
class DefaultWatchHistoryRepository @Inject constructor(
    @param:Named(STORE) private val store: DataStore<Preferences>,
    private val accounts: AccountRepository,
) : WatchHistoryRepository {

    private val prefs = store.data.catch { emit(emptyPreferences()) }

    override val history: Flow<List<String>> =
        combine(accounts.account, prefs) { account, p ->
            account?.let { p[historyKey(it)] }?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
        }.distinctUntilChanged()

    override val qualities: Flow<Map<String, StreamQuality>> =
        combine(accounts.account, prefs) { account, p ->
            StreamQuality.decode(account?.let { p[qualityKey(it)] })
        }.distinctUntilChanged()

    override suspend fun recordWatched(channel: LiveChannel) {
        val account = accounts.account.first() ?: return
        val key = historyKey(account)
        val entry = channel.key.clean()
        edit { p ->
            val current = p[key]?.split('\n')?.filter { it.isNotEmpty() && it != entry }.orEmpty()
            p[key] = (listOf(entry) + current).take(MAX_HISTORY).joinToString("\n")
        }
    }

    override suspend fun recordQuality(channel: LiveChannel, quality: StreamQuality) {
        val account = accounts.account.first() ?: return
        val key = qualityKey(account)
        val entry = channel.key.clean()
        edit { p ->
            val map = LinkedHashMap(StreamQuality.decode(p[key]))
            map.remove(entry)
            map[entry] = quality
            // Les plus anciennes sortent en premier : taille bornée (mémoire et disque).
            while (map.size > MAX_QUALITIES) map.remove(map.keys.first())
            p[key] = StreamQuality.encode(map)
        }
    }

    override suspend fun clearHistory() {
        val account = accounts.account.first() ?: return
        edit { it.remove(historyKey(account)) }
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        try {
            store.edit { block(it) }
        } catch (_: IOException) {
            // Non enregistré : sans conséquence pour la lecture.
        }
    }

    /** Une entrée par compte : serveur + identifiant (jamais le mot de passe). */
    private fun historyKey(a: SavedAccount) = stringPreferencesKey("hist.${a.type}.${a.server}.${a.username.orEmpty()}")

    private fun qualityKey(a: SavedAccount) = stringPreferencesKey("quality.${a.type}.${a.server}.${a.username.orEmpty()}")

    companion object {
        const val STORE = "history"
        const val MAX_HISTORY = 50
        const val MAX_QUALITIES = 500
    }
}
