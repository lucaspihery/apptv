package io.github.lucaspihery.apptv.feature.live

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.lucaspihery.apptv.core.data.account.AccountError
import io.github.lucaspihery.apptv.core.data.live.EpgRepository
import io.github.lucaspihery.apptv.core.data.live.LiveChannel
import io.github.lucaspihery.apptv.core.data.live.StreamQuality
import io.github.lucaspihery.apptv.core.data.live.VirtualCategories
import io.github.lucaspihery.apptv.core.designsystem.component.GlassButton
import io.github.lucaspihery.apptv.core.designsystem.component.GlassClickable
import io.github.lucaspihery.apptv.core.designsystem.component.GlassTextField
import io.github.lucaspihery.apptv.core.designsystem.glass.GlassBackground
import io.github.lucaspihery.apptv.core.designsystem.glass.GlassPanel
import io.github.lucaspihery.apptv.core.designsystem.icon.AppIcons
import io.github.lucaspihery.apptv.core.designsystem.theme.Glass
import io.github.lucaspihery.apptv.core.designsystem.theme.GlassShapes
import io.github.lucaspihery.apptv.core.designsystem.theme.overscanPadding

private const val ALL_KEY = "@all"

object ChannelsTags {
    const val CHANNEL = "live.channel"
    const val CATEGORY = "live.category"
    const val SEARCH_FIELD = "live.search"
    const val TOP_SEARCH = "live.top.search"
    const val TOP_SETTINGS = "live.top.settings"
    const val TOP_ACCOUNT = "live.top.account"
    const val RAIL = "live.rail"
}

/**
 * Écran d'arrivée après la connexion (menu principal) : barre du haut, rail d'icônes,
 * catégories, puis chaînes avec logo et programme en cours.
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
            clearHistory = viewModel::clearHistory,
            openGuide = { onOpenGuide(state.selectedCategoryId?.takeIf { it != SEARCH && it != VirtualCategories.HISTORY }) },
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
    val clearHistory: () -> Unit = {},
)

/** Panneau ouvert depuis les boutons en haut à droite. */
private enum class Sheet { NONE, ACCOUNT, SETTINGS }

/**
 * Menu principal : nom de l'appli en haut à gauche, Recherche / Paramètres / Compte en haut
 * à droite, petit rail d'icônes à gauche (Direct, Mes chaînes, Historique, Guide TV), puis les
 * catégories et les chaînes.
 */
