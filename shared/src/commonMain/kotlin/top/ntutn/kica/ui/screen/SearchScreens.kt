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
import top.ntutn.kica.ui.filterBlockedSummaries
import top.ntutn.kica.ui.component.ComicGrid
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
    onBack: () -> Unit,
    onComicClick: (ComicSummary) -> Unit,
) {
    var query by remember(initialQuery, initialCategory) { mutableStateOf(initialQuery) }
    var selectedCategory by remember(initialCategory) { mutableStateOf(initialCategory) }
    var criteria by remember(initialQuery, initialCategory) {
        mutableStateOf(
            SearchCriteria(initialQuery.trim(), initialCategory)
                .takeIf { it.keyword.isNotEmpty() || it.category != null },
        )
    }
    var refresh by remember { mutableIntStateOf(0) }
    val searchFailed = stringResource(Res.string.search_failed)
    var state by remember(initialQuery, initialCategory) {
        mutableStateOf<LoadState<List<ComicSummary>>>(
            if (criteria == null) LoadState.Idle else LoadState.Loading,
        )
    }
    var loadedPage by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(0) }
    var loadingMore by remember { mutableStateOf(false) }
    var loadMoreError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val settings by library.settings().collectAsState(initial = AppSettings())
    val blocked = settings.blockedCategories

    LaunchedEffect(criteria, refresh) {
        val current = criteria
        loadedPage = 0
        totalPages = 0
        loadingMore = false
        loadMoreError = null
        if (current == null) {
            state = LoadState.Idle
        } else {
            state = LoadState.Loading
            state = runCatching {
                repository.search(
                    keyword = current.keyword,
                    categories = current.category?.let(::listOf).orEmpty(),
                    page = 1,
                )
            }.fold(
                onSuccess = { result ->
                    loadedPage = result.page
                    totalPages = result.totalPages
                    LoadState.Data(result.items.filterBlockedSummaries(blocked))
                },
                onFailure = { LoadState.Error(it.message ?: searchFailed) },
            )
        }
    }
    val canLoadMore = state is LoadState.Data && loadedPage < totalPages
    val requestLoadMore: () -> Unit = request@{
        val current = criteria ?: return@request
        if (!canLoadMore || loadingMore) return@request
        val nextPage = loadedPage + 1
        loadingMore = true
        loadMoreError = null
        scope.launch {
            val result = runCatching {
                repository.search(
                    keyword = current.keyword,
                    categories = current.category?.let(::listOf).orEmpty(),
                    page = nextPage,
                )
            }
            if (criteria != current) return@launch
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
