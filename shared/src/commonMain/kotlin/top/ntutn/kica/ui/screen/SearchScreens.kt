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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Icon
import io.github.composefluent.component.Text
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.ArrowLeft
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.LoadState
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.back
import top.ntutn.kica.resources.categories
import top.ntutn.kica.resources.search
import top.ntutn.kica.resources.search_hint
import top.ntutn.kica.resources.tags_source
import top.ntutn.kica.resources.tags_search_hint
import top.ntutn.kica.ui.component.ComicGrid
import top.ntutn.kica.ui.component.FluentChip
import top.ntutn.kica.ui.component.FluentIconButton
import top.ntutn.kica.ui.component.FluentPrimaryButton
import top.ntutn.kica.ui.component.FluentTextField
import top.ntutn.kica.ui.component.LoadStateContent


@Composable
internal fun SearchScreen(
    store: top.ntutn.kica.ui.store.ComicListStore,
    library: LibraryRepository,
    initialQuery: String,
    initialCategory: String?,
    fromTag: Boolean = false,
    onBack: () -> Unit,
    onComicClick: (ComicSummary) -> Unit,
) {
    val draft by store.draft.collectAsState()
    val list by store.state.collectAsState()
    val query = draft.query
    val selectedCategory = draft.category
    val tagSource = draft.fromTag
    val state = list.result
    val loadingMore = list.loadingMore
    val canLoadMore = list.canLoadMore
    val loadMoreError = list.loadMoreError
    val loadedPages = list.pages
    val requestLoadMore = store::loadMore
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FluentIconButton(onClick = onBack) {
                Icon(Icons.Regular.ArrowLeft, contentDescription = stringResource(Res.string.back))
            }
            FluentTextField(
                value = query,
                onValueChange = store::editQuery,
                placeholder = { Text(stringResource(Res.string.search_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FluentPrimaryButton(
                onClick = store::submit,
            ) { Text(stringResource(Res.string.search)) }
        }
        selectedCategory?.let { category ->
            Spacer(Modifier.height(10.dp))
            FluentChip(
                selected = true,
                onClick = store::clearCategory,
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
                    onRetry = store::retry,
                ) {
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
}

internal data class SearchCriteria(
    val keyword: String,
    val category: String?,
)
