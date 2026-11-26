//! Transport HTTP. Le trait permet de tester le client sans réseau ;
//! l'implémentation réelle utilise `ureq` (synchrone, rustls + ring).
//! Les appels sont bloquants : Kotlin les lance sur `Dispatchers.IO`.

use std::io::Read;
use std::sync::{Arc, OnceLock};
use std::time::Duration;

use crate::error::CoreError;
use crate::secret::{SensitiveUrl, scrub};

pub struct HttpResponse {
    pub status: u16,
    pub body: Box<dyn Read + Send>,
}

pub trait HttpClient: Send + Sync {
    /// `secrets` : valeurs à retirer des messages d'erreur éventuels.
    fn get(&self, url: &SensitiveUrl, secrets: &[&str]) -> Result<HttpResponse, CoreError>;
}

pub const USER_AGENT: &str = concat!("apptv/", env!("CARGO_PKG_VERSION"), " (Android TV)");

pub struct UreqHttp {
    agent: ureq::Agent,
}

impl UreqHttp {
    pub fn new() -> Self {
        let config = ureq::Agent::config_builder()
            .timeout_connect(Some(Duration::from_secs(10)))
            .timeout_recv_response(Some(Duration::from_secs(30)))
            .http_status_as_error(false)
            .user_agent(USER_AGENT)
            .build();
        Self {
            agent: config.into(),
        }
    }

    /// Agent partagé (réutilisation des connexions).
    pub fn shared() -> Arc<dyn HttpClient> {
        static SHARED: OnceLock<Arc<UreqHttp>> = OnceLock::new();
        SHARED.get_or_init(|| Arc::new(UreqHttp::new())).clone()
    }
}

impl Default for UreqHttp {
    fn default() -> Self {
        Self::new()
    }
}

impl HttpClient for UreqHttp {
    fn get(&self, url: &SensitiveUrl, secrets: &[&str]) -> Result<HttpResponse, CoreError> {
        match self.agent.get(url.expose()).call() {
            Ok(resp) => {
                let status = resp.status().as_u16();
                let body = resp.into_body().into_reader();
                Ok(HttpResponse {
                    status,
                    body: Box::new(body),
                })
            }
            Err(ureq::Error::Timeout(_)) => Err(CoreError::Timeout),
            Err(e) => Err(CoreError::Network {
                detail: scrub(&e.to_string(), secrets),
            }),
        }
    }
}
