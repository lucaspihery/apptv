package io.github.lucaspihery.apptv.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.lucaspihery.apptv.core.data.core.CoreResult
import io.github.lucaspihery.apptv.core.data.live.EpgRepository
import io.github.lucaspihery.apptv.core.data.live.FavoritesRepository
import io.github.lucaspihery.apptv.core.data.live.LiveChannel
import io.github.lucaspihery.apptv.core.data.live.LiveRepository
import io.github.lucaspihery.apptv.core.data.live.Program
import io.github.lucaspihery.apptv.core.data.live.VirtualCategories
import io.github.lucaspihery.apptv.core.designsystem.component.GlassClickable
import io.github.lucaspihery.apptv.core.designsystem.glass.GlassBackground
import io.github.lucaspihery.apptv.core.designsystem.glass.GlassPanel
import io.github.lucaspihery.apptv.core.designsystem.theme.Glass
import io.github.lucaspihery.apptv.core.designsystem.theme.GlassShapes
import io.github.lucaspihery.apptv.core.designsystem.theme.overscanPadding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class GuideUiState(
    val loading: Boolean = true,
    val title: String = "",
    val channels: List<LiveChannel> = emptyList(),
)

@HiltViewModel
class GuideViewModel @Inject constructor(
    savedState: SavedStateHandle,
    live: LiveRepository,
    favorites: FavoritesRepository,
    val epg: EpgRepository,
) : ViewModel() {
    val categoryId: String? = savedState.get<String>(PlayerViewModel.ARG_CATEGORY)?.takeIf { it.isNotEmpty() }
    private val _state = MutableStateFlow(GuideUiState())
    val state: StateFlow<GuideUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val catalog = (live.catalog() as? CoreResult.Ok)?.value
            val channels = catalog?.playlist(categoryId, favorites.favorites.first()).orEmpty()
            val title = when (categoryId) {
                null -> ALL
                VirtualCategories.FAVORITES -> FAVORITES_NAME
                else -> catalog?.categories?.firstOrNull { it.id == categoryId }?.name.orEmpty()
            }
            _state.value = GuideUiState(loading = false, title = title, channels = channels)
        }
    }
}

/** Fenêtre affichée : 3 heures à partir de la demi-heure en cours. */
private const val WINDOW_S = 3 * 3600L
private const val SLOT_S = 30 * 60L
private val CHANNEL_COLUMN = 240.dp
private val ROW_HEIGHT = 64.dp

/**
 * Grille du guide pour une catégorie. Les programmes ne sont demandés que pour les
 * lignes visibles. OK sur un programme : regarder la chaîne.
 */
@Composable
fun GuideRoute(onPlay: (categoryId: String?, index: Int) -> Unit, viewModel: GuideViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GuideScreen(state, viewModel.epg) { index -> onPlay(viewModel.categoryId, index) }
}

@Composable
fun GuideScreen(state: GuideUiState, epg: EpgRepository, onPlay: (index: Int) -> Unit) {
    val p = Glass.palette
    val now by rememberNowSeconds()
    val windowStart = remember(now / SLOT_S) { now / SLOT_S * SLOT_S }
    var focused by remember { mutableStateOf<Pair<LiveChannel, Program?>?>(null) }
    val firstCell = remember { FocusRequester() }

    GlassBackground {
        Column(Modifier.fillMaxSize().overscanPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Guide TV", style = MaterialTheme.typography.headlineMedium, color = p.content)
                Text(state.title, style = MaterialTheme.typography.titleMedium, color = p.contentMuted, maxLines = 1)
            }
            GlassPanel(Modifier.fillMaxWidth().weight(1f), contentPadding = 12.dp) {
                when {
                    state.loading -> Text("Chargement…", color = p.contentMuted)
                    state.channels.isEmpty() -> Text("Aucune chaîne dans cette catégorie.", color = p.contentMuted)
                    else -> Column {
                        TimeRuler(windowStart)
                        LaunchedEffect(Unit) { runCatching { firstCell.requestFocus() } }
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(vertical = 6.dp),
                        ) {
                            itemsIndexed(state.channels, contentType = { _, _ -> "row" }) { index, channel ->
                                GuideRow(
                                    channel = channel,
                                    epg = epg,
                                    windowStart = windowStart,
                                    now = now,
                                    firstCellModifier = if (index == 0) Modifier.focusRequester(firstCell) else Modifier,
                                    onFocused = { focused = channel to it },
                                    onClick = { onPlay(index) },
                                )
                            }
                        }
                    }
                }
            }
            Details({ focused }, Modifier.fillMaxWidth().height(96.dp))
        }
    }
}

