plugins {
    kotlin("multiplatform") version "2.4.0" apply false
    kotlin("jvm") version "2.4.0" apply false
    kotlin("plugin.serialization") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    id("org.jetbrains.compose") version "1.11.1" apply false
    id("com.android.application") version "9.1.1" apply false
    id("com.android.kotlin.multiplatform.library") version "9.1.1" apply false
    id("app.cash.sqldelight") version "2.1.0" apply false
}

allprojects {
    group = "top.ntutn.kica"
    version = "0.1.0"
}
