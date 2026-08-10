import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
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
    implementation("app.cash.sqldelight:sqlite-driver:2.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.5.0")
    implementation("io.github.vinceglb:filekit-dialogs:0.14.2")
    implementation("net.java.dev.jna:jna-platform:5.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("ch.qos.logback:logback-classic:1.5.38")
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
