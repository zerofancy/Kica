package top.ntutn.kica.ui.screen

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Icon
import io.github.composefluent.component.Text
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.ArrowLeft
import io.github.composefluent.icons.regular.ArrowSync
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.LoadState
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.back
import top.ntutn.kica.resources.empty
import top.ntutn.kica.resources.load_failed
import top.ntutn.kica.resources.random_books
import top.ntutn.kica.resources.random_comics
import top.ntutn.kica.resources.recommended
import top.ntutn.kica.resources.retry
import top.ntutn.kica.resources.shuffle_batch
import top.ntutn.kica.ui.ComicFilterNotice
import top.ntutn.kica.ui.rememberComicFilter
import top.ntutn.kica.ui.store.RandomComicsStore
import top.ntutn.kica.ui.store.HomeStore
import top.ntutn.kica.ui.component.ComicCard
import top.ntutn.kica.ui.component.ComicGrid
import top.ntutn.kica.ui.component.ErrorCard
import top.ntutn.kica.ui.component.FluentButton
import top.ntutn.kica.ui.component.FluentCard
import top.ntutn.kica.ui.component.FluentIconButton
import top.ntutn.kica.ui.component.FluentProgressBar
import top.ntutn.kica.ui.component.FluentProgressRing
import top.ntutn.kica.ui.component.SectionTitle
import top.ntutn.kica.ui.component.LoadStateContent
import top.ntutn.kica.ui.PlatformVerticalScrollbar
import top.ntutn.kica.ui.PlatformHorizontalScrollbar

