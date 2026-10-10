plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.glimmerview"
    compileSdk = 37

    defaultConfig {
        // Identifiant définitif (3 octobre 2026) : lié à la clé de signature, il ne doit plus changer.
        applicationId = "app.glimmerview"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.0-dev"

        ndk {
            // Téléphones (arm64) + émulateur (x86_64) : suffisant pour la Phase 0.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        // Les paquets .dwp de la galerie contiennent déjà des images compressées (WebP, PNG).
        noCompress += "dwp"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":format"))
    implementation(project(":render"))
    implementation(project(":ml"))
    implementation(project(":wallpaper"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
