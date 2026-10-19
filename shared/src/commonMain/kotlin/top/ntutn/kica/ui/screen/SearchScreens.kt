package top.ntutn.kica.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Icon
import io.github.composefluent.component.Text
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.ArrowLeft
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.data.cancellableResult
import top.ntutn.kica.data.PicaRepository
import top.ntutn.kica.model.AppSettings
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.LoadState
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.back
import top.ntutn.kica.resources.categories
import top.ntutn.kica.resources.search
import top.ntutn.kica.resources.search_failed
import top.ntutn.kica.resources.search_hint
import top.ntutn.kica.resources.tags_source
import top.ntutn.kica.resources.tags_search_hint
import top.ntutn.kica.ui.component.ComicGrid
import top.ntutn.kica.ui.component.ComicGridPage
import top.ntutn.kica.ui.component.FluentChip
import top.ntutn.kica.ui.component.FluentIconButton
import top.ntutn.kica.ui.component.FluentPrimaryButton
import top.ntutn.kica.ui.component.FluentTextField
import top.ntutn.kica.ui.PlatformVerticalScrollbar
import top.ntutn.kica.ui.component.LoadStateContent


@Composable
internal fun SearchScreen(
    repository: PicaRepository,
    library: LibraryRepository,
    initialQuery: String,
    initialCategory: String?,
    fromTag: Boolean = false,
    onBack: () -> Unit,
    onComicClick: (ComicSummary) -> Unit,
) {
    val pages = LocalSearchPages.current
    val page = remember(initialQuery, initialCategory, fromTag) {
        val key = top.ntutn.kica.model.AppRoute.Search(initialQuery, initialCategory, fromTag).toString()
        pages?.getOrPut(key) { SearchPageState(initialQuery, initialCategory, fromTag) }
            ?: SearchPageState(initialQuery, initialCategory, fromTag)
    }
    var query by page.query
    var tagSource by page.fromTag
    var selectedCategory by page.category
    var criteria by page.criteria
    var refresh by page.refresh
    val searchFailed = stringResource(Res.string.search_failed)
    var state by page.result
    var loadedPage by page.loadedPage
    var loadedPages by page.loadedPages
    var totalPages by page.totalPages
    var loadingMore by page.loadingMore
    var loadMoreError by page.loadMoreError
    val scope = rememberCoroutineScope()

    LaunchedEffect(criteria, refresh) {
        if (page.completed == (criteria to refresh) && state is LoadState.Data) return@LaunchedEffect
        page.completed = null
        page.generation++
        val generation = page.generation
        val current = criteria
        loadedPage = 0
        loadedPages = emptyList()
        totalPages = 0
        loadingMore = false
        loadMoreError = null
        if (current == null) {
            state = LoadState.Idle
        } else {
            state = LoadState.Loading
            state = cancellableResult {
                repository.search(
                    keyword = current.keyword,
                    categories = current.category?.let(::listOf).orEmpty(),
                    page = 1,
                )
            }.fold(
                onSuccess = { result ->
                    loadedPage = result.page
                    loadedPages = listOf(ComicGridPage(result.page, result.items.map { it.id }))
                    totalPages = result.totalPages
                    LoadState.Data(result.items)
                },
                onFailure = { LoadState.Error(it.message ?: searchFailed) },
            )
            if (generation == page.generation && state is LoadState.Data) page.completed = current to refresh
        }
    }
    DisposableEffect(page) {
        onDispose { page.generation++; loadingMore = false }
    }
    val canLoadMore = state is LoadState.Data && loadedPage < totalPages
    val requestLoadMore: () -> Unit = request@{
        val current = criteria ?: return@request
        if (!canLoadMore || loadingMore) return@request
        val nextPage = loadedPage + 1
        val generation = page.generation
        loadingMore = true
        loadMoreError = null
        scope.launch {
            val result = cancellableResult {
                repository.search(
                    keyword = current.keyword,
                    categories = current.category?.let(::listOf).orEmpty(),
                    page = nextPage,
                )
            }
            if (criteria != current || generation != page.generation) return@launch
            result.fold(
                onSuccess = { page ->
                    loadedPages = loadedPages + ComicGridPage(nextPage, page.items.map { it.id })
                    val existing = (state as? LoadState.Data)?.value.orEmpty()
                    val merged = (existing + page.items)
                        .distinctBy(ComicSummary::id)
                    state = LoadState.Data(merged)
                    loadedPage = maxOf(nextPage, page.page)
                    totalPages = page.totalPages
                },
                onFailure = {
                    loadMoreError = it.message ?: searchFailed
                },
            )
            loadingMore = false
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FluentIconButton(onClick = onBack) {
                Icon(Icons.Regular.ArrowLeft, contentDescription = stringResource(Res.string.back))
            }
            FluentTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(Res.string.search_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FluentPrimaryButton(
                onClick = {
                    val keyword = query.trim()
                    if (keyword != initialQuery.trim()) tagSource = false
                    if (keyword.isNotEmpty() || selectedCategory != null) {
                        val nextCriteria = SearchCriteria(keyword, selectedCategory)
                        if (criteria == nextCriteria) refresh++ else criteria = nextCriteria
                    }
                },
            ) { Text(stringResource(Res.string.search)) }
        }
        selectedCategory?.let { category ->
            Spacer(Modifier.height(10.dp))
            FluentChip(
                selected = true,
                onClick = {
                    selectedCategory = null
                    criteria = query.trim()
                        .takeIf(String::isNotEmpty)
                        ?.let { SearchCriteria(keyword = it, category = null) }
                },
                label = { Text("${stringResource(Res.string.categories)}：$category") },
            )
        }
        Spacer(Modifier.height(16.dp))
        if (tagSource) {
            Text(stringResource(Res.string.tags_source, initialQuery))
            Text(stringResource(Res.string.tags_search_hint))
        }
        Box(Modifier.weight(1f)) {
            when (val value = state) {
                LoadState.Idle -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.search_hint),
                        color = FluentTheme.colors.text.text.secondary,
                    )
                }
                else -> LoadStateContent(
                    state = value,
                    onRetry = { refresh++ },
                ) {
                    ComicGrid(
                        comics = it,
                        onComicClick = onComicClick,
                        loadingMore = loadingMore,
                        canLoadMore = canLoadMore,
                        loadMoreError = loadMoreError,
                        onLoadMore = requestLoadMore,
                        pages = loadedPages,
                    )
                }
            }
        }
    }
}

internal data class SearchCriteria(
    val keyword: String,
    val category: String?,
)
