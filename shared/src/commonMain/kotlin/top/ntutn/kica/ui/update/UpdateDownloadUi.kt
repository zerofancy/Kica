package top.ntutn.kica.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import top.ntutn.kica.ui.component.FluentButton
import top.ntutn.kica.ui.component.FluentPrimaryButton
import top.ntutn.kica.ui.component.FluentProgressBar
import top.ntutn.kica.ui.component.FluentTextButton
import top.ntutn.kica.update.UpdateDownloadState
import top.ntutn.kica.update.UpdateHostActions
import top.ntutn.kica.update.UpdateInstallResult
import top.ntutn.kica.update.UpdateStore
import top.ntutn.updater.DownloadProgress
import top.ntutn.updater.DownloadedFile

@Composable
internal fun UpdateDownloadContent(store: UpdateStore, state: UpdateDownloadState, actions: UpdateHostActions) {
    when (state) {
        UpdateDownloadState.Idle -> Unit
        is UpdateDownloadState.Downloading -> {
            Text("正在下载更新…")
            DownloadProgressContent(state.progress)
            FluentButton(onClick = store::cancelDownload) { Text("取消下载") }
        }
        is UpdateDownloadState.Finalizing -> {
            Text(if (store.info.android) "正在校验并保存…" else "正在校验并移到系统下载目录…")
            FluentProgressBar(Modifier.fillMaxWidth())
            Text("正在完成文件交付，请稍候；关闭提示不会中断保存。", style = FluentTheme.typography.caption)
        }
        is UpdateDownloadState.Failed -> {
            Text(state.error.message, color = FluentTheme.colors.system.critical)
            RetryButton("重试下载或保存", state.error.retryAt, onClick = store::retryDownload)
        }
        is UpdateDownloadState.Completed -> CompletedDownloadContent(store, state.file, actions)
    }
}

@Composable
private fun DownloadProgressContent(progress: DownloadProgress) {
    val total = progress.totalBytes?.takeIf { it > 0 }
    Text(if (total == null) "已下载 ${updateByteCount(progress.downloadedBytes)} · 总大小未知"
        else "${updateByteCount(progress.downloadedBytes)} / ${updateByteCount(total)}")
    if (total == null) FluentProgressBar(Modifier.fillMaxWidth())
    else FluentProgressBar((progress.downloadedBytes.toDouble() / total).toFloat().coerceIn(0f, 1f), Modifier.fillMaxWidth())
}

@Composable
internal fun CompletedDownloadContent(store: UpdateStore, file: DownloadedFile, actions: UpdateHostActions) {
    // Scope belongs to the foreground dialog, never to the retained Store/Activity container.
    // Lock/background/dismissal removes it and cancels APK preparation before dispatch.
    val scope = rememberCoroutineScope()
    var busy by remember(file) { mutableStateOf(false) }
    var permissionRequired by remember(file) { mutableStateOf(false) }
    var feedback by remember(file) { mutableStateOf<String?>(null) }
    var missingFile by remember(file) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (store.info.android) "安装包下载完成" else "安装包已保存到系统下载目录", style = FluentTheme.typography.bodyStrong)
        if (!store.info.android) {
            SelectionContainer { Text(file.localPath) }
            Text("请退出 Kica 后手动安装。Windows 请运行安装程序；Linux 请使用系统软件安装器安装 DEB。不会自动运行安装包。")
        }
        feedback?.let { Text(it) }
        if (missingFile) {
            FluentButton(onClick = store::redownload, enabled = !busy) { Text("重新下载安装包") }
        }
        if (store.info.android && permissionRequired) {
            Text("安装更新需要允许 Kica 安装未知来源应用。授权返回后，请再次点击安装。")
            FluentButton(onClick = {
                if (!actions.openInstallSettings()) feedback = "无法打开安装权限设置，请在系统设置中手动授权。"
            }, enabled = !busy) { Text("前往设置") }
        }
        FluentPrimaryButton(
            onClick = {
                busy = true
                feedback = null
                scope.launch {
                    try {
                        if (!file.exists()) {
                            missingFile = true
                            feedback = "安装包已不存在，请重新下载。"
                            return@launch
                        }
                        coroutineContext.ensureActive()
                        if (store.info.android) {
                            when (actions.install(file)) {
                                UpdateInstallResult.Dispatched -> {
                                    permissionRequired = false
                                    feedback = "已交给系统安装器，请在系统界面确认。安装结果以重新启动后的实际版本为准。"
                                }
                                UpdateInstallResult.PermissionRequired -> permissionRequired = true
                                UpdateInstallResult.Failed -> feedback = "无法交给系统安装器，请重试或使用浏览器下载安装包。"
                            }
                        } else actions.exitForManualInstall()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        feedback = "无法完成安装交接；安装包未被删除，请稍后重试。"
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "正在准备…" else if (store.info.android) "安装更新" else "退出并手动安装")
        }
        FluentTextButton(onClick = store::dismiss, enabled = !busy) {
            Text("稍后安装")
        }
    }
}
