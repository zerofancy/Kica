package top.ntutn.kica.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Mica
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import io.github.composefluent.surface.Card
import kotlinx.coroutines.launch
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.model.AppSettings
import top.ntutn.kica.ui.component.SectionTitle

/**
 * Shared UI for the AI operation options screen.
 *
 * The host Activity (Android-only) supplies [onToggle] to persist the policy change
 * through the platform-specific SAEP bridge.
 */
@Composable
fun AiOperationOptionsScreen(
    library: LibraryRepository,
    onToggle: (Boolean) -> Unit,
) {
    val settings by library.settings().collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Mica(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("AI 操作选项")

            Card(modifier = Modifier.fillMaxSize()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "允许 GUI Agent 操作本应用",
                        style = FluentTheme.typography.bodyStrong,
                    )
                    Text(
                        "开启后，受信任的 Agent 可在本应用页面执行截图、点击等自动化操作。" +
                            "关闭后，所有 Agent 操作将被系统拦截。",
                        style = FluentTheme.typography.caption,
                        color = FluentTheme.colors.text.text.secondary,
                    )
                    Switcher(
                        checked = settings.aiOperationEnabled,
                        onCheckStateChange = { enabled ->
                            scope.launch {
                                library.updateSettings(settings.copy(aiOperationEnabled = enabled))
                                onToggle(enabled)
                            }
                        },
                    )
                    Text(
                        if (settings.aiOperationEnabled) "当前状态：已允许 AI 操作"
                        else "当前状态：已禁止 AI 操作",
                        style = FluentTheme.typography.caption,
                    )
                }
            }
        }
    }
}
