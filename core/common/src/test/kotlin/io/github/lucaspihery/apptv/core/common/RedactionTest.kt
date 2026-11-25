package io.github.lucaspihery.apptv.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RedactionTest {
    @Test
    fun `une URL de lecture ne garde que l'hôte`() {
        val r = Redaction.redactUrl("http://exemple.tv:8080/live/alice/secret/42.ts")
        assertEquals("http://exemple.tv:8080/…", r)
        assertFalse(r.contains("secret"))
    }

    @Test
    fun `une URL d'API perd sa requête`() {
        val r = Redaction.redactUrl("https://exemple.tv/player_api.php?username=a&password=secret")
        assertEquals("https://exemple.tv/…", r)
    }

    @Test
    fun `une URL illisible est entièrement masquée`() {
        assertEquals("<url>", Redaction.redactUrl("pas une url secret"))
    }
}
