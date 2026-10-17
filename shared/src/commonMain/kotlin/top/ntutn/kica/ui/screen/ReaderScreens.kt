package top.ntutn.kica.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.composefluent.component.Icon
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.ArrowExpand
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.data.PicaRepository
import top.ntutn.kica.data.PlatformServices
import top.ntutn.kica.model.ComicDetail
import top.ntutn.kica.model.Episode
import top.ntutn.kica.model.HistoryEntry
import top.ntutn.kica.model.LoadState
import top.ntutn.kica.model.PageRef
import top.ntutn.kica.model.ReaderMode
import top.ntutn.kica.model.ReadingProgress
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.action_failed
import top.ntutn.kica.resources.copy_success
import top.ntutn.kica.resources.fullscreen
import top.ntutn.kica.resources.load_failed
import top.ntutn.kica.resources.reader_first_chapter
import top.ntutn.kica.resources.reader_last_chapter
import top.ntutn.kica.resources.save_success
import top.ntutn.kica.ui.PlatformBackHandler
import top.ntutn.kica.ui.component.EmptyContent
import top.ntutn.kica.ui.component.FluentIconButton
import top.ntutn.kica.ui.component.FluentProgressRing
import top.ntutn.kica.ui.component.LoadStateContent
import top.ntutn.kica.ui.progress
import top.ntutn.kica.ui.toSummary

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
    var currentEpisodeId by remember { mutableStateOf(episodeId) }
    var pendingInitialPage by remember { mutableStateOf<Int?>(null) }
    var contextMenu by remember { mutableStateOf<ImageContextMenuState?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val copySuccessMsg = stringResource(Res.string.copy_success)
    val saveSuccessMsg = stringResource(Res.string.save_success)
    val failedMsg = stringResource(Res.string.action_failed)
    val restoredState by key(comicId, currentEpisodeId) {
        produceState(false to null as ReadingProgress?) {
            value = true to library.readingProgress(comicId, currentEpisodeId)
        }
    }
    val progressLoaded = restoredState.first
    val restoredProgress = restoredState.second
    val comic by produceState<ComicDetail?>(null, comicId) {
        value = runCatching { repository.comic(comicId) }.getOrNull()
    }
    val episodes by produceState<List<Episode>>(emptyList(), comicId) {
        value = runCatching { repository.episodes(comicId) }.getOrDefault(emptyList())
    }
    val episodeTitle by remember(episodes, currentEpisodeId) {
        mutableStateOf(episodes.firstOrNull { it.id == currentEpisodeId }?.title ?: currentEpisodeId)
    }
    val pagesState by key(comicId, currentEpisodeId, refresh) {
        produceState<LoadState<List<PageRef>>>(LoadState.Loading) {
            value = runCatching { repository.pages(comicId, currentEpisodeId) }.fold(
                onSuccess = { LoadState.Data(it) },
                onFailure = { error ->
                    val task = library.downloads().first()
                        .firstOrNull { it.comic.id == comicId && it.episode.id == currentEpisodeId }
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
    }
    val scope = rememberCoroutineScope()
    val currentIndex = episodes.indexOfFirst { it.id == currentEpisodeId }
    val nextEpisode = if (currentIndex >= 0) episodes.getOrNull(currentIndex + 1) else null
    val prevEpisode = episodes.getOrNull(currentIndex - 1)
    val canNext = nextEpisode != null
    val canPrev = prevEpisode != null
    var chapterChanging by remember { mutableStateOf(false) }
    var currentReadPage by remember(currentEpisodeId) { mutableStateOf<Int?>(null) }
    val bodyFocus = remember { FocusRequester() }
    val toolbarFocus = remember { ReaderToolbarFocus() }
    var bodyFocused by remember { mutableStateOf(false) }
    var keyboardInteraction by remember { mutableStateOf(false) }
    var helpVisible by remember { mutableStateOf(false) }
    var restoreToolbarFocus by remember { mutableStateOf<FocusRequester?>(null) }
    var nativeDialogActive by remember { mutableStateOf(false) }
    val keyboard = remember { ReaderKeyboardController() }
    val keyState = rememberReaderKeyState()
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val firstChapterMessage = stringResource(Res.string.reader_first_chapter)
    val lastChapterMessage = stringResource(Res.string.reader_last_chapter)
    val onChangeChapter: (Boolean) -> Unit = { forward ->
        val target = if (forward) nextEpisode else prevEpisode
        if (target != null && !chapterChanging) {
            chapterChanging = true
            keyState.blockHeldKeys()
            pendingInitialPage = if (forward) 0 else Int.MAX_VALUE
            currentEpisodeId = target.id
        }
    }
    val keyboardChangeChapter: (Boolean) -> Unit = { forward ->
        if (currentIndex >= 0 && !chapterChanging) {
            if (if (forward) canNext else canPrev) onChangeChapter(forward)
            else toast = if (forward) lastChapterMessage else firstChapterMessage
        }
    }
    val initialPage = pendingInitialPage ?: currentReadPage ?: restoredProgress?.pageIndex ?: 0
    val readingEpisodeId = currentEpisodeId
    val readingMode = mode
    val savePage: (Int) -> Unit = { page ->
        if (readingEpisodeId == currentEpisodeId && readingMode == mode) {
            currentReadPage = page
            pendingInitialPage = null
            chapterChanging = false
        }
        val saved = progress(comicId, readingEpisodeId, page, readingMode)
        val savedTitle = episodeTitle
        scope.launch {
            library.saveProgress(saved)
            comic?.toSummary()?.let { summary ->
                library.addHistory(
                    HistoryEntry(
                        comic = summary,
                        episodeId = saved.episodeId,
                        episodeTitle = savedTitle,
                        pageIndex = page,
                        updatedAtEpochMillis = saved.updatedAtEpochMillis,
                    ),
                )
            }
        }
    }
    LaunchedEffect(restoredProgress) {
        if (pendingInitialPage == null) {
            restoredProgress?.let { mode = it.mode }
        }
    }
    LaunchedEffect(mode) {
        fit = when (mode) {
            ReaderMode.VERTICAL -> PageFit.WIDTH
            ReaderMode.PAGED_LEFT_TO_RIGHT, ReaderMode.PAGED_RIGHT_TO_LEFT,
            ReaderMode.DOUBLE_LEFT_TO_RIGHT, ReaderMode.DOUBLE_RIGHT_TO_LEFT -> PageFit.HEIGHT
        }
    }
    LaunchedEffect(toast) {
        val msg = toast
        if (!msg.isNullOrBlank()) {
            delay(2_000L)
            if (toast == msg) toast = null
        }
    }

    LaunchedEffect(windowFocused) {
        if (!windowFocused) keyState.clear()
    }
    LaunchedEffect(helpVisible) {
        if (!helpVisible && platformServices.isDesktop) {
            val previousFocus = restoreToolbarFocus
            if (controlsVisible && previousFocus != null) previousFocus.requestFocus() else bodyFocus.requestFocus()
            restoreToolbarFocus = null
        }
    }
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible && platformServices.isDesktop) bodyFocus.requestFocus()
    }
    LaunchedEffect(currentEpisodeId) {
        if (platformServices.isDesktop) bodyFocus.requestFocus()
    }
    val openHelp: () -> Unit = {
        restoreToolbarFocus = if (controlsVisible) toolbarFocus.focused else null
        helpVisible = true
    }
    val dismissMenu: () -> Unit = {
        contextMenu = null
        if (platformServices.isDesktop) bodyFocus.requestFocus()
    }
    val handleCopy: (PageRef) -> Unit = { page ->
        scope.launch {
            val ok = runCatching { platformServices.copyImage(page) }.getOrDefault(false)
            toast = if (ok) copySuccessMsg else failedMsg
        }
    }
    val handleSave: (PageRef) -> Unit = { page ->
        scope.launch {
            nativeDialogActive = true
            try {
                val ok = runCatching { platformServices.saveImage(page) }.getOrDefault(false)
                toast = if (ok) saveSuccessMsg else failedMsg
            } finally { nativeDialogActive = false }
        }
    }
    val onImageMenu: (PageRef, IntOffset, IntOffset) -> Unit = { page, origin, offset ->
        contextMenu = ImageContextMenuState(page, origin, offset)
    }
    PlatformBackHandler(enabled = platformServices.isDesktop && !controlsVisible) { controlsVisible = true }
    val returnToBodyAfterMouse: () -> Unit = {
        if (platformServices.isDesktop && !keyboardInteraction) bodyFocus.requestFocus()
    }

    val toolbarScrollState = rememberScrollState()
    Column(Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { event ->
        if (!platformServices.isDesktop) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyUp) {
            keyState.release(event.key)
            return@onPreviewKeyEvent false
        }
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        keyboardInteraction = true
        val repeated = keyState.press(event.key)
        val command = readerCommand(event.key, mode, event.isShiftPressed, event.isCtrlPressed, event.isAltPressed, event.isMetaPressed)
            ?: return@onPreviewKeyEvent false
        if (!windowFocused || helpVisible || contextMenu != null || nativeDialogActive) return@onPreviewKeyEvent false
        when (command) {
            ReaderCommand.TOGGLE_TOOLBAR -> { if (!repeated) controlsVisible = !controlsVisible; true }
            ReaderCommand.HELP -> { if (!repeated) openHelp(); true }
            else -> if (bodyFocused) {
                if (!chapterChanging && !keyState.isBlocked(event.key) && (!repeated || readerAcceptsRepeat(command))) {
                    keyboard.execute?.invoke(command, repeated)
                }
                true
            } else false
        }
    }.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            keyboardInteraction = false
        }
    }) {
        if (controlsVisible) {
            ReaderToolbar(
                mode = mode,
                fit = fit,
                isDesktop = platformServices.isDesktop,
                toolbarFocus = toolbarFocus,
                toolbarScrollState = toolbarScrollState,
                onBack = onBack,
                onModeChange = { mode = it; returnToBodyAfterMouse() },
                onFitChange = { fit = it; returnToBodyAfterMouse() },
                onHelp = openHelp,
                onHide = { controlsVisible = false },
            )
        }
        Box(Modifier.weight(1f)
            .then(if (platformServices.isDesktop) Modifier
                .focusRequester(bodyFocus)
                .onFocusChanged { bodyFocused = it.isFocused }
                .focusable()
                .border(if (bodyFocused) 1.dp else 0.dp, if (bodyFocused) Color(0xFF77AAFF) else Color.Transparent)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        bodyFocus.requestFocus()
                    }
                } else Modifier)) {
            LoadStateContent(pagesState, onRetry = { refresh++ }) { pages ->
                key(currentEpisodeId, mode, refresh) {
                    when {
                        !progressLoaded -> FluentProgressRing(Modifier.align(Alignment.Center))
                        pages.isEmpty() -> EmptyContent()
                        mode == ReaderMode.VERTICAL -> VerticalReader(pages, initialPage, fit, savePage, canNext, canPrev, onChangeChapter, onImageMenu, keyboard.takeIf { platformServices.isDesktop }, keyboardChangeChapter)
                        mode == ReaderMode.DOUBLE_LEFT_TO_RIGHT || mode == ReaderMode.DOUBLE_RIGHT_TO_LEFT ->
                            DoubleReader(pages, initialPage, mode == ReaderMode.DOUBLE_RIGHT_TO_LEFT, fit, savePage, canNext, canPrev, onChangeChapter, onImageMenu, keyboard.takeIf { platformServices.isDesktop }, keyboardChangeChapter)
                        else -> PagedReader(pages, initialPage, mode == ReaderMode.PAGED_RIGHT_TO_LEFT, fit, savePage, canNext, canPrev, onChangeChapter, onImageMenu, keyboard.takeIf { platformServices.isDesktop }, keyboardChangeChapter)
                    }
                }
            }
            if (!controlsVisible) {
                FluentIconButton(
                    onClick = {
                        controlsVisible = true
                        if (platformServices.isDesktop) bodyFocus.requestFocus()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding(),
                ) {
                    Icon(Icons.Regular.ArrowExpand, contentDescription = stringResource(Res.string.fullscreen), tint = Color.White)
                }
            }
            contextMenu?.let { menu ->
                ReaderImageContextMenu(menu, dismissMenu, handleCopy, handleSave)
            }
            toast?.takeIf { it.isNotBlank() }?.let { toastMessage ->
                ReaderToast(toastMessage)
            }
        }
    }
    if (helpVisible) ReaderKeyboardHelp(mode = mode, onDismiss = { helpVisible = false })
}
