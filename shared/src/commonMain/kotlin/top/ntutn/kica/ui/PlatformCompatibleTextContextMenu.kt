package top.ntutn.kica.ui

import androidx.compose.runtime.Composable

@Composable
internal expect fun PlatformCompatibleTextContextMenu(
    content: @Composable () -> Unit,
)
