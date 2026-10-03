plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.glimmerview.ml"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core"))
    implementation(libs.kotlinx.coroutines.android)
    // Phase 0 : les deux runtimes sont embarqués pour pouvoir les comparer sur l'appareil.
    implementation(libs.litert)
    implementation(libs.onnxruntime.android)
    testImplementation(libs.junit)
}
