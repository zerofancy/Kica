package top.ntutn.kica.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.data.DownloadCoordinator
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.data.PicaRepository
import top.ntutn.kica.model.AppSettings
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.DownloadStatus
import top.ntutn.kica.model.HistoryEntry
import top.ntutn.kica.model.LoadState
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.cancel
import top.ntutn.kica.resources.downloads
import top.ntutn.kica.resources.favorites
import top.ntutn.kica.resources.history
import top.ntutn.kica.resources.load_failed
import top.ntutn.kica.resources.pause
import top.ntutn.kica.resources.resume
import top.ntutn.kica.ui.filterBlockedSummaries
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
    repository: PicaRepository,
    library: LibraryRepository,
    onComicClick: (ComicSummary) -> Unit,
) {
    var refresh by remember { mutableIntStateOf(0) }
    val loadFailed = stringResource(Res.string.load_failed)
    var state by remember { mutableStateOf<LoadState<List<ComicSummary>>>(LoadState.Loading) }
    var loadedPage by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(0) }
    var loadingMore by remember { mutableStateOf(false) }
    var loadMoreError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val settings by library.settings().collectAsState(initial = AppSettings())
    val blocked = settings.blockedCategories

    LaunchedEffect(refresh) {
        loadedPage = 0
        totalPages = 0
        loadingMore = false
        loadMoreError = null
        state = LoadState.Loading
        state = runCatching { repository.favorites(page = 1) }.fold(
            onSuccess = { result ->
                loadedPage = result.page
                totalPages = result.totalPages
                LoadState.Data(result.items.filterBlockedSummaries(blocked))
            },
            onFailure = { LoadState.Error(it.message ?: loadFailed) },
        )
    }
    val canLoadMore = state is LoadState.Data && loadedPage < totalPages
    val requestLoadMore: () -> Unit = request@{
        if (!canLoadMore || loadingMore) return@request
        val nextPage = loadedPage + 1
        val requestedRefresh = refresh
        loadingMore = true
        loadMoreError = null
        scope.launch {
            val result = runCatching { repository.favorites(page = nextPage) }
            if (refresh != requestedRefresh) return@launch
            result.fold(
                onSuccess = { page ->
                    val existing = (state as? LoadState.Data)?.value.orEmpty()
                    val merged = (existing + page.items)
                        .distinctBy(ComicSummary::id)
                        .filterBlockedSummaries(blocked)
                    state = LoadState.Data(merged)
                    loadedPage = maxOf(nextPage, page.page)
                    totalPages = page.totalPages
                },
                onFailure = {
                    loadMoreError = it.message ?: loadFailed
                },
            )
            loadingMore = false
        }
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        SectionTitle(stringResource(Res.string.favorites))
        Box(Modifier.weight(1f)) {
            LoadStateContent(state, onRetry = { refresh++ }) {
                ComicGrid(
                    comics = it,
                    onComicClick = onComicClick,
                    loadingMore = loadingMore,
                    canLoadMore = canLoadMore,
                    loadMoreError = loadMoreError,
                    onLoadMore = requestLoadMore,
                )
            }
        }
    }
}

@Composable
internal fun HistoryScreen(library: LibraryRepository, onClick: (HistoryEntry) -> Unit) {
    val historyItems by library.history().collectAsState(initial = emptyList())
    val settings by library.settings().collectAsState(initial = AppSettings())
    val blocked = settings.blockedCategories
    val listState = rememberLazyListState()
    val visible = if (blocked.isEmpty()) {
        historyItems
    } else {
        historyItems.filterNot { it.comic.categories.any { category -> category in blocked } }
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        SectionTitle(stringResource(Res.string.history))
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
    coordinator: DownloadCoordinator,
    library: LibraryRepository,
) {
    val tasks by coordinator.tasks.collectAsState()
    val settings by library.settings().collectAsState(initial = AppSettings())
    val blocked = settings.blockedCategories
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val visible = if (blocked.isEmpty()) {
        tasks
    } else {
        tasks.filterNot { it.comic.categories.any { category -> category in blocked } }
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        SectionTitle(stringResource(Res.string.downloads))
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
                                            FluentTextButton(onClick = { scope.launch { coordinator.pause(task.id) } }) {
                                                Text(stringResource(Res.string.pause))
                                            }
                                        DownloadStatus.PAUSED, DownloadStatus.FAILED ->
                                            FluentTextButton(onClick = { scope.launch { coordinator.resume(task.id) } }) {
                                                Text(stringResource(Res.string.resume))
                                            }
                                        else -> Unit
                                    }
                                    if (task.status != DownloadStatus.COMPLETED) {
                                        FluentTextButton(onClick = { scope.launch { coordinator.cancel(task.id) } }) {
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
