package top.ntutn.kica.ui

import androidx.compose.runtime.Composable


@Composable
internal expect fun PlatformLifecycleObserver(
    onBackground: () -> Unit,
    onForeground: () -> Unit,
)
