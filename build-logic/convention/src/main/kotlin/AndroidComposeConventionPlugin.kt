import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Active Compose (+ Compose for TV) sur un module Android. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.withPlugin("com.android.application") {
                extensions.configure<ApplicationExtension> { buildFeatures.compose = true }
            }
            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> { buildFeatures.compose = true }
            }
            dependencies {
                val bom = platform(libs.lib("compose-bom"))
                add("implementation", bom)
                add("androidTestImplementation", bom)
                add("implementation", libs.lib("compose-ui"))
                add("implementation", libs.lib("compose-foundation"))
                add("implementation", libs.lib("compose-ui-tooling-preview"))
                add("implementation", libs.lib("tv-material"))
                add("debugImplementation", libs.lib("compose-ui-tooling"))
                add("debugImplementation", libs.lib("compose-ui-test-manifest"))
                add("androidTestImplementation", libs.lib("compose-ui-test-junit4"))
            }
        }
    }
}
