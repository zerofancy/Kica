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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.data.PlatformServices
import top.ntutn.kica.model.PageRef
import top.ntutn.kica.model.ReaderMode
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.action_failed
import top.ntutn.kica.resources.copy_success
import top.ntutn.kica.resources.fullscreen
import top.ntutn.kica.resources.reader_first_chapter
import top.ntutn.kica.resources.reader_last_chapter
import top.ntutn.kica.resources.reader_progress_adjusted
import top.ntutn.kica.resources.save_success
import top.ntutn.kica.ui.PlatformBackHandler
import top.ntutn.kica.ui.component.EmptyContent
import top.ntutn.kica.ui.component.FluentIconButton
import top.ntutn.kica.ui.component.LoadStateContent

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReaderContent(
    store: ReaderStore,
    ui: ReaderUiState,
    uiToken: Any,
    platformServices: PlatformServices,
    onBack: () -> Unit,
) {
    val mode = ui.mode
    val fit = ui.fit
    val currentEpisodeId = ui.episodeId
    val episodes = ui.episodes
    val chapterChanging = ui.chapterChanging
    val pagesState = ui.pages
    val initialPage = ui.initialPage
    var controlsVisible by remember { mutableStateOf(true) }
    var contextMenu by remember { mutableStateOf<ImageContextMenuState?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val copySuccessMsg = stringResource(Res.string.copy_success)
    val saveSuccessMsg = stringResource(Res.string.save_success)
    val failedMsg = stringResource(Res.string.action_failed)
    val scope = rememberCoroutineScope()
    val currentIndex = episodes.indexOfFirst { it.id == currentEpisodeId }
    val canNext = currentIndex >= 0 && episodes.getOrNull(currentIndex + 1) != null
    val canPrev = currentIndex > 0
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
        if (store.changeChapter(forward)) keyState.blockHeldKeys()
    }
    val keyboardChangeChapter: (Boolean) -> Unit = { forward ->
        if (currentIndex >= 0 && !chapterChanging) {
            if (if (forward) canNext else canPrev) onChangeChapter(forward)
            else toast = if (forward) lastChapterMessage else firstChapterMessage
        }
    }
    val readingEpisodeId = currentEpisodeId
    val generation = ui.layoutGeneration
    val savePage: (Int) -> Unit = { page ->
        store.reportPage(store.entryId, readingEpisodeId, generation, page, uiToken)
    }
    val adjustedMessage = stringResource(Res.string.reader_progress_adjusted)
    // Transient, localized presentation only; adjustment detection belongs to the store.
    LaunchedEffect(ui.adjusted) { if (ui.adjusted) toast = adjustedMessage }
    DisposableEffect(keyState) { onDispose { keyState.clear() } }
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
            val ok = try { platformServices.copyImage(page) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { false }
            if (store.isBound(uiToken)) toast = if (ok) copySuccessMsg else failedMsg
        }
    }
    val handleSave: (PageRef) -> Unit = { page ->
        scope.launch {
            nativeDialogActive = true
            try {
                val ok = try { platformServices.saveImage(page) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { false }
                if (store.isBound(uiToken)) toast = if (ok) saveSuccessMsg else failedMsg
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
                onModeChange = {
                    store.changeMode(it)
                    returnToBodyAfterMouse()
                },
                onFitChange = { store.changeFit(it); returnToBodyAfterMouse() },
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
            LoadStateContent(pagesState, onRetry = store::retryPages) { pages ->
                key(store.entryId, currentEpisodeId, mode, generation) {
                    when {
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
