//! Playlists M3U / M3U8 « étendues » (`#EXTINF` + attributs `tvg-*`).

pub mod parser;

pub use parser::{M3uEntry, M3uHeader, parse};
