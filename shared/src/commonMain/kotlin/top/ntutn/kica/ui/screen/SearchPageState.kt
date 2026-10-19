package top.ntutn.kica.ui.screen

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.LoadState
import top.ntutn.kica.ui.component.ComicGridPage

internal val LocalSearchPages = staticCompositionLocalOf<MutableMap<String, SearchPageState>?> { null }

internal class SearchPageState(query: String, category: String?, fromTag: Boolean) {
    val query = mutableStateOf(query)
    val category = mutableStateOf(category)
    val fromTag = mutableStateOf(fromTag)
    val criteria = mutableStateOf(SearchCriteria(query.trim(), category).takeIf { query.isNotBlank() || category != null })
    val refresh = mutableIntStateOf(0)
    val result = mutableStateOf<LoadState<List<ComicSummary>>>(LoadState.Idle)
    val loadedPage = mutableIntStateOf(0)
    val loadedPages = mutableStateOf<List<ComicGridPage>>(emptyList())
    val totalPages = mutableIntStateOf(0)
    val loadingMore = mutableStateOf(false)
    val loadMoreError = mutableStateOf<String?>(null)
    var completed: Pair<SearchCriteria?, Int>? = null
    var generation = 0
}
