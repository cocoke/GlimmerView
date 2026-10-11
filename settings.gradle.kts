pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // ONNX Runtime compilé pour GlimmerView (tools/onnxruntime/build_onnxruntime.py). Exclusif : ce
        // groupe ne peut venir que du dépôt local, jamais d'un dépôt distant qui publierait le même nom.
        exclusiveContent {
            forRepository { maven { url = uri("third_party/maven") } }
            filter { includeGroup("app.glimmerview.thirdparty") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "GlimmerView"

include(":app")
include(":core")
include(":format")
include(":render")
include(":ml")
include(":wallpaper")
