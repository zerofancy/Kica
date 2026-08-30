package top.ntutn.kica.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.event.MouseEvent

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal actual fun Modifier.imageContextMenuGesture(
    onTriggered: (Offset) -> Unit,
): Modifier {
    val callback = rememberUpdatedState(onTriggered)
    return pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Press) continue
                val awt = event.awtEventOrNull ?: continue
                if (awt.button == MouseEvent.BUTTON3) {
                    val change = event.changes.firstOrNull() ?: continue
                    change.consume()
                    callback.value(change.position)
                }
            }
        }
    }
}
