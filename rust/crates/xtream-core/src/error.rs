//! Erreurs du cœur, exposées telles quelles à Kotlin (`CoreException`).
//!
//! Invariant : aucun message ne contient de mot de passe ni d'URL complète.
//! Les messages venant de la couche réseau passent par `secret::scrub`.

#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error, uniffi::Error)]
pub enum CoreError {
    /// Saisie invalide (adresse du serveur, identifiant vide…).
    #[error("saisie invalide : {message}")]
    InvalidInput { message: String },
    /// Hôte introuvable, connexion refusée, TLS…
    #[error("erreur réseau : {message}")]
    Network { message: String },
    #[error("délai dépassé")]
    Timeout,
    /// Réponse HTTP non 2xx.
    #[error("réponse HTTP {status}")]
    Http { status: u16 },
    /// Identifiants refusés par le serveur.
    #[error("identifiants refusés")]
    AuthFailed,
    /// Compte expiré, banni ou désactivé.
    #[error("compte inactif ({status})")]
    AccountInactive { status: String },
    /// Le serveur a répondu, mais pas dans un format exploitable.
    #[error("réponse inattendue : {message}")]
    InvalidResponse { message: String },
}

pub type CoreResult<T> = Result<T, CoreError>;
