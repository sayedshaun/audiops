plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Versions shared by the source sets below.
val cmp = "1.12.2" // Compose Multiplatform, stable (maps to Jetpack Compose 1.12.1)
val material3 = "1.12.0-alpha03" // CMP Material 3 with the Expressive APIs (Jetpack 1.5.0-alpha22)
val lifecycle = "2.11.0"

kotlin {
    android {
        namespace = "com.audiops.shared"
        compileSdk = 37
        minSdk = 26
        androidResources { enable = true }
    }
    jvm("desktop")

    sourceSets {
        // JVM code shared by Android and desktop: DSP, WAV, chunking, model downloads.
        val jvmShared = create("jvmShared") { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(jvmShared)
        val desktopMain = getByName("desktopMain") { dependsOn(jvmShared) }
        val desktopTest = getByName("desktopTest")

        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:$cmp")
            implementation("org.jetbrains.compose.foundation:foundation:$cmp")
            implementation("org.jetbrains.compose.ui:ui:$cmp")
            implementation("org.jetbrains.compose.material3:material3:$material3")
            implementation("org.jetbrains.compose.components:components-resources:$cmp")
            implementation("org.jetbrains.compose.ui:ui-tooling-preview:$cmp")
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            api("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycle")
            api("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:$lifecycle")
            api("androidx.datastore:datastore-preferences-core:1.3.0")
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
        }
        jvmShared.dependencies {
            api("com.github.wendykierp:JTransforms:3.1")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.13.0")
        }
        desktopMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
        }
        desktopTest.dependencies {
            implementation("junit:junit:4.13.2")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}

compose.resources {
    packageOfResClass = "com.audiops.shared.resources"
}
