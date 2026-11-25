package io.github.lucaspihery.apptv.core.data

import io.github.lucaspihery.apptv.core.data.crypto.AesGcmCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import javax.crypto.KeyGenerator

class AesGcmCipherTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AesGcmCipher { key }

    @Test
    fun `aller-retour`() {
        val token = cipher.encrypt("p@ss wörd")
        assertEquals("p@ss wörd", cipher.decrypt(token))
    }

    @Test
    fun `le jeton ne contient pas le secret et change à chaque fois`() {
        val a = cipher.encrypt("motdepasse")
        val b = cipher.encrypt("motdepasse")
        assertFalse(a.contains("motdepasse"))
        assertNotEquals(a, b)
    }

    @Test
    fun `jeton altéré ou illisible`() {
        val token = cipher.encrypt("secret")
        val altered = token.dropLast(4) + if (token.endsWith("AAAA")) "BBBB" else "AAAA"
        assertNull(cipher.decrypt(altered))
        assertNull(cipher.decrypt("pas du base64 !"))
        assertNull(cipher.decrypt(""))
    }

    @Test
    fun `une autre clé ne déchiffre pas`() {
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertNull(AesGcmCipher { other }.decrypt(cipher.encrypt("secret")))
    }
}
