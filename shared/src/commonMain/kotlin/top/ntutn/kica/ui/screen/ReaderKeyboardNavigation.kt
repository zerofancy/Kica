package top.ntutn.kica.ui.screen

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun VerticalReaderKeyboard(
    controller: ReaderKeyboardController?,
    state: LazyListState,
    ready: Boolean,
    changeChapter: (Boolean) -> Unit,
    anchorEnd: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember(state) { mutableStateOf(false) }
    val smallStep = with(LocalDensity.current) { 64.dp.toPx() }
    BindReaderKeyboard(controller) { command, repeated ->
        if (ready && !busy && !state.isScrollInProgress) {
            if (command != ReaderCommand.END) anchorEnd(false)
            val chapter = readerChapterDirection(command, !state.canScrollBackward, !state.canScrollForward, repeated)
            if (chapter != null) {
                changeChapter(chapter)
            } else if (!repeated || readerAcceptsRepeat(command)) {
                busy = true
                scope.launch {
                    try {
                        val viewport = state.layoutInfo.let { it.viewportEndOffset - it.viewportStartOffset }
                        when (command) {
                            ReaderCommand.SMALL_FORWARD -> state.scrollBy(smallStep)
                            ReaderCommand.SMALL_BACKWARD -> state.scrollBy(-smallStep)
                            ReaderCommand.FORWARD -> state.animateScrollBy(viewport * .9f)
                            ReaderCommand.BACKWARD -> state.animateScrollBy(-viewport * .9f)
                            ReaderCommand.START -> state.scrollToItem(0)
                            ReaderCommand.END -> { state.scrollToReaderEnd(); anchorEnd(true) }
                            else -> Unit
                        }
                    } finally { busy = false }
                }
            }
        }
    }
}

internal suspend fun LazyListState.scrollToReaderEnd() {
    val last = layoutInfo.totalItemsCount - 1
    if (last >= 0) {
        scrollToItem(last)
        // A tall last image must end at its bottom, rather than its top edge.
        val lastSize = layoutInfo.visibleItemsInfo.lastOrNull { it.index == last }?.size ?: 0
        scrollBy(lastSize.toFloat())
    }
}

@Composable
internal fun PagedReaderKeyboard(
    controller: ReaderKeyboardController?,
    state: PagerState,
    ready: Boolean,
    changeChapter: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember(state) { mutableStateOf(false) }
    BindReaderKeyboard(controller) { command, repeated ->
        if (ready && !repeated && !busy && !state.isScrollInProgress && state.pageCount > 0) {
            val chapter = readerChapterDirection(command, state.currentPage == 0, state.currentPage == state.pageCount - 1, false)
            if (chapter != null) {
                changeChapter(chapter)
            } else {
                val target = when (command) {
                    ReaderCommand.START -> 0
                    ReaderCommand.END -> state.pageCount - 1
                    ReaderCommand.FORWARD -> state.currentPage + 1
                    ReaderCommand.BACKWARD -> state.currentPage - 1
                    else -> state.currentPage
                }.coerceIn(0, state.pageCount - 1)
                if (target != state.currentPage) {
                    busy = true
                    scope.launch {
                        try { state.animateScrollToPage(target) } finally { busy = false }
                    }
                }
            }
        }
    }
}
