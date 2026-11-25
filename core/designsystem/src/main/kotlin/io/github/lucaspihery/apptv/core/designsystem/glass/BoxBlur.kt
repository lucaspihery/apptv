package io.github.lucaspihery.apptv.core.designsystem.glass

/**
 * Flou par boîtes séparable sur des pixels ARGB (trois passes ≈ flou gaussien).
 * Utilisé une seule fois sur une image réduite (≈ 1/12 de l'écran) : coût
 * négligeable, aucun flou en temps réel (Android 11 n'a pas `RenderEffect`).
 */
internal object BoxBlur {
    fun blur(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int = 3) {
        require(pixels.size == width * height) { "taille incohérente" }
        if (radius < 1 || width == 0 || height == 0) return
        val tmp = IntArray(pixels.size)
        repeat(passes) {
            horizontal(pixels, tmp, width, height, radius)
            vertical(tmp, pixels, width, height, radius)
        }
    }

    private fun horizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
                for (k in -r..r) {
                    val xx = (x + k).coerceIn(0, w - 1)
                    val p = src[row + xx]
                    a += p ushr 24; rr += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
                }
                dst[row + x] = pack(a / n, rr / n, g / n, b / n)
            }
        }
    }

    private fun vertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        for (x in 0 until w) {
            for (y in 0 until h) {
                var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
                for (k in -r..r) {
                    val yy = (y + k).coerceIn(0, h - 1)
                    val p = src[yy * w + x]
                    a += p ushr 24; rr += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
                }
                dst[y * w + x] = pack(a / n, rr / n, g / n, b / n)
            }
        }
    }

    private fun pack(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
}
