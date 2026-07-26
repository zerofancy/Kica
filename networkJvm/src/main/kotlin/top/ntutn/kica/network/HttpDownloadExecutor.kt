package top.ntutn.kica.network

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.ntutn.kica.data.PicaRepository
import top.ntutn.kica.model.DownloadStatus
import top.ntutn.kica.model.DownloadTask

class HttpDownloadExecutor(
    private val repository: PicaRepository,
    private val client: OkHttpClient = OkHttpClient(),
    private val maxRetries: Int = 5,
) {
    suspend fun execute(
        task: DownloadTask,
        onProgress: suspend (DownloadTask) -> Unit,
    ): DownloadTask {
        val pages = repository.pages(task.comic.id, task.episode.id)
        var current = task.copy(
            status = DownloadStatus.RUNNING,
            totalPages = pages.size,
            error = null,
        )
        onProgress(current)
        val root = Path.of(task.targetLocation)
            .resolve(safeName(task.comic.title))
            .resolve(safeName(task.episode.title))
        withContext(Dispatchers.IO) { Files.createDirectories(root) }

        pages.forEachIndexed { index, page ->
            val extension = extensionOf(page.originalName, page.imageUrl)
            val destination = root.resolve("${(index + 1).toString().padStart(4, '0')}.$extension")
            if (!Files.isRegularFile(destination) || Files.size(destination) == 0L) {
                downloadWithRetry(page.imageUrl, destination)
            }
            current = current.copy(completedPages = index + 1)
            onProgress(current)
        }
        return current.copy(status = DownloadStatus.COMPLETED, error = null).also { onProgress(it) }
    }

    private suspend fun downloadWithRetry(url: String, destination: Path) {
        var lastError: Throwable? = null
        repeat(maxRetries) { attempt ->
            try {
                withContext(Dispatchers.IO) {
                    val token = repository.session.value?.token
                    val request = Request.Builder()
                        .url(url)
                        .header("user-agent", PicaProtocol.USER_AGENT)
                        .apply {
                            if (!token.isNullOrBlank()) header("authorization", token)
                        }
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw PicaApiException("Image request failed with HTTP ${response.code}.")
                        }
                        val body = response.body ?: throw PicaApiException("Image response was empty.")
                        val temporary = destination.resolveSibling("${destination.fileName}.part")
                        body.byteStream().use { input ->
                            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING)
                        }
                        Files.move(
                            temporary,
                            destination,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE,
                        )
                    }
                }
                return
            } catch (error: Throwable) {
                lastError = error
                if (attempt + 1 < maxRetries) {
                    delay(250L shl attempt.coerceAtMost(4))
                }
            }
        }
        throw PicaApiException(lastError?.message ?: "Image download failed.")
    }

    private fun safeName(value: String): String {
        val cleaned = value.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "").trim().trimEnd('.')
        return cleaned.take(80).ifBlank { "untitled" }
    }

    private fun extensionOf(name: String, url: String): String {
        val candidate = name.substringAfterLast('.', url.substringAfterLast('.', "jpg"))
            .substringBefore('?')
            .lowercase()
        return candidate.takeIf { it in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp") } ?: "jpg"
    }
}

