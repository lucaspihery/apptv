package io.github.lucaspihery.apptv.core.testing

import io.github.lucaspihery.apptv.core.data.account.AccountRepository
import io.github.lucaspihery.apptv.core.data.account.AccountType
import io.github.lucaspihery.apptv.core.data.account.LoginResult
import io.github.lucaspihery.apptv.core.data.account.SavedAccount
import io.github.lucaspihery.apptv.core.data.account.XtreamLinkCredentials
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Dépôt de comptes en mémoire, pilotable depuis les tests. */
class FakeAccountRepository : AccountRepository {
    private val state = MutableStateFlow<SavedAccount?>(null)
    override val account: StateFlow<SavedAccount?> = state

    /** Résultat renvoyé par la prochaine connexion ; `null` = succès par défaut. */
    var nextResult: LoginResult? = null

    /** Si défini, la connexion attend ce signal (pour tester l'état « en cours »). */
    var gate: CompletableDeferred<Unit>? = null

    var xtreamLink: XtreamLinkCredentials? = null
    val calls = mutableListOf<String>()

    override suspend fun loginXtream(server: String, username: String, password: String): LoginResult {
        calls += "xtream $server $username"
        gate?.await()
        return finish(SavedAccount(AccountType.XTREAM, server, username, null, null, null))
    }

    override suspend fun loginM3u(url: String): LoginResult {
        calls += "m3u"
        gate?.await()
        return finish(SavedAccount(AccountType.M3U, "exemple.tv", null, null, null, 10))
    }

    override fun detectXtreamLink(url: String): XtreamLinkCredentials? = xtreamLink

    override suspend fun logout() {
        calls += "logout"
        state.value = null
    }

    fun setAccount(account: SavedAccount?) {
        state.value = account
    }

    private fun finish(account: SavedAccount): LoginResult {
        val r = nextResult ?: LoginResult.Success(account)
        if (r is LoginResult.Success) state.value = r.account
        return r
    }
}
