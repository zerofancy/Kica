package top.ntutn.kica.ui.screen

import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import top.ntutn.kica.data.FileLocationProvider
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.data.PicaRepository
import top.ntutn.kica.model.ComicDetail
import top.ntutn.kica.model.Episode
import top.ntutn.kica.model.LoadState
import top.ntutn.kica.model.PageRef
import top.ntutn.kica.model.ReaderMode
import top.ntutn.kica.model.ReadingProgress
import top.ntutn.kica.ui.navigation.SessionProgressCoordinator
import top.ntutn.kica.ui.progress
import top.ntutn.kica.ui.toSummary

/** Only confirmed entry state and page-sized coordinates, never network results or native handles. */
@Serializable
internal data class ReaderSnapshot(
    val version: Int = 1,
    val ready: Boolean = false,
    val episodeId: String? = null,
    val pageIndex: Int = 0,
    val mode: String = ReaderMode.VERTICAL.name,
    val fit: String = PageFit.WIDTH.name,
)

internal data class ReaderUiState(
    val entry: ReaderEntryState = ReaderEntryState.Checking,
    val episodeId: String,
    val episodes: List<Episode> = emptyList(),
    val comic: ComicDetail? = null,
    val pages: LoadState<List<PageRef>> = LoadState.Loading,
    val mode: ReaderMode = ReaderMode.VERTICAL,
    val fit: PageFit = PageFit.WIDTH,
    val initialPage: Int = 0,
    val chapterChanging: Boolean = false,
    val layoutGeneration: Int = 0,
    val adjusted: Boolean = false,
)

