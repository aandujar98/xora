plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val discordPartnerAar = layout.projectDirectory.file("libs/discord_partner_sdk.aar").asFile
val discordSocialSdkEnabled = discordPartnerAar.exists()

android {
    namespace = "com.arcadia.shell.launcher"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        buildConfigField("boolean", "DISCORD_SOCIAL_SDK_ENABLED", discordSocialSdkEnabled.toString())
        // Used by :app manifest placeholder for AuthenticationActivity deep link.
        buildConfigField(
            "String",
            "DISCORD_DEFAULT_APPLICATION_ID",
            "\"1531690290526683176\"",
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
        implementation(files(discordPartnerAar))
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
