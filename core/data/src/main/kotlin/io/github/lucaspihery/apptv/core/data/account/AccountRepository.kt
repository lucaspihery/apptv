package io.github.lucaspihery.apptv.core.data.account

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.lucaspihery.apptv.core.common.IoDispatcher
import io.github.lucaspihery.apptv.core.data.core.CoreResult
import io.github.lucaspihery.apptv.core.data.core.IptvCore
import io.github.lucaspihery.apptv.core.data.crypto.SecretCipher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

interface AccountRepository {
    /** Compte actif, ou `null` si personne n'est connecté. */
    val account: Flow<SavedAccount?>

    suspend fun loginXtream(server: String, username: String, password: String): LoginResult

    suspend fun loginM3u(url: String): LoginResult

    /** Lien M3U collé qui est en fait un compte Xtream (`get.php`). */
    fun detectXtreamLink(url: String): XtreamLinkCredentials?

    suspend fun logout()
}

/**
 * Un seul compte au jalon 1 ; le multi-comptes (jalon 6) ajoutera un identifiant
 * de compte devant chaque clé.
 */
@Singleton
class DefaultAccountRepository @Inject constructor(
    private val store: DataStore<Preferences>,
    private val core: IptvCore,
    private val cipher: SecretCipher,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : AccountRepository {

    override val account: Flow<SavedAccount?> = store.data.map { it.toAccount() }

    override suspend fun loginXtream(server: String, username: String, password: String): LoginResult =
        withContext(io) {
            val normalized = when (val n = core.normalizeServer(server)) {
                is CoreResult.Ok -> n.value
                is CoreResult.Err -> return@withContext LoginResult.Failure(n.error)
            }
            val user = username.trim()
            when (val r = core.xtreamLogin(normalized, user, password)) {
                is CoreResult.Err -> LoginResult.Failure(r.error)
                is CoreResult.Ok -> {
                    val account = SavedAccount(
                        type = AccountType.XTREAM,
                        server = normalized,
                        username = user,
                        expiresAt = r.value.expiresAt,
                        maxConnections = r.value.maxConnections,
                        channelCount = null,
                    )
                    save(account, secret = password)
                }
            }
        }

    override suspend fun loginM3u(url: String): LoginResult = withContext(io) {
        val trimmed = url.trim()
        when (val r = core.probePlaylist(trimmed)) {
            is CoreResult.Err -> LoginResult.Failure(r.error)
            is CoreResult.Ok -> {
                val account = SavedAccount(
                    type = AccountType.M3U,
                    server = hostOf(trimmed),
                    username = null,
                    expiresAt = null,
                    maxConnections = null,
                    channelCount = r.value.channelCount,
                )
                save(account, secret = trimmed)
            }
        }
    }

    override fun detectXtreamLink(url: String): XtreamLinkCredentials? = core.detectXtreamLink(url)

    override suspend fun logout() {
        withContext(io) {
            try {
                store.edit { it.clear() }
            } catch (_: IOException) {
                // Rien à faire : l'état reste « connecté » et l'UI le montre.
            }
        }
    }

    /** Secret déchiffré (mot de passe ou lien M3U), pour construire les requêtes. */
    suspend fun secret(): String? = withContext(io) {
        store.data.first()[Keys.SECRET]?.let(cipher::decrypt)
    }

    private suspend fun save(account: SavedAccount, secret: String): LoginResult = try {
        val token = cipher.encrypt(secret)
        store.edit { p ->
            p.clear()
            p[Keys.TYPE] = account.type.name
            p[Keys.SERVER] = account.server
            account.username?.let { p[Keys.USERNAME] = it }
            account.expiresAt?.let { p[Keys.EXPIRES_AT] = it }
            account.maxConnections?.let { p[Keys.MAX_CONNECTIONS] = it }
            account.channelCount?.let { p[Keys.CHANNEL_COUNT] = it }
            p[Keys.SECRET] = token
        }
        LoginResult.Success(account)
    } catch (_: IOException) {
        LoginResult.Failure(AccountError.Storage)
    } catch (_: java.security.GeneralSecurityException) {
        LoginResult.Failure(AccountError.Storage)
    }

    private fun Preferences.toAccount(): SavedAccount? {
        val type = this[Keys.TYPE]?.let { runCatching { AccountType.valueOf(it) }.getOrNull() } ?: return null
        if (this[Keys.SECRET] == null) return null
        return SavedAccount(
            type = type,
            server = this[Keys.SERVER] ?: return null,
            username = this[Keys.USERNAME],
            expiresAt = this[Keys.EXPIRES_AT],
            maxConnections = this[Keys.MAX_CONNECTIONS],
            channelCount = this[Keys.CHANNEL_COUNT],
        )
    }

    private fun hostOf(url: String): String =
        runCatching { URI(url).host }.getOrNull() ?: "playlist"

    private object Keys {
        val TYPE = stringPreferencesKey("account.type")
        val SERVER = stringPreferencesKey("account.server")
        val USERNAME = stringPreferencesKey("account.username")
        val EXPIRES_AT = longPreferencesKey("account.expires_at")
        val MAX_CONNECTIONS = longPreferencesKey("account.max_connections")
        val CHANNEL_COUNT = longPreferencesKey("account.channel_count")
        val SECRET = stringPreferencesKey("account.secret")
    }
}
