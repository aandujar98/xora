import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
// Public Spotify app Client ID (no secret) — same idea as Discord's application id.
// local.properties can override with spotify.client.id=...
val spotifyClientId = localProperties.getProperty(
    "spotify.client.id",
    "a5770d1ecf1e4edc9bc9c8adbf7a629f",
).orEmpty().trim()

android {
    namespace = "com.arcadia.shell.scraper"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"${spotifyClientId.replace("\"", "\\\"")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:database"))
    implementation(project(":core:datastore"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
