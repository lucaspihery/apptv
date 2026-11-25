package io.github.lucaspihery.apptv.feature.onboarding

import io.github.lucaspihery.apptv.core.data.account.AccountError

/** Message affiché à l'utilisateur (jamais de secret : les erreurs n'en contiennent pas). */
fun AccountError.userMessage(): String = when (this) {
    is AccountError.InvalidInput -> "Vérifiez la saisie : $reason."
    is AccountError.Network -> "Serveur injoignable. Vérifiez l'adresse et la connexion Internet."
    AccountError.Timeout -> "Le serveur ne répond pas (délai dépassé)."
    is AccountError.Http -> "Le serveur a répondu avec une erreur HTTP $status."
    AccountError.AuthFailed -> "Identifiant ou mot de passe refusé par le serveur."
    is AccountError.AccountInactive -> "Ce compte n'est pas actif (statut : $status)."
    is AccountError.InvalidResponse -> "Réponse inattendue du serveur : $reason."
    AccountError.Storage -> "Impossible d'enregistrer le compte sur la TV."
}
