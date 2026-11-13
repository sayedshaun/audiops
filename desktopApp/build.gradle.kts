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

    testImplementation("junit:junit:4.13.2")
}

// Release version from the Git tag (see .github/workflows/release.yml); MSI needs MAJOR.MINOR.BUILD.
val audiopsVersion: String = providers.gradleProperty("audiopsVersion").getOrElse("0.1.1")

// Installers (DEB, RPM, MSI, EXE) accept only MAJOR.MINOR.PATCH: drop a pre-release suffix such
// as "-beta.1". The full version is still shown in the app.
val packageVersionNumber: String = audiopsVersion.substringBefore('-')

compose.desktop {
    application {
        mainClass = "com.audiops.desktop.MainKt"
        jvmArgs += "-Daudiops.version=$audiopsVersion"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Msi, TargetFormat.Exe)
            // Display name: launcher, Linux menu entry, Windows Start menu and install folder.
            packageName = "Audiops"
            packageVersion = packageVersionNumber
            description = "On-device audio toolkit"
            vendor = "Audiops"
            licenseFile.set(project.rootProject.file("LICENSE").takeIf { it.exists() })
            linux {
                packageName = "audiops" // Debian/RPM package names must be lower case
                shortcut = true
                menuGroup = "AudioVideo"
                iconFile.set(project.file("icons/audiops.png"))
                appCategory = "AudioVideo"
            }
            windows {
                menu = true
                shortcut = true
                dirChooser = true
                menuGroup = "Audiops"
                iconFile.set(project.file("icons/audiops.ico"))
                // Keep stable across releases so MSI upgrades replace the old install.
                upgradeUuid = "6f1c3b0e-5d2a-4a7e-9b8c-2f4d1e6a9c35"
            }
        }
    }
}
