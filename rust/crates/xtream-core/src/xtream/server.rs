//! Normalisation de l'adresse du serveur saisie par l'utilisateur.

use crate::error::{CoreError, CoreResult};
use crate::secret::Secret;

/// Adresse de base du panneau, sans `player_api.php`, sans `/` final.
/// Exemple : `http://exemple.tv:8080` ou `https://exemple.tv/panel`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ServerBase(url::Url);

impl ServerBase {
    /// Accepte `exemple.tv:8080`, `http://exemple.tv:8080/`,
    /// `http://exemple.tv:8080/player_api.php?username=…` ou un lien `get.php`.
    pub fn parse(input: &str) -> CoreResult<Self> {
        let trimmed = input.trim();
        if trimmed.is_empty() {
            return Err(invalid("adresse du serveur vide"));
        }
        let with_scheme = if trimmed.contains("://") {
            trimmed.to_owned()
        } else {
            format!("http://{trimmed}")
        };
        let mut url =
            url::Url::parse(&with_scheme).map_err(|_| invalid("adresse du serveur illisible"))?;
        if !matches!(url.scheme(), "http" | "https") {
            return Err(invalid("seuls http et https sont acceptés"));
        }
        if url.host_str().is_none_or(str::is_empty) {
            return Err(invalid("hôte manquant"));
        }
        url.set_query(None);
        url.set_fragment(None);
        // L'adresse ne doit pas porter d'identifiants (user:pass@hote).
        let _ = url.set_username("");
        let _ = url.set_password(None);

        let mut segments: Vec<String> = url
            .path_segments()
            .map(|s| s.filter(|p| !p.is_empty()).map(str::to_owned).collect())
            .unwrap_or_default();
        if segments
            .last()
            .is_some_and(|last| matches!(last.as_str(), "player_api.php" | "get.php" | "xmltv.php"))
        {
            segments.pop();
        }
        url.set_path(&segments.join("/"));
        Ok(Self(url))
    }

    pub fn url(&self) -> &url::Url {
        &self.0
    }

    /// Forme textuelle stockée et affichée (ne contient aucun secret).
    pub fn as_str(&self) -> &str {
        self.0.as_str().trim_end_matches('/')
    }
}

/// Identifiants d'un compte Xtream.
#[derive(Debug, Clone)]
pub struct Credentials {
    pub server: ServerBase,
    pub username: String,
    pub password: Secret,
}

impl Credentials {
    pub fn new(server: &str, username: &str, password: &str) -> CoreResult<Self> {
        let username = username.trim();
        if username.is_empty() {
            return Err(invalid("identifiant vide"));
        }
        if password.is_empty() {
            return Err(invalid("mot de passe vide"));
        }
        Ok(Self {
            server: ServerBase::parse(server)?,
            username: username.to_owned(),
            password: Secret::new(password),
        })
    }

    /// Valeurs à masquer dans tout message d'erreur.
    pub fn secrets(&self) -> [&str; 2] {
        [self.password.expose(), &self.username]
    }
}

fn invalid(message: &str) -> CoreError {
    CoreError::InvalidInput {
        detail: message.to_owned(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn base(s: &str) -> String {
        ServerBase::parse(s).expect("valide").as_str().to_owned()
    }

    #[test]
    fn ajoute_http_si_absent() {
        assert_eq!(base("exemple.tv:8080"), "http://exemple.tv:8080");
    }

    #[test]
    fn retire_slash_final_et_player_api() {
        assert_eq!(base("http://exemple.tv:8080/"), "http://exemple.tv:8080");
        assert_eq!(
            base("http://exemple.tv:8080/player_api.php?username=a&password=b"),
            "http://exemple.tv:8080"
        );
        assert_eq!(
            base("https://exemple.tv/panel/get.php?username=a&password=b&type=m3u_plus"),
            "https://exemple.tv/panel"
        );
    }

    #[test]
    fn retire_les_identifiants_de_l_hote() {
        assert_eq!(base("http://a:b@exemple.tv"), "http://exemple.tv");
    }

    #[test]
    fn refuse_les_saisies_invalides() {
        assert!(ServerBase::parse("").is_err());
        assert!(ServerBase::parse("ftp://exemple.tv").is_err());
        assert!(ServerBase::parse("http://").is_err());
    }

    #[test]
    fn identifiants_obligatoires() {
        assert!(Credentials::new("exemple.tv", " ", "x").is_err());
        assert!(Credentials::new("exemple.tv", "alice", "").is_err());
        let c = Credentials::new("exemple.tv", " alice ", "pw").expect("valide");
        assert_eq!(c.username, "alice");
        assert!(!format!("{c:?}").contains("pw\""));
    }
}
