//! Parseur M3U en flux : une entrée à la fois, jamais toute la playlist en mémoire.

use std::io::BufRead;

use crate::error::{CoreError, CoreResult};

/// Attributs de la ligne `#EXTM3U`.
#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct M3uHeader {
    /// Guide XMLTV annoncé (`url-tvg` ou `x-tvg-url`).
    pub epg_url: Option<String>,
}

#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct M3uEntry {
    pub name: String,
    /// URL du flux. Peut contenir des identifiants : ne jamais la journaliser.
    pub url: String,
    pub duration: Option<i64>,
    pub tvg_id: Option<String>,
    pub tvg_name: Option<String>,
    pub tvg_logo: Option<String>,
    pub group: Option<String>,
    /// `catchup-days` si présent.
    pub catchup_days: Option<i64>,
}

/// Lit une playlist et appelle `on_entry` pour chaque entrée complète.
/// Renvoie l'en-tête. Les lignes illisibles sont ignorées.
pub fn parse<R: BufRead>(
    mut reader: R,
    mut on_entry: impl FnMut(M3uEntry),
) -> CoreResult<M3uHeader> {
    let mut header = M3uHeader::default();
    let mut pending: Option<M3uEntry> = None;
    let mut pending_group: Option<String> = None;
    let mut line = Vec::new();
    let mut first = true;
    let mut saw_header = false;

    loop {
        line.clear();
        let n = reader
            .read_until(b'\n', &mut line)
            .map_err(|e| CoreError::Network {
                message: e.kind().to_string(),
            })?;
        if n == 0 {
            break;
        }
        let text = String::from_utf8_lossy(&line);
        let mut t = text.trim();
        if first {
            t = t.trim_start_matches('\u{feff}');
            first = false;
            if !t.is_empty() && !t.starts_with('#') && t.starts_with('<') {
                return Err(CoreError::InvalidResponse {
                    message: "le lien renvoie une page HTML, pas une playlist M3U".to_owned(),
                });
            }
        }
        if t.is_empty() {
            continue;
        }
        if let Some(rest) = t.strip_prefix("#EXTM3U") {
            saw_header = true;
            let attrs = parse_attributes(rest);
            header.epg_url = attr(&attrs, "url-tvg").or_else(|| attr(&attrs, "x-tvg-url"));
        } else if let Some(rest) = t.strip_prefix("#EXTINF:") {
            pending = Some(parse_extinf(rest));
            pending_group = None;
        } else if let Some(rest) = t.strip_prefix("#EXTGRP:") {
            let g = rest.trim();
            if !g.is_empty() {
                pending_group = Some(g.to_owned());
            }
        } else if t.starts_with('#') {
            // #EXTVLCOPT, #KODIPROP… : gérés dans un jalon ultérieur.
        } else if let Some(mut entry) = pending.take() {
            entry.url = t.to_owned();
            if entry.group.is_none() {
                entry.group = pending_group.take();
            }
            on_entry(entry);
        } else if t.contains("://") {
            // URL sans #EXTINF : on la garde avec un nom dérivé.
            on_entry(M3uEntry {
                name: t.rsplit('/').next().unwrap_or(t).to_owned(),
                url: t.to_owned(),
                ..Default::default()
            });
        }
    }
    if !saw_header && first {
        return Err(CoreError::InvalidResponse {
            message: "playlist vide".to_owned(),
        });
    }
    Ok(header)
}

fn parse_extinf(rest: &str) -> M3uEntry {
    // Format : <durée> [attr="val" …],<nom>
    let (meta, name) = split_name(rest);
    let meta = meta.trim();
    let (duration_str, attrs_str) = match meta.find(char::is_whitespace) {
        Some(i) => (&meta[..i], &meta[i..]),
        None => (meta, ""),
    };
    let attrs = parse_attributes(attrs_str);
    let tvg_name = attr(&attrs, "tvg-name");
    let name = name.trim();
    M3uEntry {
        name: if name.is_empty() {
            tvg_name.clone().unwrap_or_default()
        } else {
            name.to_owned()
        },
        url: String::new(),
        duration: duration_str.trim().parse::<f64>().ok().map(|d| d as i64),
        tvg_id: attr(&attrs, "tvg-id"),
        tvg_name,
        tvg_logo: attr(&attrs, "tvg-logo"),
        group: attr(&attrs, "group-title"),
        catchup_days: attr(&attrs, "catchup-days").and_then(|v| v.parse().ok()),
    }
}

