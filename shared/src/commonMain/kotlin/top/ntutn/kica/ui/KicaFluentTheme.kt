package top.ntutn.kica.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.darkColors
import io.github.composefluent.lightColors
import top.ntutn.kica.model.ThemePreference

@OptIn(ExperimentalFluentApi::class)
@Composable
fun KicaFluentTheme(
    preference: ThemePreference = ThemePreference.SYSTEM,
    forceDarkSystemBars: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (preference) {
        ThemePreference.SYSTEM -> systemDark
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val fluentColors = remember(dark) {
        if (dark) darkColors(Color(0xFF0F6CBD)) else lightColors(Color(0xFF0F6CBD))
    }

    PlatformSystemBars(dark || forceDarkSystemBars)

    FluentTheme(
        colors = fluentColors,
        compactMode = true,
        useAcrylicPopup = false,
    ) {
        PlatformCompatibleTextContextMenu {
            content()
        }
    }
}
