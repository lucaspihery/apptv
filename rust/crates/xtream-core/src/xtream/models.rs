//! Réponses brutes de `player_api.php` (désérialisation tolérante) et modèle
//! nettoyé exposé au reste du cœur.

use serde::Deserialize;

use crate::lenient;

#[derive(Debug, Default, Deserialize)]
#[serde(default)]
pub(crate) struct RawAuthResponse {
    #[serde(deserialize_with = "lenient::opt_object")]
    pub user_info: Option<RawUserInfo>,
    #[serde(deserialize_with = "lenient::opt_object")]
    pub server_info: Option<RawServerInfo>,
}

#[derive(Debug, Default, Deserialize)]
#[serde(default)]
pub(crate) struct RawUserInfo {
    #[serde(deserialize_with = "lenient::opt_string")]
    pub username: Option<String>,
    #[serde(deserialize_with = "lenient::flag")]
    pub auth: bool,
    #[serde(deserialize_with = "lenient::opt_string")]
    pub status: Option<String>,
    #[serde(deserialize_with = "lenient::opt_i64")]
    pub exp_date: Option<i64>,
    #[serde(deserialize_with = "lenient::flag")]
    pub is_trial: bool,
    #[serde(deserialize_with = "lenient::opt_i64")]
    pub active_cons: Option<i64>,
    #[serde(deserialize_with = "lenient::opt_i64")]
    pub max_connections: Option<i64>,
    #[serde(deserialize_with = "lenient::string_list")]
    pub allowed_output_formats: Vec<String>,
}

#[derive(Debug, Default, Deserialize)]
#[serde(default)]
pub(crate) struct RawServerInfo {
    #[serde(deserialize_with = "lenient::opt_string")]
    pub timezone: Option<String>,
    #[serde(deserialize_with = "lenient::opt_i64")]
    pub timestamp_now: Option<i64>,
}

/// Compte authentifié (sans aucun secret).
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct AccountInfo {
    pub username: String,
    /// `Active`, `Expired`, `Banned`, `Disabled`… tel que renvoyé par le serveur.
    pub status: String,
    /// Expiration (secondes Unix), `None` = illimité ou inconnu.
    pub expires_at: Option<i64>,
    pub is_trial: bool,
    pub active_connections: Option<i64>,
    pub max_connections: Option<i64>,
    /// Formats autorisés pour le direct (`ts`, `m3u8`…).
    pub allowed_formats: Vec<String>,
    pub server_timezone: Option<String>,
    /// Heure du serveur (secondes Unix) pour corriger un décalage d'horloge de la TV.
    pub server_time: Option<i64>,
}
