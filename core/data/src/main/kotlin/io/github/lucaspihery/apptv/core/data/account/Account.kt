package io.github.lucaspihery.apptv.core.data.account

enum class AccountType { XTREAM, M3U }

/**
 * Compte enregistré, sans secret. Le mot de passe (Xtream) ou le lien (M3U)
 * est stocké chiffré à part et n'est lu qu'au moment de construire une requête.
 */
data class SavedAccount(
    val type: AccountType,
    /** Xtream : adresse normalisée du serveur ; M3U : hôte seul (affichage). */
    val server: String,
    /** Xtream uniquement. */
    val username: String?,
    /** Expiration (secondes Unix), si connue. */
    val expiresAt: Long?,
    val maxConnections: Long?,
    /** M3U : nombre de chaînes au moment de la connexion. */
    val channelCount: Long?,
)

/** Erreurs de connexion, sans aucun secret. Les textes affichés sont dans l'UI. */
sealed interface AccountError {
    data class InvalidInput(val reason: String) : AccountError
    data class Network(val reason: String) : AccountError
    data object Timeout : AccountError
    data class Http(val status: Int) : AccountError
    data object AuthFailed : AccountError
    data class AccountInactive(val status: String) : AccountError
    data class InvalidResponse(val reason: String) : AccountError
    data object Storage : AccountError
}

sealed interface LoginResult {
    data class Success(val account: SavedAccount) : LoginResult
    data class Failure(val error: AccountError) : LoginResult
}

/** Identifiants Xtream extraits d'un lien `get.php`. `toString` masque le mot de passe. */
class XtreamLinkCredentials(val server: String, val username: String, val password: String) {
    override fun toString() = "XtreamLinkCredentials(server=$server, username=$username, password=***)"
}
