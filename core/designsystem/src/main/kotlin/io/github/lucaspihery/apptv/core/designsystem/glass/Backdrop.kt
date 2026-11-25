package io.github.lucaspihery.apptv.core.designsystem.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import io.github.lucaspihery.apptv.core.designsystem.theme.GlassPalette
import io.github.lucaspihery.apptv.core.designsystem.theme.ThemeMode

/**
 * Arrière-plan précalculé, en deux versions :
 * - [background] : dégradé + halos de couleur, légèrement flouté (fond d'écran) ;
 * - [frosted] : la même image beaucoup plus floutée, que les panneaux de verre
 *   dessinent sous eux (à leur position) pour simuler un flou d'arrière-plan.
 *
 * Les deux images sont petites (≈ 1/12 de l'écran) et agrandies au dessin :
 * le filtrage bilinéaire ajoute du flou gratuitement. Calcul unique par
 * taille d'écran et palette (quelques ms), mis en cache par l'appelant.
 */
@Immutable
class Backdrop(val background: ImageBitmap, val frosted: ImageBitmap)

internal object BackdropRenderer {
    private const val DOWNSCALE = 12

    fun render(widthPx: Int, heightPx: Int, palette: GlassPalette): Backdrop {
        val w = (widthPx / DOWNSCALE).coerceAtLeast(16)
        val h = (heightPx / DOWNSCALE).coerceAtLeast(9)
        val base = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        paint(Canvas(base), w.toFloat(), h.toFloat(), palette)

        val pixels = IntArray(w * h)
        base.getPixels(pixels, 0, w, 0, 0, w, h)
        val soft = pixels.copyOf()
        BoxBlur.blur(soft, w, h, radius = 1)
        BoxBlur.blur(pixels, w, h, radius = (w / 24).coerceAtLeast(3))
        // Verre : on éclaircit (sombre) ou blanchit (clair) légèrement l'image floutée.
        val veil = if (palette.mode == ThemeMode.DARK) 0.06f else 0.20f
        for (i in pixels.indices) pixels[i] = mixWhite(pixels[i], veil)

        val background = Bitmap.createBitmap(soft, w, h, Bitmap.Config.ARGB_8888)
        val frosted = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        base.recycle()
        return Backdrop(background.asImageBitmap(), frosted.asImageBitmap())
    }

    private fun paint(canvas: Canvas, w: Float, h: Float, p: GlassPalette) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, w, h,
            p.backgroundStart.toArgb(), p.backgroundEnd.toArgb(), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        val blobAlpha = if (p.mode == ThemeMode.DARK) 0.55f else 0.45f
        blob(canvas, paint, w * 0.18f, h * 0.20f, w * 0.45f, p.accent.color.copy(alpha = blobAlpha))
        blob(canvas, paint, w * 0.85f, h * 0.30f, w * 0.38f, p.accent.secondary.copy(alpha = blobAlpha * 0.8f))
        blob(canvas, paint, w * 0.60f, h * 0.95f, w * 0.50f, p.accent.color.copy(alpha = blobAlpha * 0.6f))
    }

    private fun blob(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, color: Color) {
        paint.shader = RadialGradient(
            cx, cy, r, color.toArgb(), color.copy(alpha = 0f).toArgb(), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, r, paint)
    }

    private fun mixWhite(argb: Int, t: Float): Int {
        val a = argb ushr 24
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        fun m(c: Int) = (c + (255 - c) * t).toInt().coerceIn(0, 255)
        return (a shl 24) or (m(r) shl 16) or (m(g) shl 8) or m(b)
    }
}
