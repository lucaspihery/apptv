plugins {
    alias(libs.plugins.apptv.android.library)
    alias(libs.plugins.apptv.rust)
}

android {
    namespace = "io.github.lucaspihery.apptv.core.ffi"
}

dependencies {
    // UniFFI appelle la bibliothèque native via JNA (artefact AAR qui embarque libjnidispatch).
    api(variantOf(libs.jna) { artifactType("aar") })
}
