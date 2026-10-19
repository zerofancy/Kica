package top.ntutn.kica.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.composefluent.component.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.data.tagKey
import top.ntutn.kica.model.AppSettings
import top.ntutn.kica.resources.*
import top.ntutn.kica.ui.PlatformBackHandler
import top.ntutn.kica.ui.PlatformVerticalScrollbar
import top.ntutn.kica.ui.component.*

@Composable
internal fun TagDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnBackPress = false)) {
        PlatformBackHandler(true, onDismiss)
        BoxWithConstraints {
        val height = maxHeight * 0.9f
        FluentCard {
            val scroll = rememberScrollState()
            Box(Modifier.heightIn(max = height)) {
            Column(
                Modifier.widthIn(max = 540.dp).fillMaxWidth().verticalScroll(scroll).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(title)
                content()
                FluentButton(onClick = onDismiss) { Text(stringResource(Res.string.tags_close)) }
            }
            PlatformVerticalScrollbar(scroll, Modifier.align(Alignment.CenterEnd))
            }
        }
        }
    }
}

@Composable
internal fun TagBlockSettingsEntry(library: LibraryRepository) {
    var open by remember { mutableStateOf(false) }
    val settings by library.settings().collectAsState(initial = AppSettings())
    FluentButton(onClick = { open = true }) { Text(stringResource(Res.string.tags_manage, settings.blockedTags.size)) }
    if (open) TagBlockDialog(library) { open = false }
}

@Composable
internal fun TagBlockDialog(library: LibraryRepository, onDismiss: () -> Unit) {
    val settings by library.settings().collectAsState(initial = AppSettings())
    var input by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val failed = stringResource(Res.string.tags_save_failed)
    val duplicate = stringResource(Res.string.tags_duplicate)
    val saved = stringResource(Res.string.tags_updated)
    val update: ((Set<String>) -> Set<String>) -> Unit = { transform ->
        if (!busy) {
            busy = true
            scope.launch {
                try {
                    library.updateBlockedTags(transform)
                    feedback = saved
                    input = ""
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    feedback = failed
                } finally {
                    busy = false
                }
            }
        }
    }
    TagDialog(stringResource(Res.string.tags_manage, settings.blockedTags.size), onDismiss) {
        Text(stringResource(Res.string.tags_rules_hint))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FluentTextField(input, { input = it }, Modifier.weight(1f), enabled = !busy,
                placeholder = { Text(stringResource(Res.string.tags_input)) })
            FluentButton(enabled = !busy && input.isNotBlank(), onClick = {
                val tag = input.trim()
                if (settings.blockedTags.any { tagKey(it) == tagKey(tag) }) feedback = duplicate
                else update { current -> if (current.any { tagKey(it) == tagKey(tag) }) current else current + tag }
            }) { Text(stringResource(Res.string.tags_add)) }
        }
        FluentTextField(filter, { filter = it }, Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(Res.string.tags_filter)) })
        val shown = settings.blockedTags.filter { tagKey(filter) in tagKey(it) }.sortedBy(::tagKey)
        val listState = rememberLazyListState()
        Box(Modifier.fillMaxWidth().heightIn(min = 64.dp, max = 240.dp)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().padding(end = 12.dp)) {
                if (shown.isEmpty()) item { Text(stringResource(Res.string.tags_rule_empty)) }
                items(shown, key = { it }) { tag ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tag, Modifier.weight(1f))
                        FluentTextButton(enabled = !busy, onClick = { update { it - tag } }) {
                            Text(stringResource(Res.string.tags_unblock))
                        }
                    }
                }
            }
            PlatformVerticalScrollbar(listState, Modifier.align(Alignment.CenterEnd))
        }
        feedback?.let { Text(it) }
        FluentButton(enabled = !busy && settings.blockedTags.isNotEmpty(), onClick = { confirm = true }) {
            Text(stringResource(Res.string.tags_clear))
        }
    }
    if (confirm) TagDialog(stringResource(Res.string.tags_clear), { confirm = false }) {
        Text(stringResource(Res.string.tags_clear_confirm))
        FluentButton(enabled = !busy, onClick = { confirm = false; update { emptySet() } }) {
            Text(stringResource(Res.string.tags_confirm))
        }
    }
}
