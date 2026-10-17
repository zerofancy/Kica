package top.ntutn.kica.ui.screen

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.composefluent.component.Icon
import io.github.composefluent.component.Text
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.ArrowLeft
import io.github.composefluent.icons.regular.Maximize
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.model.ReaderMode
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.back
import top.ntutn.kica.resources.fit_height
import top.ntutn.kica.resources.fit_width
import top.ntutn.kica.resources.fullscreen
import top.ntutn.kica.resources.reader_double_ltr
import top.ntutn.kica.resources.reader_double_rtl
import top.ntutn.kica.resources.reader_keyboard
import top.ntutn.kica.resources.reader_ltr
import top.ntutn.kica.resources.reader_mode
import top.ntutn.kica.resources.reader_rtl
import top.ntutn.kica.resources.reader_vertical
import top.ntutn.kica.ui.PlatformHorizontalScrollbar
import top.ntutn.kica.ui.component.FluentChip
import top.ntutn.kica.ui.component.FluentIconButton
import top.ntutn.kica.ui.component.FluentTextButton

@Composable
internal fun ReaderToolbar(
    mode: ReaderMode,
    fit: PageFit,
    isDesktop: Boolean,
    toolbarFocus: ReaderToolbarFocus,
    toolbarScrollState: ScrollState,
    onBack: () -> Unit,
    onModeChange: (ReaderMode) -> Unit,
    onFitChange: (PageFit) -> Unit,
    onHelp: () -> Unit,
    onHide: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xCC202020))
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .focusGroup()
                .horizontalScroll(toolbarScrollState)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FluentIconButton(onClick = onBack, modifier = Modifier.readerToolbarItem(toolbarFocus)) {
                Icon(Icons.Regular.ArrowLeft, contentDescription = stringResource(Res.string.back), tint = Color.White)
            }
            Text(stringResource(Res.string.reader_mode), color = Color.White)
            ReaderModeButton(mode == ReaderMode.VERTICAL, Res.string.reader_vertical, toolbarFocus) {
                onModeChange(ReaderMode.VERTICAL)
            }
            ReaderModeButton(mode == ReaderMode.PAGED_LEFT_TO_RIGHT, Res.string.reader_ltr, toolbarFocus) {
                onModeChange(ReaderMode.PAGED_LEFT_TO_RIGHT)
            }
            ReaderModeButton(mode == ReaderMode.PAGED_RIGHT_TO_LEFT, Res.string.reader_rtl, toolbarFocus) {
                onModeChange(ReaderMode.PAGED_RIGHT_TO_LEFT)
            }
            if (isDesktop) {
                ReaderModeButton(mode == ReaderMode.DOUBLE_LEFT_TO_RIGHT, Res.string.reader_double_ltr, toolbarFocus) {
                    onModeChange(ReaderMode.DOUBLE_LEFT_TO_RIGHT)
                }
                ReaderModeButton(mode == ReaderMode.DOUBLE_RIGHT_TO_LEFT, Res.string.reader_double_rtl, toolbarFocus) {
                    onModeChange(ReaderMode.DOUBLE_RIGHT_TO_LEFT)
                }
            }
            ReaderModeButton(fit == PageFit.WIDTH, Res.string.fit_width, toolbarFocus) {
                onFitChange(PageFit.WIDTH)
            }
            ReaderModeButton(fit == PageFit.HEIGHT, Res.string.fit_height, toolbarFocus) {
                onFitChange(PageFit.HEIGHT)
            }
            if (isDesktop) {
                FluentTextButton(onClick = onHelp, modifier = Modifier.readerToolbarItem(toolbarFocus)) { Text(stringResource(Res.string.reader_keyboard) + " (F1)", color = Color.White) }
            }
            FluentIconButton(onClick = { onHide() }, modifier = Modifier.readerToolbarItem(toolbarFocus)) {
                Icon(Icons.Regular.Maximize, contentDescription = stringResource(Res.string.fullscreen), tint = Color.White)
            }
        }
        PlatformHorizontalScrollbar(toolbarScrollState)
    }
}

@Composable
internal fun ReaderModeButton(selected: Boolean, label: org.jetbrains.compose.resources.StringResource, toolbarFocus: ReaderToolbarFocus? = null, onClick: () -> Unit) {
    FluentChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(label)) },
        modifier = Modifier.readerToolbarItem(toolbarFocus).padding(horizontal = 3.dp),
    )
}
