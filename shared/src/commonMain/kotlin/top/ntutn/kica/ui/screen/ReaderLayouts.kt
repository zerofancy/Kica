package top.ntutn.kica.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import top.ntutn.kica.model.PageRef
import top.ntutn.kica.ui.PlatformVerticalScrollbar

@Composable
internal fun VerticalReader(
    pages: List<PageRef>,
    initialPage: Int,
    fit: PageFit,
    onPageChanged: (Int) -> Unit,
    canNext: Boolean,
    canPrev: Boolean,
    onChangeChapter: (Boolean) -> Unit,
    onImageMenu: (PageRef, IntOffset, IntOffset) -> Unit,
    keyboard: ReaderKeyboardController? = null,
    keyboardChangeChapter: (Boolean) -> Unit = onChangeChapter,
) {
    val listState = rememberLazyListState()
    var ready by remember { mutableStateOf(false) }
    val startPage = remember { initialPage }
    val pageChanged = rememberUpdatedState(onPageChanged)
    LaunchedEffect(listState, pages) {
        if (pages.isNotEmpty()) {
            listState.scrollToItem(startPage.coerceIn(pages.indices))
            if (startPage == Int.MAX_VALUE) listState.scrollToReaderEnd()
            ready = true
            snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect { pageChanged.value(it) }
        }
    }
    // Images can grow from their placeholder height after End/previous-chapter navigation.
    // Keep the bottom anchored until the next user navigation or pointer interaction.
    var endAnchored by remember { mutableStateOf(initialPage == Int.MAX_VALUE) }
    LaunchedEffect(listState, endAnchored, ready) {
        if (endAnchored && ready) {
            snapshotFlow { listState.layoutInfo }.collect {
                if (endAnchored && listState.canScrollForward) listState.scrollToReaderEnd()
            }
        }
    }
    VerticalReaderKeyboard(keyboard, listState, ready, keyboardChangeChapter) { endAnchored = it }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press || event.type == PointerEventType.Scroll) endAnchored = false
                    }
                }
            }
            .wheelChapterOverflow(
                atStart = { !listState.canScrollBackward },
                atEnd = { !listState.canScrollForward },
                onNext = { if (canNext) onChangeChapter(true) },
                onPrev = { if (canPrev) onChangeChapter(false) },
            )
            .touchChapterOverflow(
                atStart = { !listState.canScrollBackward },
                atEnd = { !listState.canScrollForward },
                isHorizontal = false,
                forwardSign = -1,
                onNext = { if (canNext) onChangeChapter(true) },
                onPrev = { if (canPrev) onChangeChapter(false) },
            ),
    ) {
        PagePreloader(pages, { listState.firstVisibleItemIndex }, fit)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().focusProperties { canFocus = keyboard == null }) {
            items(pages, key = { it.index }) { page ->
                ZoomablePage(page, Modifier.fillMaxWidth().heightIn(min = 480.dp), fit, onImageMenu)
            }
        }
        PlatformVerticalScrollbar(listState, Modifier.align(Alignment.CenterEnd))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PagedReader(
    pages: List<PageRef>,
    initialPage: Int,
    reverse: Boolean,
    fit: PageFit,
    onPageChanged: (Int) -> Unit,
    canNext: Boolean,
    canPrev: Boolean,
    onChangeChapter: (Boolean) -> Unit,
    onImageMenu: (PageRef, IntOffset, IntOffset) -> Unit,
    keyboard: ReaderKeyboardController? = null,
    keyboardChangeChapter: (Boolean) -> Unit = onChangeChapter,
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val pagerModifier = Modifier
        .fillMaxSize()
        .focusProperties { canFocus = keyboard == null }
        .mouseWheelPaging(
            pagerState,
            onOverflowForward = { if (canNext) onChangeChapter(true) },
            onOverflowBackward = { if (canPrev) onChangeChapter(false) },
        )
        .touchChapterOverflow(
            atStart = { pagerState.currentPage == 0 },
            atEnd = { pagerState.currentPage >= pages.size - 1 },
            isHorizontal = true,
            forwardSign = if (reverse) 1 else -1,
            onNext = { if (canNext) onChangeChapter(true) },
            onPrev = { if (canPrev) onChangeChapter(false) },
        )
    var ready by remember { mutableStateOf(false) }
    val startPage = remember { initialPage }
    val pageChanged = rememberUpdatedState(onPageChanged)
    LaunchedEffect(pagerState, pages) {
        if (pages.isNotEmpty()) {
            pagerState.scrollToPage(startPage.coerceIn(pages.indices))
            ready = true
            snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect { pageChanged.value(it) }
        }
    }
    PagedReaderKeyboard(keyboard, pagerState, ready, keyboardChangeChapter)
    PagePreloader(pages, { pagerState.currentPage }, fit)
    HorizontalPager(
        state = pagerState,
        reverseLayout = reverse,
        modifier = pagerModifier,
    ) { index ->
        ZoomablePage(pages[index], Modifier.fillMaxSize(), fit, onImageMenu)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DoubleReader(
    pages: List<PageRef>,
    initialPage: Int,
    reverse: Boolean,
    fit: PageFit,
    onPageChanged: (Int) -> Unit,
    canNext: Boolean,
    canPrev: Boolean,
    onChangeChapter: (Boolean) -> Unit,
    onImageMenu: (PageRef, IntOffset, IntOffset) -> Unit,
    keyboard: ReaderKeyboardController? = null,
    keyboardChangeChapter: (Boolean) -> Unit = onChangeChapter,
) {
    val pairs = remember(pages) { pages.chunked(2) }
    val pagerState = rememberPagerState(pageCount = { pairs.size })
    val pagerModifier = Modifier
        .fillMaxSize()
        .focusProperties { canFocus = keyboard == null }
        .mouseWheelPaging(
            pagerState,
            onOverflowForward = { if (canNext) onChangeChapter(true) },
            onOverflowBackward = { if (canPrev) onChangeChapter(false) },
        )
        .touchChapterOverflow(
            atStart = { pagerState.currentPage == 0 },
            atEnd = { pagerState.currentPage >= pairs.size - 1 },
            isHorizontal = true,
            forwardSign = if (reverse) 1 else -1,
            onNext = { if (canNext) onChangeChapter(true) },
            onPrev = { if (canPrev) onChangeChapter(false) },
        )
    var ready by remember { mutableStateOf(false) }
    val startPage = remember { initialPage }
    val pageChanged = rememberUpdatedState(onPageChanged)
    LaunchedEffect(pagerState, pairs) {
        if (pairs.isNotEmpty()) {
            pagerState.scrollToPage((startPage / 2).coerceIn(pairs.indices))
            ready = true
            snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect { pageChanged.value(it * 2) }
        }
    }
    PagedReaderKeyboard(keyboard, pagerState, ready, keyboardChangeChapter)
    PagePreloader(pages, { pagerState.currentPage * 2 }, fit)
    HorizontalPager(state = pagerState, reverseLayout = reverse, modifier = pagerModifier) { index ->
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
            pairs[index].forEach { page ->
                ZoomablePage(page, Modifier.weight(1f).fillMaxHeight(), fit, onImageMenu)
            }
        }
    }
}
