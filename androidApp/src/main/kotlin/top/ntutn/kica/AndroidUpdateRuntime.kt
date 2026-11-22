package top.ntutn.kica

import android.content.Context
import android.os.Build
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import top.ntutn.kica.update.UpdateAppInfo
import top.ntutn.kica.update.UpdateRuntime
import top.ntutn.updater.DownloadedFile
import top.ntutn.updater.TargetDetection
import top.ntutn.updater.UpdateVersion
import top.ntutn.updater.UpdaterConfig
import top.ntutn.updater.createAndroidBrowserOpener
import top.ntutn.updater.createAndroidUpdateDownloader
import top.ntutn.updater.createUpdateChecker
import top.ntutn.updater.detectUpdateTarget

/** Process-owned; only the application context may be retained here. */
internal class AndroidUpdateRuntime(context: Context) : UpdateRuntime {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("application_updates", Context.MODE_PRIVATE)
    private val automaticClaimed = AtomicBoolean(false)
    private val autoCheck = MutableStateFlow(preferences.getBoolean("automatic", true))
    override val autoCheckEnabled = autoCheck.asStateFlow()
    private val target = (detectUpdateTarget() as? TargetDetection.Supported)?.target
    @Suppress("DEPRECATION")
    private val installed = app.packageManager.getPackageInfo(app.packageName, 0)
    private val code = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else {
        @Suppress("DEPRECATION")
        installed.versionCode.toLong()
    }
    private val processArch = when (System.getProperty("os.arch")?.lowercase()) {
        "aarch64", "arm64" -> "ARM64"
        "amd64", "x86_64" -> "X86_64"
        else -> null
    }
    private val reason = when {
        BuildConfig.DEBUG -> "开发版本默认禁用生产更新"
        !Process.is64Bit() || target == null || processArch == null -> "此 Android 进程架构暂不支持更新"
        target.arch.name != processArch -> "当前进程与 Android 平台架构不一致，无法安全检查更新"
        code <= 0 -> "无法确认已安装版本"
        else -> null
    }
    override val info = UpdateAppInfo(
        code, installed.versionName ?: "未知", "Android / ${target?.arch ?: "未知"} / apk",
        android = true, enabled = reason == null, disabledReason = reason,
    )
    override val checker = if (info.enabled) createUpdateChecker(
        UpdaterConfig("https://zp.ntutn.top", "kica", UpdateVersion(code, info.versionName), target!!, "apk"),
    ) else null
    override val downloader = if (info.enabled) createAndroidUpdateDownloader(app) else null
    override val browser = if (info.enabled) createAndroidBrowserOpener(app) else null

    override suspend fun setAutoCheckEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        check(preferences.edit().putBoolean("automatic", enabled).commit()) { "保存更新偏好失败" }
        autoCheck.value = enabled
    }

    override fun claimAutomaticCheck() = automaticClaimed.compareAndSet(false, true)
    override suspend fun exportDownload(file: DownloadedFile) = file
    override suspend fun close() {
        checker?.close()
        downloader?.close()
        downloader?.awaitIdle()
    }
}
