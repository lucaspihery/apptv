package io.github.lucaspihery.apptv.core.designsystem.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object GlassShapes {
    val panel = RoundedCornerShape(32.dp)
    val card = RoundedCornerShape(24.dp)
    val control = RoundedCornerShape(18.dp)
    val pill = RoundedCornerShape(50)
}

/** Durées d'animation : courtes pour rester fluides sur une TV peu puissante. */
object GlassMotion {
    const val FOCUS_MS = 180
    const val FOCUSED_SCALE = 1.06f
}

/**
 * Zone de sécurité d'overscan : 5 % de 960×540 dp (48 dp horizontal, 27 dp vertical),
 * recommandation Android TV. À appliquer sur le contenu de chaque écran.
 */
fun Modifier.overscanPadding(): Modifier = padding(horizontal = 48.dp, vertical = 27.dp)
