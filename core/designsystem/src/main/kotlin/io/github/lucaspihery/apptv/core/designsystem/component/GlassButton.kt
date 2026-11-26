package io.github.lucaspihery.apptv.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.lucaspihery.apptv.core.designsystem.glass.specularBorder
import io.github.lucaspihery.apptv.core.designsystem.theme.Glass
import io.github.lucaspihery.apptv.core.designsystem.theme.GlassMotion
import io.github.lucaspihery.apptv.core.designsystem.theme.GlassShapes

/**
 * Surface de verre focalisable : base des boutons, cartes et champs.
 *
 * Au focus : agrandissement léger, remplissage plus clair, bordure plus
 * marquée et halo de la couleur d'accent — lisible à 3 m.
 */
@Composable
fun GlassClickable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    shape: Shape = GlassShapes.control,
    focusedScale: Float = GlassMotion.FOCUSED_SCALE,
    /** Halo au focus. À couper dans les longues listes : l'ombre colorée coûte cher sur une TV modeste. */
    focusedGlow: Boolean = true,
    /**
     * Discret : rien n'est dessiné au repos (ni fond ni bordure), seulement au focus ou si
     * sélectionné. Pour les listes et les icônes, afin de ne pas aligner des cadres partout.
     */
    quiet: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = Glass.palette
    val accent = p.accent.color
    val idleFill = when {
        selected -> accent.copy(alpha = if (quiet) 0.16f else 0.28f)
        quiet -> Color.Transparent
        else -> p.panelFill
    }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = idleFill,
            contentColor = p.content,
            focusedContainerColor = if (selected) accent.copy(alpha = 0.55f) else p.panelFillFocused,
            focusedContentColor = p.content,
            pressedContainerColor = accent.copy(alpha = 0.45f),
            pressedContentColor = p.content,
            disabledContainerColor = p.panelFill.copy(alpha = p.panelFill.alpha * 0.5f),
            disabledContentColor = p.contentMuted,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        border = ClickableSurfaceDefaults.border(
            border = if (quiet) Border.None else Border(specularBorder(p), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, accent.copy(alpha = 0.95f)), shape = shape),
            pressedBorder = Border(BorderStroke(2.dp, accent), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(
            glow = Glow.None,
            focusedGlow = if (focusedGlow) Glow(elevationColor = accent.copy(alpha = 0.55f), elevation = 18.dp) else Glow.None,
        ),
        content = content,
    )
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    GlassClickable(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        selected = selected,
        shape = GlassShapes.pill,
    ) {
        Box(Modifier.padding(horizontal = 28.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = Color.Unspecified)
        }
    }
}
