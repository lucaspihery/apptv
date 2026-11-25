//! Valeurs sensibles (mot de passe, URL contenant des identifiants).
//!
//! Règle : un secret ne doit jamais apparaître dans un log, un message d'erreur
//! ou une sortie `Debug`. Ces types n'implémentent pas `Display` et leur `Debug`
//! est masqué ; la valeur n'est accessible qu'explicitement via `expose()`.

use std::fmt;

/// Texte masqué dans toutes les sorties de diagnostic.
#[derive(Clone, PartialEq, Eq)]
pub struct Secret(String);

impl Secret {
    pub fn new(value: impl Into<String>) -> Self {
        Self(value.into())
    }

    /// Accès explicite à la valeur. À n'utiliser que pour construire une requête.
    pub fn expose(&self) -> &str {
        &self.0
    }

    pub fn is_empty(&self) -> bool {
        self.0.is_empty()
    }
}

impl fmt::Debug for Secret {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("Secret(***)")
    }
}

/// URL qui contient des identifiants (API Xtream, URL de lecture, lien M3U).
///
/// `Debug` n'affiche que le schéma et l'hôte.
#[derive(Clone, PartialEq, Eq)]
pub struct SensitiveUrl(String);

impl SensitiveUrl {
    pub(crate) fn new(value: String) -> Self {
        Self(value)
    }

    pub fn expose(&self) -> &str {
        &self.0
    }

    /// Forme affichable sans risque : `http://hote:port/…`.
    pub fn redacted(&self) -> String {
        redact_url(&self.0)
    }
}

impl fmt::Debug for SensitiveUrl {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "SensitiveUrl({})", self.redacted())
    }
}

/// Garde uniquement `schéma://hôte[:port]` d'une URL.
pub fn redact_url(raw: &str) -> String {
    match url::Url::parse(raw) {
        Ok(u) => {
            let host = u.host_str().unwrap_or("?");
            match u.port() {
                Some(p) => format!("{}://{}:{}/…", u.scheme(), host, p),
                None => format!("{}://{}/…", u.scheme(), host),
            }
        }
        Err(_) => "<url invalide>".to_owned(),
    }
}

/// Remplace toute occurrence des secrets donnés dans un texte libre
/// (par exemple un message d'erreur venant de la couche réseau).
pub fn scrub(text: &str, secrets: &[&str]) -> String {
    let mut out = text.to_owned();
    for s in secrets.iter().filter(|s| s.len() >= 2) {
        out = out.replace(s, "***");
        let encoded = percent_encoding::utf8_percent_encode(s, percent_encoding::NON_ALPHANUMERIC)
            .to_string();
        if encoded != *s {
            out = out.replace(&encoded, "***");
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn debug_secret_est_masque() {
        let s = Secret::new("hunter2");
        assert_eq!(format!("{s:?}"), "Secret(***)");
    }

    #[test]
    fn debug_url_sensible_ne_montre_que_l_hote() {
        let u = SensitiveUrl::new("http://exemple.tv:8080/live/alice/hunter2/42.ts".to_owned());
        let dbg = format!("{u:?}");
        assert!(!dbg.contains("hunter2"));
        assert!(!dbg.contains("alice"));
        assert!(dbg.contains("exemple.tv:8080"));
    }

    #[test]
    fn scrub_retire_le_secret_brut_et_encode() {
        let msg = "échec http://h/player_api.php?username=a&password=p%40ss et p@ss";
        let out = scrub(msg, &["p@ss"]);
        assert!(!out.contains("p@ss"));
        assert!(!out.contains("p%40ss"));
    }
}
