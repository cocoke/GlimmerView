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
    // Runtime unique depuis l'ADR 0001 : ONNX Runtime compilé sur mesure (tools/onnxruntime), sans télémétrie.
    implementation(libs.onnxruntime.android)
    testImplementation(libs.junit)
}
