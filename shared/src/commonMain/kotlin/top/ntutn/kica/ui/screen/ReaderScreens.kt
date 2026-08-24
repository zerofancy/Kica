package top.ntutn.kica.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import io.github.composefluent.component.Icon
import io.github.composefluent.component.Text
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.ArrowExpand
import io.github.composefluent.icons.regular.ArrowLeft
import io.github.composefluent.icons.regular.Maximize
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.data.PicaRepository
import top.ntutn.kica.data.PlatformServices
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.ComicDetail
import top.ntutn.kica.model.HistoryEntry
import top.ntutn.kica.model.LoadState
import top.ntutn.kica.model.PageRef
import top.ntutn.kica.model.ReaderMode
import top.ntutn.kica.model.ReadingProgress
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.back
import top.ntutn.kica.resources.downloads
import top.ntutn.kica.resources.episodes
import top.ntutn.kica.resources.fit_height
import top.ntutn.kica.resources.fit_width
import top.ntutn.kica.resources.fullscreen
import top.ntutn.kica.resources.load_failed
import top.ntutn.kica.resources.reader_double_ltr
import top.ntutn.kica.resources.reader_double_rtl
import top.ntutn.kica.resources.reader_ltr
import top.ntutn.kica.resources.reader_mode
import top.ntutn.kica.resources.reader_rtl
import top.ntutn.kica.resources.reader_vertical
import top.ntutn.kica.ui.component.EmptyContent
import top.ntutn.kica.ui.component.FluentChip
import top.ntutn.kica.ui.component.FluentIconButton
import top.ntutn.kica.ui.component.FluentProgressRing
import top.ntutn.kica.ui.progress
import top.ntutn.kica.ui.toSummary
import top.ntutn.kica.ui.PlatformVerticalScrollbar
import top.ntutn.kica.ui.PlatformHorizontalScrollbar
import top.ntutn.kica.ui.component.LoadStateContent

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReaderScreen(
    comicId: String,
    episodeId: String,
    repository: PicaRepository,
    library: LibraryRepository,
    platformServices: PlatformServices,
    onBack: () -> Unit,
) {
    var refresh by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(ReaderMode.VERTICAL) }
    var fit by remember { mutableStateOf(PageFit.WIDTH) }
    var controlsVisible by remember { mutableStateOf(true) }
    val loadFailed = stringResource(Res.string.load_failed)
    val restoredState by produceState(false to null as ReadingProgress?, comicId, episodeId) {
        value = true to library.readingProgress(comicId, episodeId)
    }
    val progressLoaded = restoredState.first
    val restoredProgress = restoredState.second
    val metadata by produceState<Pair<ComicSummary, String>?>(null, comicId, episodeId) {
        value = try {
            val detail = repository.comic(comicId)
            val episodeTitle = repository.episodes(comicId)
                .firstOrNull { it.id == episodeId }
                ?.title
                ?: episodeId
            detail.toSummary() to episodeTitle
        } catch (_: Throwable) {
            null
        }
    }
    val pagesState by produceState<LoadState<List<PageRef>>>(LoadState.Loading, comicId, episodeId, refresh) {
        value = runCatching { repository.pages(comicId, episodeId) }.fold(
            onSuccess = { LoadState.Data(it) },
            onFailure = { error ->
                val task = library.downloads().first()
                    .firstOrNull { it.comic.id == comicId && it.episode.id == episodeId }
                val offline = task?.let {
                    platformServices.fileLocationProvider.downloadedPages(it)
                }.orEmpty()
                if (offline.isNotEmpty()) {
                    LoadState.Data(offline, fromCache = true)
                } else {
                    LoadState.Error(error.message ?: loadFailed)
                }
            },
        )
    }
    val scope = rememberCoroutineScope()
    val initialPage = restoredProgress?.pageIndex ?: 0
    val savePage: (Int) -> Unit = { page ->
        scope.launch {
            val saved = progress(comicId, episodeId, page, mode)
            library.saveProgress(saved)
            metadata?.let { (comic, episodeTitle) ->
                library.addHistory(
                    HistoryEntry(
                        comic = comic,
                        episodeId = episodeId,
                        episodeTitle = episodeTitle,
                        pageIndex = page,
                        updatedAtEpochMillis = saved.updatedAtEpochMillis,
                    ),
                )
            }
        }
    }
    LaunchedEffect(restoredProgress) {
        restoredProgress?.let { mode = it.mode }
    }

    val toolbarScrollState = rememberScrollState()
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        if (controlsVisible) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC202020))
                    .statusBarsPadding(),
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .horizontalScroll(toolbarScrollState)
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FluentIconButton(onClick = onBack) {
                        Icon(Icons.Regular.ArrowLeft, contentDescription = stringResource(Res.string.back), tint = Color.White)
                    }
                    Text(stringResource(Res.string.reader_mode), color = Color.White)
                    ReaderModeButton(mode == ReaderMode.VERTICAL, Res.string.reader_vertical) {
                        mode = ReaderMode.VERTICAL
                    }
                    ReaderModeButton(mode == ReaderMode.PAGED_LEFT_TO_RIGHT, Res.string.reader_ltr) {
                        mode = ReaderMode.PAGED_LEFT_TO_RIGHT
                    }
                    ReaderModeButton(mode == ReaderMode.PAGED_RIGHT_TO_LEFT, Res.string.reader_rtl) {
                        mode = ReaderMode.PAGED_RIGHT_TO_LEFT
                    }
                    if (platformServices.isDesktop) {
                        ReaderModeButton(mode == ReaderMode.DOUBLE_LEFT_TO_RIGHT, Res.string.reader_double_ltr) {
                            mode = ReaderMode.DOUBLE_LEFT_TO_RIGHT
                        }
                        ReaderModeButton(mode == ReaderMode.DOUBLE_RIGHT_TO_LEFT, Res.string.reader_double_rtl) {
                            mode = ReaderMode.DOUBLE_RIGHT_TO_LEFT
                        }
                    }
                    ReaderModeButton(fit == PageFit.WIDTH, Res.string.fit_width) {
                        fit = PageFit.WIDTH
                    }
                    ReaderModeButton(fit == PageFit.HEIGHT, Res.string.fit_height) {
                        fit = PageFit.HEIGHT
                    }
                    FluentIconButton(onClick = { controlsVisible = false }) {
                        Icon(Icons.Regular.Maximize, contentDescription = stringResource(Res.string.fullscreen), tint = Color.White)
                    }
                }
                PlatformHorizontalScrollbar(toolbarScrollState)
            }
        }
        Box(Modifier.weight(1f)) {
            LoadStateContent(pagesState, onRetry = { refresh++ }) { pages ->
                when {
                    !progressLoaded -> FluentProgressRing(Modifier.align(Alignment.Center))
                    pages.isEmpty() -> EmptyContent()
                    mode == ReaderMode.VERTICAL -> VerticalReader(pages, initialPage, fit, savePage)
                    mode == ReaderMode.DOUBLE_LEFT_TO_RIGHT || mode == ReaderMode.DOUBLE_RIGHT_TO_LEFT ->
                        DoubleReader(pages, initialPage, mode == ReaderMode.DOUBLE_RIGHT_TO_LEFT, fit, savePage)
                    else -> PagedReader(pages, initialPage, mode == ReaderMode.PAGED_RIGHT_TO_LEFT, fit, savePage)
                }
            }
            if (!controlsVisible) {
                FluentIconButton(
                    onClick = { controlsVisible = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding(),
                ) {
                    Icon(Icons.Regular.ArrowExpand, contentDescription = stringResource(Res.string.fullscreen), tint = Color.White)
                }
            }
        }
    }
}

