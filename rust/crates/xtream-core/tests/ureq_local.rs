//! Tests de bout en bout du transport `ureq` contre un petit serveur HTTP local.

use std::io::{BufRead, BufReader, Write};
use std::net::TcpListener;
use std::thread;

use xtream_core::CoreError;
use xtream_core::ffi::{m3u_probe, xtream_authenticate};

/// Sert une seule requête avec la réponse donnée ; renvoie l'adresse et la ligne de requête reçue.
fn serve_once(status: &'static str, body: &'static str) -> (String, thread::JoinHandle<String>) {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let addr = listener.local_addr().unwrap().to_string();
    let handle = thread::spawn(move || {
        let (mut stream, _) = listener.accept().unwrap();
        let mut reader = BufReader::new(stream.try_clone().unwrap());
        let mut request_line = String::new();
        reader.read_line(&mut request_line).unwrap();
        loop {
            let mut l = String::new();
            reader.read_line(&mut l).unwrap();
            if l == "\r\n" || l.is_empty() {
                break;
            }
        }
        write!(
            stream,
            "HTTP/1.1 {status}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",
            body.len()
        )
        .unwrap();
        request_line
    });
    (addr, handle)
}

#[test]
fn authentification_reelle_en_http() {
    let (addr, h) = serve_once(
        "200 OK",
        r#"{"user_info":{"username":"demo","auth":1,"status":"Active","exp_date":null,"max_connections":"1"},"server_info":{"timezone":"UTC"}}"#,
    );
    let info = xtream_authenticate(addr, "demo".into(), "s3cr3t!".into()).expect("ok");
    assert_eq!(info.username, "demo");
    assert_eq!(info.max_connections, Some(1));
    let request = h.join().unwrap();
    assert!(request.starts_with("GET /player_api.php?username=demo&password=s3cr3t%21 "));
}

#[test]
fn identifiants_refuses_en_http() {
    let (addr, h) = serve_once("200 OK", "[]");
    assert_eq!(
        xtream_authenticate(addr, "demo".into(), "faux".into()),
        Err(CoreError::AuthFailed)
    );
    h.join().unwrap();
}

#[test]
fn erreur_reseau_sans_mot_de_passe() {
    // Port fermé : on réserve un port puis on le libère.
    let port = TcpListener::bind("127.0.0.1:0")
        .unwrap()
        .local_addr()
        .unwrap()
        .port();
    let err = xtream_authenticate(
        format!("127.0.0.1:{port}"),
        "demo".into(),
        "MotDePasseSecret".into(),
    )
    .unwrap_err();
    let shown = format!("{err} {err:?}");
    assert!(
        matches!(err, CoreError::Network { .. } | CoreError::Timeout),
        "{shown}"
    );
    assert!(!shown.contains("MotDePasseSecret"), "{shown}");
}

#[test]
fn erreur_m3u_sans_identifiants() {
    let port = TcpListener::bind("127.0.0.1:0")
        .unwrap()
        .local_addr()
        .unwrap()
        .port();
    let err = m3u_probe(format!(
        "http://127.0.0.1:{port}/get.php?username=alice&password=MotDePasseSecret"
    ))
    .unwrap_err();
    let shown = format!("{err} {err:?}");
    assert!(!shown.contains("MotDePasseSecret"), "{shown}");
    assert!(!shown.contains("alice"), "{shown}");
}
