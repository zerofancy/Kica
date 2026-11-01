package top.ntutn.kica.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.DarkDefaultContextMenuRepresentation
import androidx.compose.foundation.LightDefaultContextMenuRepresentation
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.composefluent.FluentTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal actual fun PlatformCompatibleTextContextMenu(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        // Override inside FluentTheme: its desktop provider installs the old 1.8 menu APIs.
        // https://github.com/compose-fluent/compose-fluent-ui/issues/164#issuecomment-5622011230
        LocalTextContextMenu provides composeCompatibleTextContextMenu(),
        LocalContextMenuRepresentation provides if (FluentTheme.colors.darkMode) {
            DarkDefaultContextMenuRepresentation
        } else {
            LightDefaultContextMenuRepresentation
        },
        content = content,
    )
}

@OptIn(ExperimentalFoundationApi::class)
internal fun composeCompatibleTextContextMenu(): TextContextMenu = TextContextMenu.Default
