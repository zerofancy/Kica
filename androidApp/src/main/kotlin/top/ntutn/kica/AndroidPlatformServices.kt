package top.ntutn.kica

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.ntutn.kica.data.CredentialStore
import top.ntutn.kica.data.FileLocationProvider
import top.ntutn.kica.data.PlatformServices
import top.ntutn.kica.model.DownloadTask
import top.ntutn.kica.model.PageRef

internal class AndroidPlatformServices(
    private val context: Context,
    private val documentTreePicker: AndroidDocumentTreePicker,
    private val legacyStoragePermission: AndroidLegacyStoragePermissionRequester,
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

    override suspend fun copyImage(page: PageRef): Boolean = withContext(Dispatchers.IO) {
        val bytes = readPageBytes(page)
        if (bytes == null) {
            return@withContext copyText(page.imageUrl)
        }
        runCatching {
            val ext = imageExtension(bytes, page.originalName)
            val cacheDir = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val dest = File(cacheDir, "copy_${System.currentTimeMillis()}.$ext")
            ByteArrayInputStream(bytes).use { input -> FileOutputStream(dest).use { output -> input.copyTo(output) } }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", dest)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "Kica image", uri))
            true
        }.getOrDefault(false)
    }

    private fun copyText(text: String): Boolean {
        if (text.isBlank()) return false
        return runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Kica image", text))
            true
        }.getOrDefault(false)
    }

    override suspend fun saveImage(page: PageRef): Boolean {
        if (
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED &&
            !legacyStoragePermission.request()
        ) {
            return false
        }
        return withContext(Dispatchers.IO) {
            val bytes = readPageBytes(page) ?: return@withContext false
            runCatching {
                val suggestedName = page.originalName.ifBlank { "kica_${page.index}" }
                val cleanName = safeName(suggestedName)
                val (base, nameExt) = splitNameAndExt(cleanName)
                val ext = nameExt
                    ?.lowercase()
                    ?.takeIf { it in IMAGE_EXTENSIONS }
                    ?: imageExtension(bytes, cleanName)
                val mime = guessMimeType(ext)
                val displayName = "$base.$ext"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + File.separator + "Kica",
                        )
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                val uri = context.contentResolver.insert(collection, values) ?: return@runCatching false
                val out = context.contentResolver.openOutputStream(uri)
                    ?: run {
                        context.contentResolver.delete(uri, null, null)
                        return@runCatching false
                    }
                out.use { it.write(bytes) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                } else {
                    @Suppress("DEPRECATION")
                    context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
                }
                true
            }.getOrDefault(false)
        }
    }

    private fun splitNameAndExt(name: String): Pair<String, String?> {
        val idx = name.lastIndexOf('.')
        return if (idx <= 0 || idx == name.length - 1) {
            name to null
        } else {
            name.substring(0, idx) to name.substring(idx + 1)
        }
    }

    private fun imageExtension(bytes: ByteArray, sourceName: String): String = when {
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a),
        ) -> "png"
        bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "jpg"
        bytes.size >= 6 && String(bytes, 0, 6, Charsets.US_ASCII).startsWith("GIF") -> "gif"
        bytes.size >= 12 && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
        else -> sourceName.substringAfterLast('.', "jpg").lowercase()
            .takeIf { it in IMAGE_EXTENSIONS } ?: "jpg"
    }

    private fun guessMimeType(ext: String): String = when (ext.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        else -> "image/jpeg"
    }

    private suspend fun readPageBytes(page: PageRef): ByteArray? {
        page.localPath?.takeIf { it.isNotBlank() }?.let { path ->
            val file = File(path)
            if (file.isFile) return runCatching { file.readBytes() }.getOrNull()
        }
        if (page.imageUrl.isBlank()) return null
        return runCatching {
            val connection = java.net.URI(page.imageUrl).toURL().openConnection() as HttpURLConnection
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

    private fun safeName(value: String): String =
        value.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "").trim().trimEnd('.')
            .take(80)
            .ifBlank { "untitled" }

    private companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    }
}
