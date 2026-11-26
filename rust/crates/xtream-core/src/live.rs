//! Catalogue des chaînes en direct (catégories + chaînes), pour Xtream et M3U.
//!
//! Le catalogue ne contient aucun identifiant : pour Xtream, l'URL de lecture est
//! construite à la demande (`xtream_live_url`). Pour M3U, l'URL de la playlist
//! est la seule source : elle est gardée dans `LiveChannel::url`, masquée par `Debug`.

use std::collections::{HashMap, HashSet};
use std::io::{BufReader, Read};

use serde::Deserialize;
use serde::de::DeserializeOwned;

use crate::error::{CoreError, CoreResult};
use crate::http::HttpClient;
use crate::lenient;
use crate::m3u;
use crate::secret::scrub;

/// Taille maximale lue pour la liste des chaînes Xtream (souvent 5 à 20 Mo).
pub const MAX_STREAMS_JSON: u64 = 64 * 1024 * 1024;

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct LiveCategory {
    pub id: String,
    pub name: String,
}

#[derive(Clone, PartialEq, Eq, uniffi::Record)]
pub struct LiveChannel {
    pub name: String,
    /// Numéro de chaîne donné par le serveur, si présent.
    pub number: Option<i64>,
    pub logo: Option<String>,
    pub category_id: Option<String>,
    /// Identifiant guide (`epg_channel_id` ou `tvg-id`).
    pub epg_id: Option<String>,
    /// Xtream : identifiant du flux (l'URL se construit avec `xtream_live_url`).
    pub stream_id: Option<i64>,
    /// M3U : URL du flux. Peut contenir des identifiants : ne jamais la journaliser.
    pub url: Option<String>,
}

impl std::fmt::Debug for LiveChannel {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("LiveChannel")
            .field("name", &self.name)
            .field("number", &self.number)
            .field("category_id", &self.category_id)
            .field("stream_id", &self.stream_id)
            .field("url", &self.url.as_ref().map(|_| "***"))
            .finish()
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Default, uniffi::Record)]
pub struct LiveCatalog {
    /// Catégories ayant au moins une chaîne, dans l'ordre du serveur.
    pub categories: Vec<LiveCategory>,
    pub channels: Vec<LiveChannel>,
}

#[derive(Debug, Default, Deserialize)]
#[serde(default)]
pub(crate) struct RawLiveCategory {
    #[serde(deserialize_with = "lenient::opt_string")]
    pub category_id: Option<String>,
    #[serde(deserialize_with = "lenient::opt_string")]
    pub category_name: Option<String>,
}

#[derive(Debug, Default, Deserialize)]
#[serde(default)]
pub(crate) struct RawLiveStream {
    #[serde(deserialize_with = "lenient::opt_i64")]
    pub num: Option<i64>,
    #[serde(deserialize_with = "lenient::opt_string")]
    pub name: Option<String>,
    #[serde(deserialize_with = "lenient::opt_i64")]
    pub stream_id: Option<i64>,
    #[serde(deserialize_with = "lenient::opt_string")]
    pub stream_icon: Option<String>,
    #[serde(deserialize_with = "lenient::opt_string")]
    pub category_id: Option<String>,
    #[serde(deserialize_with = "lenient::opt_string")]
    pub epg_channel_id: Option<String>,
}

/// Élément de liste tolérant : un élément qui n'est pas un objet est ignoré.
struct Item<T>(Option<T>);

impl<'de, T: DeserializeOwned> Deserialize<'de> for Item<T> {
    fn deserialize<D: serde::Deserializer<'de>>(d: D) -> Result<Self, D::Error> {
        lenient::opt_object(d).map(Item)
    }
}

