package top.ntutn.kica.ui.screen

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.launch
import top.ntutn.kica.model.ReaderMode

internal enum class ReaderCommand {
    SMALL_FORWARD, SMALL_BACKWARD, FORWARD, BACKWARD, START, END,
    NEXT_CHAPTER, PREVIOUS_CHAPTER, TOGGLE_TOOLBAR, HELP,
}

internal fun readerCommand(
    key: Key,
    mode: ReaderMode,
    shift: Boolean = false,
    ctrl: Boolean = false,
    alt: Boolean = false,
    meta: Boolean = false,
): ReaderCommand? {
    if (alt || meta) return null
    if (ctrl) return if (shift) null else when (key) {
        Key.PageDown -> ReaderCommand.NEXT_CHAPTER
        Key.PageUp -> ReaderCommand.PREVIOUS_CHAPTER
        else -> null
    }
    if (shift) return if (key == Key.Spacebar) ReaderCommand.BACKWARD else null
    val vertical = mode == ReaderMode.VERTICAL
    val reverse = mode == ReaderMode.PAGED_RIGHT_TO_LEFT || mode == ReaderMode.DOUBLE_RIGHT_TO_LEFT
    return when (key) {
        Key.DirectionDown -> if (vertical) ReaderCommand.SMALL_FORWARD else ReaderCommand.FORWARD
        Key.DirectionUp -> if (vertical) ReaderCommand.SMALL_BACKWARD else ReaderCommand.BACKWARD
        Key.DirectionRight -> if (vertical) null else if (reverse) ReaderCommand.BACKWARD else ReaderCommand.FORWARD
        Key.DirectionLeft -> if (vertical) null else if (reverse) ReaderCommand.FORWARD else ReaderCommand.BACKWARD
        Key.PageDown, Key.Spacebar -> ReaderCommand.FORWARD
        Key.PageUp -> ReaderCommand.BACKWARD
        Key.MoveHome -> ReaderCommand.START
        Key.MoveEnd -> ReaderCommand.END
        Key.F -> ReaderCommand.TOGGLE_TOOLBAR
        Key.F1 -> ReaderCommand.HELP
        else -> null
    }
}

/** Kept outside the changing page subtree so holding a key cannot skip chapters. */
internal class ReaderKeyState {
    private val pressed = mutableSetOf<Key>()
    private val blocked = mutableSetOf<Key>()
    fun press(key: Key): Boolean = !pressed.add(key)
    fun blockHeldKeys() { blocked.addAll(pressed) }
    fun isBlocked(key: Key): Boolean = key in blocked
    fun release(key: Key) { pressed.remove(key); blocked.remove(key) }
    fun clear() { pressed.clear(); blocked.clear() }
}

/** Desktop also observes releases delivered to popups, avoiding stuck keys after focus changes. */
@Composable
internal expect fun rememberReaderKeyState(): ReaderKeyState

internal class ReaderKeyboardController {
    var execute: ((ReaderCommand, Boolean) -> Unit)? = null
}

internal class ReaderToolbarFocus {
    var focused: FocusRequester? = null
}

@Composable
internal fun Modifier.readerToolbarItem(memory: ReaderToolbarFocus?): Modifier {
    if (memory == null) return this
    val requester = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return focusRequester(requester).bringIntoViewRequester(bringIntoView).onFocusChanged {
        if (it.isFocused) {
            memory.focused = requester
            scope.launch { bringIntoView.bringIntoView() }
        } else if (memory.focused === requester) memory.focused = null
    }
}

@Composable
internal fun BindReaderKeyboard(
    controller: ReaderKeyboardController?,
    execute: (ReaderCommand, Boolean) -> Unit,
) {
    val current = rememberUpdatedState(execute)
    DisposableEffect(controller) {
        val handler: (ReaderCommand, Boolean) -> Unit = { command, repeated -> current.value(command, repeated) }
        controller?.execute = handler
        onDispose {
            if (controller?.execute === handler) controller.execute = null
        }
    }
}

internal fun readerAcceptsRepeat(command: ReaderCommand): Boolean =
    command == ReaderCommand.SMALL_FORWARD || command == ReaderCommand.SMALL_BACKWARD

/** Return a chapter request only when a fresh press starts at an existing boundary. */
internal fun readerChapterDirection(command: ReaderCommand, atStart: Boolean, atEnd: Boolean, repeated: Boolean): Boolean? {
    if (repeated) return null
    return when (command) {
        ReaderCommand.NEXT_CHAPTER -> true
        ReaderCommand.PREVIOUS_CHAPTER -> false
        ReaderCommand.SMALL_FORWARD, ReaderCommand.FORWARD -> true.takeIf { atEnd }
        ReaderCommand.SMALL_BACKWARD, ReaderCommand.BACKWARD -> false.takeIf { atStart }
        else -> null
    }
}
