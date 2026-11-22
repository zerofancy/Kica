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
import androidx.compose.runtime.collectAsState
import com.arkivanov.essenty.instancekeeper.getOrCreate
import top.ntutn.kica.ui.navigation.PageComponent
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import top.ntutn.kica.ui.modal.AppDialog
import androidx.compose.ui.window.DialogProperties
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.data.PlatformServices
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
internal fun ReaderScreen(page: PageComponent, onBack: () -> Unit) {
    val store = remember(page) {
        val route = page.route as AppRoute.Reader
        val snapshot = page.stateKeeper.consume("reader", ReaderSnapshot.serializer())
        val retained = page.instanceKeeper.getOrCreate("reader-store") {
            ReaderStore(page.entryId, route.comicId, route.episodeId,
                page.services.pica, page.services.library, page.session.progressWriter,
                page.services.platform.fileLocationProvider, snapshot)
        }
        if (!page.stateKeeper.isRegistered("reader")) {
            page.stateKeeper.register("reader", ReaderSnapshot.serializer()) { retained.snapshot() }
        }
        retained
    }
    ReaderStoreScreen(store, page.services.platform, onBack)
}

/** UI shell is intentionally rebound on every composition/host recreation. */
@Composable
internal fun ReaderStoreScreen(store: ReaderStore, platformServices: PlatformServices, onBack: () -> Unit) {
    val ui by store.state.collectAsState()
    val token = remember(store) { Any() }
    var attached by remember(store, token) { mutableStateOf(false) }
    DisposableEffect(store, token) {
        store.bind(token)
        store.ensureLoaded()
        attached = true
        onDispose { store.unbind(token) }
    }
    // Bind advances the layout generation. Never mount a retained Ready layout using the
    // pre-effect snapshot: its initial positioned-page callback would necessarily be stale.
    // collectAsState may deliver the binding update a frame after the local attachment flag.
    if (!attached || !store.isBound(token) || ui.layoutGeneration != store.state.value.layoutGeneration) {
        Box(Modifier.fillMaxSize().background(FluentTheme.colors.background.solid.base), contentAlignment = Alignment.Center) {
            FluentProgressRing()
        }
        return
    }
    val cancel = { if (store.cancel()) onBack() }
    when (val state = ui.entry) {
        is ReaderEntryState.Ready -> ReaderContent(store, ui, token, platformServices, cancel)
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
                        FluentPrimaryButton(store::retryEntry) { Text(stringResource(Res.string.retry)) }
                        FluentTextButton(cancel) { Text(stringResource(Res.string.cancel)) }
                    }
                }
            }
            if (state is ReaderEntryState.Choice) {
                ReaderProgressDialog(
                    title = ui.episodes.firstOrNull { it.id == ui.episodeId }?.title ?: ui.episodeId,
                    page = state.progress.pageIndex.toLong() + 1,
                    onContinue = { store.choose(true) },
                    onStartOver = { store.choose(false) },
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
    AppDialog(onDismissRequest = onCancel, properties = DialogProperties(dismissOnBackPress = false)) {
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