@Composable
private fun TimeRuler(windowStart: Long) {
    val p = Glass.palette
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Box(Modifier.width(CHANNEL_COLUMN))
        BoxWithConstraints(Modifier.weight(1f)) {
            val slots = (WINDOW_S / SLOT_S).toInt()
            for (i in 0 until slots) {
                Text(
                    formatTime(windowStart + i * SLOT_S),
                    style = MaterialTheme.typography.labelMedium,
                    color = p.contentMuted,
                    modifier = Modifier.offset(x = maxWidth * i / slots),
                )
            }
        }
    }
}

@Composable
private fun GuideRow(
    channel: LiveChannel,
    epg: EpgRepository,
    windowStart: Long,
    now: Long,
    firstCellModifier: Modifier,
    onFocused: (Program?) -> Unit,
    onClick: () -> Unit,
) {
    val p = Glass.palette
    val programs by rememberPrograms(epg, channel, EpgRepository.GRID)
    val windowEnd = windowStart + WINDOW_S
    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.width(CHANNEL_COLUMN).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChannelLogo(channel, 56.dp, 36.dp)
            Text(
                channel.name,
                style = MaterialTheme.typography.labelLarge,
                color = p.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val perSecond: Dp = maxWidth / WINDOW_S.toFloat()
            val visible = programs.orEmpty().filter { it.end > windowStart && it.start < windowEnd }
            if (visible.isEmpty()) {
                ProgramCell(
                    title = if (programs == null) "…" else "Pas de guide pour cette chaîne",
                    subtitle = null,
                    onAir = true,
                    modifier = firstCellModifier.fillMaxWidth().fillMaxHeight(),
                    onFocused = { onFocused(null) },
                    onClick = onClick,
                )
            } else {
                visible.forEachIndexed { i, program ->
                    val from = maxOf(program.start, windowStart)
                    val to = minOf(program.end, windowEnd)
                    ProgramCell(
                        title = program.title,
                        subtitle = "${formatTime(program.start)} – ${formatTime(program.end)}",
                        onAir = program.isOnAir(now),
                        modifier = (if (i == 0) firstCellModifier else Modifier)
                            .offset(x = perSecond * (from - windowStart).toFloat())
                            .width((perSecond * (to - from).toFloat() - 4.dp).coerceAtLeast(8.dp))
                            .fillMaxHeight(),
                        onFocused = { onFocused(program) },
                        onClick = onClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramCell(
    title: String,
    subtitle: String?,
    onAir: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val p = Glass.palette
    GlassClickable(
        onClick = onClick,
        selected = onAir,
        shape = GlassShapes.control,
        focusedScale = 1.0f,
        focusedGlow = false,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocused() },
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = Color.Unspecified, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = p.contentMuted, maxLines = 1)
            }
        }
    }
}

/** Détails du programme focalisé (seule cette zone se recompose quand le focus bouge). */
@Composable
private fun Details(focused: () -> Pair<LiveChannel, Program?>?, modifier: Modifier) {
    val p = Glass.palette
    Column(modifier.background(Color.Transparent).padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val (channel, program) = focused() ?: return@Column
        Text(
            buildString {
                append(channel.name)
                if (program != null) append(" · ${formatTime(program.start)} – ${formatTime(program.end)}")
            },
            style = MaterialTheme.typography.labelLarge,
            color = p.contentMuted,
        )
        Text(program?.title ?: "Pas de guide", style = MaterialTheme.typography.titleMedium, color = p.content, maxLines = 1)
        program?.description?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = p.contentMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
