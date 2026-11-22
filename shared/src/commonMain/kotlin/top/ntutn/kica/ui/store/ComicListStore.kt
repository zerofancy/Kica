package top.ntutn.kica.ui.store

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import top.ntutn.kica.data.cancellableResult
import top.ntutn.kica.model.*
import top.ntutn.kica.ui.component.ComicGridPage
import top.ntutn.kica.ui.navigation.AppServices
import top.ntutn.kica.ui.navigation.SessionData

@Serializable
internal data class SearchSnapshot(
    val query: String = "", val category: String? = null, val fromTag: Boolean = false,
    val keyword: String? = null, val submittedCategory: String? = null,
    val loadedPageHint: Int = 1,
)

internal data class ComicListState(
    val result: LoadState<List<ComicSummary>> = LoadState.Idle,
    val pages: List<ComicGridPage> = emptyList(),
    val loadedPage: Int = 0, val totalPages: Int = 0,
    val loadingMore: Boolean = false, val loadMoreError: String? = null,
    val restoringRange: Boolean = false,
) { val canLoadMore get() = result is LoadState.Data && loadedPage < totalPages }

internal class ComicListStore(
    private val services: AppServices,
    private val session: SessionData,
    val favorites: Boolean,
    snapshot: SearchSnapshot,
) : PageStore() {
    private val mutableDraft = MutableStateFlow(snapshot)
    val draft = mutableDraft.asStateFlow()
    private val mutable = MutableStateFlow(ComicListState(restoringRange = snapshot.loadedPageHint > 1))
    val state: StateFlow<ComicListState> = mutable
    private var restoreThrough = snapshot.loadedPageHint.coerceAtLeast(1)
    private var started = false
    fun snapshot(): SearchSnapshot {
        val current = draft.value
        val optional = top.ntutn.kica.ui.navigation.SnapshotPolicy.includeOptional
        return current.copy(
            query = if (optional) current.query.takeIf { it.length <= 4_096 } ?: current.keyword.orEmpty()
                else current.keyword.orEmpty(),
            category = if (optional) current.category else current.submittedCategory,
            loadedPageHint = if (state.value.restoringRange) maxOf(restoreThrough, state.value.loadedPage)
                else state.value.loadedPage.coerceAtLeast(1))
    }
    private var generation = 0
    private var request: Job? = null
    private var favoriteVersion: Map<String, Boolean?> = emptyMap()
    private var dirty = false
    init {
        if (favorites) scope.launch {
            session.mutations.collect { changes ->
                val next = changes.mapValues { it.value.favorite }
                if (next != favoriteVersion) {
                    dirty = dirty || next.any { (id, value) -> value == true && favoriteVersion[id] != true }
                    favoriteVersion = next
                    val current = mutable.value
                    val items = (current.result as? LoadState.Data)?.value
                    if (items != null) mutable.value = current.copy(result = LoadState.Data(items.filter { next[it.id] != false }))
                }
            }
        }
    }
    override fun ensureLoaded() {
        if (!started) { started = true; refreshInitial(resetRange = false) }
        else if (dirty) { dirty = false; generation++; request?.cancel(); load(1, preserveTail = true) }
    }
    fun editQuery(query: String) { mutableDraft.value = mutableDraft.value.copy(query = query) }
    fun submit() {
        val d = mutableDraft.value
        val keyword = d.query.trim()
        if (keyword.isEmpty() && d.category == null) return
        mutableDraft.value = d.copy(keyword = keyword, submittedCategory = d.category,
            fromTag = d.fromTag && keyword == d.keyword)
        refresh()
    }
    fun clearCategory() {
        val d = mutableDraft.value
        mutableDraft.value = d.copy(category = null, keyword = d.query.trim().takeIf { it.isNotEmpty() }, submittedCategory = null)
        refresh()
    }
    fun retry() = refreshInitial(resetRange = !state.value.restoringRange)
    fun refresh() = refreshInitial(resetRange = true)
    private fun refreshInitial(resetRange: Boolean) {
        if (resetRange) { restoreThrough = 1; mutable.value = mutable.value.copy(restoringRange = false) }
        generation++
        request?.cancel()
        if (!favorites && mutableDraft.value.keyword == null && mutableDraft.value.submittedCategory == null) {
            mutable.value = ComicListState(); return
        }
        load(1)
    }
    fun loadMore() {
        if (!mutable.value.canLoadMore || mutable.value.loadingMore || request?.isActive == true) return
        load(mutable.value.loadedPage + 1)
    }
    private fun load(page: Int, preserveTail: Boolean = false) {
        val token = generation
        val criteria = mutableDraft.value
        val before = mutable.value
        mutable.value = before.copy(
            result = if (before.result is LoadState.Data) before.result else LoadState.Loading,
            loadingMore = page > 1, loadMoreError = null,
        )
        request = scope.launch {
            try {
                val result = cancellableResult {
                    if (favorites) services.pica.favorites(page) else services.pica.search(
                        criteria.keyword.orEmpty(), criteria.submittedCategory?.let(::listOf).orEmpty(), page)
                }
                if (token != generation) return@launch
                result.fold({ response ->
                    val old = mutable.value
                    val previous = if (page == 1 && !preserveTail) emptyList() else (old.result as? LoadState.Data)?.value.orEmpty()
                    val items = (if (preserveTail) response.items + previous else previous + response.items).distinctBy { it.id }
                        .filter { !favorites || session.mutations.value[it.id]?.favorite != false }
                    mutable.value = ComicListState(LoadState.Data(items),
                        (if (preserveTail) old.pages.filter { it.number != response.page } else if (page == 1) emptyList() else old.pages) +
                            ComicGridPage(response.page, response.items.map { it.id }),
                        if (preserveTail) maxOf(old.loadedPage, response.page) else response.page, response.totalPages,
                        restoringRange = (if (preserveTail) maxOf(old.loadedPage, response.page) else response.page) <
                            minOf(restoreThrough, response.totalPages))
                }, {
                    val old = mutable.value
                    mutable.value = old.copy(result = if (old.result is LoadState.Data) old.result
                        else LoadState.Error(it.message ?: "Load failed"), loadMoreError = it.message, loadingMore = false)
                })
            } finally {
                if (token == generation) mutable.value = mutable.value.copy(loadingMore = false)
            }
            if (token == generation && mutable.value.loadedPage == page &&
                page < restoreThrough && mutable.value.canLoadMore && mutable.value.loadMoreError == null) load(page + 1)
        }
    }
}