/// Liste JSON tolérante : tableau, objet indexé (`{"1": {…}}`) ou rien d'exploitable.
pub(crate) fn lenient_list<T: DeserializeOwned>(body: &[u8]) -> CoreResult<Vec<T>> {
    let b = body
        .strip_prefix(b"\xEF\xBB\xBF")
        .unwrap_or(body)
        .trim_ascii();
    let invalid = || CoreError::InvalidResponse {
        detail: if b.first() == Some(&b'<') {
            "le serveur a renvoyé une page HTML au lieu de JSON".to_owned()
        } else {
            "JSON illisible".to_owned()
        },
    };
    match b.first() {
        None => Ok(Vec::new()),
        Some(b'[') => serde_json::from_slice::<Vec<Item<T>>>(b)
            .map(|v| v.into_iter().filter_map(|i| i.0).collect())
            .map_err(|_| invalid()),
        Some(b'{') => {
            let map: Vec<(String, Item<T>)> = serde_json::from_slice::<HashMap<String, Item<T>>>(b)
                .map_err(|_| invalid())?
                .into_iter()
                .collect();
            let mut map = map;
            // Ordre stable : clés numériques croissantes, sinon alphabétique.
            map.sort_by(
                |(a, _), (b, _)| match (a.parse::<i64>(), b.parse::<i64>()) {
                    (Ok(x), Ok(y)) => x.cmp(&y),
                    _ => a.cmp(b),
                },
            );
            Ok(map.into_iter().filter_map(|(_, i)| i.0).collect())
        }
        Some(b'n') if b == b"null" => Ok(Vec::new()),
        _ => Err(invalid()),
    }
}

/// Assemble le catalogue Xtream à partir des deux réponses brutes.
pub(crate) fn xtream_catalog(categories: &[u8], streams: &[u8]) -> CoreResult<LiveCatalog> {
    let raw_categories: Vec<RawLiveCategory> = lenient_list(categories)?;
    let raw_streams: Vec<RawLiveStream> = lenient_list(streams)?;

    let channels: Vec<LiveChannel> = raw_streams
        .into_iter()
        .filter_map(|s| {
            let stream_id = s.stream_id?;
            Some(LiveChannel {
                name: s
                    .name
                    .map(|n| n.trim().to_owned())
                    .filter(|n| !n.is_empty())
                    .unwrap_or_else(|| format!("Chaîne {stream_id}")),
                number: s.num.filter(|n| *n > 0),
                logo: s.stream_icon.filter(|u| u.starts_with("http")),
                category_id: s.category_id,
                epg_id: s.epg_channel_id,
                stream_id: Some(stream_id),
                url: None,
            })
        })
        .collect();

    let used: HashSet<&str> = channels
        .iter()
        .filter_map(|c| c.category_id.as_deref())
        .collect();
    let mut seen = HashSet::new();
    let categories = raw_categories
        .into_iter()
        .filter_map(|c| {
            let id = c.category_id?;
            if !used.contains(id.as_str()) || !seen.insert(id.clone()) {
                return None;
            }
            let name = c
                .category_name
                .map(|n| n.trim().to_owned())
                .filter(|n| !n.is_empty())
                .unwrap_or_else(|| format!("Catégorie {id}"));
            Some(LiveCategory { id, name })
        })
        .collect();
    Ok(LiveCatalog {
        categories,
        channels,
    })
}

/// Lit une playlist M3U en flux et construit le catalogue (une catégorie par groupe).
pub(crate) fn m3u_catalog(http: &dyn HttpClient, raw_url: &str) -> CoreResult<LiveCatalog> {
    let (resp, owned) = crate::ffi::open_playlist(http, raw_url)?;
    let secrets: Vec<&str> = owned.iter().map(String::as_str).collect();
    let mut catalog = LiveCatalog::default();
    let mut groups = HashSet::new();
    m3u::parse(
        BufReader::with_capacity(64 * 1024, resp.take(u64::MAX)),
        |e| {
            if let Some(g) = &e.group
                && groups.insert(g.clone())
            {
                catalog.categories.push(LiveCategory {
                    id: g.clone(),
                    name: g.clone(),
                });
            }
            catalog.channels.push(LiveChannel {
                name: if e.name.trim().is_empty() {
                    "Sans nom".to_owned()
                } else {
                    e.name.trim().to_owned()
                },
                number: None,
                logo: e.tvg_logo.filter(|u| u.starts_with("http")),
                category_id: e.group,
                epg_id: e.tvg_id,
                stream_id: None,
                url: Some(e.url),
            });
        },
    )
    .map_err(|e| match e {
        CoreError::Network { detail } => CoreError::Network {
            detail: scrub(&detail, &secrets),
        },
        other => other,
    })?;
    if catalog.channels.is_empty() {
        return Err(CoreError::InvalidResponse {
            detail: "la playlist ne contient aucune chaîne".to_owned(),
        });
    }
    Ok(catalog)
}

