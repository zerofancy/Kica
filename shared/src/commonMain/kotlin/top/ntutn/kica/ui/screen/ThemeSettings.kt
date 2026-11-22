package top.ntutn.kica.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import io.github.composefluent.component.Text
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.model.ThemePreference
import top.ntutn.kica.resources.*
import top.ntutn.kica.ui.component.FluentChip

@Composable
internal fun ThemeSettingsCard(store: top.ntutn.kica.ui.store.SettingsStore) {
    val settings by store.settings.collectAsState()
    val scroll = androidx.compose.foundation.rememberScrollState()
    SettingCard(stringResource(Res.string.theme)) {
        Row(modifier = Modifier.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemePreference.entries.forEach { preference ->
                val label = when (preference) {
                    ThemePreference.SYSTEM -> Res.string.theme_system
                    ThemePreference.LIGHT -> Res.string.theme_light
                    ThemePreference.DARK -> Res.string.theme_dark
                }
                FluentChip(
                    selected = settings.theme == preference,
                    onClick = { store.update { it.copy(theme = preference) } },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        top.ntutn.kica.ui.PlatformHorizontalScrollbar(scroll)
    }
}
