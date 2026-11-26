plugins {
    alias(libs.plugins.apptv.android.application)
    alias(libs.plugins.apptv.android.compose)
    alias(libs.plugins.apptv.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.lucaspihery.apptv"

    defaultConfig {
        applicationId = "io.github.lucaspihery.apptv"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // En attendant une clé de publication : signée avec la clé de debug pour pouvoir
            // installer sur la TV la version optimisée (R8, non débogable), bien plus fluide.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:live"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":core:testing"))
}
