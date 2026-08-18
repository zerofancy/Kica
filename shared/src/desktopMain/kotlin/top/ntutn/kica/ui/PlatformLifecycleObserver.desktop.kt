package top.ntutn.kica.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener

@Composable
internal actual fun PlatformLifecycleObserver(
    onBackground: () -> Unit,
    onForeground: () -> Unit,
) {
    val currentOnBackground by rememberUpdatedState(onBackground)
    val currentOnForeground by rememberUpdatedState(onForeground)
    DisposableEffect(Unit) {
        val listener = object : WindowFocusListener {
            override fun windowGainedFocus(e: WindowEvent?) {
                currentOnForeground()
            }

            override fun windowLostFocus(e: WindowEvent?) {
                currentOnBackground()
            }
        }
        val windows = java.awt.Window.getWindows().toList()
        if (windows.isNotEmpty()) {
            val target = windows.first()
            target.addWindowFocusListener(listener)
        }
        onDispose {
            val windows = java.awt.Window.getWindows().toList()
            if (windows.isNotEmpty()) {
                windows.first().removeWindowFocusListener(listener)
            }
        }
    }
}