@Composable
internal fun HomeScreen(
    store: HomeStore,
    library: LibraryRepository,
    randomLoader: RandomComicsStore,
    onComicClick: (ComicSummary) -> Unit,
) {
    val recommendationsState by store.state.collectAsState()
    val randomState by randomLoader.state.collectAsState()
    val gridState = rememberLazyGridState()
    val recommendationsFilter = rememberComicFilter(
        (recommendationsState as? LoadState.Data)?.value.orEmpty(), library,
    )
    val randomFilter = rememberComicFilter(randomState.items.orEmpty(), library)

    val anchorKeys = buildList {
        addAll(listOf("recommendations-title", "recommendations-body", "section-spacer", "random-title"))
        if (randomState.items == null) add("random-state") else {
            if (randomState.isLoading) add("random-progress")
            if (randomState.errorMessage != null) add("random-error")
            add("random-filter")
            if (randomFilter.visible.isEmpty()) add("random-empty")
            else addAll(randomFilter.visible.map { "random:${it.id}" })
        }
    }
    top.ntutn.kica.ui.component.PreserveGridAnchor(gridState, anchorKeys,
        ready = recommendationsState !is LoadState.Loading && !randomState.isLoading &&
            recommendationsFilter.pending == 0 && randomFilter.pending == 0)
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "recommendations-title", span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(Res.string.recommended))
            }
            when (val value = recommendationsState) {
                is LoadState.Data -> item(key = "recommendations-body", span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        ComicFilterNotice(recommendationsFilter, library)
                        HorizontalComicRow(
                            comics = recommendationsFilter.visible,
                            onComicClick = onComicClick,
                        )
                    }
                }
                is LoadState.Error -> item(key = "recommendations-body", span = { GridItemSpan(maxLineSpan) }) {
                    ErrorCard(value.message) { store.refresh() }
                }
                else -> item(key = "recommendations-body", span = { GridItemSpan(maxLineSpan) }) {
                    FluentProgressBar(Modifier.fillMaxWidth())
                }
            }
            item(key = "section-spacer", span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(6.dp))
            }
            item(key = "random-title", span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(Res.string.random_comics)) {
                    RandomRefreshButton(randomLoader)
                }
            }
            if (randomState.items == null) {
                item(key = "random-state", span = { GridItemSpan(maxLineSpan) }) {
                    when {
                        randomState.isLoading -> FluentProgressBar(Modifier.fillMaxWidth())
                        randomState.errorMessage != null -> randomState.errorMessage?.let { ErrorCard(it, randomLoader::refresh) }
                    }
                }
            } else {
                if (randomState.isLoading) {
                    item(key = "random-progress", span = { GridItemSpan(maxLineSpan) }) {
                        FluentProgressBar(Modifier.fillMaxWidth())
                    }
                }
                randomState.errorMessage?.let { message ->
                    item(key = "random-error", span = { GridItemSpan(maxLineSpan) }) {
                        RandomRefreshError(message, randomLoader::refresh)
                    }
                }
                item(key = "random-filter", span = { GridItemSpan(maxLineSpan) }) {
                    ComicFilterNotice(randomFilter, library)
                }
                val visibleRandom = randomFilter.visible
                if (visibleRandom.isEmpty()) {
                    item(key = "random-empty", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(Res.string.empty),
                            color = FluentTheme.colors.text.text.secondary,
                        )
                    }
                } else {
                    gridItems(
                        items = visibleRandom,
                        key = { "random:${it.id}" },
                    ) { comic ->
                        ComicCard(comic, onComicClick)
                    }
                }
            }
        }
        PlatformVerticalScrollbar(gridState, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
internal fun RandomComicsScreen(
    loader: RandomComicsStore,
    library: LibraryRepository,
    onBack: () -> Unit,
    onComicClick: (ComicSummary) -> Unit,
) {
    val state by loader.state.collectAsState()
    val filter = rememberComicFilter(state.items.orEmpty(), library)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    Column(
        modifier = Modifier.fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.key == Key.F5 && event.type == KeyEventType.KeyUp) {
                    loader.refresh()
                    true
                } else {
                    false
                }
            }
            .focusRequester(focusRequester)
            .focusable(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FluentIconButton(onClick = onBack) {
                Icon(Icons.Regular.ArrowLeft, contentDescription = stringResource(Res.string.back))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.random_books), style = FluentTheme.typography.title)
            Spacer(Modifier.weight(1f))
            RandomRefreshButton(loader)
        }
        when (val items = state.items) {
            null -> LoadStateContent(
                state = if (state.isLoading) {
                    LoadState.Loading
                } else {
                    LoadState.Error(state.errorMessage ?: stringResource(Res.string.load_failed))
                },
                modifier = Modifier.weight(1f),
                onRetry = loader::refresh,
            ) {}
            else -> Column(Modifier.weight(1f).fillMaxWidth()) {
                ComicFilterNotice(filter, library)
                if (state.isLoading) {
                    FluentProgressBar(Modifier.fillMaxWidth())
                }
                state.errorMessage?.let { message ->
                    RandomRefreshError(message, loader::refresh)
                }
                ComicGrid(
                    comics = filter.visible,
                    onComicClick = onComicClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun RandomRefreshButton(loader: RandomComicsStore) {
    val state by loader.state.collectAsState()
    FluentButton(
        onClick = loader::refresh,
        enabled = !state.isLoading,
    ) {
        if (state.isLoading) {
            FluentProgressRing(Modifier.size(16.dp), size = 16.dp)
        } else {
            Icon(
                Icons.Regular.ArrowSync,
                contentDescription = stringResource(Res.string.shuffle_batch),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(stringResource(Res.string.shuffle_batch))
    }
}

@Composable
internal fun RandomRefreshError(message: String, onRetry: () -> Unit) {
    FluentCard(modifier = Modifier.fillMaxWidth(), onClick = onRetry) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = FluentTheme.colors.system.critical,
            )
            Text(stringResource(Res.string.retry), style = FluentTheme.typography.bodyStrong)
        }
    }
}

@Composable
internal fun HorizontalComicRow(comics: List<ComicSummary>, onComicClick: (ComicSummary) -> Unit) {
    if (comics.isEmpty()) {
        Text(stringResource(Res.string.empty), color = FluentTheme.colors.text.text.secondary)
        return
    }
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(comics, key = { it.id }) { comic ->
                Box(Modifier.width(160.dp)) { ComicCard(comic, onComicClick) }
            }
        }
        PlatformHorizontalScrollbar(listState)
    }
}
