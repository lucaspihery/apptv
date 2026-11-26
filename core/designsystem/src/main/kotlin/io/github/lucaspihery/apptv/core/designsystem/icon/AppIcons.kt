package io.github.lucaspihery.apptv.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Icônes au trait (24 × 24), dessinées ici pour ne pas embarquer une bibliothèque
 * d'icônes entière. Teinte appliquée par `Icon(tint = …)`.
 */
object AppIcons {
    val Search: ImageVector by lazy {
        icon("search") {
            circle(10.5f, 10.5f, 6.5f)
            moveTo(15.4f, 15.4f)
            lineTo(20.5f, 20.5f)
        }
    }

    val Settings: ImageVector by lazy {
        icon("settings") {
            // Roue dentée : 8 dents, contour en zigzag autour d'un moyeu.
            val teeth = 8
            val outer = 9.5f
            val inner = 7.2f
            for (i in 0 until teeth * 4) {
                val a = (i / (teeth * 4f)) * 2 * PI - PI / 2
                val r = if (i % 4 == 1 || i % 4 == 2) outer else inner
                val x = 12f + r * cos(a).toFloat()
                val y = 12f + r * sin(a).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
            circle(12f, 12f, 3f)
        }
    }

    val Account: ImageVector by lazy {
        icon("account") {
            circle(12f, 8.5f, 3.8f)
            moveTo(4.5f, 20.5f)
            curveTo(5.2f, 16.6f, 8.2f, 14.6f, 12f, 14.6f)
            curveTo(15.8f, 14.6f, 18.8f, 16.6f, 19.5f, 20.5f)
        }
    }

    /** Direct : un téléviseur. */
    val Live: ImageVector by lazy {
        icon("live") {
            roundRect(3f, 5.5f, 21f, 17.5f, 2f)
            moveTo(9f, 21f)
            lineTo(15f, 21f)
            moveTo(9f, 2.5f)
            lineTo(12f, 5.5f)
            lineTo(15f, 2.5f)
        }
    }

    /** Guide des programmes : une grille horaire. */
    val Guide: ImageVector by lazy {
        icon("guide") {
            roundRect(3f, 4f, 21f, 20f, 2f)
            moveTo(3f, 9f)
            lineTo(21f, 9f)
            moveTo(3f, 14.5f)
            lineTo(21f, 14.5f)
            moveTo(9f, 9f)
            lineTo(9f, 20f)
            moveTo(15f, 14.5f)
            lineTo(15f, 20f)
        }
    }

    /** « Mes chaînes » : une étoile. */
    val Favorites: ImageVector by lazy {
        icon("favorites") {
            for (i in 0 until 10) {
                val a = i * PI / 5 - PI / 2
                val r = if (i % 2 == 0) 9.5f else 4.2f
                val x = 12f + r * cos(a).toFloat()
                val y = 12.6f + r * sin(a).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }

    /** Historique : une horloge. */
    val History: ImageVector by lazy {
        icon("history") {
            circle(12f, 12f, 8.5f)
            moveTo(12f, 7.5f)
            lineTo(12f, 12f)
            lineTo(15f, 14f)
        }
    }

    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
            .build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx + r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx - r, y1 = cy)
        close()
    }

    private fun PathBuilder.roundRect(left: Float, top: Float, right: Float, bottom: Float, r: Float) {
        moveTo(left + r, top)
        lineTo(right - r, top)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = right, y1 = top + r)
        lineTo(right, bottom - r)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = right - r, y1 = bottom)
        lineTo(left + r, bottom)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = left, y1 = bottom - r)
        lineTo(left, top + r)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = left + r, y1 = top)
        close()
    }
}
