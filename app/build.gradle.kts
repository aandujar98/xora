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
        // v0.3.14 (0.3.0 revisions: 1 = 321, 2 = 322, 3 = 323, 4 = 324; 0.3.1 = 325;
        // 0.3.2 r1 = 326, r2 = 327; 0.3.3 = 328; 0.3.4 = 329; 0.3.5 = 330; 0.3.6 = 331;
        // 0.3.7 = 332; 0.3.8 = 333; 0.3.9 = 334; 0.3.10 = 335; 0.3.11 = 336;
        // 0.3.12 = 337; 0.3.13 = 338; 0.3.14 = 339; 0.3.15 = 340; 0.3.16 = 341;
        // 0.3.17 = 342; 0.3.18 = 343; 0.3.19 = 344; 0.3.20 = 345; 0.3.21 = 346;
        // 0.3.22 = 347; 0.3.23 = 348; 0.3.24 = 349; 0.3.25 = 350; 0.3.26 = 351;
        // 0.3.27 = 352; 0.3.28 = 353; 0.3.29 = 354; 0.3.30 = 355; 0.3.31 = 356;
        // 0.3.32 = 357; 0.3.33 = 358; 0.3.34 = 359; 0.3.35 = 360; 0.3.36 = 361;
        // 0.3.37 = 362; 0.3.38 = 363; 0.3.39 = 364; 0.3.40 = 365; 0.3.41 = 366; 0.3.42 = 367;
        // 0.3.43 = 368; 0.3.44 = 369; 0.3.45 = 370; 0.3.46 = 371; 0.3.47 = 372).
        versionCode = 372
        versionName = "0.3.47"
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
    implementation(libs.androidx.media)

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
