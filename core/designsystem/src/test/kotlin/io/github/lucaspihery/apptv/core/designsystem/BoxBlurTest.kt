package io.github.lucaspihery.apptv.core.designsystem

import io.github.lucaspihery.apptv.core.designsystem.glass.BoxBlur
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxBlurTest {
    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    private fun level(p: Int) = p and 0xFF

    @Test
    fun `une image uniforme ne change pas`() {
        val px = IntArray(20 * 10) { gray(120) }
        BoxBlur.blur(px, 20, 10, radius = 3)
        assertTrue(px.all { it == gray(120) })
    }

    @Test
    fun `un point blanc s'étale et perd en intensité`() {
        val w = 21
        val h = 21
        val px = IntArray(w * h) { gray(0) }
        px[10 * w + 10] = gray(255)
        BoxBlur.blur(px, w, h, radius = 2)
        val center = level(px[10 * w + 10])
        assertTrue("centre atténué", center in 1..254)
        assertTrue("voisin éclairci", level(px[10 * w + 12]) > 0)
        assertEquals("loin du point : noir", 0, level(px[0]))
        assertEquals("alpha conservé", 0xFF, px[0] ushr 24)
    }

    @Test
    fun `rayon nul ou image vide`() {
        val px = intArrayOf(gray(10), gray(200))
        BoxBlur.blur(px, 2, 1, radius = 0)
        assertEquals(gray(10), px[0])
        BoxBlur.blur(IntArray(0), 0, 0, radius = 3)
    }
}
