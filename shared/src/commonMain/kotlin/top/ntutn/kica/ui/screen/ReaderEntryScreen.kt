package top.ntutn.kica.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.data.PicaRepository
import top.ntutn.kica.data.PlatformServices
import top.ntutn.kica.model.Episode
import top.ntutn.kica.model.AppRoute
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.back
import top.ntutn.kica.resources.cancel
import top.ntutn.kica.resources.reader_continue
import top.ntutn.kica.resources.reader_progress_failed
import top.ntutn.kica.resources.reader_progress_message
import top.ntutn.kica.resources.reader_progress_title
import top.ntutn.kica.resources.reader_start_over
import top.ntutn.kica.resources.retry
import top.ntutn.kica.ui.PlatformBackHandler
import top.ntutn.kica.ui.PlatformVerticalScrollbar
import top.ntutn.kica.ui.component.FluentButton
import top.ntutn.kica.ui.component.FluentCard
import top.ntutn.kica.ui.component.FluentPrimaryButton
import top.ntutn.kica.ui.component.FluentProgressRing
import top.ntutn.kica.ui.component.FluentTextButton

@Composable
internal fun ReaderScreen(
    comicId: String,
    episodeId: String,
    repository: PicaRepository,
    library: LibraryRepository,
    platformServices: PlatformServices,
    onBack: () -> Unit,
) = key(comicId, episodeId, library) {
    val retainedSessions = LocalReaderSessions.current
    val memory = remember {
        retainedSessions?.getOrPut(AppRoute.Reader(comicId, episodeId)) { ReaderSessionMemory() } ?: ReaderSessionMemory()
    }
    val entry = memory.entry
    var retry by remember { mutableIntStateOf(0) }
    val cancel = { if (entry.cancel()) onBack() }
    DisposableEffect(entry) { onDispose { entry.interruptRead() } }
    LaunchedEffect(entry, retry) { entry.readProgress { library.readingProgress(comicId, episodeId) } }
    val episodes by produceState<List<Episode>>(emptyList(), comicId) {
        value = runCatching { repository.episodes(comicId) }.getOrDefault(emptyList())
    }
    when (val state = entry.state) {
        is ReaderEntryState.Ready -> ReaderContent(
            comicId, memory.episodeId ?: episodeId, repository, library, platformServices,
            memory.position ?: state.start, episodes, memory, cancel,
        )
        ReaderEntryState.Cancelled -> Unit
        else -> {
            PlatformBackHandler(true, cancel)
            Box(Modifier.fillMaxSize().background(FluentTheme.colors.background.solid.base)) {
                FluentTextButton(cancel, Modifier.align(Alignment.TopStart).padding(16.dp)) {
                    Text(stringResource(Res.string.back))
                }
                if (state == ReaderEntryState.Checking) {
                    FluentProgressRing(Modifier.align(Alignment.Center))
                } else if (state == ReaderEntryState.Failed) {
                    Column(Modifier.align(Alignment.Center).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(Res.string.reader_progress_failed))
                        FluentPrimaryButton({ retry++ }) { Text(stringResource(Res.string.retry)) }
                        FluentTextButton(cancel) { Text(stringResource(Res.string.cancel)) }
                    }
                }
            }
            if (state is ReaderEntryState.Choice) {
                ReaderProgressDialog(
                    title = episodes.firstOrNull { it.id == episodeId }?.title ?: episodeId,
                    page = state.progress.pageIndex.toLong() + 1,
                    onContinue = { entry.choose(true) },
                    onStartOver = { entry.choose(false) },
                    onCancel = cancel,
                )
            }
        }
    }
}

@Composable
private fun ReaderProgressDialog(
    title: String,
    page: Long,
    onContinue: () -> Unit,
    onStartOver: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(dismissOnBackPress = false)) {
        PlatformBackHandler(true, onCancel)
        val continueFocus = remember { FocusRequester() }
        val scroll = rememberScrollState()
        LaunchedEffect(Unit) { continueFocus.requestFocus() }
        FluentCard(Modifier.widthIn(max = 480.dp).background(FluentTheme.colors.background.solid.base, FluentTheme.shapes.overlay)) {
            // Include actions in the scroll area so even very small windows keep every action reachable.
            Box(Modifier.padding(24.dp).heightIn(max = 440.dp)) {
                Column(
                    Modifier.verticalScroll(scroll).padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stringResource(Res.string.reader_progress_title), style = FluentTheme.typography.subtitle)
                    Text(stringResource(Res.string.reader_progress_message, title, page))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FluentTextButton(onCancel) { Text(stringResource(Res.string.cancel)) }
                        FluentButton(onStartOver) { Text(stringResource(Res.string.reader_start_over)) }
                        FluentPrimaryButton(onContinue, Modifier.focusRequester(continueFocus)) {
                            Text(stringResource(Res.string.reader_continue))
                        }
                    }
                }
                PlatformVerticalScrollbar(scroll, Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}
