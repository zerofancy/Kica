package top.ntutn.kica.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.ntutn.kica.ui.modal.AppDialog
import top.ntutn.kica.ui.modal.LocalModalInteractionState
import top.ntutn.kica.ui.modal.ModalOwner
import androidx.compose.ui.window.DialogProperties
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Instant
import top.ntutn.kica.ui.PlatformBackHandler
import top.ntutn.kica.ui.PlatformVerticalScrollbar
import top.ntutn.kica.ui.component.FluentButton
import top.ntutn.kica.ui.component.FluentCard
import top.ntutn.kica.ui.component.FluentPrimaryButton
import top.ntutn.kica.ui.component.FluentProgressBar
import top.ntutn.kica.ui.component.FluentTextButton
import top.ntutn.kica.update.UpdateDownloadState
import top.ntutn.kica.update.UpdateFeedback
import top.ntutn.kica.update.UpdateHostActions
import top.ntutn.kica.update.UpdateStore

/** Kept outside account-keyed content. Unmounting cancels foreground install preparation only. */
@Composable
internal fun UpdateHost(
    store: UpdateStore,
    actions: UpdateHostActions,
    foreground: Boolean,
    presentationAllowed: Boolean,
) {
    val state by store.state.collectAsState()
    val owner = remember { ModalOwner() }
    val otherModalOpen = LocalModalInteractionState.current?.hasOtherInteractions(owner) == true
    if (!foreground || !presentationAllowed || otherModalOpen || !state.dialogVisible) return
    UpdateDialog(owner, onDismiss = store::dismiss) {
        Text("应用更新", style = FluentTheme.typography.title)
        Text("当前版本 ${store.info.versionName} (${store.info.versionCode})")
        Text(store.info.targetLabel, style = FluentTheme.typography.caption)
        if (state.checking) {
            Text("正在检查更新…")
            FluentProgressBar(Modifier.fillMaxWidth())
        }
        state.feedback?.let { feedback ->
            UpdateFeedbackContent(feedback)
            if (feedback is UpdateFeedback.Failure) {
                RetryButton("重新检查", feedback.error.retryAt, !state.checking, store::check)
            }
        }
        state.available?.takeIf { !state.checking }?.let { release ->
            Text("新版本 ${release.version.name} (${release.version.code})", style = FluentTheme.typography.bodyStrong)
            release.publishedAt?.let { Text("发布时间：$it", style = FluentTheme.typography.caption) }
            Text("安装包：${release.packageSize?.let(::updateByteCount) ?: "大小未知"}")
            Text(
                if (release.checksum == null) "发布方未提供校验和；下载后无法验证摘要。"
                else "下载后核对发布方声明的 ${release.checksum!!.algorithm.name} 摘要。",
                style = FluentTheme.typography.caption,
            )
            release.description?.takeIf { it.isNotBlank() }?.let {
                Text("更新说明", style = FluentTheme.typography.bodyStrong)
                SelectionContainer { Text(it) }
            }
            UpdateDownloadContent(store, state.download, actions)
            if (state.download is UpdateDownloadState.Idle) {
                FluentPrimaryButton(onClick = store::download, modifier = Modifier.fillMaxWidth()) { Text("下载更新") }
            }
            if (!state.downloading && (store.info.android || state.download !is UpdateDownloadState.Completed)) {
                FluentTextButton(onClick = store::browserDownload, enabled = !state.browserOpening) {
                    Text(if (state.browserOpening) "正在打开浏览器…" else "使用浏览器下载")
                }
                if (state.feedback is UpdateFeedback.Failure) {
                    Text("也可复制下载地址到浏览器：", style = FluentTheme.typography.caption)
                    SelectionContainer { Text(release.downloadUrl, style = FluentTheme.typography.caption) }
                }
            }
        }
        FluentTextButton(onClick = store::dismiss) {
            Text(if (state.downloading) "隐藏进度（继续下载）" else "关闭")
        }
    }
}

@Composable
private fun UpdateFeedbackContent(feedback: UpdateFeedback) {
    Text(when (feedback) {
        is UpdateFeedback.Disabled -> feedback.reason
        UpdateFeedback.UpToDate -> "当前无需更新。"
        UpdateFeedback.NoMatchingPackage -> "暂无适用于此平台、架构和安装包类型的更新。"
        is UpdateFeedback.Failure -> feedback.error.message
        UpdateFeedback.BrowserDispatched -> "已交给浏览器；请在浏览器中完成下载和手动安装。"
    })
}

@Composable
private fun UpdateDialog(owner: ModalOwner, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AppDialog(owner = owner, onDismissRequest = onDismiss, properties = DialogProperties(dismissOnBackPress = false)) {
        PlatformBackHandler(true, onDismiss)
        UpdateDialogSurface(content)
    }
}

@Composable
internal fun UpdateDialogSurface(content: @Composable ColumnScope.() -> Unit) {
    FluentCard(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
        val scroll = rememberScrollState()
        Box(Modifier.heightIn(max = 640.dp)) {
            Column(
                Modifier.verticalScroll(scroll).padding(24.dp).padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
            PlatformVerticalScrollbar(scroll, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
internal fun RetryButton(label: String, retryAt: Instant?, enabled: Boolean = true, onClick: () -> Unit) {
    val seconds = retrySeconds(retryAt)
    FluentButton(onClick = onClick, enabled = enabled && seconds == 0L) {
        Text(if (seconds > 0) "$label（${seconds}秒后）" else label)
    }
}

@Composable
private fun retrySeconds(retryAt: Instant?): Long {
    var seconds by remember(retryAt) { mutableLongStateOf(0L) }
    LaunchedEffect(retryAt) {
        do {
            seconds = retryAt?.let {
                ((it - Clock.System.now()).inWholeMilliseconds + 999).div(1000).coerceAtLeast(0)
            } ?: 0L
            if (seconds > 0) delay(250)
        } while (seconds > 0)
    }
    return seconds
}

internal fun updateByteCount(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024 * 1024)} GiB ($bytes 字节)"
    bytes >= 1024L * 1024 -> "${bytes / (1024 * 1024)} MiB ($bytes 字节)"
    bytes >= 1024 -> "${bytes / 1024} KiB"
    else -> "$bytes 字节"
}
