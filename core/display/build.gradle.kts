plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.arcadia.shell.display"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":core:model"))

    implementation(libs.androidx.core.ktx)
    // ActivityResult / OnBackPressed owners must be grafted onto Presentation ComposeViews.
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.runtime)
    // Presentation is a Dialog, so its view tree has none of the owners Compose expects. These
    // provide the setViewTree*Owner extensions used to graft the host Activity's owners onto it.
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.savedstate.compose)

    testImplementation(libs.junit)

    implementation(libs.kotlinx.coroutines.android)
}