internal enum class PageFit {
    WIDTH,
    HEIGHT,
}

internal const val PRELOAD_AHEAD = 3
internal const val PRELOAD_BEHIND = 1

@Composable
internal fun ReaderModeButton(selected: Boolean, label: org.jetbrains.compose.resources.StringResource, onClick: () -> Unit) {
    FluentChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(label)) },
        modifier = Modifier.padding(horizontal = 3.dp),
    )
}

@Composable
internal fun VerticalReader(
    pages: List<PageRef>,
    initialPage: Int,
    fit: PageFit,
    onPageChanged: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(initialPage, pages.size) {
        if (pages.isNotEmpty()) listState.scrollToItem(initialPage.coerceIn(pages.indices))
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect(onPageChanged)
    }
    Box(Modifier.fillMaxSize()) {
        PagePreloader(pages, { listState.firstVisibleItemIndex }, fit)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(pages, key = { it.index }) { page ->
                ZoomablePage(page, Modifier.fillMaxWidth().heightIn(min = 480.dp), fit)
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
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val pagerModifier = Modifier.fillMaxSize().mouseWheelPaging(pagerState)
    LaunchedEffect(initialPage, pages.size) {
        if (pages.isNotEmpty()) pagerState.scrollToPage(initialPage.coerceIn(pages.indices))
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect(onPageChanged)
    }
    PagePreloader(pages, { pagerState.currentPage }, fit)
    HorizontalPager(
        state = pagerState,
        reverseLayout = reverse,
        modifier = pagerModifier,
    ) { index ->
        ZoomablePage(pages[index], Modifier.fillMaxSize(), fit)
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
) {
    val pairs = remember(pages) { pages.chunked(2) }
    val pagerState = rememberPagerState(pageCount = { pairs.size })
    val pagerModifier = Modifier.fillMaxSize().mouseWheelPaging(pagerState)
    LaunchedEffect(initialPage, pairs.size) {
        if (pairs.isNotEmpty()) pagerState.scrollToPage((initialPage / 2).coerceIn(pairs.indices))
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect { onPageChanged(it * 2) }
    }
    PagePreloader(pages, { pagerState.currentPage * 2 }, fit)
    HorizontalPager(state = pagerState, reverseLayout = reverse, modifier = pagerModifier) { index ->
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
            pairs[index].forEach { page ->
                ZoomablePage(page, Modifier.weight(1f).fillMaxHeight(), fit)
            }
        }
    }
}

@Composable
internal fun ZoomablePage(page: PageRef, modifier: Modifier, fit: PageFit) {
    var scale by remember(page.index) { mutableFloatStateOf(1f) }
    Box(
        modifier = modifier.pointerInput(page.index) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } >= 2) {
                        scale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = page.localPath ?: page.imageUrl,
            contentDescription = page.originalName,
            modifier = Modifier.fillMaxSize().scale(scale),
            contentScale = if (fit == PageFit.WIDTH) ContentScale.FillWidth else ContentScale.Fit,
        )
    }
}

