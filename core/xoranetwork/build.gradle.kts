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

// Nakama client server key (socket.server_key) for api.xoranetwork.com. This is the client API
// key, NOT the console password / HTTP key / database credentials — those must never ship in the
// app. The value stays out of git: local.properties `xora.network.server.key` or the
// XORA_NAKAMA_SERVER_KEY environment variable (CI secret). Blank = feature shows "not configured".
val xoraNetworkServerKey = (
    localProperties.getProperty("xora.network.server.key")
        ?: System.getenv("XORA_NAKAMA_SERVER_KEY")
    ).orEmpty().trim()

android {
    namespace = "com.arcadia.shell.xoranetwork"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        buildConfigField(
            "String",
            "XORA_NETWORK_SERVER_KEY",
            "\"${xoraNetworkServerKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"",
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
