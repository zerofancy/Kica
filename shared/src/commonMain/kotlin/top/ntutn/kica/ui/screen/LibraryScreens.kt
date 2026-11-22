package top.ntutn.kica.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.DownloadStatus
import top.ntutn.kica.model.HistoryEntry
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.cancel
import top.ntutn.kica.resources.downloads
import top.ntutn.kica.resources.favorites
import top.ntutn.kica.resources.history
import top.ntutn.kica.resources.pause
import top.ntutn.kica.resources.resume
import top.ntutn.kica.ui.rememberComicFilter
import top.ntutn.kica.ui.ComicFilterNotice
import top.ntutn.kica.ui.component.ComicGrid
import top.ntutn.kica.ui.component.EmptyContent
import top.ntutn.kica.ui.component.FluentCard
import top.ntutn.kica.ui.component.FluentProgressBar
import top.ntutn.kica.ui.component.FluentTextButton
import top.ntutn.kica.ui.component.SectionTitle
import top.ntutn.kica.ui.progress
import top.ntutn.kica.ui.state.translatedTitle
import top.ntutn.kica.ui.component.LoadStateContent
import top.ntutn.kica.ui.PlatformVerticalScrollbar

@Composable
internal fun FavoritesScreen(
    store: top.ntutn.kica.ui.store.ComicListStore,
    library: LibraryRepository,
    onComicClick: (ComicSummary) -> Unit,
) {
    val list by store.state.collectAsState()
    val state = list.result
    val loadedPages = list.pages
    val loadingMore = list.loadingMore
    val loadMoreError = list.loadMoreError
    val canLoadMore = list.canLoadMore
    val requestLoadMore = store::loadMore
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        SectionTitle(stringResource(Res.string.favorites))
        Box(Modifier.weight(1f)) {
            LoadStateContent(state, onRetry = store::retry) {
                ComicGrid(
                    comics = it,
                    onComicClick = onComicClick,
                    loadingMore = loadingMore,
                    canLoadMore = canLoadMore,
                    loadMoreError = loadMoreError,
                    onLoadMore = requestLoadMore,
                    pages = loadedPages,
                    anchorReady = !list.restoringRange,
                )
            }
        }
    }
}

@Composable
internal fun HistoryScreen(store: top.ntutn.kica.ui.store.LibraryStore, library: LibraryRepository, onClick: (HistoryEntry) -> Unit) {
    val historyItems by store.history.collectAsState()
    val listState = rememberLazyListState()
    val options by store.options.collectAsState()
    val visibleLimit = options.visibleLimit
    val filter = rememberComicFilter(historyItems.map { it.comic }, library, verificationLimit = visibleLimit)
    AutoVerifyLibrary(listState, filter) { store.verifyMore() }
    val showHidden = options.showHidden
    val visibleIds = filter.visible.map { it.id }.toSet()
    val visible = historyItems.filter { showHidden || it.comic.id in visibleIds }
    top.ntutn.kica.ui.component.PreserveListAnchor(listState, visible.map { it.comic.id }, ready = filter.pending == 0)
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        SectionTitle(stringResource(Res.string.history))
        ComicFilterNotice(filter, library, showHidden) { store.toggleHidden() }
        if (visible.isEmpty()) {
            EmptyContent(Modifier.weight(1f))
        } else {
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visible, key = { it.comic.id }) { entry ->
                        val displayTitle = translatedTitle(entry.comic.title)
                        FluentCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onClick(entry) },
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = entry.comic.coverUrl,
                                    contentDescription = displayTitle,
                                    modifier = Modifier.size(64.dp),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    filter.reason(entry.comic.id)?.let { Text(stringResource(it)) }
                                    Text(displayTitle, style = FluentTheme.typography.bodyStrong)
                                    Text(
                                        "${entry.episodeTitle} · ${entry.pageIndex + 1}",
                                        color = FluentTheme.colors.text.text.secondary,
                                    )
                                }
                            }
                        }
                    }
                }
                PlatformVerticalScrollbar(listState, Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

@Composable
internal fun DownloadsScreen(
    store: top.ntutn.kica.ui.store.LibraryStore,
    library: LibraryRepository,
) {
    val tasks by store.tasks.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val options by store.options.collectAsState()
    val visibleLimit = options.visibleLimit
    val filter = rememberComicFilter(tasks.map { it.comic }, library, verificationLimit = visibleLimit)
    AutoVerifyLibrary(listState, filter) { store.verifyMore() }
    val showHidden = options.showHidden
    val visibleIds = filter.visible.map { it.id }.toSet()
    val visible = tasks.filter { showHidden || it.comic.id in visibleIds }
    top.ntutn.kica.ui.component.PreserveListAnchor(listState, visible.map { it.id }, ready = filter.pending == 0)
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        SectionTitle(stringResource(Res.string.downloads))
        ComicFilterNotice(filter, library, showHidden) { store.toggleHidden() }
        if (visible.isEmpty()) {
            EmptyContent(Modifier.weight(1f))
        } else {
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible, key = { it.id }) { task ->
                        val displayTitle = translatedTitle(task.comic.title)
                        FluentCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                filter.reason(task.comic.id)?.let { Text(stringResource(it)) }
                                Text(displayTitle, style = FluentTheme.typography.bodyStrong)
                                Text(task.episode.title, color = FluentTheme.colors.text.text.secondary)
                                Spacer(Modifier.height(8.dp))
                                FluentProgressBar(
                                    progress = if (task.totalPages <= 0) 0f
                                    else task.completedPages.toFloat() / task.totalPages,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    when (task.status) {
                                        DownloadStatus.RUNNING, DownloadStatus.QUEUED ->
                                            FluentTextButton(onClick = { store.pause(task.id) }) {
                                                Text(stringResource(Res.string.pause))
                                            }
                                        DownloadStatus.PAUSED, DownloadStatus.FAILED ->
                                            FluentTextButton(onClick = { store.resume(task.id) }) {
                                                Text(stringResource(Res.string.resume))
                                            }
                                        else -> Unit
                                    }
                                    if (task.status != DownloadStatus.COMPLETED) {
                                        FluentTextButton(onClick = { store.cancel(task.id) }) {
                                            Text(stringResource(Res.string.cancel))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                PlatformVerticalScrollbar(listState, Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

@Composable
private fun AutoVerifyLibrary(
    listState: androidx.compose.foundation.lazy.LazyListState,
    filter: top.ntutn.kica.ui.ComicFilterState,
    onMore: () -> Unit,
) {
    LaunchedEffect(filter.uncheckedIds.size, filter.pending, filter.visible.size) {
        if (filter.uncheckedIds.isEmpty() || filter.pending > 0) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            filter.visible.isEmpty() || top.ntutn.kica.ui.component.shouldLoadMore(
                totalItemsCount = layout.totalItemsCount,
                lastVisibleItemIndex = layout.visibleItemsInfo.lastOrNull()?.index ?: -1,
            )
        }.first { it }
        onMore()
    }
}