@Composable
internal fun PagePreloader(pages: List<PageRef>, currentIndexProvider: () -> Int, fit: PageFit) {
    val platformContext = LocalPlatformContext.current
    val imageLoader = remember(platformContext) { SingletonImageLoader.get(platformContext) }
    val currentSnapshot = remember { mutableIntStateOf(currentIndexProvider()) }
    LaunchedEffect(pages) {
        snapshotFlow { currentIndexProvider() }
            .distinctUntilChanged()
            .collect { currentSnapshot.intValue = it }
    }
    val preloadTargets = remember(pages, currentSnapshot.intValue, fit) {
        val cur = currentSnapshot.intValue.coerceIn(pages.indices)
        val lo = (cur - PRELOAD_BEHIND).coerceAtLeast(0)
        val hi = (cur + PRELOAD_AHEAD).coerceAtMost(pages.lastIndex)
        pages.subList(lo, hi + 1)
    }
    LaunchedEffect(preloadTargets, fit, imageLoader, platformContext) {
        for (page in preloadTargets) {
            val target = page.localPath ?: page.imageUrl ?: continue
            val req = ImageRequest.Builder(platformContext)
                .data(target)
                .build()
            imageLoader.enqueue(req)
        }
    }
}
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.mouseWheelPaging(state: androidx.compose.foundation.pager.PagerState): Modifier {
    val scope = rememberCoroutineScope()
    var paging by remember(state) { mutableStateOf(false) }
    return pointerInput(state, scope) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll) continue

                val scroll = event.changes.firstOrNull()?.scrollDelta ?: continue
                val delta = if (abs(scroll.y) >= abs(scroll.x)) scroll.y else scroll.x
                if (delta != 0f) {
                    event.changes.forEach { it.consume() }
                    if (!paging && state.pageCount > 0) {
                        val direction = if (delta > 0f) 1 else -1
                        val target = (state.currentPage + direction).coerceIn(0, state.pageCount - 1)
                        if (target != state.currentPage) {
                            paging = true
                            scope.launch {
                                try {
                                    state.animateScrollToPage(target)
                                } finally {
                                    paging = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
