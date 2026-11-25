# CLAUDE.md : AppTV (lecteur IPTV Android TV)

Lecteur IPTV pour Android TV qui utilise les identifiants de l'utilisateur (Xtream Codes
`player_api.php` ou playlist M3U). L'appli ne fournit aucun contenu. Priorité absolue :
**la meilleure qualité d'image et la plus grande fluidité que le flux propose** (résolution
native jusqu'à la 4K, HDR, fréquence d'image d'origine, zapping rapide), sur une TV qui a
peu de RAM. TV de référence : TCL 65", MediaTek, Android 11, 1 à 2 Go de RAM.

## Architecture

```
rust/                     Cœur Rust (workspace Cargo)
  crates/xtream-core/     client Xtream, parseur M3U, (XMLTV, cache SQLite : jalons 2 et 4)
  crates/uniffi-bindgen/  générateur des bindings Kotlin
build-logic/convention/   plugins Gradle de convention + plugin Rust (cargo-ndk, UniFFI)
app/                      Activity unique, NavHost typé, manifeste TV
core/common               dispatchers Hilt, masquage des URL (Redaction)
core/native               .so Rust + bindings UniFFI générés (aucun code à la main)
core/data                 comptes, DataStore, chiffrement AES-GCM (Android Keystore), IptvCore
core/designsystem         thème verre, GlassBackground/GlassPanel, GlassClickable, GlassTextField
core/testing              MainDispatcherRule, FakeAccountRepository
feature/onboarding        connexion Xtream / M3U
```

Dépendances entre modules : `feature:*` → `core:*`. **Seul `core:data` dépend de `core:native`** :
les types UniFFI ne sortent pas de `core:data` (interface `IptvCore`, testable sans
bibliothèque native).

- Le cœur Rust est exposé par UniFFI (proc-macros, voir `rust/crates/xtream-core/src/ffi.rs`).
  Ses fonctions sont **bloquantes** : on les appelle sur `@IoDispatcher`.
- HTTP en Rust : `ureq` 3 (synchrone, rustls + ring, racines webpki). Pas de runtime async.
  Limite connue : un serveur HTTPS avec certificat auto-signé échoue.
- **Lecture vidéo : Media3 ExoPlayer uniquement** (jalon 3). Rust ne lit jamais la vidéo.
- Rust compilé pour `arm64-v8a`, `armeabi-v7a` (beaucoup de TV sont en 32 bits) et `x86_64`
  (émulateur). Profil release : `opt-level = "s"`, LTO, `panic = "abort"`, strip.
- minSdk 23, **targetSdk 36** (choix de Lucas, pour pouvoir publier sur Play), compileSdk 36.
- AGP 9 (Kotlin intégré à AGP) ; le plugin Compose est en version Kotlin 2.4.x.

## Décisions et pièges à respecter

1. **Secrets.** Le mot de passe et les URL de lecture/API (qui le contiennent) n'apparaissent
   jamais dans les logs, les erreurs, `toString` ou `Debug`.
   - Rust : `Secret` et `SensitiveUrl` (Debug masqué), `scrub()` sur les messages réseau.
     `CoreError` ne contient jamais d'URL complète.
   - Kotlin : `Redaction.redactUrl()` avant tout log. `LoginUiState.toString()` masque le mot de passe.
   - Sur disque : mot de passe (ou lien M3U) chiffré en AES-256-GCM, avec une clé non
     exportable d'Android Keystore (`KeystoreKeyProvider`). Pas de `security-crypto` (déprécié).
2. **Désérialisation tolérante.** Les serveurs Xtream mélangent nombres, chaînes, `null`,
   `""`, `[]` au lieu de `{}`. Utiliser `lenient::*` (`opt_i64`, `opt_string`, `flag`,
   `string_list`, `opt_object`) : un champ mal typé ne doit jamais faire échouer le document.
3. **Vidéo en `SurfaceView` en plein écran** (résolution native, HDR, changement de fréquence).
   Sur la TCL MediaTek, la fenêtre restait opaque au-dessus de la vidéo (son sans image) :
   pendant la lecture, vider le fond de la fenêtre et de la racine Compose, et ne rien dessiner
   d'opaque sous l'image. Ne pas utiliser `GlassBackground` sur l'écran de lecture.
4. **ExoPlayer n'a qu'une sortie vidéo.** Un gestionnaire unique `VideoOutputs` attribue la
   vidéo à une seule vue : le plein écran est prioritaire, le mini-lecteur la récupère à la
   fermeture. Personne d'autre n'appelle `setVideoSurface*`. Mini-lecteur en `TextureView`.
5. **Mémoire limitée.** Buffer de timeshift borné ; listes virtualisées (`LazyVerticalGrid`) ;
   EPG chargé seulement pour les chaînes visibles ; images Coil à la taille d'affichage ;
   XMLTV lu en flux (`quick-xml`), M3U lu en flux, jamais un fichier entier en mémoire.
6. **Verre à deux niveaux** (`GlassEffects`) :
   - API ≥ 31 (`FULL`) : vrai flou d'arrière-plan **seulement si les mesures le permettent**
     (jalon 7). Au jalon 1, même rendu que `SIMULATED`.
   - API < 31 (`SIMULATED`, cas de la TCL) : fond flouté **précalculé** (`BackdropRenderer` :
     image réduite à 1/12, flou par boîtes une fois, cache), couches translucides, bordure
     spéculaire, lueur intérieure. **Pas de flou en direct.**
   - `REDUCED` : réglage « Effets réduits » (aplats translucides, pas d'image de fond).
   - **Jamais de flou ni de superposition coûteuse sur la vidéo** : un dégradé sombre simple.
7. **Télécommande.** Tout est focalisable au D-pad. `GlassTextField` ne rend le champ
   éditable (et n'ouvre le clavier) qu'après OK, pour ne pas ouvrir le clavier à chaque
   passage. Focus : agrandissement ≤ 1,06, remplissage plus clair, bordure d'accent, halo.
   Animations < 250 ms. Zones d'overscan : `Modifier.overscanPadding()` (48 × 27 dp).

## Commandes

```bash
# Rust
cd rust && cargo fmt --all && cargo clippy --all-targets -- -D warnings && cargo test

# Android (exige rustup, cargo-ndk 4 et un NDK : ANDROID_NDK_HOME ou SDK/ndk)
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew assembleDebug -Papptv.abis=armeabi-v7a   # plus rapide, pour la TCL 32 bits
./gradlew assembleDebug -Papptv.abis=x86_64        # émulateur
./gradlew :feature:onboarding:connectedDebugAndroidTest   # focus, sur émulateur/TV
```

Les bindings Kotlin sont générés dans `core/native/build/generated/` à chaque build ; ne pas
les copier dans `src/`.

## Méthode

- Travail par jalons (voir README), petites PR, tests à chaque jalon.
- Avant un jalon : décrire ce qui va être fait. À la fin : dire ce qui a été vérifié et ce
  qui ne l'a pas été. **Ne jamais prétendre avoir testé sur la TV.**
- Tests : `cargo test` + clippy sans avertissement ; tests JVM pour ViewModels, dépôts,
  chiffrement, parseurs ; tests d'instrumentation limités à la navigation et au focus.
- Versions des dépendances : `gradle/libs.versions.toml` et `rust/Cargo.toml`, vérifiées le
  25/11/2026. Les revérifier en ligne avant toute montée de version.
