buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // Fixe la version du compilateur Kotlin utilisée par le support Kotlin intégré d'AGP 9
        // (AGP n'embarque que KGP 2.2.10 par défaut, trop ancien pour les dépendances actuelles).
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
