plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.glimmerview.wallpaper"
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
    implementation(project(":core"))
    implementation(project(":render"))
    implementation(libs.androidx.core.ktx)
}
