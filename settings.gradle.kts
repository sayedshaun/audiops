pluginManagement {
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

rootProject.name = "Audiops"

include(":shared") // Kotlin Multiplatform library: shared UI, state and the JVM audio core
include(":app") // Android application and the Android audio engine
