//! Construction des URL de l'API et de lecture.
//!
//! Toutes contiennent le mot de passe : elles sont renvoyées en `SensitiveUrl`
//! et ne doivent jamais être journalisées.

use super::server::Credentials;
use crate::secret::SensitiveUrl;

/// `…/player_api.php?username=…&password=…[&action=…][&k=v…]`
pub fn api_url(creds: &Credentials, action: Option<&str>, extra: &[(&str, &str)]) -> SensitiveUrl {
    let mut url = creds.server.url().clone();
    push_segments(&mut url, &["player_api.php"]);
    {
        let mut q = url.query_pairs_mut();
        q.append_pair("username", &creds.username);
        q.append_pair("password", creds.password.expose());
        if let Some(a) = action {
            q.append_pair("action", a);
        }
        for (k, v) in extra {
            q.append_pair(k, v);
        }
    }
    SensitiveUrl::new(url.into())
}

/// Type de flux Xtream.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum StreamKind {
    Live,
    Movie,
    Series,
}

impl StreamKind {
    fn segment(self) -> &'static str {
        match self {
            StreamKind::Live => "live",
            StreamKind::Movie => "movie",
            StreamKind::Series => "series",
        }
    }
}

/// `…/{live|movie|series}/{user}/{pass}/{id}.{ext}`
///
/// Pour le direct, `ext` vaut `ts` (MPEG-TS) ou `m3u8` (HLS) selon
/// `allowed_output_formats`.
pub fn stream_url(
    creds: &Credentials,
    kind: StreamKind,
    stream_id: i64,
    ext: &str,
) -> SensitiveUrl {
    let mut url = creds.server.url().clone();
    let file = format!("{stream_id}.{}", ext.trim_start_matches('.'));
    push_segments(
        &mut url,
        &[
            kind.segment(),
            &creds.username,
            creds.password.expose(),
            &file,
        ],
    );
    SensitiveUrl::new(url.into())
}

fn push_segments(url: &mut url::Url, segments: &[&str]) {
    if let Ok(mut path) = url.path_segments_mut() {
        path.pop_if_empty();
        for s in segments {
            path.push(s);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn creds() -> Credentials {
        Credentials::new("http://exemple.tv:8080/", "alice", "p@ss/word").expect("valide")
    }

    #[test]
    fn url_api_sans_action() {
        let u = api_url(&creds(), None, &[]);
        assert_eq!(
            u.expose(),
            "http://exemple.tv:8080/player_api.php?username=alice&password=p%40ss%2Fword"
        );
    }

    #[test]
    fn url_api_avec_action_et_parametres() {
        let u = api_url(
            &creds(),
            Some("get_short_epg"),
            &[("stream_id", "12"), ("limit", "2")],
        );
        assert!(
            u.expose()
                .ends_with("&action=get_short_epg&stream_id=12&limit=2")
        );
    }

    #[test]
    fn url_api_conserve_le_chemin_du_panneau() {
        let c = Credentials::new("https://exemple.tv/panel", "a", "b").expect("valide");
        assert_eq!(
            api_url(&c, None, &[]).expose(),
            "https://exemple.tv/panel/player_api.php?username=a&password=b"
        );
    }

    #[test]
    fn url_de_lecture_encode_les_segments() {
        let u = stream_url(&creds(), StreamKind::Live, 42, "ts");
        assert_eq!(
            u.expose(),
            "http://exemple.tv:8080/live/alice/p@ss%2Fword/42.ts"
        );
        let v = stream_url(&creds(), StreamKind::Movie, 7, ".mkv");
        assert!(v.expose().ends_with("/movie/alice/p@ss%2Fword/7.mkv"));
    }

    #[test]
    fn debug_ne_fuit_pas() {
        let u = stream_url(&creds(), StreamKind::Series, 1, "mp4");
        assert!(!format!("{u:?}").contains("p@ss"));
    }
}