/// Lit au plus `limit` octets d'un corps de réponse.
pub(crate) fn read_limited(
    body: Box<dyn Read + Send>,
    limit: u64,
    secrets: &[&str],
) -> CoreResult<Vec<u8>> {
    let mut buf = Vec::new();
    body.take(limit)
        .read_to_end(&mut buf)
        .map_err(|e| CoreError::Network {
            detail: scrub(&e.to_string(), secrets),
        })?;
    Ok(buf)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::http::HttpResponse;
    use crate::secret::SensitiveUrl;

    const CATEGORIES: &str = r#"[
        {"category_id":"1","category_name":"France | TNT","parent_id":0},
        {"category_id":2,"category_name":"Sport"},
        {"category_id":"3","category_name":"Vide"},
        null
    ]"#;
    const STREAMS: &str = r#"[
        {"num":1,"name":"TF1 HD","stream_type":"live","stream_id":"101","stream_icon":"http://logo/tf1.png","epg_channel_id":"TF1.fr","category_id":"1"},
        {"num":"2","name":" France 2 ","stream_id":102,"stream_icon":"","epg_channel_id":null,"category_id":1},
        {"num":3,"name":"Sans id","stream_id":null,"category_id":"1"},
        {"num":4,"name":"","stream_id":104,"category_id":"2"},
        []
    ]"#;

    #[test]
    fn catalogue_xtream_tolerant() {
        let c = xtream_catalog(CATEGORIES.as_bytes(), STREAMS.as_bytes()).expect("ok");
        assert_eq!(
            c.categories,
            vec![
                LiveCategory {
                    id: "1".into(),
                    name: "France | TNT".into()
                },
                LiveCategory {
                    id: "2".into(),
                    name: "Sport".into()
                },
            ]
        );
        assert_eq!(c.channels.len(), 3);
        assert_eq!(c.channels[0].name, "TF1 HD");
        assert_eq!(c.channels[0].stream_id, Some(101));
        assert_eq!(c.channels[0].logo.as_deref(), Some("http://logo/tf1.png"));
        assert_eq!(c.channels[1].name, "France 2");
        assert_eq!(c.channels[1].number, Some(2));
        assert_eq!(c.channels[1].logo, None);
        assert_eq!(c.channels[1].category_id.as_deref(), Some("1"));
        assert_eq!(c.channels[2].name, "Chaîne 104");
    }

    #[test]
    fn listes_vides_ou_en_objet() {
        let c = xtream_catalog(b"[]", b"").expect("ok");
        assert!(c.channels.is_empty());
        let s: Vec<RawLiveStream> =
            lenient_list(br#"{"2":{"stream_id":2},"10":{"stream_id":10},"1":{"stream_id":1}}"#)
                .expect("ok");
        let ids: Vec<_> = s.iter().map(|s| s.stream_id).collect();
        assert_eq!(ids, vec![Some(1), Some(2), Some(10)]);
        assert!(lenient_list::<RawLiveStream>(b"<html>").is_err());
    }

    struct Fixed(&'static str);

    impl HttpClient for Fixed {
        fn get(&self, _: &SensitiveUrl, _: &[&str]) -> CoreResult<HttpResponse> {
            Ok(HttpResponse {
                status: 200,
                body: Box::new(std::io::Cursor::new(self.0.as_bytes())),
            })
        }
    }

    #[test]
    fn catalogue_m3u_par_groupe() {
        let c = m3u_catalog(
            &Fixed(include_str!("../tests/fixtures/playlist.m3u")),
            "http://exemple.tv/get.php?username=u&password=p",
        )
        .expect("ok");
        assert_eq!(c.channels.len(), 5);
        assert_eq!(c.categories.len(), 3);
        assert_eq!(c.categories[0].name, "France | TNT");
        assert_eq!(
            c.channels[0].url.as_deref(),
            Some("http://exemple.tv/live/u/p/1.ts")
        );
        assert!(!format!("{:?}", c.channels[0]).contains("/live/u/p"));
    }
}
