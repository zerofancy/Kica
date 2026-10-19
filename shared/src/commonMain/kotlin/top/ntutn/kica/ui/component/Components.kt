package top.ntutn.kica.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Icon
import io.github.composefluent.component.Text
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.Image
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.LoadState
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.empty
import top.ntutn.kica.resources.loading
import top.ntutn.kica.resources.offline_message
import top.ntutn.kica.resources.retry
import top.ntutn.kica.resources.tap_to_retry
import top.ntutn.kica.resources.tags_filtered_empty
import top.ntutn.kica.resources.tags_filtered_page
import top.ntutn.kica.resources.tags_unverified_page
import top.ntutn.kica.resources.tags_empty_page
import top.ntutn.kica.ui.state.translatedTitle
import top.ntutn.kica.ui.PlatformVerticalScrollbar
import top.ntutn.kica.ui.LocalTagLibrary
import top.ntutn.kica.ui.rememberComicFilter
import top.ntutn.kica.ui.ComicFilterNotice

@Composable
fun <T> LoadStateContent(
    state: LoadState<T>,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    content: @Composable (T) -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            LoadState.Idle, LoadState.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FluentProgressRing()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(Res.string.loading))
            }
            is LoadState.Error -> ErrorCard(state.message, onRetry)
            is LoadState.Data -> {
                content(state.value)
                if (state.fromCache) {
                    Text(
                        text = stringResource(Res.string.offline_message),
                        modifier = Modifier.align(Alignment.TopCenter)
                            .background(FluentTheme.colors.system.neutralBackground)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        color = FluentTheme.colors.text.text.primary,
                        style = FluentTheme.typography.caption,
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorCard(message: String, onRetry: () -> Unit) {
    FluentCard(
        modifier = Modifier.padding(24.dp),
        onClick = onRetry,
    ) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = FluentTheme.colors.system.critical)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(Res.string.tap_to_retry), style = FluentTheme.typography.bodyStrong)
        }
    }
}

@Composable
fun EmptyContent(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(Res.string.empty), color = FluentTheme.colors.text.text.secondary)
    }
}

@Composable
fun ComicGrid(
    comics: List<ComicSummary>,
    onComicClick: (ComicSummary) -> Unit,
    modifier: Modifier = Modifier,
    loadingMore: Boolean = false,
    canLoadMore: Boolean = false,
    loadMoreError: String? = null,
    onLoadMore: () -> Unit = {},
    pages: List<ComicGridPage> = emptyList(),
    state: LazyGridState = rememberLazyGridState(),
) {
    val library = LocalTagLibrary.current
    if (library == null) {
        RawComicGrid(comicGridEntries(comics, pages), onComicClick, modifier, loadingMore, canLoadMore,
            loadMoreError, onLoadMore, pages.lastOrNull()?.number ?: comics.size, state)
        return
    }
    val filter = rememberComicFilter(comics, library)
    RawComicGrid(
        comicGridEntries(filter.visible, pages, filter.hiddenIds, filter.unknownIds),
        onComicClick, modifier, loadingMore,
        canLoadMore && filter.pending == 0, loadMoreError, onLoadMore,
        pages.lastOrNull()?.number ?: comics.size,
        state,
    ) {
        Column {
            ComicFilterNotice(filter, library)
            if (comics.isNotEmpty() && filter.visible.isEmpty()) {
                Text(stringResource(Res.string.tags_filtered_empty))
            }
        }
    }
}

