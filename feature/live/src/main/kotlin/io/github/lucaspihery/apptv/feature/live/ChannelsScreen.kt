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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import io.github.lucaspihery.apptv.core.data.live.EpgRepository
import io.github.lucaspihery.apptv.core.data.live.LiveChannel
import io.github.lucaspihery.apptv.core.data.live.VirtualCategories
import io.github.lucaspihery.apptv.core.designsystem.component.GlassButton
import io.github.lucaspihery.apptv.core.designsystem.component.GlassClickable
import io.github.lucaspihery.apptv.core.designsystem.component.GlassTextField
import io.github.lucaspihery.apptv.core.designsystem.glass.GlassBackground
import io.github.lucaspihery.apptv.core.designsystem.glass.GlassPanel
import io.github.lucaspihery.apptv.core.designsystem.theme.Glass
import io.github.lucaspihery.apptv.core.designsystem.theme.overscanPadding

private const val ALL_KEY = "@all"
private const val LOGOUT_KEY = "@logout"
private const val SEARCH_KEY = "@search-entry"
private const val GUIDE_KEY = "@guide-entry"

object ChannelsTags {
    const val CHANNEL = "live.channel"
    const val CATEGORY = "live.category"
    const val SEARCH_FIELD = "live.search"
}

/**
 * Écran d'arrivée après la connexion : catégories à gauche (avec « Rechercher »,
 * « Guide TV » et « Mes chaînes »), chaînes à droite avec logo et programme en cours.
 * OK : regarder. Appui long sur OK : ajouter ou retirer de « Mes chaînes ».
 */
@Composable
fun ChannelsRoute(
    onPlay: (categoryId: String?, index: Int) -> Unit,
    onOpenGuide: (categoryId: String?) -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: ChannelsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChannelsScreen(
        state = state,
        epg = viewModel.epg,
        actions = ChannelsActions(
            focusCategory = viewModel::focusCategory,
            selectCategory = viewModel::selectCategory,
            initialIndex = viewModel::lastPlayedIndex,
            play = { index ->
                val (categoryId, position) = viewModel.playTarget(index)
                onPlay(categoryId, position)
            },
            toggleFavorite = viewModel::toggleFavorite,
            setQuery = viewModel::setQuery,
            openGuide = { onOpenGuide(state.selectedCategoryId?.takeIf { it != SEARCH }) },
            retry = { viewModel.load(refresh = true) },
            logout = { viewModel.logout(onLoggedOut) },
        ),
    )
}

/** Actions de l'écran, regroupées pour garder des paramètres stables. */
class ChannelsActions(
    val focusCategory: (String?) -> Unit,
    val selectCategory: (String?) -> Unit,
    val initialIndex: (categoryId: String?) -> Int,
    val play: (index: Int) -> Unit,
    val toggleFavorite: (LiveChannel) -> Unit,
    val setQuery: (String) -> Unit,
    val openGuide: () -> Unit,
    val retry: () -> Unit,
    val logout: () -> Unit,
)

@Composable
fun ChannelsScreen(state: ChannelsUiState, epg: EpgRepository, actions: ChannelsActions) {
    GlassBackground {
        Box(Modifier.fillMaxSize().overscanPadding()) {
            when {
                state.loading -> Message("Chargement des chaînes…")
                state.error != null -> ErrorPanel(state.error, actions.retry, actions.logout)
                else -> Content(state, epg, actions)
            }
        }
    }
}