@Composable
fun ChannelsScreen(state: ChannelsUiState, epg: EpgRepository, actions: ChannelsActions) {
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    val searchFocus = remember { FocusRequester() }
    val channelFocus = remember { FocusRequester() }
    val openSearch = {
        sheet = Sheet.NONE
        actions.selectCategory(SEARCH)
        runCatching { searchFocus.requestFocus() }
        Unit
    }
    GlassBackground {
        Column(Modifier.fillMaxSize().overscanPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TopBar(
                searchSelected = state.selectedCategoryId == SEARCH && sheet == Sheet.NONE,
                sheet = sheet,
                onSearch = openSearch,
                onSettings = { sheet = if (sheet == Sheet.SETTINGS) Sheet.NONE else Sheet.SETTINGS },
                onAccount = { sheet = if (sheet == Sheet.ACCOUNT) Sheet.NONE else Sheet.ACCOUNT },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    // Le panneau remplace le contenu : le D-pad ne peut pas s'en échapper vers une liste cachée.
                    sheet != Sheet.NONE -> SheetPanel(sheet, actions.logout, close = { sheet = Sheet.NONE })
                    state.loading -> Message("Chargement des chaînes…")
                    state.error != null -> ErrorPanel(state.error, actions.retry, actions.logout)
                    else -> Content(state, epg, actions, channelFocus, searchFocus)
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    searchSelected: Boolean,
    sheet: Sheet,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onAccount: () -> Unit,
) {
    val p = Glass.palette
    val now by rememberNowSeconds()
    Row(
        Modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            buildAnnotatedString {
                append("App")
                withStyle(SpanStyle(color = p.accent.color)) { append("TV") }
            },
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
            color = p.content,
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(formatTime(now), style = MaterialTheme.typography.labelLarge, color = p.contentMuted)
        Spacer(Modifier.width(4.dp))
        RoundIconButton(AppIcons.Search, "Rechercher", searchSelected, onSearch, Modifier.testTag(ChannelsTags.TOP_SEARCH))
        RoundIconButton(AppIcons.Settings, "Paramètres", sheet == Sheet.SETTINGS, onSettings, Modifier.testTag(ChannelsTags.TOP_SETTINGS))
        RoundIconButton(AppIcons.Account, "Compte", sheet == Sheet.ACCOUNT, onAccount, Modifier.testTag(ChannelsTags.TOP_ACCOUNT))
    }
}

/** Bouton rond à icône (barre du haut et rail latéral). */
@Composable
private fun RoundIconButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    GlassClickable(
        onClick = onClick,
        selected = selected,
        shape = CircleShape,
        focusedGlow = false,
        quiet = true,
        modifier = modifier.size(size),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(size * 0.5f).align(Alignment.Center))
    }
}

/** Petit rail d'icônes à gauche : Direct, Mes chaînes, Historique, Guide TV. */
@Composable
private fun SideRail(
    state: ChannelsUiState,
    actions: ChannelsActions,
    channelFocus: FocusRequester,
) {
    val categoryId = state.selectedCategoryId
    // Icônes posées sur le fond, sans cadre ; seule l'entrée active est marquée.
    Box(Modifier.width(48.dp).fillMaxHeight()) {
        Column(
            Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val live = categoryId != SEARCH && categoryId != VirtualCategories.FAVORITES && categoryId != VirtualCategories.HISTORY
            RailItem(AppIcons.Live, "Direct", selected = live) {
                actions.selectCategory(null)
                runCatching { channelFocus.requestFocus() }
            }
            RailItem(AppIcons.Favorites, FAVORITES_NAME, selected = categoryId == VirtualCategories.FAVORITES) {
                actions.selectCategory(VirtualCategories.FAVORITES)
                runCatching { channelFocus.requestFocus() }
            }
            RailItem(AppIcons.History, HISTORY_NAME, selected = categoryId == VirtualCategories.HISTORY) {
                actions.selectCategory(VirtualCategories.HISTORY)
                runCatching { channelFocus.requestFocus() }
            }
            if (state.hasGuide) {
                RailItem(AppIcons.Guide, "Guide TV", selected = false, onClick = actions.openGuide)
            }
        }
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RoundIconButton(icon, label, selected, onClick, Modifier.testTag(ChannelsTags.RAIL), size = 40.dp)
        // Trait d'accent sous l'entrée active, comme un onglet.
        Box(
            Modifier
                .size(width = 14.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) Glass.palette.accent.color else Color.Transparent),
        )
    }
}

