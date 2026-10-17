package top.ntutn.kica.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import io.github.composefluent.surface.Card
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.model.PageRef
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.copy
import top.ntutn.kica.resources.save
import top.ntutn.kica.ui.PlatformBackHandler
import top.ntutn.kica.ui.component.FluentTextButton

internal data class ImageContextMenuState(
    val page: PageRef,
    val pageWindowOrigin: IntOffset,
    val clickLocalOffset: IntOffset,
)

@Composable
internal fun ReaderImageContextMenu(
    menu: ImageContextMenuState,
    onDismiss: () -> Unit,
    onCopy: (PageRef) -> Unit,
    onSave: (PageRef) -> Unit,
) {
    val popupOrigin = menu.pageWindowOrigin + menu.clickLocalOffset
    Popup(
        alignment = Alignment.TopStart,
        offset = popupOrigin,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, clippingEnabled = false),
    ) {
        PlatformBackHandler(enabled = true, onBack = onDismiss)
        Box(
            Modifier
                .width(IntrinsicSize.Min),
        ) {
            Card(
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .padding(4.dp)
                    .background(FluentTheme.colors.background.solid.base, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
            ) {
                val menuFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { menuFocus.requestFocus() }
                Column(Modifier.width(IntrinsicSize.Max).readerMenuKeys()) {
                    FluentTextButton(
                        onClick = {
                            onDismiss()
                            onCopy(menu.page)
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(menuFocus),
                    ) { Text(stringResource(Res.string.copy)) }
                    FluentTextButton(
                        onClick = {
                            onDismiss()
                            onSave(menu.page)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(Res.string.save)) }
                }
            }
        }
    }
}

@Composable
internal fun ReaderToast(toastMessage: String) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(bottom = 48.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Card(
            modifier = Modifier.padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = toastMessage,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