/// Coupe à la première virgule hors guillemets.
fn split_name(s: &str) -> (&str, &str) {
    let mut in_quotes = false;
    for (i, c) in s.char_indices() {
        match c {
            '"' => in_quotes = !in_quotes,
            ',' if !in_quotes => return (&s[..i], &s[i + 1..]),
            _ => {}
        }
    }
    (s, "")
}

/// `clé="valeur"` ou `clé=valeur`, clés en minuscules.
fn parse_attributes(s: &str) -> Vec<(String, String)> {
    let mut out = Vec::new();
    let mut chars = s.char_indices().peekable();
    while let Some(&(start, c)) = chars.peek() {
        if c.is_whitespace() {
            chars.next();
            continue;
        }
        let mut key_end = start;
        let mut has_eq = false;
        while let Some(&(i, c)) = chars.peek() {
            if c == '=' {
                key_end = i;
                has_eq = true;
                chars.next();
                break;
            }
            if c.is_whitespace() {
                key_end = i;
                break;
            }
            key_end = i + c.len_utf8();
            chars.next();
        }
        let key = s[start..key_end].trim().to_ascii_lowercase();
        if !has_eq {
            continue;
        }
        let value = match chars.peek() {
            Some(&(i, '"')) => {
                chars.next();
                let vstart = i + 1;
                let mut vend = s.len();
                for (j, c) in chars.by_ref() {
                    if c == '"' {
                        vend = j;
                        break;
                    }
                }
                &s[vstart..vend.max(vstart)]
            }
            Some(&(i, _)) => {
                let mut vend = s.len();
                while let Some(&(j, c)) = chars.peek() {
                    if c.is_whitespace() {
                        vend = j;
                        break;
                    }
                    chars.next();
                }
                &s[i..vend]
            }
            None => "",
        };
        if !key.is_empty() {
            out.push((key, value.trim().to_owned()));
        }
    }
    out
}

fn attr(attrs: &[(String, String)], key: &str) -> Option<String> {
    attrs
        .iter()
        .find(|(k, _)| k == key)
        .map(|(_, v)| v.clone())
        .filter(|v| !v.is_empty())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn all(input: &str) -> (M3uHeader, Vec<M3uEntry>) {
        let mut v = Vec::new();
        let h = parse(input.as_bytes(), |e| v.push(e)).expect("ok");
        (h, v)
    }

    #[test]
    fn playlist_typique() {
        let (h, v) = all(include_str!("../../tests/fixtures/playlist.m3u"));
        assert_eq!(h.epg_url.as_deref(), Some("http://exemple.tv/xmltv.php"));
        assert_eq!(v.len(), 5);
        let f = &v[0];
        assert_eq!(f.name, "France 2 HD");
        assert_eq!(f.tvg_id.as_deref(), Some("France2.fr"));
        assert_eq!(f.tvg_logo.as_deref(), Some("http://logo/f2.png"));
        assert_eq!(f.group.as_deref(), Some("France | TNT"));
        assert_eq!(f.duration, Some(-1));
        assert_eq!(f.url, "http://exemple.tv/live/u/p/1.ts");
    }

    #[test]
    fn virgule_dans_un_attribut_et_nom_vide() {
        let (_, v) = all(include_str!("../../tests/fixtures/playlist.m3u"));
        assert_eq!(v[1].group.as_deref(), Some("Sport, Foot"));
        assert_eq!(v[1].name, "Canal Foot");
        assert_eq!(v[2].name, "Nom TVG");
        assert_eq!(v[2].catchup_days, Some(7));
    }

    #[test]
    fn extgrp_bom_crlf_et_url_seule() {
        let (_, v) = all(include_str!("../../tests/fixtures/playlist.m3u"));
        assert_eq!(v[3].group.as_deref(), Some("Groupe EXTGRP"));
        assert_eq!(v[3].name, "Chaîne Été");
        assert_eq!(v[4].name, "flux.m3u8");
    }

    #[test]
    fn page_html_refusee() {
        let err = parse("<!DOCTYPE html><html>".as_bytes(), |_| {}).unwrap_err();
        assert!(matches!(err, CoreError::InvalidResponse { .. }));
    }

    #[test]
    fn playlist_vide_refusee() {
        assert!(parse("".as_bytes(), |_| {}).is_err());
    }
}
