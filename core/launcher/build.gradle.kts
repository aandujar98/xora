plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val discordPartnerAar = layout.projectDirectory.file("libs/discord_partner_sdk.aar").asFile
val discordSocialSdkEnabled = discordPartnerAar.exists()

/** Build-time secret / id lookup: `-P` property first, then the environment, else blank. */
fun gradleOrEnv(property: String, environment: String): String =
    (providers.gradleProperty(property).orNull ?: System.getenv(environment) ?: "").trim()

android {
    namespace = "com.arcadia.shell.launcher"
    compileSdk = 37
    // Match Discord Social SDK AAR prefab (built with NDK 27).
    if (discordSocialSdkEnabled) {
        ndkVersion = "27.0.12077973"
    }

    defaultConfig {
        minSdk = 29
        buildConfigField("boolean", "DISCORD_SOCIAL_SDK_ENABLED", discordSocialSdkEnabled.toString())
        // Used by :app manifest placeholder for AuthenticationActivity deep link.
        buildConfigField(
            "String",
            "DISCORD_DEFAULT_APPLICATION_ID",
            "\"1531690290526683176\"",
        )
        // Discord never exposes guild role *names* to a user OAuth token, so the XOrA Plus gate
        // matches role snowflakes. Supply them at build time with
        // `-Pxora.plusRoleIds=<id>,<id>` (or `XORA_PLUS_ROLE_IDS` in the environment); players can
        // also paste one during onboarding. A bot token, when provided, resolves names instead.
        buildConfigField(
            "String",
            "XORA_PLUS_ROLE_IDS",
            "\"${gradleOrEnv("xora.plusRoleIds", "XORA_PLUS_ROLE_IDS")}\"",
        )
        buildConfigField(
            "String",
            "DISCORD_BOT_TOKEN",
            "\"${gradleOrEnv("xora.discordBotToken", "XORA_DISCORD_BOT_TOKEN")}\"",
        )
        if (discordSocialSdkEnabled) {
            externalNativeBuild {
                cmake {
                    cppFlags += "-std=c++20"
                    arguments += listOf("-DANDROID_STL=c++_shared")
                }
            }
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        if (discordSocialSdkEnabled) {
            prefab = true
        }
    }

    if (discordSocialSdkEnabled) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    packaging {
        jniLibs {
            // Partner SDK may ship its own libc++ / unwind; keep defaults unless linking fails.
            pickFirsts += listOf("**/libc++_shared.so")
        }
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:database"))
    api(project(":core:libretro"))
    implementation(project(":core:datastore"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)

    if (discordSocialSdkEnabled) {
        implementation(project(":core:discordpartnersdk"))
        implementation(libs.androidx.browser)
    }

    testImplementation(libs.junit)
}

if (discordSocialSdkEnabled) {
    logger.lifecycle("Discord Social SDK: using ${discordPartnerAar.name}")
} else {
    logger.lifecycle(
        "Discord Social SDK: AAR not found at ${discordPartnerAar.path} — " +
            "building status-bridge fallback (see core/launcher/libs/README.md)",
    )
}
