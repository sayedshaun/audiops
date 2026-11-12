plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Optional release signing. Set all four variables to sign release builds; otherwise
// the release variant is produced unsigned.
val releaseKeystore: String? = System.getenv("AUDIOOPS_KEYSTORE")

android {
    namespace = "com.audiops.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.audiops.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        providers.gradleProperty("audiopsAbi").orNull?.let { abi ->
            ndk { abiFilters += abi }
        }
    }

    if (releaseKeystore != null) {
        signingConfigs.create("release") {
            storeFile = file(releaseKeystore)
            storePassword = System.getenv("AUDIOOPS_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("AUDIOOPS_KEY_ALIAS")
            keyPassword = System.getenv("AUDIOOPS_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
    implementation("com.google.ai.edge.litert:litert:2.3.0")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    // Material 3 comes from :shared (CMP material3 1.12.0-alpha03 -> Jetpack 1.5.0-alpha22). Do not pin
    // a different Jetpack Material 3 here: shared code is compiled against that exact API, and the
    // Expressive APIs still change between pre-releases (a mismatch fails at runtime).
    implementation("androidx.datastore:datastore-preferences-core:1.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
