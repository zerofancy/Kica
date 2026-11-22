package top.ntutn.kica.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.ntutn.kica.data.FileLocationProvider
import top.ntutn.kica.data.InMemoryLibraryRepository
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.data.PicaRepository
import top.ntutn.kica.model.*
import top.ntutn.kica.ui.navigation.SessionProgressCoordinator
import top.ntutn.kica.ui.screen.PageFit
import top.ntutn.kica.ui.screen.ReaderEntryState
import top.ntutn.kica.ui.screen.ReaderSnapshot
import top.ntutn.kica.ui.screen.ReaderStore

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderStoreTest {
    @Test
    fun ensureLoadedIsIdempotentAcrossUiAndConfigurationRebinding() = runTest {
        val fixture = fixture()
        fixture.store.bind(Any())
        repeat(5) { fixture.store.ensureLoaded() }
        runCurrent()
        val token = Any()
        fixture.store.bind(token)
        fixture.store.unbind(token)
        fixture.store.bind(Any())
        fixture.store.ensureLoaded()
        runCurrent()
        assertEquals(1, fixture.repository.comicCalls)
        assertEquals(1, fixture.repository.episodeCalls)
        assertEquals(listOf("e1"), fixture.repository.pageCalls)
        assertEquals(1, fixture.reads())
        fixture.store.onDestroy()
    }

    @Test
    fun readySnapshotRestoresActualChapterWithoutReadingEntryProgress() = runTest {
        val fixture = fixture(snapshot = ReaderSnapshot(ready = true, episodeId = "e2", pageIndex = 3,
            mode = ReaderMode.PAGED_RIGHT_TO_LEFT.name, fit = PageFit.WIDTH.name))
        fixture.store.bind(Any())
        fixture.store.ensureLoaded()
        runCurrent()
        val state = fixture.store.state.value
        assertIs<ReaderEntryState.Ready>(state.entry)
        assertEquals("e2", state.episodeId)
        assertEquals(3, state.initialPage)
        assertEquals(ReaderMode.PAGED_RIGHT_TO_LEFT, state.mode)
        assertEquals(PageFit.WIDTH, state.fit)
        assertEquals(0, fixture.reads())
        assertEquals(listOf("e2"), fixture.repository.pageCalls)
        assertEquals(null, fixture.memory.readingProgress("comic", "e2"))
        assertTrue(Json.encodeToString(fixture.store.snapshot()).length < 1_024)
        fixture.store.onDestroy()
    }

    @Test
    fun unconfirmedSnapshotAndNewExternalEntryStillAskForChoice() = runTest {
        val memory = InMemoryLibraryRepository()
        memory.saveProgress(ReadingProgress("comic", "e1", 3, ReaderMode.DOUBLE_RIGHT_TO_LEFT, 1))
        for (snapshot in listOf(null, ReaderSnapshot(ready = false), ReaderSnapshot(version = 99, ready = true))) {
            val fixture = fixture(memory = memory, snapshot = snapshot)
            fixture.store.ensureLoaded()
            runCurrent()
            assertIs<ReaderEntryState.Choice>(fixture.store.state.value.entry)
            assertEquals(emptyList(), fixture.repository.pageCalls)
            fixture.store.choose(true)
            fixture.store.choose(false)
            runCurrent()
            assertEquals(3, fixture.store.state.value.initialPage)
            assertEquals(1, fixture.reads())
            fixture.store.onDestroy()
        }
    }

    @Test
    fun oldModeChapterEntryAndUnmountedReportsAreRejected() = runTest {
        val fixture = fixture()
        val token = Any()
        fixture.store.bind(token)
        fixture.store.ensureLoaded()
        runCurrent()
        val first = fixture.store.state.value
        assertFalse(fixture.store.reportPage("other-entry", "e1", first.layoutGeneration, 2))
        fixture.store.changeMode(ReaderMode.PAGED_LEFT_TO_RIGHT)
        assertFalse(fixture.store.reportPage("entry", "e1", first.layoutGeneration, 2))
        val second = fixture.store.state.value
        assertFalse(fixture.store.reportPage("entry", "e1", second.layoutGeneration, 50))
        assertTrue(fixture.store.changeChapter(true))
        runCurrent()
        assertFalse(fixture.store.reportPage("entry", "e1", second.layoutGeneration, 2))
        val newToken = Any()
        fixture.store.bind(newToken)
        val third = fixture.store.state.value
        assertFalse(fixture.store.reportPage("entry", "e2", third.layoutGeneration, 2, token))
        fixture.store.unbind(token) // disposing the old shell must not clear the new binding
        assertTrue(fixture.store.isBound(newToken))
        fixture.store.unbind(newToken)
        assertFalse(fixture.store.reportPage("entry", "e2", third.layoutGeneration, 2))
        assertEquals(null, fixture.memory.readingProgress("comic", "e1"))
        fixture.store.onDestroy()
    }

    @Test
    fun duplicateLayoutReportsWriteOnceAndAcceptedProgressSurvivesPop() = runTest {
        val memory = InMemoryLibraryRepository()
        var writes = 0
        val library = object : LibraryRepository by memory {
            override suspend fun saveProgress(progress: ReadingProgress) {
                delay(100)
                writes++
                memory.saveProgress(progress)
            }
        }
        val fixture = fixture(memory = memory, library = library)
        fixture.store.bind(Any())
        fixture.store.ensureLoaded()
        runCurrent()
        val state = fixture.store.state.value
        repeat(3) { assertTrue(fixture.store.reportPage("entry", "e1", state.layoutGeneration, 2)) }
        fixture.store.onDestroy()
        assertFalse(fixture.store.reportPage("entry", "e1", state.layoutGeneration, 3))
        assertTrue(fixture.writer.drain())
        assertEquals(1, writes)
        assertEquals(2, memory.readingProgress("comic", "e1")?.pageIndex)
    }

    @Test
    fun internalChapterIntentStaysInMemoryButSnapshotUsesLastActualPage() = runTest {
        val fixture = fixture()
        fixture.store.bind(Any())
        fixture.store.ensureLoaded()
        runCurrent()
        fixture.store.reportPage("entry", "e1", fixture.store.state.value.layoutGeneration, 2)
        val gate = CompletableDeferred<Unit>()
        fixture.repository.pageGate = gate
        assertTrue(fixture.store.changeChapter(true))
        runCurrent()
        val token = Any()
        fixture.store.bind(token)
        fixture.store.unbind(token)
        fixture.store.bind(Any())
        fixture.store.ensureLoaded()
        assertEquals("e2", fixture.store.state.value.episodeId)
        assertEquals(0, fixture.store.state.value.initialPage)
        assertEquals("e1", fixture.store.snapshot().episodeId)
        assertEquals(2, fixture.store.snapshot().pageIndex)
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("e1", "e2"), fixture.repository.pageCalls)
        assertEquals(1, fixture.reads())
        assertEquals(null, fixture.memory.readingProgress("comic", "e2"))
        fixture.store.onDestroy()
    }

    @Test
    fun failedChapterRetryKeepsDecisionAndDoesNotReadProgressAgain() = runTest {
        val fixture = fixture()
        fixture.repository.failPages = true
        fixture.store.ensureLoaded()
        runCurrent()
        assertIs<LoadState.Error>(fixture.store.state.value.pages)
        fixture.store.ensureLoaded()
        runCurrent()
        assertEquals(1, fixture.repository.pageCalls.size)
        fixture.repository.failPages = false
        fixture.store.retryPages()
        runCurrent()
        assertIs<LoadState.Data<List<PageRef>>>(fixture.store.state.value.pages)
        assertEquals(2, fixture.repository.pageCalls.size)
        assertEquals(1, fixture.reads())
        fixture.store.onDestroy()
    }

    @Test
    fun offlineFallbackKeepsDownloadAndOnlyReportsActuallyDisplayedPage() = runTest {
        val memory = InMemoryLibraryRepository()
        val task = DownloadTask("download", ComicSummary("comic", "Fixture"), Episode("e1", "comic", 1, "Chapter 1", 5),
            status = DownloadStatus.COMPLETED)
        memory.upsertDownload(task)
        val offline = listOf(PageRef(0, "", localPath = "/fixture/page.png"))
        val fixture = fixture(memory = memory, offlinePages = offline)
        fixture.repository.failPages = true
        fixture.store.bind(Any())
        fixture.store.ensureLoaded()
        runCurrent()
        val state = fixture.store.state.value
        val pages = assertIs<LoadState.Data<List<PageRef>>>(state.pages)
        assertEquals(offline, pages.value)
        assertTrue(pages.fromCache)
        assertEquals(null, memory.readingProgress("comic", "e1"))
        assertTrue(fixture.store.reportPage("entry", "e1", state.layoutGeneration, 0))
        assertTrue(fixture.writer.drain())
        assertEquals(task, memory.downloads().first().single())
        fixture.store.onDestroy()
    }

    private data class Fixture(
        val store: ReaderStore,
        val repository: Repository,
        val writer: SessionProgressCoordinator,
        val memory: InMemoryLibraryRepository,
        val reads: () -> Int,
    )

    private fun TestScope.fixture(
        memory: InMemoryLibraryRepository = InMemoryLibraryRepository(),
        library: LibraryRepository = memory,
        snapshot: ReaderSnapshot? = null,
        offlinePages: List<PageRef> = emptyList(),
    ): Fixture {
        var reads = 0
        val counted = object : LibraryRepository by library {
            override suspend fun readingProgress(comicId: String, episodeId: String): ReadingProgress? {
                reads++
                return library.readingProgress(comicId, episodeId)
            }
        }
        val repository = Repository()
        val writer = SessionProgressCoordinator(backgroundScope, counted)
        val scope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]))
        val files = object : FileLocationProvider {
            override suspend fun defaultDownloadLocation() = ""
            override suspend fun chooseExportLocation(): String? = null
            override suspend fun downloadedPages(task: DownloadTask) = offlinePages
        }
        return Fixture(ReaderStore("entry", "comic", "e1", repository, counted, writer, files, snapshot, scope),
            repository, writer, memory) { reads }
    }

    private class Repository : PicaRepository {
        override val session = MutableStateFlow<UserSession?>(null)
        var comicCalls = 0
        var episodeCalls = 0
        val pageCalls = mutableListOf<String>()
        var pageGate: CompletableDeferred<Unit>? = null
        var failPages = false
        override suspend fun comic(id: String): ComicDetail { comicCalls++; return ComicDetail(id, "Fixture") }
        override suspend fun episodes(comicId: String): List<Episode> {
            episodeCalls++
            return (1..2).map { Episode("e$it", comicId, it, "Chapter $it", 5) }
        }
        override suspend fun pages(comicId: String, episodeId: String): List<PageRef> {
            pageCalls += episodeId
            pageGate?.await()
            if (failPages) error("Offline fixture")
            return (0..4).map { PageRef(it, "", "Page $it") }
        }
        override suspend fun restoreSession() = Unit
        override suspend fun updateNetworkSettings(settings: NetworkSettings) = Unit
        override suspend fun login(email: String, password: String): UserSession = error("Unused")
        override suspend fun logout() = Unit
        override suspend fun recommendations(): List<ComicSummary> = error("Unused")
        override suspend fun randomComics(): List<ComicSummary> = error("Unused")
        override suspend fun categories(): List<ComicCategory> = error("Unused")
        override suspend fun ranking(period: RankPeriod): List<ComicSummary> = error("Unused")
        override suspend fun search(keyword: String, categories: List<String>, page: Int): ComicPage = error("Unused")
        override suspend fun favorites(page: Int): ComicPage = error("Unused")
        override suspend fun toggleFavorite(comicId: String): Boolean = error("Unused")
        override suspend fun like(comicId: String): Boolean = error("Unused")
    }
}
