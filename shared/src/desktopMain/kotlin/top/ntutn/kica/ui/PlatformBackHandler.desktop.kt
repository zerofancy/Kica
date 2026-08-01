package top.ntutn.kica.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

@Composable
internal actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    val entry = remember { DesktopBackHandlerEntry(enabled, onBack) }
    SideEffect {
        entry.enabled = enabled
        entry.onBack = onBack
    }
    DisposableEffect(entry) {
        val keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        if (desktopBackHandlerRegistry.isEmpty) {
            keyboardFocusManager.addKeyEventDispatcher(desktopEscapeDispatcher)
        }
        desktopBackHandlerRegistry.register(entry)
        onDispose {
            desktopBackHandlerRegistry.unregister(entry)
            if (desktopBackHandlerRegistry.isEmpty) {
                keyboardFocusManager.removeKeyEventDispatcher(desktopEscapeDispatcher)
            }
        }
    }
}

internal class DesktopBackHandlerEntry(
    var enabled: Boolean,
    var onBack: () -> Unit,
)

internal class DesktopBackHandlerRegistry {
    private val entries = mutableListOf<DesktopBackHandlerEntry>()

    val isEmpty: Boolean
        get() = entries.isEmpty()

    fun register(entry: DesktopBackHandlerEntry) {
        entries += entry
    }

    fun unregister(entry: DesktopBackHandlerEntry) {
        entries -= entry
    }

    fun handleBack(): Boolean {
        val entry = entries.lastOrNull { it.enabled } ?: return false
        entry.onBack()
        return true
    }
}

private val desktopBackHandlerRegistry = DesktopBackHandlerRegistry()
private val desktopEscapeDispatcher = KeyEventDispatcher { event ->
    event.id == KeyEvent.KEY_PRESSED &&
        event.keyCode == KeyEvent.VK_ESCAPE &&
        desktopBackHandlerRegistry.handleBack()
}
