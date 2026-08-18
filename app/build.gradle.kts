import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Real signing material lives in an untracked keystore.properties so it never reaches the repo.
val releaseKeystore = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

val discordPartnerAar = rootProject.file("core/launcher/libs/discord_partner_sdk.aar")
val discordSocialSdkEnabled = discordPartnerAar.exists()

android {
    namespace = "com.arcadia.shell"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sora.shell"
        minSdk = 29
        targetSdk = 37
        // Bump for every Desktop sideload so PackageManager accepts the update.
        versionCode = 194
        versionName = "0.2.117"
        // Deep-link scheme for Discord Social SDK AuthenticationActivity.
        manifestPlaceholders["discordApplicationId"] = "1531690290526683176"
    }

    signingConfigs {
        // Shared project keystore so debug/release sideloads from any machine share one cert.
        // Without this, each PC's local ~/.android/debug.keystore causes INSTALL_FAILED_UPDATE.
        getByName("debug") {
            val shared = rootProject.file("debug.keystore")
            if (shared.isFile) {
                storeFile = shared
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        if (releaseKeystore.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(releaseKeystore.getProperty("storeFile"))
                storePassword = releaseKeystore.getProperty("storePassword")
                keyAlias = releaseKeystore.getProperty("keyAlias")
                keyPassword = releaseKeystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // No applicationIdSuffix: Desktop sideloads must share com.sora.shell with release
            // so updates replace the installed app (0.1.7.debug used .debug and installed as new).
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // Prefer keystore.properties release cert; otherwise the shared project debug keystore
            // so machine-local debug keys never produce conflicting com.sora.shell installs.
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // Built-in Kotlin derives its jvmTarget from targetCompatibility, so this configures both.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // Libretro host and Discord Social SDK both ship libc++_shared.
            pickFirsts += listOf("**/libc++_shared.so")
        }
    }

    sourceSets {
        getByName("main") {
            // Stub AuthenticationActivity only when the partner AAR is absent (avoids duplicate class).
            if (!discordSocialSdkEnabled) {
                java.srcDir("src/noDiscordSdk/java")
            }
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:display"))
    implementation(project(":core:datastore"))
    implementation(project(":core:database"))
    implementation(project(":core:scanner"))
    implementation(project(":core:launcher"))
    implementation(project(":core:libretro"))
    implementation(project(":core:retroachievements"))
    implementation(project(":core:input"))
    implementation(project(":core:scraper"))
    implementation(project(":core:xoranetwork"))
    implementation(project(":feature:home"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)

    // Singleton Coil loader with bounded memory/disk (see ArcadiaApplication).
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)
}
