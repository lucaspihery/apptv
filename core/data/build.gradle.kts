plugins {
    alias(libs.plugins.apptv.android.library)
    alias(libs.plugins.apptv.hilt)
}

android {
    namespace = "io.github.lucaspihery.apptv.core.data"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:native"))
    implementation(libs.androidx.datastore.preferences)
}
