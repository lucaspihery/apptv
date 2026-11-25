package io.github.lucaspihery.apptv.core.common

import java.net.URI

/**
 * Masquage des secrets avant tout log.
 *
 * Les URL de lecture et d'API contiennent le mot de passe : ne jamais passer
 * une URL brute à `Log`, utiliser [redactUrl].
 */
object Redaction {
    /** Ne garde que `schéma://hôte[:port]/…`. */
    fun redactUrl(url: String): String = try {
        val u = URI(url.trim())
        val port = if (u.port >= 0) ":${u.port}" else ""
        if (u.scheme == null || u.host == null) "<url>" else "${u.scheme}://${u.host}$port/…"
    } catch (_: Exception) {
        "<url>"
    }
}
