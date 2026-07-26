package top.ntutn.kica

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import top.ntutn.kica.data.CredentialStore
import top.ntutn.kica.data.FileLocationProvider
import top.ntutn.kica.data.PlatformServices
import top.ntutn.kica.model.DownloadTask
import top.ntutn.kica.model.PageRef

internal class AndroidPlatformServices(
    private val context: Context,
    private val documentTreePicker: AndroidDocumentTreePicker,
) : PlatformServices {
    override val platformName: String = "Android"
    override val isDesktop: Boolean = false
    override val credentialStore: CredentialStore = AndroidCredentialStore(context)
    override val fileLocationProvider: FileLocationProvider = object : FileLocationProvider {
        override suspend fun defaultDownloadLocation(): String {
            val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "downloads")
            directory.mkdirs()
            return directory.absolutePath
        }

        override suspend fun chooseExportLocation(): String? = documentTreePicker.choose()

        override suspend fun downloadedPages(task: DownloadTask): List<PageRef> {
            val directory = File(task.targetLocation)
                .resolve(safeName(task.comic.title))
                .resolve(safeName(task.episode.title))
            return directory.listFiles()
                ?.filter(File::isFile)
                ?.sortedBy(File::getName)
                ?.mapIndexed { index, file ->
                    PageRef(index = index, imageUrl = "", originalName = file.name, localPath = file.absolutePath)
                }
                .orEmpty()
        }
    }

    override suspend fun shareFile(path: String): Boolean =
        runCatching {
            val file = File(path)
            if (!file.isFile) return@runCatching false
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            context.startActivity(
                Intent(Intent.ACTION_SEND)
                    .setType("application/octet-stream")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)

    override suspend fun openExternalUrl(url: String): Boolean =
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)

    private fun safeName(value: String): String =
        value.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "").trim().trimEnd('.')
            .take(80)
            .ifBlank { "untitled" }
}
