plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.davidemarcoli.sixmensa.shared"

    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        // Kept at the phone's floor: :wear raises it to 30 on its own side.
        minSdk = 26

        consumerProguardFiles("consumer-rules.pro")

        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"https://six-mensa-api.homelab.davidemarcoli.dev/\"",
        )
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// `api` rather than `implementation` wherever a type appears in a signature that :app or
// :wear touches — Koin definitions, Flows, Retrofit's api interface, ViewModel.
dependencies {
    api(libs.androidx.core.ktx)
    api(libs.androidx.datastore.preferences)
    api(libs.androidx.lifecycle.viewmodel.compose)

    api(libs.koin.android)

    api(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    api(libs.okhttp)
    implementation(libs.okhttp.logging)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
}
