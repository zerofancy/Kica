package top.ntutn.kica.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

@Composable
internal actual fun rememberReaderKeyState(): ReaderKeyState {
    val state = remember { ReaderKeyState() }
    DisposableEffect(state) {
        val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val releases = KeyEventDispatcher { event ->
            if (event.id == KeyEvent.KEY_RELEASED) state.release(Key(event.keyCode, event.keyLocation))
            false
        }
        manager.addKeyEventDispatcher(releases)
        onDispose {
            manager.removeKeyEventDispatcher(releases)
            state.clear()
        }
    }
    return state
}
