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

include(":shared") // Kotlin Multiplatform: shared UI, state and the JVM-portable audio core
include(":app") // Android application and the Android audio engine
include(":desktopApp") // Linux and Windows desktop application
