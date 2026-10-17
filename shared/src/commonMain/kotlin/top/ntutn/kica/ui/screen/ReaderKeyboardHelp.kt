package top.ntutn.kica.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.model.ReaderMode
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.reader_keyboard
import top.ntutn.kica.resources.reader_keyboard_close
import top.ntutn.kica.resources.reader_keys_boundary
import top.ntutn.kica.resources.reader_keys_chapters
import top.ntutn.kica.resources.reader_keys_controls
import top.ntutn.kica.resources.reader_keys_focus
import top.ntutn.kica.resources.reader_keys_ltr
import top.ntutn.kica.resources.reader_keys_paging
import top.ntutn.kica.resources.reader_keys_rtl
import top.ntutn.kica.resources.reader_keys_vertical
import top.ntutn.kica.ui.PlatformBackHandler
import top.ntutn.kica.ui.PlatformVerticalScrollbar
import top.ntutn.kica.ui.component.FluentCard
import top.ntutn.kica.ui.component.FluentTextButton

@Composable
internal fun ReaderKeyboardHelp(mode: ReaderMode, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnBackPress = false)) {
        PlatformBackHandler(enabled = true, onBack = onDismiss)
        val closeFocus = remember { FocusRequester() }
        val scroll = rememberScrollState()
        var scrollFocused by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { closeFocus.requestFocus() }
        FluentCard(Modifier.widthIn(max = 620.dp).background(FluentTheme.colors.background.solid.base, FluentTheme.shapes.overlay)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(Res.string.reader_keyboard), style = FluentTheme.typography.subtitle)
                Box(Modifier.weight(1f, fill = false).heightIn(max = 440.dp)) {
                    Column(
                        Modifier.onFocusChanged { scrollFocused = it.isFocused }
                            .border(if (scrollFocused) 1.dp else 0.dp, Color(0xFF77AAFF))
                            .verticalScroll(scroll).padding(end = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val direction = when (mode) {
                            ReaderMode.VERTICAL -> Res.string.reader_keys_vertical
                            ReaderMode.PAGED_RIGHT_TO_LEFT, ReaderMode.DOUBLE_RIGHT_TO_LEFT -> Res.string.reader_keys_rtl
                            else -> Res.string.reader_keys_ltr
                        }
                        Text(stringResource(direction))
                        Text(stringResource(Res.string.reader_keys_paging))
                        Text(stringResource(Res.string.reader_keys_chapters))
                        Text(stringResource(Res.string.reader_keys_controls))
                        Text(stringResource(Res.string.reader_keys_focus))
                        Text(stringResource(Res.string.reader_keys_boundary))
                    }
                    PlatformVerticalScrollbar(scroll, Modifier.align(Alignment.CenterEnd))
                }
                FluentTextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).focusRequester(closeFocus)) {
                    Text(stringResource(Res.string.reader_keyboard_close))
                }
            }
        }
    }
}

@Composable
internal fun Modifier.readerMenuKeys(): Modifier {
    val focus = LocalFocusManager.current
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) false else when (event.key) {
            Key.DirectionDown -> { focus.moveFocus(FocusDirection.Next); true }
            Key.DirectionUp -> { focus.moveFocus(FocusDirection.Previous); true }
            else -> false
        }
    }
}
