import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "dev.davidemarcoli.sixmensa.wear"

    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        // Must match the phone app: Play pairs a watch APK to its phone APK by
        // applicationId, and a standalone install still wants the same identity.
        applicationId = "dev.davidemarcoli.zmittag"
        // Wear OS 3 is API 30 and is the oldest version still receiving updates.
        // Wear Compose Material3 does not support the API 25/28 watches at all.
        minSdk = 30
        targetSdk = 37
        // Own range, deliberately far above :app's. Play requires an APK with a higher
        // minSdkVersion to carry a higher versionCode, and this module's floor is 30
        // against the phone's 26 — so the watch must always stay ahead. A +1 offset
        // would collide the moment the phone ships its next release.
        versionCode = 1000
        versionName = "1.0.1"
    }

    androidResources {
        localeFilters += listOf("en", "de")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // Wear's own Compose stack. Deliberately no androidx.compose.material3 here — the
    // phone components assume a rectangular screen and rectangular touch targets.
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)
    implementation(libs.androidx.wear.tooling.preview)
    debugImplementation(libs.androidx.wear.compose.ui.tooling)

    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material3)
    implementation(libs.androidx.wear.protolayout.expression)
    debugImplementation(libs.androidx.wear.tiles.tooling)
    implementation(libs.androidx.wear.tiles.tooling.preview)
    implementation(libs.androidx.concurrent.futures.ktx)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
}
