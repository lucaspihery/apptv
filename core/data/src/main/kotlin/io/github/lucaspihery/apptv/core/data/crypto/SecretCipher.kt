package io.github.lucaspihery.apptv.core.data.crypto

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.io.encoding.Base64

/** Chiffre les secrets (mot de passe, lien M3U) avant de les écrire sur disque. */
interface SecretCipher {
    fun encrypt(plain: String): String

    /** `null` si le jeton est illisible ou si la clé a disparu. */
    fun decrypt(token: String): String?
}

/**
 * AES-256-GCM. Jeton = Base64(IV 12 octets ‖ texte chiffré + tag 128 bits).
 * La clé vient d'Android Keystore en production ([KeystoreKeyProvider]) ;
 * une clé logicielle suffit pour les tests JVM.
 */
class AesGcmCipher(private val key: () -> SecretKey) : SecretCipher {
    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        check(iv.size == IV_SIZE) { "IV inattendu" }
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encode(iv + sealed)
    }

    override fun decrypt(token: String): String? = try {
        val bytes = Base64.decode(token)
        if (bytes.size <= IV_SIZE) {
            null
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(TAG_BITS, bytes, 0, IV_SIZE),
            )
            String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
        }
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
