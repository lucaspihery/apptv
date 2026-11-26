package io.github.lucaspihery.apptv.feature.live

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.lucaspihery.apptv.core.data.core.CoreResult
import io.github.lucaspihery.apptv.core.data.live.LiveChannel
import io.github.lucaspihery.apptv.core.data.live.EpgRepository
import io.github.lucaspihery.apptv.core.data.live.FavoritesRepository
import io.github.lucaspihery.apptv.core.data.live.LiveRepository
import io.github.lucaspihery.apptv.core.data.live.Program
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val channel: LiveChannel? = null,
    /** Position dans la liste de zapping (1-based) et taille de cette liste. */
    val position: Int = 0,
    val total: Int = 0,
    val buffering: Boolean = true,
    val error: String? = null,
    /** Programme en cours puis suivants (guide), vide si inconnu. */
    val programs: List<Program> = emptyList(),
)

/**
 * Lecteur du direct. Arguments de navigation : `categoryId` (null = toutes) et
 * `index` dans la liste de cette catégorie. Haut/bas zappent dans cette liste.
 */
@OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    savedState: SavedStateHandle,
    private val live: LiveRepository,
    private val favorites: FavoritesRepository,
    private val epg: EpgRepository,
) : ViewModel() {

    private val categoryId: String? = savedState.get<String>(ARG_CATEGORY)?.takeIf { it.isNotEmpty() }
    private var index: Int = savedState.get<Int>(ARG_INDEX) ?: 0
    private var channels: List<LiveChannel> = emptyList()
    private var tuneJob: Job? = null
    private var epgJob: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    val player: ExoPlayer = run {
        // Beaucoup de serveurs Xtream redirigent de http vers https (ou l'inverse).
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(USER_AGENT)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .build()
            .apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        _state.update { it.copy(buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE) }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        // Le message d'ExoPlayer peut contenir l'URL : on n'affiche que le code.
                        _state.update { it.copy(buffering = false, error = "Lecture impossible (${error.errorCodeName}).") }
                    }
                })
            }
    }

    init {
        viewModelScope.launch {
            val catalog = (live.catalog() as? CoreResult.Ok)?.value
            channels = catalog?.playlist(categoryId, favorites.favorites.first()).orEmpty()
            if (channels.isEmpty()) {
                _state.update { it.copy(buffering = false, error = "Chaîne introuvable.") }
            } else {
                tune(index.coerceIn(0, channels.lastIndex))
            }
        }
    }

    fun next() = zap(+1)
    fun previous() = zap(-1)

    /** Relance la chaîne courante (après une erreur). */
    fun retry() {
        if (channels.isNotEmpty()) tune(index)
    }

    private fun zap(step: Int) {
        if (channels.size < 2) return
        tune(Math.floorMod(index + step, channels.size))
    }

    private fun tune(i: Int) {
        index = i
        val channel = channels[i]
        _state.update {
            PlayerUiState(
                channel = channel,
                position = i + 1,
                total = channels.size,
                buffering = true,
                programs = epg.cached(channel).orEmpty(),
            )
        }
        tuneJob?.cancel()
        epgJob?.cancel()
        epgJob = viewModelScope.launch {
            val programs = epg.programs(channel)
            _state.update { if (it.channel === channel) it.copy(programs = programs) else it }
        }
        tuneJob = viewModelScope.launch {
            val url = live.streamUrl(channel)
            if (url == null) {
                _state.update { it.copy(buffering = false, error = "Adresse du flux indisponible.") }
                return@launch
            }
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
        }
    }

    override fun onCleared() {
        player.release()
    }

    companion object {
        const val ARG_CATEGORY = "categoryId"
        const val ARG_INDEX = "index"
        private const val USER_AGENT = "apptv/0.1 (Android TV)"
    }
}
