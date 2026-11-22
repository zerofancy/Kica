package top.ntutn.kica.update

import kotlinx.coroutines.flow.StateFlow
import top.ntutn.updater.BrowserOpener
import top.ntutn.updater.DownloadedFile
import top.ntutn.updater.UpdateChecker
import top.ntutn.updater.UpdateDownloader

/** Platform/application-owned resources. Never retains an Activity or Window. */
interface UpdateRuntime {
    val info: UpdateAppInfo
    val checker: UpdateChecker?
    val downloader: UpdateDownloader?
    val browser: BrowserOpener?
    val autoCheckEnabled: StateFlow<Boolean>
    suspend fun setAutoCheckEnabled(enabled: Boolean)

    /** Application/process latch, independent of Activity recreation and login sessions. */
    fun claimAutomaticCheck(): Boolean

    /** Desktop moves the completed SDK file to the system Downloads directory; Android returns it unchanged. */
    suspend fun exportDownload(file: DownloadedFile): DownloadedFile

    /** Final application shutdown only, not a settings page or Activity recreation. */
    suspend fun close()
}

data class UpdateAppInfo(
    val versionCode: Long,
    val versionName: String,
    val targetLabel: String,
    val android: Boolean,
    val enabled: Boolean,
    val disabledReason: String? = null,
)

enum class UpdateInstallResult { Dispatched, PermissionRequired, Failed }

/** Current foreground host callbacks; never retained by an application service or Store. */
class UpdateHostActions(
    val install: suspend (DownloadedFile) -> UpdateInstallResult = { UpdateInstallResult.Failed },
    val openInstallSettings: () -> Boolean = { false },
    val exitForManualInstall: () -> Unit = {},
)
