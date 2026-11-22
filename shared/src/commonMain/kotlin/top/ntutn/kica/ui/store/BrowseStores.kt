package top.ntutn.kica.ui.store

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import top.ntutn.kica.data.cancellableResult
import top.ntutn.kica.model.*
import top.ntutn.kica.ui.navigation.AppServices
import top.ntutn.kica.ui.state.RandomComicsUiState

internal class RandomComicsStore(
    private val fetch: suspend () -> List<ComicSummary>,
    private val readCache: suspend () -> List<ComicSummary>?,
    private val writeCache: suspend (List<ComicSummary>) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) : PageStore(scope) {
    constructor(services: AppServices) : this(services.pica::randomComics,
        services.library::cachedRandomComics, services.library::cacheRandomComics,
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate))
    private var started = false
    private val mutable = MutableStateFlow(RandomComicsUiState())
    val state: StateFlow<RandomComicsUiState> = mutable
    override fun ensureLoaded() { if (!started) { started = true; load(true) } }
    fun refresh() { if (!mutable.value.isLoading) load(false) }
    private fun load(cache: Boolean) {
        mutable.value = mutable.value.startLoading()
        scope.launch {
            if (cache) cancellableResult { readCache() }.getOrNull()?.let {
                mutable.value = mutable.value.loadSuccess(it).startLoading()
            }
            cancellableResult { fetch() }.fold({
                mutable.value = mutable.value.loadSuccess(it)
                cancellableResult { writeCache(it) }
            }, { mutable.value = mutable.value.loadFailure(it.message ?: "Load failed") })
        }
    }
}

internal class HomeStore(private val services: AppServices) : PageStore() {
    private var started = false
    private val mutable = MutableStateFlow<LoadState<List<ComicSummary>>>(LoadState.Loading)
    val state: StateFlow<LoadState<List<ComicSummary>>> = mutable
    override fun ensureLoaded() { if (!started) { started = true; refresh() } }
    fun refresh() { scope.launch {
        val cached = cancellableResult { services.library.cachedRecommendations() }.getOrNull()
        if (mutable.value !is LoadState.Data && cached != null) mutable.value = LoadState.Data(cached, true)
        cancellableResult { services.pica.recommendations() }.fold({
            mutable.value = LoadState.Data(it)
            cancellableResult { services.library.cacheRecommendations(it) }
        }, { if (mutable.value !is LoadState.Data) mutable.value = LoadState.Error(it.message ?: "Load failed") })
    } }
}

internal class DiscoverStore(private val services: AppServices, period: RankPeriod) : PageStore() {
    private val mutableSelected = MutableStateFlow(period)
    val selected = mutableSelected.asStateFlow()
    private val mutableCategories = MutableStateFlow<LoadState<List<ComicCategory>>>(LoadState.Loading)
    val categories = mutableCategories.asStateFlow()
    private val mutableRanking = MutableStateFlow<LoadState<List<ComicSummary>>>(LoadState.Loading)
    val ranking = mutableRanking.asStateFlow()
    private val rankings = mutableMapOf<RankPeriod, LoadState<List<ComicSummary>>>()
    private val generations = mutableMapOf<RankPeriod, Int>()
    private var started = false
    override fun ensureLoaded() { if (!started) { started = true; refresh() } }
    fun select(period: RankPeriod) {
        mutableSelected.value = period
        mutableRanking.value = rankings[period] ?: LoadState.Loading
        if (period !in rankings) loadRanking(period)
    }
    private fun loadRanking(period: RankPeriod) {
        val generation = (generations[period] ?: 0) + 1
        generations[period] = generation
        if (rankings[period] !is LoadState.Data) rankings[period] = LoadState.Loading
        scope.launch {
            val result = cancellableResult { services.pica.ranking(period) }.fold(
                { LoadState.Data(it) }, { LoadState.Error(it.message ?: "Load failed") })
            if (generations[period] != generation) return@launch
            if (result !is LoadState.Error || rankings[period] !is LoadState.Data) rankings[period] = result
            if (mutableSelected.value == period) mutableRanking.value = rankings.getValue(period)
        }
    }
    fun updateBlocked(mutableCategories: Set<String>) { scope.launch {
        val current = services.library.settings().first()
        services.library.updateSettings(current.copy(blockedCategories = mutableCategories))
    } }
    fun refresh() {
        loadRanking(mutableSelected.value)
        scope.launch {
            val cached = cancellableResult { services.library.cachedCategories() }.getOrNull()
            if (mutableCategories.value !is LoadState.Data && cached != null) mutableCategories.value = LoadState.Data(cached, true)
            cancellableResult { services.pica.categories() }.fold({
                mutableCategories.value = LoadState.Data(it)
                cancellableResult { services.library.cacheCategories(it) }
            }, { if (mutableCategories.value !is LoadState.Data) mutableCategories.value = LoadState.Error(it.message ?: "Load failed") })
        }
    }
}
