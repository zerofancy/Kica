package top.ntutn.kica.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.ntutn.kica.update.UpdateAppInfo
import top.ntutn.kica.update.UpdateRuntime
import top.ntutn.updater.DownloadedFile
import top.ntutn.updater.TargetDetection
import top.ntutn.updater.UpdateVersion
import top.ntutn.updater.UpdaterConfig
import top.ntutn.updater.createDesktopBrowserOpener
import top.ntutn.updater.createDesktopUpdateDownloader
import top.ntutn.updater.createUpdateChecker
import top.ntutn.updater.detectUpdateTarget

internal class DesktopUpdateRuntime(private val dataDirectory: Path) : UpdateRuntime {
    private val automaticClaimed = AtomicBoolean(false)
    private val exports = Mutex()
    private var closed = false // Protected by exports; close also waits for final disk publication.
    private val preferencesFile = dataDirectory.resolve("update-preferences.properties")
    private val autoCheck = MutableStateFlow(runCatching {
        val preferences = Properties()
        if (Files.isRegularFile(preferencesFile)) Files.newInputStream(preferencesFile).use(preferences::load)
        preferences.getProperty("automatic", "true").toBooleanStrictOrNull() ?: true
    }.getOrDefault(true))
    override val autoCheckEnabled = autoCheck.asStateFlow()
    private val target = (detectUpdateTarget() as? TargetDetection.Supported)?.target
    private val reason = when {
        !DesktopBuildMetadata.releaseBuild -> "开发运行或未知安装来源，默认禁用生产更新"
        target == null -> "此桌面进程架构暂不支持更新"
        target.os.name != DesktopBuildMetadata.os || target.arch.name != DesktopBuildMetadata.arch ->
            "当前进程与安装包平台或架构不一致，无法安全检查更新"
        !((DesktopBuildMetadata.os == "Linux" && DesktopBuildMetadata.packageType == "deb") ||
            (DesktopBuildMetadata.os == "Windows" && DesktopBuildMetadata.packageType == "exe")) ->
            "此安装包类型暂不支持更新"
        else -> null
    }
    override val info = UpdateAppInfo(
        DesktopBuildMetadata.versionCode, DesktopBuildMetadata.versionName,
        "${DesktopBuildMetadata.os} / ${DesktopBuildMetadata.arch} / ${DesktopBuildMetadata.packageType}",
        android = false, enabled = reason == null, disabledReason = reason,
    )
    override val checker = if (info.enabled) createUpdateChecker(
        UpdaterConfig(
            "https://zp.ntutn.top", "kica", UpdateVersion(info.versionCode, info.versionName),
            target!!, DesktopBuildMetadata.packageType,
        ),
    ) else null
    override val downloader = if (info.enabled) {
        createDesktopUpdateDownloader(dataDirectory.resolve("updates").toFile())
    } else null
    override val browser = if (info.enabled) createDesktopBrowserOpener() else null

    override suspend fun setAutoCheckEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        Files.createDirectories(dataDirectory)
        val preferences = Properties().apply { setProperty("automatic", enabled.toString()) }
        val temporary = Files.createTempFile(dataDirectory, ".update-preferences-", ".tmp")
        try {
            Files.newOutputStream(temporary).use { preferences.store(it, "Application update preference") }
            try {
                Files.move(temporary, preferencesFile, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, preferencesFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            autoCheck.value = enabled
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    override fun claimAutomaticCheck() = automaticClaimed.compareAndSet(false, true)
    // Keep the final move and returned handle together across cancellation. The outer context keeps
    // the caller's dispatcher so it cannot discard the handle on a cancellable dispatch back to UI.
    override suspend fun exportDownload(file: DownloadedFile): DownloadedFile = withContext(NonCancellable) {
        exports.withLock {
            check(!closed) { "更新运行时已关闭" }
            withContext(Dispatchers.IO) {
                val source = Path.of(file.localPath)
                val size = Files.size(source)
                val destination = moveDownloadToDownloads(source, systemDownloadsDirectory(), file.fileName)
                ExportedUpdateFile(destination, size)
            }
        }
    }

    override suspend fun close() {
        checker?.close()
        downloader?.close()
        downloader?.awaitIdle()
        exports.withLock { closed = true }
    }
}
