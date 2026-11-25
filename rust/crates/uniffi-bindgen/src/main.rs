//! Générateur de bindings Kotlin (appelé par Gradle, voir `build-logic`).
fn main() {
    uniffi::uniffi_bindgen_main()
}
