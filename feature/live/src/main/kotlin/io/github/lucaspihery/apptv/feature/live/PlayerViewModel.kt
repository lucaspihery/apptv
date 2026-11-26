package io.github.lucaspihery.apptv.feature.live

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
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
import io.github.lucaspihery.apptv.core.data.live.StreamQuality
import io.github.lucaspihery.apptv.core.data.live.WatchHistoryRepository
import kotlinx.coroutines.delay
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
    /** Qualité mesurée du flux en cours (définition, images par seconde), `null` avant la mesure. */
    val quality: StreamQuality? = null,
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
    private val history: WatchHistoryRepository,
) : ViewModel() {

    private val categoryId: String? = savedState.get<String>(ARG_CATEGORY)?.takeIf { it.isNotEmpty() }
    private var index: Int = savedState.get<Int>(ARG_INDEX) ?: 0
    private var channels: List<LiveChannel> = emptyList()
    private var tuneJob: Job? = null
    private var epgJob: Job? = null
    private var historyJob: Job? = null
    private var measureJob: Job? = null

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

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) measureQuality() else measureJob?.cancel()
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        // Changement de définition en cours de route (HLS adaptatif) : nouvelle mesure.
                        if (videoSize.height > 0 && _state.value.quality?.height != videoSize.height && isPlaying) measureQuality()
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
            channels = catalog?.playlist(categoryId, favorites.favorites.first(), history.history.first()).orEmpty()
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
        measureJob?.cancel()
        historyJob?.cancel()
        // Dans l'historique seulement si on reste sur la chaîne : un zapping rapide ne le remplit pas.
        historyJob = viewModelScope.launch {
            delay(HISTORY_AFTER_MS)
            history.recordWatched(channel)
        }
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

    /**
     * Mesure la qualité réelle : hauteur de l'image décodée et cadence. La cadence vient du
     * format si le flux l'annonce, sinon du nombre d'images affichées pendant quelques secondes.
     */
    private fun measureQuality() {
        val channel = _state.value.channel ?: return
        measureJob?.cancel()
        measureJob = viewModelScope.launch {
            delay(MEASURE_SETTLE_MS)
            val start = renderedFrames() ?: return@launch
            val t0 = SystemClock.elapsedRealtime()
            delay(MEASURE_WINDOW_MS)
            val end = renderedFrames() ?: return@launch
            val elapsed = SystemClock.elapsedRealtime() - t0
            val height = player.videoSize.height.takeIf { it > 0 } ?: player.videoFormat?.height ?: return@launch
            val announced = player.videoFormat?.frameRate?.takeIf { it != Format.NO_VALUE.toFloat() && it > 0f }
            val counted = if (elapsed > 0) (end - start) * 1000f / elapsed else Float.NaN
            val quality = StreamQuality(height, StreamQuality.snapFps(announced ?: counted), measured = true)
            if (_state.value.channel !== channel) return@launch
            _state.update { it.copy(quality = quality) }
            history.recordQuality(channel, quality)
        }
    }

    private fun renderedFrames(): Int? = player.videoDecoderCounters?.run {
        ensureUpdated()
        renderedOutputBufferCount
    }

    override fun onCleared() {
        player.release()
    }

    companion object {
        const val ARG_CATEGORY = "categoryId"
        const val ARG_INDEX = "index"
        private const val USER_AGENT = "apptv/0.1 (Android TV)"
        private const val HISTORY_AFTER_MS = 5_000L
        private const val MEASURE_SETTLE_MS = 1_500L
        private const val MEASURE_WINDOW_MS = 3_000L
    }
}
