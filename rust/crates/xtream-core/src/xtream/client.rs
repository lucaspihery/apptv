use std::io::Read;
use std::sync::Arc;

use super::models::{AccountInfo, RawAuthResponse};
use super::server::Credentials;
use super::urls;
use crate::error::{CoreError, CoreResult};
use crate::http::HttpClient;

/// Taille maximale lue pour une réponse JSON courte (auth, catégories).
const MAX_SMALL_JSON: u64 = 4 * 1024 * 1024;

pub struct XtreamClient {
    creds: Credentials,
    http: Arc<dyn HttpClient>,
}

impl XtreamClient {
    pub fn new(creds: Credentials, http: Arc<dyn HttpClient>) -> Self {
        Self { creds, http }
    }

    pub fn credentials(&self) -> &Credentials {
        &self.creds
    }

    /// Vérifie les identifiants (`player_api.php` sans action).
    pub fn authenticate(&self) -> CoreResult<AccountInfo> {
        let url = urls::api_url(&self.creds, None, &[]);
        let body = self.fetch(&url, MAX_SMALL_JSON)?;
        parse_auth(&body, &self.creds.username)
    }

    fn fetch(&self, url: &crate::secret::SensitiveUrl, limit: u64) -> CoreResult<Vec<u8>> {
        let secrets = self.creds.secrets();
        let resp = self.http.get(url, &secrets)?;
        match resp.status {
            200..=299 => {}
            401 | 403 => return Err(CoreError::AuthFailed),
            status => return Err(CoreError::Http { status }),
        }
        let mut buf = Vec::new();
        resp.body
            .take(limit)
            .read_to_end(&mut buf)
            .map_err(|e| CoreError::Network {
                detail: crate::secret::scrub(&e.to_string(), &secrets),
            })?;
        Ok(buf)
    }
}

/// Interprète la réponse d'authentification.
pub(crate) fn parse_auth(body: &[u8], fallback_username: &str) -> CoreResult<AccountInfo> {
    let trimmed = trim_ascii(body);
    // Beaucoup de panneaux renvoient `[]`, `{}` ou une page vide pour des identifiants faux.
    if trimmed.is_empty() || trimmed == b"[]" || trimmed == b"{}" {
        return Err(CoreError::AuthFailed);
    }
    let raw: RawAuthResponse =
        serde_json::from_slice(trimmed).map_err(|_| CoreError::InvalidResponse {
            detail: if trimmed.first() == Some(&b'<') {
                "le serveur a renvoyé une page HTML au lieu de JSON".to_owned()
            } else {
                "JSON illisible".to_owned()
            },
        })?;
    let user = raw.user_info.ok_or(CoreError::AuthFailed)?;
    if !user.auth {
        return Err(CoreError::AuthFailed);
    }
    let status = user.status.unwrap_or_else(|| "Active".to_owned());
    if !status.eq_ignore_ascii_case("active") {
        return Err(CoreError::AccountInactive { status });
    }
    let server = raw.server_info.unwrap_or_default();
    Ok(AccountInfo {
        username: user
            .username
            .unwrap_or_else(|| fallback_username.to_owned()),
        status,
        expires_at: user.exp_date.filter(|t| *t > 0),
        is_trial: user.is_trial,
        active_connections: user.active_cons,
        max_connections: user.max_connections,
        allowed_formats: user.allowed_output_formats,
        server_timezone: server.timezone,
        server_time: server.timestamp_now,
    })
}

fn trim_ascii(b: &[u8]) -> &[u8] {
    let b = b.strip_prefix(b"\xEF\xBB\xBF").unwrap_or(b);
    b.trim_ascii()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::http::HttpResponse;
    use crate::secret::SensitiveUrl;
    use std::sync::Mutex;

    const OK_MIXED: &str = include_str!("../../tests/fixtures/auth_ok_mixed_types.json");
    const OK_NULLS: &str = include_str!("../../tests/fixtures/auth_ok_nulls.json");

    #[test]
    fn auth_ok_avec_types_melanges() {
        let a = parse_auth(OK_MIXED.as_bytes(), "x").expect("ok");
        assert_eq!(a.username, "demo");
        assert_eq!(a.status, "Active");
        assert_eq!(a.expires_at, Some(1_893_456_000));
        assert!(!a.is_trial);
        assert_eq!(a.active_connections, Some(0));
        assert_eq!(a.max_connections, Some(2));
        assert_eq!(a.allowed_formats, vec!["m3u8", "ts"]);
        assert_eq!(a.server_timezone.as_deref(), Some("Europe/Paris"));
        assert_eq!(a.server_time, Some(1_764_068_400));
    }

    #[test]
    fn auth_ok_avec_nulls() {
        let a = parse_auth(OK_NULLS.as_bytes(), "repli").expect("ok");
        assert_eq!(a.username, "repli");
        assert_eq!(a.expires_at, None);
        assert_eq!(a.max_connections, None);
        assert!(a.allowed_formats.is_empty());
        assert!(a.is_trial);
    }

    #[test]
    fn identifiants_refuses() {
        for body in [
            "",
            "[]",
            "{}",
            r#"{"user_info":{"auth":0}}"#,
            r#"{"user_info":[]}"#,
        ] {
            assert_eq!(
                parse_auth(body.as_bytes(), "x"),
                Err(CoreError::AuthFailed),
                "{body}"
            );
        }
    }

    #[test]
    fn compte_expire() {
        let body = r#"{"user_info":{"auth":1,"status":"Expired"}}"#;
        assert_eq!(
            parse_auth(body.as_bytes(), "x"),
            Err(CoreError::AccountInactive {
                status: "Expired".into()
            })
        );
    }

    #[test]
    fn page_html() {
        let err = parse_auth(b"<html>Forbidden</html>", "x").unwrap_err();
        assert!(matches!(err, CoreError::InvalidResponse { .. }));
    }

    /// Faux transport : enregistre l'URL appelée et renvoie une réponse fixe.
    struct Fake {
        status: u16,
        body: &'static str,
        seen: Mutex<Vec<String>>,
    }

    impl HttpClient for Fake {
        fn get(&self, url: &SensitiveUrl, _: &[&str]) -> CoreResult<HttpResponse> {
            self.seen.lock().unwrap().push(url.expose().to_owned());
            Ok(HttpResponse {
                status: self.status,
                body: Box::new(std::io::Cursor::new(self.body.as_bytes())),
            })
        }
    }

    fn client(status: u16, body: &'static str) -> (XtreamClient, Arc<Fake>) {
        let fake = Arc::new(Fake {
            status,
            body,
            seen: Mutex::new(Vec::new()),
        });
        let creds = Credentials::new("exemple.tv:8080", "demo", "secret").unwrap();
        (XtreamClient::new(creds, fake.clone()), fake)
    }

    #[test]
    fn authenticate_appelle_player_api() {
        let (c, fake) = client(200, OK_MIXED);
        c.authenticate().expect("ok");
        assert_eq!(
            fake.seen.lock().unwrap().as_slice(),
            ["http://exemple.tv:8080/player_api.php?username=demo&password=secret"]
        );
    }

    #[test]
    fn statuts_http() {
        assert_eq!(client(401, "").0.authenticate(), Err(CoreError::AuthFailed));
        assert_eq!(
            client(503, "").0.authenticate(),
            Err(CoreError::Http { status: 503 })
        );
    }
}
