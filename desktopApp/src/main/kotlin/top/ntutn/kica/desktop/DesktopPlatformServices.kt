package top.ntutn.kica.desktop

import java.awt.Desktop
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import top.ntutn.kica.data.CredentialStore
import top.ntutn.kica.data.FileLocationProvider
import top.ntutn.kica.data.PlatformServices
import top.ntutn.kica.model.DownloadTask
import top.ntutn.kica.model.PageRef

internal class DesktopPlatformServices(
    private val dataDirectory: Path,
) : PlatformServices {
    override val platformName: String = System.getProperty("os.name")
    override val isDesktop: Boolean = true
    override val credentialStore: CredentialStore = DesktopCredentialStore()
    override val fileLocationProvider: FileLocationProvider = object : FileLocationProvider {
        override suspend fun defaultDownloadLocation(): String {
            val path = dataDirectory.resolve("downloads")
            withContext(Dispatchers.IO) { Files.createDirectories(path) }
            return path.toString()
        }

        override suspend fun chooseExportLocation(): String? = withContext(Dispatchers.Swing) {
            JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = "Choose export directory"
            }.let { chooser ->
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    chooser.selectedFile.toPath().toString()
                } else {
                    null
                }
            }
        }

        override suspend fun downloadedPages(task: DownloadTask): List<PageRef> =
            withContext(Dispatchers.IO) {
                val directory = Path.of(task.targetLocation)
                    .resolve(safeName(task.comic.title))
                    .resolve(safeName(task.episode.title))
                if (Files.notExists(directory)) return@withContext emptyList()
                Files.list(directory).use { paths ->
                    paths.filter(Files::isRegularFile)
                        .sorted()
                        .toList()
                        .mapIndexed { index, path ->
                            PageRef(
                                index = index,
                                imageUrl = "",
                                originalName = path.fileName.toString(),
                                localPath = path.toString(),
                            )
                        }
                }
            }
    }

    override suspend fun shareFile(path: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Desktop.getDesktop().open(Path.of(path).toFile())
            true
        }.getOrDefault(false)
    }

    override suspend fun openExternalUrl(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Desktop.getDesktop().browse(URI(url))
            true
        }.getOrDefault(false)
    }

    private fun safeName(value: String): String =
        value.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "").trim().trimEnd('.')
            .take(80)
            .ifBlank { "untitled" }
}
