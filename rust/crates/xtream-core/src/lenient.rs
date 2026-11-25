//! Désérialisation tolérante.
//!
//! Les panneaux Xtream renvoient pour un même champ tantôt un nombre, tantôt une
//! chaîne (`"8080"`), tantôt `null`, `""`, ou un booléen (`"1"`, `true`). Ces
//! fonctions s'utilisent avec `#[serde(default, deserialize_with = "…")]` et ne
//! font jamais échouer tout le document pour un champ mal typé.

use serde::{Deserialize, Deserializer};
use serde_json::Value;

/// Entier optionnel : `1`, `"1"`, `1.0`, `"1.0"` → `Some(1)` ; `null`, `""`, texte → `None`.
pub fn opt_i64<'de, D: Deserializer<'de>>(d: D) -> Result<Option<i64>, D::Error> {
    Ok(value_to_i64(&Value::deserialize(d)?))
}

/// Chaîne optionnelle : nombres convertis en texte ; `null` et `""` → `None`.
pub fn opt_string<'de, D: Deserializer<'de>>(d: D) -> Result<Option<String>, D::Error> {
    Ok(match Value::deserialize(d)? {
        Value::String(s) if s.trim().is_empty() => None,
        Value::String(s) => Some(s),
        Value::Number(n) => Some(n.to_string()),
        Value::Bool(b) => Some(b.to_string()),
        _ => None,
    })
}

/// Booléen : `true`, `1`, `"1"`, `"true"`, `"yes"` → `true` ; tout le reste → `false`.
pub fn flag<'de, D: Deserializer<'de>>(d: D) -> Result<bool, D::Error> {
    Ok(match Value::deserialize(d)? {
        Value::Bool(b) => b,
        Value::Number(n) => n.as_f64().is_some_and(|v| v != 0.0),
        Value::String(s) => matches!(
            s.trim().to_ascii_lowercase().as_str(),
            "1" | "true" | "yes" | "y" | "on"
        ),
        _ => false,
    })
}

/// Liste de chaînes : accepte un tableau, une chaîne seule ou `null`.
pub fn string_list<'de, D: Deserializer<'de>>(d: D) -> Result<Vec<String>, D::Error> {
    Ok(match Value::deserialize(d)? {
        Value::Array(items) => items
            .into_iter()
            .filter_map(|v| match v {
                Value::String(s) if !s.is_empty() => Some(s),
                Value::Number(n) => Some(n.to_string()),
                _ => None,
            })
            .collect(),
        Value::String(s) if !s.is_empty() => vec![s],
        _ => Vec::new(),
    })
}

/// Objet optionnel : tout ce qui n'est pas un objet JSON (`[]`, `null`, `""`)
/// ou un objet illisible donne `None` au lieu d'une erreur.
pub fn opt_object<'de, D, T>(d: D) -> Result<Option<T>, D::Error>
where
    D: Deserializer<'de>,
    T: serde::de::DeserializeOwned,
{
    Ok(match Value::deserialize(d)? {
        v @ Value::Object(_) => serde_json::from_value(v).ok(),
        _ => None,
    })
}

fn value_to_i64(v: &Value) -> Option<i64> {
    match v {
        Value::Number(n) => n.as_i64().or_else(|| n.as_f64().map(|f| f as i64)),
        Value::String(s) => {
            let t = s.trim();
            t.parse::<i64>().ok().or_else(|| {
                t.parse::<f64>()
                    .ok()
                    .filter(|f| f.is_finite())
                    .map(|f| f as i64)
            })
        }
        Value::Bool(b) => Some(i64::from(*b)),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde::Deserialize;

    #[derive(Deserialize)]
    struct T {
        #[serde(default, deserialize_with = "opt_i64")]
        n: Option<i64>,
        #[serde(default, deserialize_with = "opt_string")]
        s: Option<String>,
        #[serde(default, deserialize_with = "flag")]
        b: bool,
        #[serde(default, deserialize_with = "string_list")]
        l: Vec<String>,
    }

    fn parse(json: &str) -> T {
        serde_json::from_str(json).expect("doit parser")
    }

    #[test]
    fn nombres_sous_toutes_les_formes() {
        assert_eq!(parse(r#"{"n":42}"#).n, Some(42));
        assert_eq!(parse(r#"{"n":"42"}"#).n, Some(42));
        assert_eq!(parse(r#"{"n":" 42 "}"#).n, Some(42));
        assert_eq!(parse(r#"{"n":"42.0"}"#).n, Some(42));
        assert_eq!(parse(r#"{"n":null}"#).n, None);
        assert_eq!(parse(r#"{"n":""}"#).n, None);
        assert_eq!(parse(r#"{"n":"abc"}"#).n, None);
        assert_eq!(parse(r#"{}"#).n, None);
    }

    #[test]
    fn chaines_et_nombres() {
        assert_eq!(parse(r#"{"s":8080}"#).s.as_deref(), Some("8080"));
        assert_eq!(parse(r#"{"s":""}"#).s, None);
        assert_eq!(parse(r#"{"s":null}"#).s, None);
    }

    #[test]
    fn drapeaux() {
        assert!(parse(r#"{"b":"1"}"#).b);
        assert!(parse(r#"{"b":1}"#).b);
        assert!(parse(r#"{"b":true}"#).b);
        assert!(!parse(r#"{"b":"0"}"#).b);
        assert!(!parse(r#"{"b":null}"#).b);
    }

    #[test]
    fn listes() {
        assert_eq!(parse(r#"{"l":["m3u8","ts"]}"#).l, vec!["m3u8", "ts"]);
        assert_eq!(parse(r#"{"l":"ts"}"#).l, vec!["ts"]);
        assert!(parse(r#"{"l":null}"#).l.is_empty());
    }
}
