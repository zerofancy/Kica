import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":networkJvm"))
    implementation(compose.desktop.currentOs)
    implementation(compose.components.resources)
    implementation(libs.sqldelight.sqlite.driver)
    implementation(libs.coil.network.okhttp)
    implementation(libs.filekit.dialogs)
    implementation(libs.jna.platform)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.logback.classic)
}

compose.desktop {
    application {
        mainClass = "top.ntutn.kica.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Deb)
            packageName = "Kica"
            packageVersion = "0.1.0"
            description = "A Fluent cross-platform PicACG client"
            vendor = "ntutn"
            modules("java.net.http", "java.sql", "jdk.charsets")
            windows {
                console = true
                menuGroup = "ntutn"
                upgradeUuid = "64b39040-4418-4a29-bf82-926038320105"
            }
        }
    }
}
