package io.github.lucaspihery.apptv.core.designsystem.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.lucaspihery.apptv.core.designsystem.theme.Glass
import io.github.lucaspihery.apptv.core.designsystem.theme.GlassEffects

/** Arrière-plan courant et taille (px) de la zone qu'il couvre. */
@Immutable
class BackdropLayer(val backdrop: Backdrop, val size: IntSize)

/** Lu par les panneaux de verre. `null` en effets réduits. */
val LocalBackdrop = staticCompositionLocalOf<BackdropLayer?> { null }

/**
 * Fond coloré des écrans de navigation (jamais pendant la lecture vidéo :
 * le plein écran doit laisser la fenêtre transparente, voir CLAUDE.md).
 */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val palette = Glass.palette
    val effects = Glass.effects
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.roundToPx() }
        val hPx = with(density) { maxHeight.roundToPx() }
        val backdrop = remember(wPx, hPx, palette, effects) {
            if (effects == GlassEffects.REDUCED || wPx <= 0 || hPx <= 0) null
            else BackdropRenderer.render(wPx, hPx, palette)
        }
        if (backdrop != null) {
            Canvas(Modifier.fillMaxSize()) {
                drawImage(
                    image = backdrop.background,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = FilterQuality.Low,
                )
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(palette.backgroundStart, palette.backgroundEnd))),
            )
        }
        val layer = remember(backdrop, wPx, hPx) { backdrop?.let { BackdropLayer(it, IntSize(wPx, hPx)) } }
        CompositionLocalProvider(LocalBackdrop provides layer) {
            Box(Modifier.fillMaxSize(), content = content)
        }
    }
}
