package top.ntutn.kica.ui

import androidx.compose.runtime.Composable

@Composable
internal actual fun PlatformCompatibleTextContextMenu(
    content: @Composable () -> Unit,
) {
    content()
}
