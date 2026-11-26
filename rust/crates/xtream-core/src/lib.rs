//! Cœur IPTV : client Xtream Codes, parseur M3U, construction des URL.
//!
//! Exposé à Kotlin par UniFFI (voir `ffi.rs`). Ne lit jamais la vidéo :
//! la lecture est faite par Media3 ExoPlayer côté Android.
//!
//! Règle de sécurité : aucun mot de passe ni URL complète dans les logs,
//! les erreurs ou les sorties `Debug` (voir `secret.rs`).

pub mod epg;
pub mod error;
pub mod ffi;
pub mod http;
pub mod lenient;
pub mod live;
pub mod m3u;
pub mod secret;
pub mod xtream;

pub use error::{CoreError, CoreResult};

uniffi::setup_scaffolding!();
