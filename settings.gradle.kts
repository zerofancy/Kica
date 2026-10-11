pluginManagement {
    repositories {
        maven("https://jitpack.io") {
            content { includeGroup("com.github.zerofancy") }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.github.zerofancy.compose-desktop-keep-kit") {
                useModule("com.github.zerofancy:ComposeDesktopKeepKit:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Kica"

include(":shared")
include(":networkJvm")
include(":desktopApp")
include(":androidApp")

