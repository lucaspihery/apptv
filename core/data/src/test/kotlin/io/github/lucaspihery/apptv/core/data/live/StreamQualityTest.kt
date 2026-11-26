package io.github.lucaspihery.apptv.core.data.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamQualityTest {
    @Test
    fun `qualité déduite du nom`() {
        assertEquals("FHD", StreamQuality.fromName("FR: TF1 FHD")?.resolutionLabel)
        assertEquals("4K", StreamQuality.fromName("Film 4K HEVC")?.resolutionLabel)
        assertEquals("4K", StreamQuality.fromName("|UHD| Sport")?.resolutionLabel)
        assertEquals("HD", StreamQuality.fromName("Arte HD")?.resolutionLabel)
        assertEquals("SD", StreamQuality.fromName("France 3 SD")?.resolutionLabel)
        assertEquals("50 fps", StreamQuality.fromName("Sport 1 FHD 50FPS")?.fpsLabel)
        assertEquals("60 fps", StreamQuality.fromName("Course 1080p 60 fps")?.fpsLabel)
        // « HDR », « HDMI » ou un 720 dans « 720p » ne sont pas des cadences ou des définitions trompeuses.
        assertNull(StreamQuality.fromName("Chaîne HDR")?.resolutionLabel)
        assertNull(StreamQuality.fromName("TF1 720p")?.fpsLabel)
        assertNull(StreamQuality.fromName("Météo"))
    }

    @Test
    fun `cadence mesurée arrondie aux valeurs standard`() {
        assertEquals(24, StreamQuality.snapFps(23.976f))
        assertEquals(25, StreamQuality.snapFps(24.6f))
        assertEquals(50, StreamQuality.snapFps(49.2f))
        assertEquals(60, StreamQuality.snapFps(59.94f))
        assertEquals(37, StreamQuality.snapFps(37.2f))
        assertNull(StreamQuality.snapFps(Float.NaN))
        assertNull(StreamQuality.snapFps(3f))
    }

    @Test
    fun `aller-retour du stockage`() {
        val map = linkedMapOf(
            "x1" to StreamQuality(1080, 50, measured = true),
            "m42:Nom\tbizarre" to StreamQuality(2160, null, measured = true),
        )
        val back = StreamQuality.decode(StreamQuality.encode(map))
        assertEquals(StreamQuality(1080, 50, true), back["x1"])
        assertEquals(StreamQuality(2160, null, true), back["m42:Nom bizarre"])
        assertEquals(emptyMap<String, StreamQuality>(), StreamQuality.decode("ligne cassée"))
    }

    @Test
    fun `historique dans son ordre, chaînes disparues ignorées`() {
        val a = LiveChannel("A", 1, null, "c", null, 1, null)
        val b = LiveChannel("B", 2, null, "c", null, 2, null)
        val catalog = LiveCatalog(emptyList(), listOf(a, b))
        assertEquals(listOf(b, a), catalog.playlist(VirtualCategories.HISTORY, emptySet(), listOf("x2", "x99", "x1")))
    }
}
