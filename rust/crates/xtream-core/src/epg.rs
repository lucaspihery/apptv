//! Guide des programmes (EPG) Xtream, chaîne par chaîne (`get_short_epg`).
//!
//! On ne télécharge jamais le XMLTV complet du fournisseur (souvent des centaines de
//! Mo pour des dizaines de milliers de chaînes) : seules les chaînes affichées sont
//! demandées, quelques programmes à la fois.

use serde::Deserialize;

use crate::error::{CoreError, CoreResult};
use crate::lenient;

/// Nombre maximal de programmes demandés pour une chaîne.
pub const MAX_PROGRAMS: u32 = 48;

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct EpgProgram {
    pub title: String,
    pub description: Option<String>,
    /// Début et fin, en secondes Unix.
    pub start: i64,
    pub end: i64,
}

#[derive(Debug, Default, Deserialize)]
#[serde(default)]
struct RawShortEpg {
    #[serde(deserialize_with = "listings")]
    epg_listings: Vec<RawProgram>,
}

#[derive(Debug, Default, Deserialize)]
#[serde(default)]
struct RawProgram {
    #[serde(deserialize_with = "lenient::opt_string")]
    title: Option<String>,
    #[serde(deserialize_with = "lenient::opt_string")]
    description: Option<String>,
    #[serde(deserialize_with = "lenient::opt_i64")]
    start_timestamp: Option<i64>,
    #[serde(deserialize_with = "lenient::opt_i64")]
    stop_timestamp: Option<i64>,
}

/// `epg_listings` : tableau d'objets ; tout autre forme donne une liste vide.
fn listings<'de, D: serde::Deserializer<'de>>(d: D) -> Result<Vec<RawProgram>, D::Error> {
    use serde_json::Value;
    Ok(match Value::deserialize(d)? {
        Value::Array(items) => items
            .into_iter()
            .filter_map(|v| serde_json::from_value(v).ok())
            .collect(),
        Value::Object(map) => map
            .into_iter()
            .filter_map(|(_, v)| serde_json::from_value(v).ok())
            .collect(),
        _ => Vec::new(),
    })
}

/// Interprète la réponse de `get_short_epg`. Programmes triés, sans doublon ni
/// programme déjà terminé avant `now`.
pub(crate) fn parse_short_epg(body: &[u8], now: i64) -> CoreResult<Vec<EpgProgram>> {
    let b = body
        .strip_prefix(b"\xEF\xBB\xBF")
        .unwrap_or(body)
        .trim_ascii();
    if b.is_empty() || b == b"[]" || b == b"null" {
        return Ok(Vec::new());
    }
    let raw: RawShortEpg = serde_json::from_slice(b).map_err(|_| CoreError::InvalidResponse {
        detail: if b.first() == Some(&b'<') {
            "le serveur a renvoyé une page HTML au lieu du guide".to_owned()
        } else {
            "guide des programmes illisible".to_owned()
        },
    })?;
    let mut out: Vec<EpgProgram> = raw
        .epg_listings
        .into_iter()
        .filter_map(|p| {
            let (start, end) = (p.start_timestamp?, p.stop_timestamp?);
            if end <= start || end <= now {
                return None;
            }
            let title = p.title.map(|t| decode_text(&t)).unwrap_or_default();
            Some(EpgProgram {
                title: if title.is_empty() {
                    "Programme".to_owned()
                } else {
                    title
                },
                description: p
                    .description
                    .map(|d| decode_text(&d))
                    .filter(|d| !d.is_empty()),
                start,
                end,
            })
        })
        .collect();
    out.sort_by_key(|p| p.start);
    out.dedup_by(|a, b| a.start == b.start);
    Ok(out)
}

/// Les panneaux Xtream encodent titres et descriptions en base64 (pas tous) :
/// on décode si le texte est du base64 valide donnant de l'UTF-8, sinon on le garde.
pub(crate) fn decode_text(s: &str) -> String {
    let t = s.trim();
    match base64_decode(t).and_then(|bytes| String::from_utf8(bytes).ok()) {
        Some(decoded)
            if !decoded.trim().is_empty()
                && decoded
                    .chars()
                    .all(|c| !c.is_control() || c.is_whitespace()) =>
        {
            decoded.trim().to_owned()
        }
        _ => t.to_owned(),
    }
}

/// Décodeur base64 standard (avec ou sans `=`). `None` si l'entrée n'en est pas.
fn base64_decode(s: &str) -> Option<Vec<u8>> {
    fn val(c: u8) -> Option<u32> {
        Some(match c {
            b'A'..=b'Z' => c - b'A',
            b'a'..=b'z' => c - b'a' + 26,
            b'0'..=b'9' => c - b'0' + 52,
            b'+' | b'-' => 62,
            b'/' | b'_' => 63,
            _ => return None,
        } as u32)
    }
    let input = s.trim_end_matches('=').as_bytes();
    // Un texte ordinaire (« Journal ») peut ressembler à du base64 : on exige une
    // longueur cohérente avec le remplissage d'origine.
    if input.is_empty() || !s.len().is_multiple_of(4) {
        return None;
    }
    let mut out = Vec::with_capacity(input.len() * 3 / 4);
    let mut acc = 0u32;
    let mut bits = 0;
    for &c in input {
        acc = (acc << 6) | val(c)?;
        bits += 6;
        if bits >= 8 {
            bits -= 8;
            out.push((acc >> bits) as u8);
            acc &= (1 << bits) - 1;
        }
    }
    Some(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    const SAMPLE: &str = r#"{"epg_listings":[
        {"id":"2","title":"TGUgMjBIMDA=","description":"SW5mb3M=","start_timestamp":"1764180000","stop_timestamp":"1764182400"},
        {"id":"1","title":"TWF0aW4=","description":"","start_timestamp":1764170000,"stop_timestamp":"1764180000"},
        {"id":"0","title":"Fini","start_timestamp":"1764100000","stop_timestamp":"1764110000"},
        {"id":"3","title":"Sans fin","start_timestamp":"1764190000","stop_timestamp":null},
        null
    ]}"#;

    #[test]
    fn programmes_tries_decodes_et_filtres() {
        let p = parse_short_epg(SAMPLE.as_bytes(), 1_764_175_000).expect("ok");
        assert_eq!(p.len(), 2);
        assert_eq!(p[0].title, "Matin");
        assert_eq!(p[0].description, None);
        assert_eq!(p[1].title, "Le 20H00");
        assert_eq!(p[1].description.as_deref(), Some("Infos"));
        assert_eq!((p[1].start, p[1].end), (1_764_180_000, 1_764_182_400));
    }

    #[test]
    fn reponses_vides_ou_bizarres() {
        assert!(parse_short_epg(b"", 0).unwrap().is_empty());
        assert!(parse_short_epg(b"[]", 0).unwrap().is_empty());
        assert!(
            parse_short_epg(br#"{"epg_listings":false}"#, 0)
                .unwrap()
                .is_empty()
        );
        assert!(parse_short_epg(b"<html>", 0).is_err());
    }

    #[test]
    fn texte_non_base64_conserve() {
        assert_eq!(decode_text("Journal"), "Journal");
        assert_eq!(decode_text("Le 20h"), "Le 20h");
        assert_eq!(decode_text("Q2luw6k="), "Ciné");
        // Huit lettres : du base64 « valide » mais qui donne des octets de contrôle.
        assert_eq!(decode_text("Football"), "Football");
    }
}
