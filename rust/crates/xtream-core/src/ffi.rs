//! Façade exposée à Kotlin via UniFFI.
//!
//! Les fonctions sont bloquantes : à appeler depuis `Dispatchers.IO`.
//! Les erreurs (`CoreException` côté Kotlin) ne contiennent aucun secret.

use std::collections::HashSet;
use std::io::{BufReader, Read};

use crate::error::{CoreError, CoreResult};
use crate::http::{HttpClient, UreqHttp};
use crate::m3u;
use crate::secret::{SensitiveUrl, scrub};
use crate::xtream::{AccountInfo, Credentials, ServerBase, XtreamClient};

/// Version du cœur (affichée dans les réglages, utile pour les rapports de bug).
#[uniffi::export]
pub fn core_version() -> String {
    env!("CARGO_PKG_VERSION").to_owned()
}

/// Normalise l'adresse du serveur telle que saisie (`exemple.tv:8080` → `http://exemple.tv:8080`).
#[uniffi::export]
pub fn normalize_server(input: String) -> CoreResult<String> {
    Ok(ServerBase::parse(&input)?.as_str().to_owned())
}

/// Vérifie des identifiants Xtream auprès du serveur.
#[uniffi::export]
pub fn xtream_authenticate(
    server: String,
    username: String,
    password: String,
) -> CoreResult<AccountInfo> {
    let creds = Credentials::new(&server, &username, &password)?;
    XtreamClient::new(creds, UreqHttp::shared()).authenticate()
}

/// Résumé d'une playlist M3U, pour valider un lien à la connexion.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct M3uSummary {
    pub entry_count: u64,
    pub group_count: u64,
    pub epg_url: Option<String>,
}

/// Télécharge et lit une playlist M3U en flux, sans la garder en mémoire.
#[uniffi::export]
pub fn m3u_probe(url: String) -> CoreResult<M3uSummary> {
    probe_with(&*UreqHttp::shared(), &url)
}

/// Identifiants Xtream retrouvés dans un lien `get.php?username=…&password=…`.
#[derive(Clone, PartialEq, Eq, uniffi::Record)]
pub struct XtreamLink {
    pub server: String,
    pub username: String,
    pub password: String,
}

impl std::fmt::Debug for XtreamLink {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("XtreamLink")
            .field("server", &self.server)
            .field("username", &self.username)
            .field("password", &"***")
            .finish()
    }
}

/// Si un lien M3U est en fait un lien Xtream (`get.php`), renvoie ses identifiants
/// pour proposer une connexion Xtream (catégories, EPG, VOD) plutôt que M3U.
#[uniffi::export]
pub fn detect_xtream_link(url: String) -> Option<XtreamLink> {
    let parsed = url::Url::parse(url.trim()).ok()?;
    let is_get = parsed
        .path_segments()
        .and_then(|mut s| s.next_back())
        .is_some_and(|last| last == "get.php" || last == "player_api.php");
    if !is_get {
        return None;
    }
    let mut username = None;
    let mut password = None;
    for (k, v) in parsed.query_pairs() {
        match k.as_ref() {
            "username" => username = Some(v.into_owned()),
            "password" => password = Some(v.into_owned()),
            _ => {}
        }
    }
    let (username, password) = (
        username.filter(|u| !u.is_empty())?,
        password.filter(|p| !p.is_empty())?,
    );
    Some(XtreamLink {
        server: ServerBase::parse(parsed.as_str()).ok()?.as_str().to_owned(),
        username,
        password,
    })
}

