plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.glimmerview.format"
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
    // DepthMap fait partie de l'API publique du format.
    api(project(":core"))

    testImplementation(libs.junit)
    // Android fournit org.json à l'exécution ; les tests sur la JVM ont besoin de la vraie bibliothèque.
    testImplementation(libs.json)
}