@Composable
private fun RawComicGrid(
    entries: List<ComicGridEntry>,
    onComicClick: (ComicSummary) -> Unit,
    modifier: Modifier,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    loadMoreError: String?,
    onLoadMore: () -> Unit,
    paginationToken: Int,
    gridState: LazyGridState,
    header: (@Composable () -> Unit)? = null,
) {
    if (entries.isEmpty() && !loadingMore && loadMoreError == null && header == null) {
        EmptyContent(modifier)
        return
    }
    val currentCanLoadMore by rememberUpdatedState(canLoadMore)
    val currentLoadingMore by rememberUpdatedState(loadingMore)
    val currentLoadMoreError by rememberUpdatedState(loadMoreError)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    val currentToken by rememberUpdatedState(paginationToken)
    val currentEntryCount by rememberUpdatedState(entries.size + if (header == null) 0 else 1)
    LaunchedEffect(gridState) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val load = layoutInfo.totalItemsCount >= currentEntryCount && shouldLoadMore(
                totalItemsCount = layoutInfo.totalItemsCount,
                lastVisibleItemIndex = lastVisibleIndex,
                canLoadMore = currentCanLoadMore,
                loadingMore = currentLoadingMore,
                hasLoadMoreError = currentLoadMoreError != null,
            )
            if (load) currentToken else null
        }.distinctUntilChanged().collect { token ->
            if (token != null) currentOnLoadMore()
        }
    }
    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (header != null) item(key = "filter-status", span = { GridItemSpan(maxLineSpan) }) { header() }
            if (entries.isEmpty() && !loadingMore && loadMoreError == null) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(Res.string.empty), Modifier.padding(24.dp))
                }
            }
            items(
                entries,
                key = { it.key },
                span = { GridItemSpan(if (it is ComicGridEntry.Comic) 1 else maxLineSpan) },
            ) { entry ->
                when (entry) {
                    is ComicGridEntry.Comic -> ComicCard(entry.comic, onComicClick)
                    is ComicGridEntry.Page -> FluentCard(Modifier.fillMaxWidth()) {
                        val message = when (entry.status) {
                            PageStatus.FILTERED -> Res.string.tags_filtered_page
                            PageStatus.UNVERIFIED -> Res.string.tags_unverified_page
                            PageStatus.EMPTY -> Res.string.tags_empty_page
                        }
                        Text(stringResource(message, entry.number), Modifier.padding(20.dp))
                    }
                }
            }
            if (loadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FluentProgressBar(Modifier.fillMaxWidth().padding(vertical = 12.dp))
                }
            } else if (loadMoreError != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = loadMoreError,
                            color = FluentTheme.colors.system.critical,
                            style = FluentTheme.typography.caption,
                        )
                        FluentButton(onClick = onLoadMore) {
                            Text(stringResource(Res.string.retry))
                        }
                    }
                }
            }
        }
        PlatformVerticalScrollbar(gridState, Modifier.align(Alignment.CenterEnd))
    }
}

internal fun shouldLoadMore(
    totalItemsCount: Int,
    lastVisibleItemIndex: Int,
    canLoadMore: Boolean = true,
    loadingMore: Boolean = false,
    hasLoadMoreError: Boolean = false,
    prefetchDistance: Int = 4,
): Boolean {
    if (!canLoadMore || loadingMore || hasLoadMoreError) return false
    if (totalItemsCount <= 0 || lastVisibleItemIndex < 0) return false
    val triggerIndex = (totalItemsCount - prefetchDistance).coerceAtLeast(0)
    return lastVisibleItemIndex >= triggerIndex
}

@Composable
fun ComicCard(comic: ComicSummary, onClick: (ComicSummary) -> Unit) {
    val displayTitle = translatedTitle(comic.title)
    FluentCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onClick(comic) },
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(0.72f)
                    .background(FluentTheme.colors.control.secondary),
                contentAlignment = Alignment.Center,
            ) {
                if (comic.coverUrl.isBlank()) {
                    Icon(
                        Icons.Regular.Image,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = FluentTheme.colors.text.text.secondary,
                    )
                } else {
                    AsyncImage(
                        model = comic.coverUrl,
                        contentDescription = displayTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(
                    displayTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = FluentTheme.typography.bodyStrong,
                )
                if (comic.author.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        comic.author,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = FluentTheme.typography.caption,
                        color = FluentTheme.colors.text.text.secondary,
                    )
                }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = FluentTheme.typography.subtitle)
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}
