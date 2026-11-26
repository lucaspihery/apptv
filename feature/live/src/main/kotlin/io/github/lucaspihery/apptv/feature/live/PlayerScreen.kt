package io.github.lucaspihery.apptv.feature.live

import android.app.Activity
import androidx.core.graphics.drawable.toDrawable
import android.view.SurfaceView
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/**
 * Lecture plein écran. La vidéo est dans une `SurfaceView` (résolution native, HDR) ;
 * pendant la lecture, le fond de la fenêtre est vidé et rien d'opaque n'est dessiné
 * sous l'image (piège TCL MediaTek : son sans image). Pas de `GlassBackground` ici.
 *
 * Télécommande : haut / bas ou CH+ / CH- pour zapper, OK pour afficher le bandeau,
 * retour pour revenir à la liste.
 */
@Composable
fun PlayerRoute(viewModel: PlayerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity

    DisposableEffect(activity) {
        val window = activity?.window
        val previous = window?.decorView?.background
        window?.setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.setBackgroundDrawable(previous)
        }
    }

    // Le bandeau s'affiche à chaque changement de chaîne ou sur OK, puis disparaît.
    var bannerTick by remember { mutableIntStateOf(0) }
    var bannerVisible by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.channel, bannerTick) {
        bannerVisible = 1
        delay(4_000)
        bannerVisible = 0
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp -> { viewModel.next(); true }
                    Key.DirectionDown, Key.ChannelDown -> { viewModel.previous(); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (state.error != null) viewModel.retry()
                        bannerTick++
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx -> SurfaceView(ctx).also { viewModel.player.setVideoSurfaceView(it) } },
            onRelease = { viewModel.player.clearVideoSurfaceView(it) },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = bannerVisible == 1 || state.error != null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerBanner(state)
        }
    }
}

@Composable
internal fun PlayerBanner(state: PlayerUiState) {
    // Dégradé sombre simple : jamais de flou ni de verre sur la vidéo.
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f)))
            .padding(start = 64.dp, end = 64.dp, top = 64.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            state.channel?.let { ChannelLogo(it, 96.dp, 56.dp) }
            state.channel?.number?.let {
                Text(it.toString(), style = MaterialTheme.typography.headlineMedium, color = Color.White.copy(alpha = 0.7f))
            }
            Text(state.channel?.name.orEmpty(), style = MaterialTheme.typography.headlineMedium, color = Color.White)
            QualityBadges(state.quality)
        }
        val now = System.currentTimeMillis() / 1000
        val current = state.programs.firstOrNull { it.end > now }
        val next = state.programs.firstOrNull { current != null && it.start >= current.end }
        if (current != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatTime(current.start)} – ${formatTime(current.end)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Text(current.title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1)
            }
            ProgressBar(current.progress(now), Modifier.width(480.dp).height(4.dp))
            if (next != null) {
                Text(
                    "Ensuite ${formatTime(next.start)} · ${next.title}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                )
            }
        }
        val status = when {
            state.error != null -> "${state.error} OK pour réessayer."
            state.buffering -> "Chargement…"
            else -> "Haut / bas pour changer de chaîne"
        }
        Text(
            "$status   ·   ${state.position} / ${state.total}",
            style = MaterialTheme.typography.bodyLarge,
            color = if (state.error != null) Color(0xFFFF8A80) else Color.White.copy(alpha = 0.75f),
        )
    }
}
