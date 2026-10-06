import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
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

// jpackage 支持 --linux-package-deps，但 Compose 插件的 linux {} DSL 没有暴露该选项，
// 因此在 packageDeb 之后把 deb 依赖补写进 control 文件。
private val secretToolPackage = "libsecret-tools"

val addDebDepends = tasks.register<AddDebDependsTask>("addDebDepends") {
    debDirectory.set(layout.buildDirectory.dir("compose/binaries/main/deb"))
    dependencies.set(listOf(secretToolPackage))
    stampFile.set(layout.buildDirectory.file("compose/binaries/main/.deb-depends.stamp"))
    dependsOn(tasks.matching { it.name == "packageDeb" })
}

tasks.matching { it.name == "packageDeb" }.configureEach {
    finalizedBy(addDebDepends)
}

abstract class AddDebDependsTask : DefaultTask() {
    /** packageDeb 的产物目录。 */
    @get:InputDirectory
    abstract val debDirectory: DirectoryProperty

    /** 需要追加到 control 文件 Depends 的系统包。 */
    @get:Input
    abstract val dependencies: ListProperty<String>

    /** 标记文件，仅用于 up-to-date 判定。 */
    @get:OutputFile
    abstract val stampFile: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun patch() {
        val directory = debDirectory.get().asFile
        val archives = directory.listFiles { file -> file.isFile && file.extension == "deb" }
            .orEmpty()
            .sortedBy(File::getName)
        check(archives.isNotEmpty()) { "在 $directory 下没有找到 .deb 文件" }
        archives.forEach(::patchArchive)
        stampFile.get().asFile.apply {
            parentFile?.mkdirs()
            writeText(archives.joinToString(separator = "\n", transform = File::getName))
        }
    }

    private fun patchArchive(archive: File) {
        val workDir = File(temporaryDir, archive.nameWithoutExtension)
        workDir.deleteRecursively()
        workDir.mkdirs()
        run("dpkg-deb", "--raw-extract", archive.absolutePath, workDir.absolutePath)
        val control = File(workDir, "DEBIAN/control")
        check(control.isFile) { "${archive.name} 缺少 DEBIAN/control" }
        val original = control.readText()
        val patched = withExtraDependencies(original)
        if (patched == original) {
            logger.lifecycle("{} 已包含依赖 {}，跳过", archive.name, dependencies.get())
            return
        }
        control.writeText(patched)
        run(
            "dpkg-deb",
            "--build",
            "--root-owner-group",
            "--uniform-compression",
            "-Zxz",
            workDir.absolutePath,
            archive.absolutePath,
        )
        logger.lifecycle("已为 {} 添加依赖 {}", archive.name, dependencies.get())
    }

    private fun withExtraDependencies(control: String): String {
        val lines = control.lines().toMutableList()
        val extra = dependencies.get()
        val existing = lines.indexOfFirst { it.startsWith("Depends:", ignoreCase = true) }
        if (existing >= 0) {
            val current = lines[existing]
                .substringAfter(':')
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
            lines[existing] = "Depends: ${(current + extra).distinct().joinToString(", ")}"
        } else {
            val line = "Depends: ${extra.joinToString(", ")}"
            val description = lines.indexOfFirst { it.startsWith("Description:", ignoreCase = true) }
            if (description >= 0) {
                lines.add(description, line)
            } else {
                lines.add(line)
            }
        }
        return lines.joinToString(separator = "\n", postfix = "\n")
    }

    private fun run(vararg arguments: String) {
        execOperations.exec { commandLine(*arguments) }
    }
}