@Composable
private fun Content(
    state: ChannelsUiState,
    epg: EpgRepository,
    actions: ChannelsActions,
    channelFocus: FocusRequester,
    searchFocus: FocusRequester,
) {
    val p = Glass.palette
    val categoryId = state.selectedCategoryId
    val channels = state.channels
    // Chaîne à focaliser à l'arrivée (ou au retour du lecteur). Calculée une fois par
    // catégorie : le focus qui se déplace dans la liste ne recompose rien.
    val target = remember(categoryId, channels) {
        actions.initialIndex(categoryId).coerceIn(0, (channels.size - 1).coerceAtLeast(0))
    }
    val channelList = rememberLazyListState(initialFirstVisibleItemIndex = target)
    val now by rememberNowSeconds()
    LaunchedEffect(categoryId, channels) {
        if (channels.isNotEmpty()) channelList.scrollToItem(target)
    }
    LaunchedEffect(Unit) {
        if (channels.isNotEmpty() && categoryId != SEARCH) runCatching { channelFocus.requestFocus() }
    }
    // Le champ de recherche n'existe qu'une fois la catégorie « recherche » affichée.
    LaunchedEffect(categoryId == SEARCH) {
        if (categoryId == SEARCH) runCatching { searchFocus.requestFocus() }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SideRail(state, actions, channelFocus)
        Box(Modifier.width(240.dp).fillMaxHeight()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Catégories",
                    style = MaterialTheme.typography.labelMedium,
                    color = p.contentMuted,
                    modifier = Modifier.padding(start = 10.dp, top = 4.dp),
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
                ) {
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
                }
            }
        }
        // Simple filet entre les catégories et les chaînes, plutôt qu'un cadre autour de chaque colonne.
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .padding(vertical = 8.dp)
                .background(Color.White.copy(alpha = 0.10f)),
        )
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 2.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        state.selectedCategoryName,
                        style = MaterialTheme.typography.titleLarge,
                        color = p.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (categoryId != SEARCH || state.query.isNotBlank()) {
                        Text(
                            "${channels.size} chaînes",
                            style = MaterialTheme.typography.labelMedium,
                            color = p.contentMuted,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (categoryId == VirtualCategories.HISTORY && channels.isNotEmpty()) {
                        GlassClickable(onClick = actions.clearHistory, shape = GlassShapes.pill, focusedGlow = false) {
                            Text(
                                "Effacer",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Unspecified,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                        }
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
                            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 6.dp),
                        ) {
                            itemsIndexed(channels, contentType = { _, _ -> "channel" }) { index, channel ->
                                ChannelRow(
                                    channel = channel,
                                    favorite = channel.key in state.favorites,
                                    quality = state.qualities[channel.key],
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
                    categoryId == VirtualCategories.HISTORY -> Hint(
                        "Aucune chaîne regardée pour l'instant. Les chaînes regardées plus de 5 secondes apparaissent ici.",
                    )
                    else -> Hint("Aucune chaîne dans cette catégorie.")
                }
            }
        }
    }
}

/** Panneau « Compte » ou « Paramètres ». Retour le ferme. */
@Composable
private fun SheetPanel(sheet: Sheet, onLogout: () -> Unit, close: () -> Unit) {
    val p = Glass.palette
    val focus = remember { FocusRequester() }
    LaunchedEffect(sheet) { runCatching { focus.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent {
                if (it.key != Key.Back) return@onPreviewKeyEvent false
                if (it.type == KeyEventType.KeyUp) close()
                true
            },
        contentAlignment = Alignment.TopEnd,
    ) {
        GlassPanel(Modifier.width(420.dp), shape = GlassShapes.card, contentPadding = 24.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (sheet) {
                    Sheet.ACCOUNT -> {
                        Text("Compte", style = MaterialTheme.typography.headlineMedium, color = p.content)
                        Text(
                            "Vos identifiants sont enregistrés sur cette TV, chiffrés.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = p.contentMuted,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassButton(text = "Se déconnecter", onClick = onLogout, modifier = Modifier.focusRequester(focus))
                            GlassButton(text = "Fermer", onClick = close)
                        }
                    }
                    else -> {
                        Text("Paramètres", style = MaterialTheme.typography.headlineMedium, color = p.content)
                        Text(
                            "Thème, effets réduits et réglages du lecteur arrivent dans une prochaine version.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = p.contentMuted,
                        )
                        GlassButton(text = "Fermer", onClick = close, modifier = Modifier.focusRequester(focus))
                    }
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
        quiet = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ChannelsTags.CATEGORY)
            .onFocusChanged { if (it.isFocused) onFocused(item.id) },
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
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
    quality: StreamQuality?,
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
        quiet = true,
        modifier = modifier.fillMaxWidth().testTag(ChannelsTags.CHANNEL),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChannelLogo(channel, width = 56.dp, height = 34.dp)
            Text(
                channel.number?.toString().orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = p.contentMuted,
                modifier = Modifier.width(32.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Qualité vue par le lecteur, sinon celle annoncée par le nom (« FHD », « 4K », « 50 fps »).
                val shown = quality ?: remember(channel.name) { StreamQuality.fromName(channel.name) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Unspecified,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    QualityBadges(shown)
                }
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
                Text("★", style = MaterialTheme.typography.titleMedium, color = p.accent.color)
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
