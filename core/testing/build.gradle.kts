plugins {
    alias(libs.plugins.apptv.android.library)
}

android {
    namespace = "io.github.lucaspihery.apptv.core.testing"
}

dependencies {
    api(project(":core:data"))
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
}
