package top.ntutn.kica.desktop

import java.awt.Desktop
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
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

    override suspend fun copyImage(page: PageRef): Boolean = withContext(Dispatchers.IO) {
        val bytes = readPageBytes(page)
        if (bytes == null) {
            return@withContext copyText(page.imageUrl)
        }
        val image = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
            ?: return@withContext copyText(page.imageUrl)
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageTransferable(image), null)
            true
        }.getOrDefault(false)
    }

    private fun copyText(text: String): Boolean {
        if (text.isBlank()) return false
        return runCatching {
            val selection = java.awt.datatransfer.StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
            true
        }.getOrDefault(false)
    }

    override suspend fun saveImage(page: PageRef): Boolean = withContext(Dispatchers.IO) {
        val bytes = readPageBytes(page) ?: return@withContext false
        runCatching {
            val suggestedName = page.originalName
                .takeIf { name -> name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }
                ?: "kica_${page.index}.${imageExtension(bytes)}"
            val chosen = withContext(Dispatchers.Swing) {
                val chooser = JFileChooser().apply {
                    dialogTitle = "Save image"
                    selectedFile = File(safeName(suggestedName.ifBlank { "kica_image" }))
                    fileFilter = FileNameExtensionFilter(
                        "Images",
                        "jpg", "jpeg", "png", "webp", "gif", "bmp",
                    )
                }
                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    chooser.selectedFile?.toPath()
                } else {
                    null
                }
            } ?: return@runCatching false
            FileOutputStream(chosen.toFile()).use { it.write(bytes) }
            true
        }.getOrDefault(false)
    }

    private fun imageExtension(bytes: ByteArray): String = when {
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a),
        ) -> "png"
        bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "jpg"
        bytes.size >= 6 && String(bytes, 0, 6, Charsets.US_ASCII).startsWith("GIF") -> "gif"
        bytes.size >= 12 && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
        else -> "jpg"
    }

    private suspend fun readPageBytes(page: PageRef): ByteArray? {
        page.localPath?.takeIf { it.isNotBlank() }?.let { path ->
            val file = File(path)
            if (file.isFile) return runCatching { file.readBytes() }.getOrNull()
        }
        if (page.imageUrl.isBlank()) return null
        return runCatching {
            val connection = URI(page.imageUrl).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "okhttp/3.8.1")
                credentialStore.readToken()?.takeIf { it.isNotBlank() }?.let {
                    connection.setRequestProperty("Authorization", it)
                }
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private class ImageTransferable(private val image: Image) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = DataFlavor.imageFlavor.equals(flavor)
        override fun getTransferData(flavor: DataFlavor): Any {
            if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
            return image
        }
    }

    private fun safeName(value: String): String =
        value.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "").trim().trimEnd('.')
            .take(80)
            .ifBlank { "untitled" }

    private companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    }
}
