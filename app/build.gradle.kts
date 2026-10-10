import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Clé de signature des versions publiées : jamais dans le dépôt. Le fichier de propriétés
 * (storeFile, storePassword, keyAlias, keyPassword) est cherché dans keystore.properties à la
 * racine du projet (ignoré par Git), sinon dans ~/.glimmerview/keystore.properties.
 */
val releaseKeystore: Properties? = listOf(
    rootProject.file("keystore.properties"),
    File(System.getProperty("user.home"), ".glimmerview/keystore.properties"),
).firstOrNull { it.isFile }?.let { file -> Properties().apply { file.inputStream().use(::load) } }

/** Un APK par architecture, seulement quand on construit une version publiée. */
val buildingRelease = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }

android {
    namespace = "app.glimmerview"
    compileSdk = 37

    defaultConfig {
        // Identifiant définitif (3 octobre 2026) : lié à la clé de signature, il ne doit plus changer.
        applicationId = "app.glimmerview"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.1.1"

        // Bibliothèque de la galerie (docs/library.md) : index, aperçus et paquets .dwp, servis par GitHub Pages.
        buildConfigField("String", "LIBRARY_URL", "\"https://cocoke.github.io/GlimmerView/\"")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore.getProperty("storeFile"))
                storePassword = releaseKeystore.getProperty("storePassword")
                keyAlias = releaseKeystore.getProperty("keyAlias")
                keyPassword = releaseKeystore.getProperty("keyPassword")
            }
        }
    }

    splits {
        abi {
            // ONNX Runtime pèse environ 33 Mo par architecture : un APK chacune plutôt qu'un APK universel.
            isEnable = buildingRelease
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            // Téléphones (arm64) + émulateur (x86_64). Gradle refuse ce filtre à côté du découpage par architecture.
            if (!buildingRelease) ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sans clé (CI, contributeurs), la version publiée reste non signée.
            signingConfig = signingConfigs.findByName("release")
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
    testImplementation(libs.json)
}
