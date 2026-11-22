package top.ntutn.kica.ui.modal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/** Registers for the entire window lifetime; keeps native dismissal and caller back handlers intact. */
@Composable
internal fun AppDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    owner: ModalOwner? = LocalModalOwner.current,
    content: @Composable () -> Unit,
) {
    ModalInteraction(owner) {
        Dialog(onDismissRequest = onDismissRequest, properties = properties, content = content)
    }
}

/** Focusable modal menus use the same registry as dialogs, including nested windows. */
@Composable
internal fun AppPopup(
    onDismissRequest: () -> Unit,
    alignment: Alignment = Alignment.TopStart,
    offset: IntOffset = IntOffset.Zero,
    properties: PopupProperties = PopupProperties(focusable = true),
    owner: ModalOwner? = LocalModalOwner.current,
    content: @Composable () -> Unit,
) {
    ModalInteraction(owner) {
        Popup(
            alignment = alignment,
            offset = offset,
            onDismissRequest = onDismissRequest,
            properties = properties,
            content = content,
        )
    }
}
