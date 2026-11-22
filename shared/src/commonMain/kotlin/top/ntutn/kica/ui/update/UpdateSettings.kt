package top.ntutn.kica.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import top.ntutn.kica.ui.component.FluentButton
import top.ntutn.kica.ui.component.FluentTextButton
import top.ntutn.kica.update.UpdateDownloadState

@Composable
internal fun UpdateSettings() {
    val store = LocalUpdateStore.current ?: return
    val state by store.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("当前版本 ${store.info.versionName} (${store.info.versionCode})")
        Text(store.info.targetLabel, style = FluentTheme.typography.caption)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("启动时检查更新", Modifier.weight(1f))
            Switcher(
                checked = state.autoCheckEnabled,
                onCheckStateChange = store::setAutoCheckEnabled,
            )
        }
        Text(
            if (store.info.enabled) "仅自动检查，不会自动下载或安装。更新使用独立网络，不继承业务代理设置。"
            else store.info.disabledReason ?: "此安装来源暂不支持更新检查。",
            style = FluentTheme.typography.caption,
            color = FluentTheme.colors.text.text.secondary,
        )
        FluentButton(onClick = store::check, enabled = !state.checking && !state.downloading) {
            Text(when {
                state.checking -> "正在检查…"
                state.download is UpdateDownloadState.Completed -> "查看已下载的更新"
                else -> "检查更新"
            })
        }
        if (state.download !is UpdateDownloadState.Idle && state.download !is UpdateDownloadState.Completed) {
            FluentTextButton(onClick = store::show) {
                Text(if (state.downloading) "查看下载进度" else "查看更新下载")
            }
        }
    }
}
