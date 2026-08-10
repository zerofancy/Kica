package top.ntutn.kica.network

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import top.ntutn.kica.data.TitleTranslationModel
import top.ntutn.kica.data.TitleTranslationModelStore
import top.ntutn.kica.model.NetworkSettings
import top.ntutn.kica.model.ProxyMode

class JvmTitleTranslationModelStore(
    private val modelDirectory: File,
    private val maxRetries: Int = 5,
    private val downloadUrl: String = TitleTranslationModel.DOWNLOAD_URL,
    private val modelFileName: String = TitleTranslationModel.FILE_NAME,
    private val expectedSizeBytes: Long = TitleTranslationModel.SIZE_BYTES,
    private val expectedSha256: String = TitleTranslationModel.SHA256,
) : TitleTranslationModelStore {
    private val downloadMutex = Mutex()
    private val activeCall = AtomicReference<Call?>(null)
    private val cancelled = AtomicBoolean(false)

    @Volatile
    private var networkSettings: NetworkSettings = NetworkSettings()

    private val modelFile: File get() = modelDirectory.resolve(modelFileName)
    private val partialFile: File get() = modelDirectory.resolve("$modelFileName.part")
    private val checksumFile: File get() = modelDirectory.resolve("$modelFileName.sha256")

    override suspend fun isModelAvailable(): Boolean = withContext(Dispatchers.IO) {
        verifiedModelPath() != null
    }

    override suspend fun ensureModel(
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): String = downloadMutex.withLock {
        withContext(Dispatchers.IO) {
            verifiedModelPath()?.let { return@withContext it }
            cancelled.set(false)
            modelDirectory.mkdirs()
            checkAvailableSpace()
            downloadVerifyAndPublishWithRetry(onProgress)
        }
    }

    override fun cancelDownload() {
        cancelled.set(true)
        activeCall.getAndSet(null)?.cancel()
    }

    override fun updateNetworkSettings(settings: NetworkSettings) {
        networkSettings = settings
    }

    private fun verifiedModelPath(): String? {
        if (!modelFile.isFile || modelFile.length() != expectedSizeBytes) return null
        val recordedHash = checksumFile.takeIf(File::isFile)?.readText()?.trim()?.lowercase()
        if (recordedHash == expectedSha256) return modelFile.absolutePath
        val actualHash = sha256(modelFile)
        if (actualHash != expectedSha256) return null
        checksumFile.writeText("$actualHash\n")
        return modelFile.absolutePath
    }

    private fun checkAvailableSpace() {
        val completedBytes = partialFile.takeIf(File::isFile)?.length() ?: 0L
        val requiredBytes = (expectedSizeBytes - completedBytes).coerceAtLeast(0L) + MIN_FREE_MARGIN
        require(modelDirectory.usableSpace >= requiredBytes) {
            "存储空间不足，模型下载至少还需要 ${requiredBytes / (1024 * 1024)} MiB"
        }
    }

    private suspend fun downloadWithRetry(onProgress: (Long, Long) -> Unit) {
        var lastError: Throwable? = null
        repeat(maxRetries) { attempt ->
            if (cancelled.get()) throw CancellationException("模型下载已取消")
            try {
                downloadOnce(onProgress)
                if (partialFile.length() == expectedSizeBytes) return
                throw IllegalStateException("模型下载大小不完整：${partialFile.length()}")
            } catch (error: Throwable) {
                if (cancelled.get() || error is CancellationException) {
                    throw CancellationException("模型下载已取消", error)
                }
                lastError = error
                if (attempt + 1 < maxRetries) delay(500L shl attempt.coerceAtMost(4))
            }
        }
        throw IllegalStateException(lastError?.message ?: "模型下载失败", lastError)
    }

    private suspend fun downloadVerifyAndPublishWithRetry(onProgress: (Long, Long) -> Unit): String {
        var lastError: Throwable? = null
        repeat(maxRetries) { attempt ->
            downloadWithRetry(onProgress)
            try {
                return verifyAndPublish()
            } catch (error: Throwable) {
                if (cancelled.get() || error is CancellationException) throw error
                lastError = error
                if (attempt + 1 < maxRetries) delay(500L shl attempt.coerceAtMost(4))
            }
        }
        throw IllegalStateException(lastError?.message ?: "模型校验失败", lastError)
    }

    private fun downloadOnce(onProgress: (Long, Long) -> Unit) {
        val existingBytes = partialFile.takeIf(File::isFile)?.length() ?: 0L
        if (existingBytes == expectedSizeBytes) return
        if (existingBytes > expectedSizeBytes) partialFile.delete()
        val resumeAt = partialFile.takeIf(File::isFile)?.length() ?: 0L
        val request = Request.Builder()
            .url(downloadUrl)
            .apply { if (resumeAt > 0L) header("Range", "bytes=$resumeAt-") }
            .build()
        val call = buildClient(networkSettings).newCall(request)
        activeCall.set(call)
        try {
            call.execute().use { response ->
                val append = resumeAt > 0L && response.code == 206
                if (!response.isSuccessful || (resumeAt > 0L && response.code !in setOf(200, 206))) {
                    throw IllegalStateException("模型下载 HTTP ${response.code}")
                }
                val body = response.body ?: throw IllegalStateException("模型下载响应为空")
                if (!append && resumeAt > 0L) partialFile.delete()
                val startingBytes = if (append) resumeAt else 0L
                FileOutputStream(partialFile, append).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = startingBytes
                        onProgress(downloaded, expectedSizeBytes)
                        while (true) {
                            if (cancelled.get()) throw CancellationException("模型下载已取消")
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            onProgress(downloaded, expectedSizeBytes)
                        }
                        output.fd.sync()
                    }
                }
            }
        } finally {
            activeCall.compareAndSet(call, null)
        }
    }

    private fun verifyAndPublish(): String {
        require(partialFile.length() == expectedSizeBytes) {
            "模型文件大小校验失败"
        }
        val actualHash = sha256(partialFile)
        if (actualHash != expectedSha256) {
            partialFile.delete()
            throw IllegalStateException("模型 SHA-256 校验失败")
        }
        try {
            Files.move(
                partialFile.toPath(),
                modelFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Throwable) {
            Files.move(partialFile.toPath(), modelFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        checksumFile.writeText("$actualHash\n")
        return modelFile.absolutePath
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun buildClient(settings: NetworkSettings): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(settings.requestTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(settings.requestTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
        when (settings.proxyMode) {
            ProxyMode.DIRECT -> builder.proxy(Proxy.NO_PROXY)
            ProxyMode.SYSTEM -> Unit
            ProxyMode.HTTP, ProxyMode.SOCKS5 -> {
                if (settings.proxyHost.isNotBlank() && settings.proxyPort in 1..65535) {
                    val type = if (settings.proxyMode == ProxyMode.HTTP) Proxy.Type.HTTP else Proxy.Type.SOCKS
                    builder.proxy(Proxy(type, InetSocketAddress(settings.proxyHost, settings.proxyPort)))
                }
            }
        }
        return builder.build()
    }

    private companion object {
        const val MIN_FREE_MARGIN = 32L * 1024 * 1024
    }
}
