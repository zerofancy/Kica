package top.ntutn.kica.ui.store

import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import top.ntutn.kica.data.cancellableResult
import top.ntutn.kica.model.*
import top.ntutn.kica.ui.navigation.AppServices
import top.ntutn.kica.ui.navigation.SessionData
import top.ntutn.kica.ui.toSummary

internal class DetailStore(
    private val id: String, private val services: AppServices, private val session: SessionData,
) : PageStore() {
    private val mutable = MutableStateFlow<LoadState<Pair<ComicDetail, List<Episode>>>>(LoadState.Loading)
    val state: StateFlow<LoadState<Pair<ComicDetail, List<Episode>>>> = mutable
    private val mutableFavoriteBusy = MutableStateFlow(false)
    val favoriteBusy = mutableFavoriteBusy.asStateFlow()
    private val mutableLikeBusy = MutableStateFlow(false)
    val likeBusy = mutableLikeBusy.asStateFlow()
    private var started = false
    init { scope.launch {
        session.mutations.collect { mutations ->
            val value = (mutable.value as? LoadState.Data)?.value ?: return@collect
            val mutation = mutations[id] ?: return@collect
            mutable.value = LoadState.Data(value.first.copy(
                isFavorite = if (mutableFavoriteBusy.value) value.first.isFavorite else mutation.favorite ?: value.first.isFavorite,
                isLiked = if (mutableLikeBusy.value) value.first.isLiked else mutation.liked ?: value.first.isLiked,
            ) to value.second)
        }
    } }
    override fun ensureLoaded() { if (!started) { started = true; refresh() } }
    fun refresh() { scope.launch {
        cancellableResult { services.pica.comic(id) to services.pica.episodes(id) }.fold({ (comic, episodes) ->
            val mutation = session.mutations.value[id]
            mutable.value = LoadState.Data(comic.copy(isFavorite = mutation?.favorite ?: comic.isFavorite,
                isLiked = mutation?.liked ?: comic.isLiked) to episodes)
        }, { if (mutable.value !is LoadState.Data) mutable.value = LoadState.Error(it.message ?: "Load failed") })
    } }
    fun favorite() {
        if (mutableFavoriteBusy.value) return
        val comic = (mutable.value as? LoadState.Data)?.value?.first ?: return
        mutableFavoriteBusy.value = true
        updateComic { it.copy(isFavorite = !comic.isFavorite) }
        scope.launch { try {
            cancellableResult { session.toggleFavorite(id, comic.isFavorite) }
            updateComic { it.copy(isFavorite = session.mutations.value[id]?.favorite ?: comic.isFavorite) }
        } finally { mutableFavoriteBusy.value = false } }
    }
    fun like() {
        if (mutableLikeBusy.value) return
        val comic = (mutable.value as? LoadState.Data)?.value?.first ?: return
        mutableLikeBusy.value = true
        updateComic { it.copy(isLiked = !comic.isLiked) }
        scope.launch { try {
            cancellableResult { session.toggleLike(id, comic.isLiked) }
            updateComic { it.copy(isLiked = session.mutations.value[id]?.liked ?: comic.isLiked) }
        } finally { mutableLikeBusy.value = false } }
    }
    private fun updateComic(transform: (ComicDetail) -> ComicDetail) {
        val current = (mutable.value as? LoadState.Data)?.value ?: return
        mutable.value = LoadState.Data(transform(current.first) to current.second)
    }
    fun download(episode: Episode) {
        val comic = (mutable.value as? LoadState.Data)?.value?.first ?: return
        scope.launch { cancellableResult {
            services.downloads.enqueue(comic.toSummary(), episode,
                services.platform.fileLocationProvider.defaultDownloadLocation())
        } }
    }
}
