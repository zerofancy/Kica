package top.ntutn.kica.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.composefluent.component.Text
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.data.*
import top.ntutn.kica.model.AppSettings
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.resources.*
import top.ntutn.kica.ui.component.FluentTextButton
import top.ntutn.kica.ui.screen.TagBlockDialog

internal val LocalTagResolver = staticCompositionLocalOf<ComicTagResolver?> { null }
internal val LocalTagLibrary = staticCompositionLocalOf<LibraryRepository?> { null }

internal data class ComicFilterState(
    val visible: List<ComicSummary>,
    val hiddenIds: Set<String>,
    val unknownIds: Set<String>,
    val pending: Int,
    val stale: Boolean,
    val retry: () -> Unit,
    val uncheckedIds: Set<String> = emptySet(),
) {
    fun reason(comicId: String) = when (comicId) {
        in hiddenIds -> Res.string.tags_blocked
        in unknownIds -> Res.string.tags_unknown_item
        in uncheckedIds -> Res.string.tags_unknown_item
        else -> null
    }
}

@Composable
internal fun rememberComicFilter(
    comics: List<ComicSummary>,
    library: LibraryRepository,
    verificationLimit: Int = Int.MAX_VALUE,
): ComicFilterState {
    val settingsValue by library.settings().collectAsState(initial = null)
    val settings = settingsValue ?: return ComicFilterState(
        emptyList(), emptySet(), comics.map { it.id }.toSet(), comics.distinctBy { it.id }.size, false, {},
    )
    val resolver = LocalTagResolver.current
    val updates = resolver?.updates?.collectAsState()?.value.orEmpty()
    var retry by remember { mutableIntStateOf(0) }
    val eligible = comics.distinctBy(ComicSummary::id)
        .filter { comic -> comic.categories.none { it in settings.blockedCategories } }
    val candidates = if (settings.blockedTags.isEmpty()) eligible else eligible.take(verificationLimit)
    val unchecked = eligible.drop(candidates.size).map { it.id }.toSet()
    val requestKey = candidates.map { it.id to it.tags }
    val resolved = remember(library, resolver) { mutableStateMapOf<Pair<String, List<String>?>, ResolvedTags>() }
    val attempts = remember(library, resolver) { mutableMapOf<Pair<String, List<String>?>, Int>() }
    val started = remember(resolved) { kotlin.time.Clock.System.now().toEpochMilliseconds() }
    LaunchedEffect(requestKey, settings.blockedTags.isNotEmpty(), retry, resolver) {
        if (settings.blockedTags.isEmpty()) return@LaunchedEffect
        coroutineScope {
            candidates.forEach { comic ->
                val key = comic.id to comic.tags
                val previous = resolved[key]
                if (previous?.tags != null || (previous != null && attempts[key] == retry)) return@forEach
                attempts[key] = retry
                resolved.remove(key)
                launch { resolved[key] = resolver?.resolve(comic) ?: ResolvedTags(comic.tags) }
            }
        }
    }
    val blockedKeys = settings.blockedTags.map(::tagKey).toSet()
    val hidden = comics.filter { it.categories.any(settings.blockedCategories::contains) }.map { it.id }.toMutableSet()
    val unknown = mutableSetOf<String>()
    var pending = 0
    var stale = false
    val visible = candidates.mapNotNull { comic ->
        if (blockedKeys.isEmpty()) return@mapNotNull comic
        val result = resolved[comic.id to comic.tags]
        val freshUpdate = updates[comic.id]?.takeIf { it.verifiedAt >= started }
        val tags = freshUpdate?.tags ?: result?.tags
        stale = stale || (freshUpdate == null && result?.stale == true)
        if (comicVisibility(comic, settings, tags) == ComicVisibility.UNKNOWN) {
            unknown += comic.id
            if (result == null) pending++
            null
        } else if (comicVisibility(comic, settings, tags) == ComicVisibility.BLOCKED) {
            hidden += comic.id
            null
        } else {
            comic.copy(tags = tags)
        }
    }
    return ComicFilterState(visible, hidden, unknown, pending, stale, { retry++ }, unchecked)
}

@Composable
internal fun ComicFilterNotice(
    state: ComicFilterState,
    library: LibraryRepository,
    showHidden: Boolean? = null,
    onToggleHidden: () -> Unit = {},
) {
    var manage by remember { mutableStateOf(false) }
    val settings by library.settings().collectAsState(initial = AppSettings())
    if (state.hiddenIds.isNotEmpty() || state.unknownIds.isNotEmpty() || state.stale || settings.blockedTags.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.hiddenIds.isNotEmpty()) Text(stringResource(Res.string.tags_hidden, state.hiddenIds.size))
            if (state.pending > 0) Text(stringResource(Res.string.tags_pending, state.pending))
            val failed = state.unknownIds.size - state.pending
            if (failed > 0) {
                Text(stringResource(Res.string.tags_unknown, failed))
                FluentTextButton(onClick = state.retry) { Text(stringResource(Res.string.tags_retry)) }
            }
            if (state.stale) Text(stringResource(Res.string.tags_stale))
            if (showHidden != null && (state.hiddenIds.isNotEmpty() || state.unknownIds.isNotEmpty() || state.uncheckedIds.isNotEmpty())) {
                FluentTextButton(onClick = onToggleHidden) {
                    Text(stringResource(if (showHidden) Res.string.tags_hide_hidden else Res.string.tags_show_hidden))
                }
            }
            FluentTextButton(onClick = { manage = true }) {
                Text(stringResource(Res.string.tags_manage, settings.blockedTags.size))
            }
        }
    }
    if (manage) TagBlockDialog(library) { manage = false }
}
