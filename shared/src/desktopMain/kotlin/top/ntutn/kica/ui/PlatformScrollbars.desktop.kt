package top.ntutn.kica.ui

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal actual fun PlatformHorizontalScrollbar(state: LazyListState, modifier: Modifier) {
    if (state.canScrollBackward || state.canScrollForward) {
        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(8.dp),
            style = themedScrollbarStyle(),
        )
    }
}

@Composable
internal actual fun PlatformHorizontalScrollbar(state: ScrollState, modifier: Modifier) {
    if (state.maxValue > 0) {
        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(8.dp),
            style = themedScrollbarStyle(),
        )
    }
}

@Composable
internal actual fun PlatformVerticalScrollbar(state: LazyListState, modifier: Modifier) {
    if (state.canScrollBackward || state.canScrollForward) {
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            modifier = modifier
                .fillMaxHeight()
                .padding(start = 8.dp)
                .width(8.dp),
            style = themedScrollbarStyle(),
        )
    }
}

@Composable
internal actual fun PlatformVerticalScrollbar(state: LazyGridState, modifier: Modifier) {
    if (state.canScrollBackward || state.canScrollForward) {
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            modifier = modifier
                .fillMaxHeight()
                .padding(start = 8.dp)
                .width(8.dp),
            style = themedScrollbarStyle(),
        )
    }
}

@Composable
internal actual fun PlatformVerticalScrollbar(state: ScrollState, modifier: Modifier) {
    if (state.maxValue > 0) {
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            modifier = modifier
                .fillMaxHeight()
                .padding(start = 8.dp)
                .width(8.dp),
            style = themedScrollbarStyle(),
        )
    }
}

@Composable
private fun themedScrollbarStyle(): ScrollbarStyle = LocalScrollbarStyle.current.copy(
    unhoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
    hoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
)
