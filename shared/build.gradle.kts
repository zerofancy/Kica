import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.sqldelight)
}

kotlin {
    android {
        namespace = "top.ntutn.kica.shared"
        compileSdk = 37
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
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.resources)
            implementation(libs.compose.fluent)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.coil.compose)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
            implementation(libs.llamatik.library)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        named("androidMain") {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
            }
        }
        named("desktopMain") {
            dependencies {
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
        named("desktopTest") {
            dependencies {
                implementation(libs.sqldelight.sqlite.driver)
                implementation(compose.desktop.currentOs.substringBeforeLast(':') + ":" + libs.versions.compose.runtime.get())
            }
        }
    }
}

val migrationBaselineRuntime = configurations.create("migrationBaselineRuntime") {
    isCanBeConsumed = false
}

dependencies {
    add(migrationBaselineRuntime.name, libs.sqldelight.sqlite.driver)
}

val migrationBaselineSources = layout.projectDirectory.dir("databases")
val generatedMigrationBaselines = layout.buildDirectory.dir("generated/migration-baselines")
val migrationBaselineGenerator = rootProject.layout.projectDirectory.file("scripts/GenerateMigrationBaselines.java")

val generateMigrationBaselines = tasks.register<JavaExec>("generateMigrationBaselines") {
    group = "verification"
    description = "Generate temporary SQLDelight database baselines from versioned SQL sources."
    classpath = migrationBaselineRuntime
    mainClass.set(migrationBaselineGenerator.asFile.absolutePath)
    args(migrationBaselineSources.asFile.absolutePath, generatedMigrationBaselines.get().asFile.absolutePath)
    inputs.file(migrationBaselineGenerator)
    inputs.files(fileTree(migrationBaselineSources) { include("*.sql") })
    outputs.dir(generatedMigrationBaselines)
}

tasks.withType<app.cash.sqldelight.gradle.VerifyMigrationTask>().configureEach {
    if (name.contains("KicaDatabase")) {
        dependsOn(generateMigrationBaselines)
        inputs.dir(generatedMigrationBaselines).withPathSensitivity(PathSensitivity.RELATIVE)
        val baselineDirectory = generatedMigrationBaselines.get().asFile
        doFirst {
            val verification = this as app.cash.sqldelight.gradle.VerifyMigrationTask
            val originalUnit = verification.compilationUnit.get()
            verification.compilationUnit.set(originalUnit.copy(
                sourceFolders = originalUnit.sourceFolders +
                    app.cash.sqldelight.gradle.SqlDelightSourceFolderImpl(baselineDirectory),
            ))
        }
    }
}

sqldelight {
    databases {
        create("KicaDatabase") {
            packageName.set("top.ntutn.kica.db")
            schemaOutputDirectory.set(layout.buildDirectory.dir("generated/schema-snapshots"))
            verifyMigrations.set(true)
        }
    }
}

compose.resources {
    generateResClass = always
    packageOfResClass = "top.ntutn.kica.resources"
    publicResClass = true
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    // Changing the opt-in must invalidate an otherwise up-to-date test run.
    inputs.property("readerWindowTest", providers.environmentVariable("KICA_READER_WINDOW_TEST").orElse("0"))
    inputs.property("tagWindowTest", providers.environmentVariable("KICA_TAG_WINDOW_TEST").orElse("0"))
}