/** Entry-owned reads; session-owned writes. No component, navigation callback or platform shell. */
internal class ReaderStore(
    val entryId: String,
    private val comicId: String,
    private val initialEpisodeId: String,
    private val repository: PicaRepository,
    private val library: LibraryRepository,
    private val writer: SessionProgressCoordinator,
    private val fileProvider: FileLocationProvider,
    snapshot: ReaderSnapshot? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : InstanceKeeper.Instance {
    private val restored = snapshot?.takeIf { it.version == 1 && it.ready && !it.episodeId.isNullOrBlank() }
    private val restoredMode = ReaderMode.entries.firstOrNull { it.name == restored?.mode } ?: ReaderMode.VERTICAL
    private val entry = ReaderEntrySession(restored?.let {
        ReaderEntryState.Ready(ReaderStart(it.pageIndex.coerceAtLeast(0), restoredMode, false))
    } ?: ReaderEntryState.Checking)
    private val mutableState = MutableStateFlow(ReaderUiState(
        entry = entry.state,
        episodeId = restored?.episodeId ?: initialEpisodeId,
        initialPage = restored?.pageIndex?.coerceAtLeast(0) ?: 0,
        mode = restoredMode,
        fit = PageFit.entries.firstOrNull { it.name == restored?.fit } ?: defaultFit(restoredMode),
    ))
    val state: StateFlow<ReaderUiState> = mutableState.asStateFlow()
    private var loaded = false
    private var destroyed = false
    private var entryJob: Job? = null
    private var pagesJob: Job? = null
    private var readGeneration = 0
    private var binding: Any? = null
    private var actualEpisode = restored?.episodeId
    private var actualPage: Int? = restored?.pageIndex
    private var lastReport: Triple<String, Int, ReaderMode>? = null
    private var acceptedProgress: ReadingProgress? = null

    /** A UI lease rejects callbacks after unmount; the application file facade has no host handle. */
    fun bind(token: Any) {
        if (binding === token || destroyed) return
        binding = token
        update { copy(layoutGeneration = layoutGeneration + 1) }
    }

    fun isBound(token: Any): Boolean = !destroyed && binding === token

    fun unbind(token: Any) {
        if (binding !== token) return
        binding = null
        update { copy(layoutGeneration = layoutGeneration + 1) }
    }

    fun ensureLoaded() {
        if (loaded || destroyed) return
        loaded = true
        scope.launch {
            try {
                val episodes = repository.episodes(comicId)
                if (!destroyed) update { copy(episodes = episodes) }
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
        }
        scope.launch {
            try {
                val comic = repository.comic(comicId)
                if (!destroyed) {
                    update { copy(comic = comic) }
                    // If detail was slower than the visible layout, complete history for the
                    // already accepted real page; never synthesize a page from a restored hint.
                    acceptedProgress?.let { accepted ->
                        val title = state.value.episodes.firstOrNull { it.id == accepted.episodeId }?.title ?: accepted.episodeId
                        writer.save(accepted, comic.toSummary(), title)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
        }
        if (entry.state is ReaderEntryState.Ready) loadPages() else readEntry()
    }

    fun retryEntry() { if (entry.state == ReaderEntryState.Failed) readEntry() }

    private fun readEntry() {
        if (destroyed || entryJob?.isActive == true) return
        entryJob = scope.launch {
            entry.readProgress {
                update { copy(entry = ReaderEntryState.Checking) }
                library.readingProgress(comicId, initialEpisodeId)
            }
            if (destroyed) return@launch
            update { copy(entry = this@ReaderStore.entry.state) }
            applyDecision()
        }
    }

    fun choose(continueReading: Boolean) {
        if (destroyed || entry.state !is ReaderEntryState.Choice) return
        entry.choose(continueReading)
        update { copy(entry = this@ReaderStore.entry.state) }
        applyDecision()
    }

    private fun applyDecision() {
        val ready = entry.state as? ReaderEntryState.Ready ?: return
        update { copy(mode = ready.start.mode, fit = defaultFit(ready.start.mode), initialPage = ready.start.pageIndex) }
        loadPages()
    }

    fun cancel(): Boolean {
        if (destroyed) return false
        val cancelled = entry.cancel()
        if (cancelled) {
            readGeneration++
            entryJob?.cancel()
            pagesJob?.cancel()
            update { copy(entry = ReaderEntryState.Cancelled, layoutGeneration = layoutGeneration + 1) }
        }
        return cancelled
    }

    fun retryPages() {
        if (!destroyed && entry.state is ReaderEntryState.Ready && pagesJob?.isActive != true) loadPages()
    }

    private fun loadPages() {
        pagesJob?.cancel()
        val request = ++readGeneration
        val chapter = state.value.episodeId
        update { copy(pages = LoadState.Loading, layoutGeneration = layoutGeneration + 1) }
        pagesJob = scope.launch {
            val result = try {
                LoadState.Data(repository.pages(comicId, chapter))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val offline = try {
                    val task = library.downloads().first().firstOrNull { it.comic.id == comicId && it.episode.id == chapter }
                    task?.let { fileProvider.downloadedPages(it) }.orEmpty()
                } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { emptyList() }
                if (offline.isNotEmpty()) LoadState.Data(offline, fromCache = true) else LoadState.Error(error.message.orEmpty())
            }
            if (destroyed || request != readGeneration) return@launch
            val size = (result as? LoadState.Data)?.value?.size ?: 0
            val start = (entry.state as? ReaderEntryState.Ready)?.start
            val adjusted = size > 0 && chapter == initialEpisodeId && start?.reportAdjustment == true &&
                state.value.initialPage != state.value.initialPage.coerceIn(0, size - 1)
            update { copy(pages = result, adjusted = adjusted) }
        }
    }

    fun changeChapter(forward: Boolean): Boolean {
        val current = state.value
        if (destroyed || current.chapterChanging || current.entry !is ReaderEntryState.Ready) return false
        val index = current.episodes.indexOfFirst { it.id == current.episodeId }
        if (index < 0) return false
        val target = current.episodes.getOrNull(index + if (forward) 1 else -1) ?: return false
        update { copy(episodeId = target.id, initialPage = if (forward) 0 else Int.MAX_VALUE,
            chapterChanging = true, adjusted = false) }
        loadPages()
        return true
    }

    fun changeMode(mode: ReaderMode) {
        if (destroyed || mode == state.value.mode) return
        update { copy(mode = mode, fit = defaultFit(mode), layoutGeneration = layoutGeneration + 1) }
    }

    fun changeFit(fit: PageFit) { if (!destroyed) update { copy(fit = fit) } }

    /** Callback identity includes entry, chapter and generation; only a mounted valid layout reports. */
    fun reportPage(entryId: String, chapter: String, generation: Int, page: Int, uiToken: Any? = binding): Boolean {
        val current = state.value
        val pages = (current.pages as? LoadState.Data)?.value ?: return false
        if (destroyed || binding == null || binding !== uiToken || current.entry !is ReaderEntryState.Ready || this.entryId != entryId ||
            chapter != current.episodeId || generation != current.layoutGeneration || page !in pages.indices) return false
        actualEpisode = chapter
        actualPage = page
        update { copy(initialPage = page, chapterChanging = false) }
        val report = Triple(chapter, page, current.mode)
        if (lastReport == report) return true
        val title = current.episodes.firstOrNull { it.id == chapter }?.title ?: chapter
        val reading = progress(comicId, chapter, page, current.mode)
        val accepted = writer.save(reading, current.comic?.toSummary(), title)
        if (accepted) {
            lastReport = report
            acceptedProgress = reading
        }
        return accepted
    }

    fun snapshot(): ReaderSnapshot {
        val current = state.value
        val ready = current.entry is ReaderEntryState.Ready
        val start = (current.entry as? ReaderEntryState.Ready)?.start
        return ReaderSnapshot(ready = ready,
            episodeId = if (ready) actualEpisode ?: initialEpisodeId else null,
            pageIndex = actualPage ?: start?.pageIndex?.coerceAtLeast(0) ?: 0,
            mode = current.mode.name, fit = current.fit.name)
    }

    override fun onDestroy() {
        if (destroyed) return
        destroyed = true
        entry.cancel()
        binding = null
        scope.cancel()
        // The session writer intentionally survives this store.
    }

    private inline fun update(transform: ReaderUiState.() -> ReaderUiState) {
        mutableState.value = mutableState.value.transform()
    }

    private fun defaultFit(mode: ReaderMode) = if (mode == ReaderMode.VERTICAL) PageFit.WIDTH else PageFit.HEIGHT
}
