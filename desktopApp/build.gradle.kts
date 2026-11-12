import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
}

compose.desktop {
    application {
        mainClass = "com.audiops.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Msi, TargetFormat.Exe)
            packageName = "audiops"
            packageVersion = "0.1.0"
            description = "On-device audio tools"
            linux { shortcut = true }
            windows {
                menu = true
                shortcut = true
                // Keep stable across releases so MSI upgrades replace the old install.
                upgradeUuid = "6f1c3b0e-5d2a-4a7e-9b8c-2f4d1e6a9c35"
            }
        }
    }
}
