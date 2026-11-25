plugins {
    alias(libs.plugins.apptv.android.library)
    alias(libs.plugins.apptv.hilt)
}

android {
    namespace = "io.github.lucaspihery.apptv.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
}
