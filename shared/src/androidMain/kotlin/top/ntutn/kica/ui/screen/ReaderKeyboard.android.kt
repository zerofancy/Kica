package top.ntutn.kica.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberReaderKeyState(): ReaderKeyState = remember { ReaderKeyState() }
