package top.ntutn.kica.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalWindowInfo
import java.awt.AWTEvent
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent

@Composable
internal actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    val windowInfo = LocalWindowInfo.current
    val entry = remember { DesktopBackHandlerEntry(enabled, onBack) }
    SideEffect {
        entry.enabled = enabled
        entry.onBack = onBack
        entry.windowFocused = { windowInfo.isWindowFocused }
    }
    DisposableEffect(entry) {
        val keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        if (desktopBackHandlerRegistry.isEmpty) {
            escapePress.release()
            keyboardFocusManager.addKeyEventDispatcher(desktopEscapeDispatcher)
            Toolkit.getDefaultToolkit().addAWTEventListener(escapeFocusListener, AWTEvent.WINDOW_FOCUS_EVENT_MASK)
        }
        desktopBackHandlerRegistry.register(entry)
        onDispose {
            desktopBackHandlerRegistry.unregister(entry)
            if (desktopBackHandlerRegistry.isEmpty) {
                keyboardFocusManager.removeKeyEventDispatcher(desktopEscapeDispatcher)
                Toolkit.getDefaultToolkit().removeAWTEventListener(escapeFocusListener)
                escapePress.release()
            }
        }
    }
}

internal class DesktopBackHandlerEntry(
    var enabled: Boolean,
    var onBack: () -> Unit,
) {
    var windowFocused: () -> Boolean = { true }
}

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
        val entry = entries.lastOrNull { it.enabled && it.windowFocused() } ?: return false
        entry.onBack()
        return true
    }
}

private val desktopBackHandlerRegistry = DesktopBackHandlerRegistry()
internal class DesktopEscapePress {
    private var pressed = false
    private var consumed = false

    fun press(onBack: () -> Boolean): Boolean {
        if (!pressed) {
            pressed = true
            consumed = onBack()
        }
        return consumed
    }

    fun release() { pressed = false; consumed = false }
}

private val escapePress = DesktopEscapePress()
private val escapeFocusListener = AWTEventListener { event ->
    // A release can be delivered to another application after switching away.
    // Closing an in-app dialog must keep the latch until the physical release.
    if (event is WindowEvent && event.id == WindowEvent.WINDOW_LOST_FOCUS &&
        event.oppositeWindow == null && event.window.isShowing
    ) escapePress.release()
}
private val desktopEscapeDispatcher = KeyEventDispatcher { event ->
    if (event.keyCode != KeyEvent.VK_ESCAPE) false else when (event.id) {
        KeyEvent.KEY_PRESSED -> escapePress.press(desktopBackHandlerRegistry::handleBack)
        KeyEvent.KEY_RELEASED -> { escapePress.release(); false }
        else -> false
    }
}
