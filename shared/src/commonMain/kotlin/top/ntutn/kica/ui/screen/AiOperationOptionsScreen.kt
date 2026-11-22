package top.ntutn.kica.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Mica
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import io.github.composefluent.surface.Card
import top.ntutn.kica.ui.PlatformVerticalScrollbar
import top.ntutn.kica.ui.component.SectionTitle
import top.ntutn.kica.ui.navigation.AiOperationOptionsComponent

/** The standalone Activity supplies the platform-specific SAEP policy bridge. */
@Composable
fun AiOperationOptionsScreen(
    component: AiOperationOptionsComponent,
    onToggle: (Boolean) -> Unit,
) {
    val state by component.state.collectAsState()
    val settings = state.settings
    val scrollState = rememberScrollState()
    // Only the platform side effect remains here. The retained store owns settings writes and
    // keeps an unacknowledged request across Activity recreation, without retaining that Activity.
    LaunchedEffect(state.pendingPolicy?.id) {
        state.pendingPolicy?.let { request ->
            onToggle(request.enabled)
            component.policyApplied(request.id)
        }
    }

    Mica(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(scrollState).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTitle("AI 操作选项")
                Card(modifier = Modifier.fillMaxSize()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("允许 GUI Agent 操作本应用", style = FluentTheme.typography.bodyStrong)
                        Text(
                            "开启后，受信任的 Agent 可在本应用页面执行截图、点击等自动化操作。" +
                                "关闭后，所有 Agent 操作将被系统拦截。",
                            style = FluentTheme.typography.caption,
                            color = FluentTheme.colors.text.text.secondary,
                        )
                        Switcher(
                            checked = settings.aiOperationEnabled,
                            onCheckStateChange = component::setEnabled,
                            enabled = !state.busy,
                        )
                        Text(
                            if (settings.aiOperationEnabled) "当前状态：已允许 AI 操作" else "当前状态：已禁止 AI 操作",
                            style = FluentTheme.typography.caption,
                        )
                    }
                }
                state.error?.let { Text(it, color = FluentTheme.colors.system.critical) }
            }
            PlatformVerticalScrollbar(scrollState, Modifier.align(Alignment.CenterEnd))
        }
    }
}
