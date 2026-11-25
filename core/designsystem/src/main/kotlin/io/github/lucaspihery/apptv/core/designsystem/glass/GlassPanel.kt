package io.github.lucaspihery.apptv.core.designsystem.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.lucaspihery.apptv.core.designsystem.theme.Glass
import io.github.lucaspihery.apptv.core.designsystem.theme.GlassPalette
import io.github.lucaspihery.apptv.core.designsystem.theme.GlassShapes

/** Bordure spéculaire : claire en haut à gauche, discrète en bas à droite. */
fun specularBorder(palette: GlassPalette, width: Dp = 1.dp, boost: Float = 1f): BorderStroke =
    BorderStroke(
        width,
        Brush.linearGradient(
            0f to palette.rimStrong.copy(alpha = (palette.rimStrong.alpha * boost).coerceAtMost(1f)),
            0.45f to palette.rimWeak,
            1f to palette.rimWeak.copy(alpha = (palette.rimWeak.alpha * 1.8f * boost).coerceAtMost(1f)),
        ),
    )

/**
 * Panneau de verre statique (non focalisable).
 *
 * Couches, du fond vers l'avant : ombre douce → image de fond floutée
 * précalculée (à la position du panneau) → voile translucide → lueur intérieure
 * en haut → bordure spéculaire. Aucun flou en direct.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShapes.panel,
    contentPadding: Dp = 32.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = Glass.palette
    val backdrop = LocalBackdrop.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .shadow(elevation = 24.dp, shape = shape, ambientColor = palette.shadow, spotColor = palette.shadow)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clip(shape)
            .drawBehind {
                if (backdrop != null) {
                    // L'image floutée couvre toute la zone du fond : on la décale
                    // pour n'en montrer que la partie située derrière le panneau.
                    // Hypothèse : GlassBackground occupe tout l'écran (origine 0,0).
                    drawImage(
                        image = backdrop.backdrop.frosted,
                        dstOffset = IntOffset(-origin.x.toInt(), -origin.y.toInt()),
                        dstSize = backdrop.size,
                        filterQuality = FilterQuality.Low,
                    )
                }
                drawRect(palette.panelFill)
                drawRect(
                    Brush.verticalGradient(
                        0f to palette.innerGlow,
                        0.35f to Color.Transparent,
                    ),
                )
            }
            .border(specularBorder(palette), shape)
            .padding(contentPadding),
        content = content,
    )
}
