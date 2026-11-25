package io.github.lucaspihery.apptv.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.lucaspihery.apptv.core.data.account.AccountRepository
import io.github.lucaspihery.apptv.core.data.account.AccountType
import io.github.lucaspihery.apptv.core.data.account.SavedAccount
import io.github.lucaspihery.apptv.core.designsystem.component.GlassButton
import io.github.lucaspihery.apptv.core.designsystem.glass.GlassBackground
import io.github.lucaspihery.apptv.core.designsystem.glass.GlassPanel
import io.github.lucaspihery.apptv.core.designsystem.theme.Glass
import io.github.lucaspihery.apptv.core.designsystem.theme.overscanPadding
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(private val accounts: AccountRepository) : ViewModel() {
    val account: StateFlow<SavedAccount?> =
        accounts.account.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            accounts.logout()
            onDone()
        }
    }
}

/** Accueil provisoire du jalon 1 : remplacé par la TV en direct au jalon 2. */
@Composable
fun HomeScreen(onLoggedOut: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val p = Glass.palette
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    GlassBackground {
        Box(Modifier.fillMaxSize().overscanPadding(), contentAlignment = Alignment.Center) {
            GlassPanel(Modifier.width(640.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Connecté", style = MaterialTheme.typography.displaySmall, color = p.content)
                    account?.let { Text(describe(it), style = MaterialTheme.typography.bodyLarge, color = p.contentMuted) }
                    Text(
                        "La TV en direct arrive au jalon 2.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.contentMuted,
                    )
                    GlassButton(
                        text = "Se déconnecter",
                        onClick = { viewModel.logout(onLoggedOut) },
                        modifier = Modifier.focusRequester(focus),
                    )
                }
            }
        }
    }
}

internal fun describe(a: SavedAccount): String = buildString {
    when (a.type) {
        AccountType.XTREAM -> append("Xtream : ${a.username} sur ${a.server}")
        AccountType.M3U -> append("Playlist M3U (${a.server}) : ${a.channelCount ?: 0} chaînes")
    }
    a.expiresAt?.let {
        append("\nExpire le ")
        append(DateFormat.getDateInstance(DateFormat.LONG).format(Date(it * 1000)))
    }
    a.maxConnections?.let { append("\nConnexions simultanées : $it") }
}
