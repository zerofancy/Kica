package top.ntutn.kica.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal actual fun PlatformCompatibleTextContextMenu(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalTextContextMenu provides composeCompatibleTextContextMenu(),
        content = content,
    )
}

@OptIn(ExperimentalFoundationApi::class)
internal fun composeCompatibleTextContextMenu(): TextContextMenu = TextContextMenu.Default