pub(crate) fn probe_with(http: &dyn HttpClient, raw_url: &str) -> CoreResult<M3uSummary> {
    let parsed = url::Url::parse(raw_url.trim()).map_err(|_| CoreError::InvalidInput {
        detail: "lien M3U illisible".to_owned(),
    })?;
    if !matches!(parsed.scheme(), "http" | "https") {
        return Err(CoreError::InvalidInput {
            detail: "seuls les liens http et https sont acceptés".to_owned(),
        });
    }
    // Les liens M3U contiennent souvent des identifiants (chemin ou paramètres).
    let owned: Vec<String> = parsed
        .query_pairs()
        .map(|(_, v)| v.into_owned())
        .chain(
            parsed
                .path_segments()
                .into_iter()
                .flatten()
                .map(str::to_owned),
        )
        .filter(|s| s.len() >= 3)
        .collect();
    let secrets: Vec<&str> = owned.iter().map(String::as_str).collect();
    let url = SensitiveUrl::new(parsed.into());
    let resp = http.get(&url, &secrets)?;
    match resp.status {
        200..=299 => {}
        401 | 403 => return Err(CoreError::AuthFailed),
        status => return Err(CoreError::Http { status }),
    }
    let mut entry_count = 0u64;
    let mut groups = HashSet::new();
    let header = m3u::parse(
        BufReader::with_capacity(64 * 1024, resp.body.take(u64::MAX)),
        |e| {
            entry_count += 1;
            if let Some(g) = e.group {
                groups.insert(g);
            }
        },
    )
    .map_err(|e| match e {
        CoreError::Network { detail } => CoreError::Network {
            detail: scrub(&detail, &secrets),
        },
        other => other,
    })?;
    if entry_count == 0 {
        return Err(CoreError::InvalidResponse {
            detail: "la playlist ne contient aucune chaîne".to_owned(),
        });
    }
    Ok(M3uSummary {
        entry_count,
        group_count: groups.len() as u64,
        epg_url: header.epg_url,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::http::HttpResponse;

    struct Fixed(u16, &'static str);

    impl HttpClient for Fixed {
        fn get(&self, _: &SensitiveUrl, _: &[&str]) -> CoreResult<HttpResponse> {
            Ok(HttpResponse {
                status: self.0,
                body: Box::new(std::io::Cursor::new(self.1.as_bytes())),
            })
        }
    }

    #[test]
    fn probe_compte_entrees_et_groupes() {
        let s = probe_with(
            &Fixed(200, include_str!("../tests/fixtures/playlist.m3u")),
            "http://exemple.tv/get.php?username=u&password=p",
        )
        .expect("ok");
        assert_eq!(s.entry_count, 5);
        assert_eq!(s.group_count, 3);
        assert_eq!(s.epg_url.as_deref(), Some("http://exemple.tv/xmltv.php"));
    }

    #[test]
    fn probe_erreurs() {
        assert_eq!(
            probe_with(&Fixed(403, ""), "http://exemple.tv/x.m3u"),
            Err(CoreError::AuthFailed)
        );
        assert!(matches!(
            probe_with(&Fixed(200, "#EXTM3U\n"), "http://exemple.tv/x.m3u"),
            Err(CoreError::InvalidResponse { .. })
        ));
        assert!(matches!(
            probe_with(&Fixed(200, ""), "file:///sdcard/x.m3u"),
            Err(CoreError::InvalidInput { .. })
        ));
    }

    #[test]
    fn detecte_un_lien_xtream() {
        let l = detect_xtream_link(
            "http://exemple.tv:8080/get.php?username=alice&password=p%40ss&type=m3u_plus&output=ts"
                .into(),
        )
        .expect("lien xtream");
        assert_eq!(l.server, "http://exemple.tv:8080");
        assert_eq!(l.username, "alice");
        assert_eq!(l.password, "p@ss");
        assert!(!format!("{l:?}").contains("p@ss"));
        assert!(detect_xtream_link("http://exemple.tv/liste.m3u".into()).is_none());
        assert!(detect_xtream_link("http://exemple.tv/get.php?username=a".into()).is_none());
    }

    #[test]
    fn normalisation_serveur() {
        assert_eq!(
            normalize_server("exemple.tv:8080/".into()).unwrap(),
            "http://exemple.tv:8080"
        );
    }
}
