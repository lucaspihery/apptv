package io.github.lucaspihery.apptv.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.lucaspihery.apptv.core.data.account.AccountError
import io.github.lucaspihery.apptv.core.data.account.AccountRepository
import io.github.lucaspihery.apptv.core.data.account.LoginResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LoginMode { XTREAM, M3U }

data class LoginUiState(
    val mode: LoginMode = LoginMode.XTREAM,
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
    val loading: Boolean = false,
    val error: AccountError? = null,
    /** Le lien M3U saisi est un lien Xtream : proposer la connexion Xtream. */
    val xtreamLinkDetected: Boolean = false,
    val loggedIn: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !loading && when (mode) {
            LoginMode.XTREAM -> server.isNotBlank() && username.isNotBlank() && password.isNotEmpty()
            LoginMode.M3U -> m3uUrl.isNotBlank()
        }

    /** Le mot de passe n'apparaît jamais dans les logs, même via `toString`. */
    override fun toString(): String =
        "LoginUiState(mode=$mode, server=$server, username=$username, password=***, " +
            "m3uUrl=${if (m3uUrl.isEmpty()) "" else "***"}, loading=$loading, error=$error, " +
            "xtreamLinkDetected=$xtreamLinkDetected, loggedIn=$loggedIn)"
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val accounts: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun setMode(mode: LoginMode) = _state.update { it.copy(mode = mode, error = null) }
    fun setServer(v: String) = _state.update { it.copy(server = v, error = null) }
    fun setUsername(v: String) = _state.update { it.copy(username = v, error = null) }
    fun setPassword(v: String) = _state.update { it.copy(password = v, error = null) }

    fun setM3uUrl(v: String) = _state.update {
        it.copy(m3uUrl = v, error = null, xtreamLinkDetected = accounts.detectXtreamLink(v) != null)
    }

    /** Remplit le formulaire Xtream à partir du lien `get.php` collé en M3U. */
    fun useDetectedXtreamLink() {
        val link = accounts.detectXtreamLink(_state.value.m3uUrl) ?: return
        _state.update {
            it.copy(
                mode = LoginMode.XTREAM,
                server = link.server,
                username = link.username,
                password = link.password,
                xtreamLinkDetected = false,
                error = null,
            )
        }
    }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = when (s.mode) {
                LoginMode.XTREAM -> accounts.loginXtream(s.server, s.username, s.password)
                LoginMode.M3U -> accounts.loginM3u(s.m3uUrl)
            }
            _state.update {
                when (result) {
                    is LoginResult.Success -> it.copy(loading = false, loggedIn = true, password = "")
                    is LoginResult.Failure -> it.copy(loading = false, error = result.error)
                }
            }
        }
    }
}
