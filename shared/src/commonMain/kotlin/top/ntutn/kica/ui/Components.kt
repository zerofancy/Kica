package top.ntutn.kica.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
) {
    if (comics.isEmpty()) {
        EmptyContent(modifier)
        return
    }
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState, comics.size, canLoadMore, loadingMore, loadMoreError) {
        if (!canLoadMore || loadingMore || loadMoreError != null) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val triggerIndex = (layoutInfo.totalItemsCount - 4).coerceAtLeast(0)
            layoutInfo.totalItemsCount > 0 && lastVisibleIndex >= triggerIndex
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd) onLoadMore()
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
            items(comics, key = { it.id }) { comic ->
                ComicCard(comic, onComicClick)
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

@Composable
fun ComicCard(comic: ComicSummary, onClick: (ComicSummary) -> Unit) {
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
                        contentDescription = comic.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(
                    comic.title,
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
