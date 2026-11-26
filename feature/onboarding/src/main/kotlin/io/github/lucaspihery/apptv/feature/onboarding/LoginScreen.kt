package io.github.lucaspihery.apptv.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.lucaspihery.apptv.core.designsystem.component.GlassButton
import io.github.lucaspihery.apptv.core.designsystem.component.GlassTextField
import io.github.lucaspihery.apptv.core.designsystem.glass.GlassBackground
import io.github.lucaspihery.apptv.core.designsystem.glass.GlassPanel
import io.github.lucaspihery.apptv.core.designsystem.theme.Glass
import io.github.lucaspihery.apptv.core.designsystem.theme.overscanPadding

object LoginTags {
    const val MODE_XTREAM = "login.mode.xtream"
    const val MODE_M3U = "login.mode.m3u"
    const val SERVER = "login.server"
    const val USERNAME = "login.username"
    const val PASSWORD = "login.password"
    const val M3U_URL = "login.m3u"
    const val SUBMIT = "login.submit"
    const val ERROR = "login.error"
}

@Composable
fun LoginRoute(onLoggedIn: () -> Unit, viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.loggedIn) { if (state.loggedIn) onLoggedIn() }
    LoginScreen(
        state = state,
        onModeChange = viewModel::setMode,
        onServerChange = viewModel::setServer,
        onUsernameChange = viewModel::setUsername,
        onPasswordChange = viewModel::setPassword,
        onM3uUrlChange = viewModel::setM3uUrl,
        onUseXtreamLink = viewModel::useDetectedXtreamLink,
        onSubmit = viewModel::submit,
    )
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    onModeChange: (LoginMode) -> Unit,
    onServerChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onM3uUrlChange: (String) -> Unit,
    onUseXtreamLink: () -> Unit,
    onSubmit: () -> Unit,
) {
    val p = Glass.palette
    val firstField = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstField.requestFocus() } }

    // Deux colonnes : sur un écran 960×540 dp, une seule colonne dépassait du panneau
    // et masquait le bouton « Se connecter ».
    GlassBackground {
        Box(Modifier.fillMaxSize().overscanPadding(), contentAlignment = Alignment.Center) {
            GlassPanel(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Connexion", style = MaterialTheme.typography.displaySmall, color = p.content)
                        Text(
                            "Vos identifiants restent sur cette TV, chiffrés.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = p.contentMuted,
                        )
                        GlassButton(
                            text = "Xtream Codes",
                            onClick = { onModeChange(LoginMode.XTREAM) },
                            selected = state.mode == LoginMode.XTREAM,
                            modifier = Modifier.testTag(LoginTags.MODE_XTREAM),
                        )
                        GlassButton(
                            text = "Lien M3U",
                            onClick = { onModeChange(LoginMode.M3U) },
                            selected = state.mode == LoginMode.M3U,
                            modifier = Modifier.testTag(LoginTags.MODE_M3U),
                        )
                    }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (state.mode) {
                            LoginMode.XTREAM -> {
                                GlassTextField(
                                    value = state.server,
                                    onValueChange = onServerChange,
                                    label = "Serveur",
                                    placeholder = "http://exemple.tv:8080",
                                    keyboardType = KeyboardType.Uri,
                                    modifier = Modifier.fillMaxWidth().focusRequester(firstField).testTag(LoginTags.SERVER),
                                )
                                GlassTextField(
                                    value = state.username,
                                    onValueChange = onUsernameChange,
                                    label = "Identifiant",
                                    modifier = Modifier.fillMaxWidth().testTag(LoginTags.USERNAME),
                                )
                                GlassTextField(
                                    value = state.password,
                                    onValueChange = onPasswordChange,
                                    label = "Mot de passe",
                                    isPassword = true,
                                    imeAction = ImeAction.Done,
                                    modifier = Modifier.fillMaxWidth().testTag(LoginTags.PASSWORD),
                                )
                            }
                            LoginMode.M3U -> {
                                GlassTextField(
                                    value = state.m3uUrl,
                                    onValueChange = onM3uUrlChange,
                                    label = "Lien de la playlist M3U",
                                    placeholder = "http://…/playlist.m3u",
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Done,
                                    modifier = Modifier.fillMaxWidth().focusRequester(firstField).testTag(LoginTags.M3U_URL),
                                )
                                if (state.xtreamLinkDetected) {
                                    Text(
                                        "Ce lien correspond à un compte Xtream : la connexion Xtream donne accès aux catégories, à l'EPG et à la VOD.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = p.contentMuted,
                                    )
                                    GlassButton(text = "Utiliser la connexion Xtream", onClick = onUseXtreamLink)
                                }
                            }
                        }
                        state.error?.let {
                            Text(
                                it.userMessage(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = p.error,
                                modifier = Modifier.testTag(LoginTags.ERROR),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        GlassButton(
                            text = if (state.loading) "Connexion…" else "Se connecter",
                            onClick = onSubmit,
                            enabled = state.canSubmit,
                            modifier = Modifier.testTag(LoginTags.SUBMIT),
                        )
                    }
                }
            }
        }
    }
}
