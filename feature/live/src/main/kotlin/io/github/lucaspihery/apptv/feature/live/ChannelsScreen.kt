package io.github.lucaspihery.apptv.feature.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.lucaspihery.apptv.core.data.account.AccountError
import io.github.lucaspihery.apptv.core.data.live.LiveChannel
import io.github.lucaspihery.apptv.core.designsystem.component.GlassButton
import io.github.lucaspihery.apptv.core.designsystem.component.GlassClickable
import io.github.lucaspihery.apptv.core.designsystem.glass.GlassBackground
import io.github.lucaspihery.apptv.core.designsystem.glass.GlassPanel
import io.github.lucaspihery.apptv.core.designsystem.theme.Glass
import io.github.lucaspihery.apptv.core.designsystem.theme.overscanPadding

object ChannelsTags {
    const val CHANNEL = "live.channel"
    const val CATEGORY = "live.category"
}

/**
 * Écran d'arrivée après la connexion : catégories à gauche, chaînes à droite.
 * [onPlay] reçoit la catégorie affichée et la position de la chaîne dans cette liste.
 */
@Composable
fun ChannelsRoute(
    onPlay: (categoryId: String?, index: Int) -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: ChannelsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChannelsScreen(
        state = state,
        onSelectCategory = viewModel::selectCategory,
        onPlay = onPlay,
        onRetry = { viewModel.load(refresh = true) },
        onLogout = { viewModel.logout(onLoggedOut) },
    )
}

@Composable
fun ChannelsScreen(
    state: ChannelsUiState,
    onSelectCategory: (String?) -> Unit,
    onPlay: (categoryId: String?, index: Int) -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
) {
    GlassBackground {
        Box(Modifier.fillMaxSize().overscanPadding()) {
            when {
                state.loading -> Message("Chargement des chaînes…")
                state.error != null -> ErrorPanel(state.error, onRetry, onLogout)
                else -> Content(state, onSelectCategory, onPlay, onLogout)
            }
        }
    }
}

@Composable
private fun Content(
    state: ChannelsUiState,
    onSelectCategory: (String?) -> Unit,
    onPlay: (categoryId: String?, index: Int) -> Unit,
    onLogout: () -> Unit,
) {
    val p = Glass.palette
    // Chaîne focalisée, conservée au retour du lecteur.
    var focusedIndex by rememberSaveable(state.selectedCategoryId) { mutableIntStateOf(0) }
    val channelFocus = remember { FocusRequester() }
    val channelList = rememberLazyListState(initialFirstVisibleItemIndex = focusedIndex)
    LaunchedEffect(state.selectedCategoryId) {
        if (state.channels.isNotEmpty() && focusedIndex < state.channels.size) {
            channelList.scrollToItem(focusedIndex)
        }
    }
    LaunchedEffect(Unit) {
        if (state.channels.isNotEmpty()) runCatching { channelFocus.requestFocus() }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        GlassPanel(Modifier.width(320.dp).fillMaxHeight(), contentPadding = 16.dp) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
            ) {
                itemsIndexed(state.categories, key = { _, c -> c.id ?: "\u0000all" }) { _, c ->
                    CategoryRow(
                        item = c,
                        selected = c.id == state.selectedCategoryId,
                        onFocused = { onSelectCategory(c.id) },
                        onClick = {
                            onSelectCategory(c.id)
                            runCatching { channelFocus.requestFocus() }
                        },
                    )
                }
                item(key = "\u0000logout") {
                    Spacer(Modifier.height(8.dp))
                    GlassButton(text = "Se déconnecter", onClick = onLogout)
                }
            }
        }
        GlassPanel(Modifier.weight(1f).fillMaxHeight(), contentPadding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        state.selectedCategoryName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = p.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        "${state.channels.size} chaînes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.contentMuted,
                    )
                }
                if (state.channels.isEmpty()) {
                    Text("Aucune chaîne dans cette catégorie.", color = p.contentMuted)
                } else {
                    LazyColumn(
                        state = channelList,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                    ) {
                        itemsIndexed(state.channels) { index, channel ->
                            ChannelRow(
                                channel = channel,
                                onClick = {
                                    focusedIndex = index
                                    onPlay(state.selectedCategoryId, index)
                                },
                                modifier = Modifier
                                    .then(if (index == focusedIndex.coerceAtMost(state.channels.lastIndex)) Modifier.focusRequester(channelFocus) else Modifier)
                                    .onFocusChanged { if (it.isFocused) focusedIndex = index },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(item: CategoryItem, selected: Boolean, onFocused: () -> Unit, onClick: () -> Unit) {
    val p = Glass.palette
    GlassClickable(
        onClick = onClick,
        selected = selected,
        focusedScale = 1.03f,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ChannelsTags.CATEGORY)
            .onFocusChanged { if (it.isFocused) onFocused() },
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                item.name,
                style = MaterialTheme.typography.labelLarge,
                color = Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(item.count.toString(), style = MaterialTheme.typography.labelMedium, color = p.contentMuted)
        }
    }
}

@Composable
private fun ChannelRow(channel: LiveChannel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = Glass.palette
    GlassClickable(
        onClick = onClick,
        focusedScale = 1.02f,
        modifier = modifier.fillMaxWidth().testTag(ChannelsTags.CHANNEL),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                channel.number?.toString().orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = p.contentMuted,
                modifier = Modifier.width(64.dp),
            )
            Text(
                channel.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = Glass.palette.content)
    }
}

@Composable
private fun ErrorPanel(error: AccountError, onRetry: () -> Unit, onLogout: () -> Unit) {
    val p = Glass.palette
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassPanel(Modifier.width(640.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Chaînes indisponibles", style = MaterialTheme.typography.headlineMedium, color = p.content)
                Text(error.liveMessage(), style = MaterialTheme.typography.bodyLarge, color = p.error)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton(text = "Réessayer", onClick = onRetry, modifier = Modifier.focusRequester(focus))
                    GlassButton(text = "Se déconnecter", onClick = onLogout)
                }
            }
        }
    }
}

/** Message affiché (les erreurs ne contiennent jamais de secret). */
internal fun AccountError.liveMessage(): String = when (this) {
    is AccountError.InvalidInput -> "Compte invalide : $reason."
    is AccountError.Network -> "Serveur injoignable. Vérifiez la connexion Internet."
    AccountError.Timeout -> "Le serveur ne répond pas (délai dépassé)."
    is AccountError.Http -> "Le serveur a répondu avec une erreur HTTP $status."
    AccountError.AuthFailed -> "Identifiants refusés par le serveur."
    is AccountError.AccountInactive -> "Ce compte n'est pas actif (statut : $status)."
    is AccountError.InvalidResponse -> "Réponse inattendue du serveur : $reason."
    AccountError.Storage -> "Impossible de lire le compte enregistré sur la TV."
}
