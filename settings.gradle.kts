pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "apptv"

include(":app")
include(":core:common")
include(":core:native")
include(":core:data")
include(":core:designsystem")
include(":core:testing")
include(":feature:onboarding")
