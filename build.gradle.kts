// Les plugins sont déclarés ici (apply false) pour être chargés une seule fois ;
// ils sont appliqués par les plugins de convention de `build-logic`.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
