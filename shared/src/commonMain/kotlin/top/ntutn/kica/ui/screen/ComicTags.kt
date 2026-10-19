package top.ntutn.kica.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.dp
import io.github.composefluent.component.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.data.*
import top.ntutn.kica.model.AppSettings
import top.ntutn.kica.resources.*
import top.ntutn.kica.ui.LocalTagResolver
import top.ntutn.kica.ui.PlatformVerticalScrollbar
import top.ntutn.kica.ui.component.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ComicTags(
    comicId: String,
    tags: List<String>?,
    library: LibraryRepository,
    platform: PlatformServices,
    onSearch: (String) -> Unit,
) {
    val settings by library.settings().collectAsState(initial = AppSettings())
    val cleaned = remember(tags) { cleanTags(tags.orEmpty()) }
    val resolver = LocalTagResolver.current
    LaunchedEffect(comicId, tags) { cancellableResult { resolver?.rememberTags(comicId, tags) } }
    var selected by remember(comicId) { mutableStateOf<String?>(null) }
    var selector by remember(comicId) { mutableStateOf(false) }
    var manage by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var returnFocus by remember { mutableStateOf<FocusRequester?>(null) }
    val scope = rememberCoroutineScope()
    val success = stringResource(Res.string.tags_updated)
    val failure = stringResource(Res.string.tags_save_failed)
    val copied = stringResource(Res.string.tags_copied)
    val copyFailed = stringResource(Res.string.tags_copy_failed)
    val blockedKeys = settings.blockedTags.map(::tagKey).toSet()
    val closeMenu = {
        selected = null
        returnFocus?.let { runCatching { it.requestFocus() } }
        Unit
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.tags_title, cleaned.size), Modifier.weight(1f))
            if (cleaned.isNotEmpty()) FluentTextButton(onClick = { selector = true }) {
                Text(stringResource(Res.string.tags_actions))
            }
        }
        if (cleaned.isEmpty()) Text(stringResource(if (tags == null) Res.string.tags_unavailable else Res.string.tags_empty))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            cleaned.forEach { tag ->
                val focus = remember(tag) { FocusRequester() }
                val blocked = tagKey(tag) in blockedKeys
                Box(Modifier.tagMenuGesture(platform.isDesktop) { returnFocus = focus; selected = tag }) {
                FluentChip(
                    selected = blocked,
                    onClick = { if (selected == null) onSearch(tag) },
                    modifier = Modifier.widthIn(max = 400.dp).focusRequester(focus)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                (event.key == Key.Menu || (event.key == Key.F10 && event.isShiftPressed))) {
                                returnFocus = focus
                                selected = tag
                                true
                            } else false
                        },
                    label = { Text(if (blocked) "$tag · ${stringResource(Res.string.tags_blocked)}" else tag) },
                )
                }
            }
        }
        if (cleaned.any { tagKey(it) in blockedKeys }) {
            Text(stringResource(Res.string.tags_detail_blocked))
            FluentTextButton(onClick = { manage = true }) {
                Text(stringResource(Res.string.tags_manage, settings.blockedTags.size))
            }
        }
        feedback?.let { Text(it) }
    }
    if (selector) TagDialog(stringResource(Res.string.tags_actions), { selector = false }) {
        val state = rememberLazyListState()
        Box(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
            LazyColumn(state = state, modifier = Modifier.fillMaxWidth().padding(end = 12.dp)) {
                items(cleaned, key = { tagKey(it) }) { tag ->
                    FluentTextButton(onClick = { selected = tag }, modifier = Modifier.fillMaxWidth()) { Text(tag) }
                }
            }
            PlatformVerticalScrollbar(state, Modifier.align(Alignment.CenterEnd))
        }
    }
    selected?.let { tag ->
        val blocked = tagKey(tag) in blockedKeys
        TagDialog(tag, closeMenu) {
            FluentButton(onClick = { selected = null; selector = false; onSearch(tag) }) {
                Text(stringResource(Res.string.tags_search))
            }
            FluentButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        feedback = if (platform.copyText(tag)) copied else copyFailed
                        closeMenu()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        feedback = copyFailed
                    } finally { busy = false }
                }
            }) { Text(stringResource(Res.string.tags_copy)) }
            FluentButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        library.updateBlockedTags { current ->
                            if (blocked) current.filterNot { tagKey(it) == tagKey(tag) }.toSet()
                            else current + tag
                        }
                        feedback = success
                        closeMenu()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        feedback = failure
                    } finally { busy = false }
                }
            }) { Text(stringResource(if (blocked) Res.string.tags_unblock else Res.string.tags_block)) }
            feedback?.let { Text(it) }
        }
    }
    if (manage) TagBlockDialog(library) { manage = false }
}

@Composable
private fun Modifier.tagMenuGesture(desktop: Boolean, onMenu: () -> Unit): Modifier {
    if (!desktop) return imageContextMenuGesture { onMenu() }
    val callback by rememberUpdatedState(onMenu)
    return pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    event.changes.forEach { it.consume() }
                    callback()
                }
            }
        }
    }
}