@Composable
private fun Content(state: ChannelsUiState, epg: EpgRepository, actions: ChannelsActions) {
    val p = Glass.palette
    val categoryId = state.selectedCategoryId
    val channels = state.channels
    // Chaîne à focaliser à l'arrivée (ou au retour du lecteur). Calculée une fois par
    // catégorie : le focus qui se déplace dans la liste ne recompose rien.
    val target = remember(categoryId, channels) {
        actions.initialIndex(categoryId).coerceIn(0, (channels.size - 1).coerceAtLeast(0))
    }
    val channelFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val channelList = rememberLazyListState(initialFirstVisibleItemIndex = target)
    val now by rememberNowSeconds()
    LaunchedEffect(categoryId, channels) {
        if (channels.isNotEmpty()) channelList.scrollToItem(target)
    }
    LaunchedEffect(Unit) {
        if (channels.isNotEmpty()) runCatching { channelFocus.requestFocus() }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        GlassPanel(Modifier.width(320.dp).fillMaxHeight(), contentPadding = 16.dp) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
            ) {
                item(key = SEARCH_KEY, contentType = "entry") {
                    CategoryRow(
                        item = CategoryItem(SEARCH, "Rechercher", -1),
                        selected = categoryId == SEARCH,
                        onFocused = actions.focusCategory,
                        onClick = {
                            actions.selectCategory(SEARCH)
                            runCatching { searchFocus.requestFocus() }
                        },
                    )
                }
                if (state.hasGuide) {
                    item(key = GUIDE_KEY, contentType = "entry") {
                        CategoryRow(
                            item = CategoryItem(GUIDE_KEY, "Guide TV", -1),
                            selected = false,
                            onFocused = {},
                            onClick = actions.openGuide,
                        )
                    }
                }
                items(state.categories, key = { it.id ?: ALL_KEY }, contentType = { "category" }) { c ->
                    CategoryRow(
                        item = c,
                        selected = c.id == categoryId,
                        onFocused = actions.focusCategory,
                        onClick = {
                            actions.selectCategory(c.id)
                            runCatching { channelFocus.requestFocus() }
                        },
                    )
                }
                item(key = LOGOUT_KEY, contentType = "logout") {
                    Spacer(Modifier.height(8.dp))
                    GlassButton(text = "Se déconnecter", onClick = actions.logout)
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
                    if (categoryId != SEARCH || state.query.isNotBlank()) {
                        Text(
                            "${channels.size} chaînes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = p.contentMuted,
                        )
                    }
                }
                if (categoryId == SEARCH) {
                    GlassTextField(
                        value = state.query,
                        onValueChange = actions.setQuery,
                        label = "Nom de la chaîne",
                        placeholder = "OK pour écrire, par exemple « france 2 »",
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocus).testTag(ChannelsTags.SEARCH_FIELD),
                    )
                }
                when {
                    channels.isNotEmpty() -> {
                        val requester = Modifier.focusRequester(channelFocus)
                        LazyColumn(
                            state = channelList,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                        ) {
                            itemsIndexed(channels, contentType = { _, _ -> "channel" }) { index, channel ->
                                ChannelRow(
                                    channel = channel,
                                    favorite = channel.key in state.favorites,
                                    epg = epg,
                                    now = now,
                                    onClick = { actions.play(index) },
                                    onLongClick = { actions.toggleFavorite(channel) },
                                    modifier = if (index == target) requester else Modifier,
                                )
                            }
                        }
                    }
                    categoryId == SEARCH -> Hint(
                        if (state.query.isBlank()) "Tapez quelques lettres du nom de la chaîne."
                        else "Aucune chaîne ne correspond à « ${state.query} ».",
                    )
                    categoryId == VirtualCategories.FAVORITES -> Hint(
                        "Aucune chaîne dans « Mes chaînes ». Restez appuyé sur OK sur une chaîne pour l'ajouter.",
                    )
                    else -> Hint("Aucune chaîne dans cette catégorie.")
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = Glass.palette.contentMuted,
        modifier = Modifier.padding(8.dp),
    )
}

@Composable
private fun CategoryRow(item: CategoryItem, selected: Boolean, onFocused: (String?) -> Unit, onClick: () -> Unit) {
    val p = Glass.palette
    GlassClickable(
        onClick = onClick,
        selected = selected,
        focusedScale = 1.03f,
        focusedGlow = false,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ChannelsTags.CATEGORY)
            .onFocusChanged { if (it.isFocused) onFocused(item.id) },
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
            if (item.count >= 0) {
                Text(item.count.toString(), style = MaterialTheme.typography.labelMedium, color = p.contentMuted)
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: LiveChannel,
    favorite: Boolean,
    epg: EpgRepository,
    now: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = Glass.palette
    val programs by rememberPrograms(epg, channel)
    val current = programs?.firstOrNull { it.end > now }
    val next = programs?.firstOrNull { current != null && it.start >= current.end }
    GlassClickable(
        onClick = onClick,
        onLongClick = onLongClick,
        focusedScale = 1.02f,
        focusedGlow = false,
        modifier = modifier.fillMaxWidth().testTag(ChannelsTags.CHANNEL),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ChannelLogo(channel)
            Text(
                channel.number?.toString().orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = p.contentMuted,
                modifier = Modifier.width(48.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Unspecified,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (current != null) {
                    NowLine(current, now, Modifier.fillMaxWidth(0.9f))
                }
                if (next != null) {
                    Text(
                        "Ensuite ${formatTime(next.start)} · ${next.title}",
                        style = MaterialTheme.typography.labelMedium,
                        color = p.contentMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (favorite) {
                Text("★", style = MaterialTheme.typography.titleLarge, color = p.accent.color)
            }
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
