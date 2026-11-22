package top.ntutn.kica.update

import kotlinx.coroutines.flow.MutableStateFlow
import top.ntutn.updater.BrowserOpenResult
import top.ntutn.updater.BrowserOpener
import top.ntutn.updater.DownloadProgress
import top.ntutn.updater.DownloadResult
import top.ntutn.updater.DownloadedFile
import top.ntutn.updater.UpdateArch
import top.ntutn.updater.UpdateCheckResult
import top.ntutn.updater.UpdateChecker
import top.ntutn.updater.UpdateDownloader
import top.ntutn.updater.UpdateOs
import top.ntutn.updater.UpdateRelease
import top.ntutn.updater.UpdateTarget
import top.ntutn.updater.UpdateVersion
import top.ntutn.updater.UpdaterConfig

internal val testRelease = UpdateRelease(
    software = "kica",
    target = UpdateTarget(UpdateOs.Windows, UpdateArch.X86_64),
    packageType = "exe",
    version = UpdateVersion(4, "0.1.4"),
    downloadUrl = "https://example.com/update.exe",
    softwareDisplayName = "Kica",
    distributionTitle = "Kica 0.1.4",
    packageName = "update.exe",
    distributionId = "release-4",
    packageId = "package-4",
    publishedAt = null,
    packageSize = null,
    descriptionPlainText = "Release notes",
    descriptionMarkdown = null,
)

internal class FakeUpdateRuntime(enabled: Boolean = true) : UpdateRuntime {
    override val info = UpdateAppInfo(3, "0.1.3", "Windows X86_64 exe", false, enabled, "开发版本禁用更新。")
    var checks = 0
    var downloads = 0
    var exports = 0
    var browserCalls = 0
    var closes = 0
    var deletes = 0
    var claimed = false
    var checkBlock: suspend () -> UpdateCheckResult = { UpdateCheckResult.Available(testRelease) }
    var downloadBlock: suspend ((DownloadProgress) -> Unit) -> DownloadResult = { DownloadResult.Completed(FakeUpdateFile()) }
    var exportBlock: suspend (DownloadedFile) -> DownloadedFile = { it }
    var browserBlock: suspend () -> BrowserOpenResult = { BrowserOpenResult.Dispatched }
    override val autoCheckEnabled = MutableStateFlow(true)
    var preferenceBlock: suspend (Boolean) -> Unit = { autoCheckEnabled.value = it }
    override val checker = object : UpdateChecker {
        override val config = UpdaterConfig(
            "https://example.com", "kica", UpdateVersion(3, "0.1.3"), testRelease.target, "exe",
        )
        override suspend fun check(): UpdateCheckResult { checks++; return checkBlock() }
        override fun close() { closes++ }
    }
    override val downloader = object : UpdateDownloader {
        override suspend fun download(release: UpdateRelease, onProgress: (DownloadProgress) -> Unit): DownloadResult {
            downloads++
            return downloadBlock(onProgress)
        }
        override suspend fun delete(file: DownloadedFile) { deletes++ }
        override suspend fun awaitIdle() = Unit
        override fun close() { closes++ }
    }
    override val browser = BrowserOpener { browserCalls++; browserBlock() }
    override suspend fun setAutoCheckEnabled(enabled: Boolean) { preferenceBlock(enabled) }
    override fun claimAutomaticCheck(): Boolean = if (claimed) false else { claimed = true; true }
    override suspend fun exportDownload(file: DownloadedFile): DownloadedFile { exports++; return exportBlock(file) }
    override suspend fun close() { closes++ }
}

internal class FakeUpdateFile(override val localPath: String = "/downloads/update.exe") : DownloadedFile {
    override val fileName = "update.exe"
    override val sizeBytes = 100L
    override suspend fun exists() = true
}
