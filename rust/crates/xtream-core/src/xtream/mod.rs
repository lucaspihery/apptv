//! Client pour l'API Xtream Codes (`player_api.php`).

pub mod client;
pub mod models;
pub mod server;
pub mod urls;

pub use client::XtreamClient;
pub use models::AccountInfo;
pub use server::{Credentials, ServerBase};
