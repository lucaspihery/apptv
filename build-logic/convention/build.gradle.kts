plugins {
    `kotlin-dsl`
}

group = "io.github.lucaspihery.apptv.buildlogic"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.hilt.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "apptv.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "apptv.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "apptv.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("hilt") {
            id = "apptv.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("feature") {
            id = "apptv.feature"
            implementationClass = "FeatureConventionPlugin"
        }
        register("rust") {
            id = "apptv.rust"
            implementationClass = "RustAndroidConventionPlugin"
        }
    }
}
