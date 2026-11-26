plugins {
    alias(libs.plugins.apptv.feature)
}

android {
    namespace = "io.github.lucaspihery.apptv.feature.live"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
}
