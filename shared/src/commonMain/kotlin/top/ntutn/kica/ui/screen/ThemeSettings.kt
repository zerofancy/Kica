package top.ntutn.kica.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import io.github.composefluent.component.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.model.AppSettings
import top.ntutn.kica.model.ThemePreference
import top.ntutn.kica.resources.*
import top.ntutn.kica.ui.component.FluentChip

@Composable
internal fun ThemeSettingsCard(library: LibraryRepository) {
    val settings by library.settings().collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    SettingCard(stringResource(Res.string.theme)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemePreference.entries.forEach { preference ->
                val label = when (preference) {
                    ThemePreference.SYSTEM -> Res.string.theme_system
                    ThemePreference.LIGHT -> Res.string.theme_light
                    ThemePreference.DARK -> Res.string.theme_dark
                }
                FluentChip(
                    selected = settings.theme == preference,
                    onClick = { scope.launch { library.updateSettings(library.settings().first().copy(theme = preference)) } },
                    label = { Text(stringResource(label)) },
                )
            }
        }
    }
}
