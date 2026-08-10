import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.kotlin.multiplatform.library")
    id("app.cash.sqldelight")
}

kotlin {
    android {
        namespace = "top.ntutn.kica.shared"
        compileSdk = 36
        minSdk = 26
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        androidResources {
            enable = true
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.components.resources)
            implementation("io.github.compose-fluent:fluent:v0.1.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
            implementation("io.coil-kt.coil3:coil-compose:3.5.0")
            implementation("app.cash.sqldelight:runtime:2.1.0")
            implementation("app.cash.sqldelight:coroutines-extensions:2.1.0")
            implementation("com.llamatik:library:1.9.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
        named("androidMain") {
            dependencies {
                implementation("androidx.activity:activity-compose:1.12.2")
                implementation("androidx.core:core-ktx:1.16.0")
            }
        }
        named("desktopMain") {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
            }
        }
        named("desktopTest") {
            dependencies {
                implementation("app.cash.sqldelight:sqlite-driver:2.1.0")
            }
        }
    }
}

sqldelight {
    databases {
        create("KicaDatabase") {
            packageName.set("top.ntutn.kica.db")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}

compose.resources {
    packageOfResClass = "top.ntutn.kica.resources"
    publicResClass = true
}
