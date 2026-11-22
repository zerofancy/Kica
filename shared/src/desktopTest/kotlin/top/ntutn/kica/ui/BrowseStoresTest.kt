package top.ntutn.kica.ui

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import java.lang.reflect.Proxy
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import top.ntutn.kica.data.*
import top.ntutn.kica.model.*
import top.ntutn.kica.ui.navigation.AppServices
import top.ntutn.kica.ui.navigation.SessionData
import top.ntutn.kica.ui.store.*

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseStoresTest {
    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader,
        arrayOf(T::class.java)) { _, method, _ -> error("Unexpected call: ${method.name}") } as T

    private fun services(repository: PicaRepository) = AppServices(repository,
        InMemoryLibraryRepository(), unused(), unused(), unused())

    @Test
    fun paginationDeduplicatesRequestsAndFavoriteChangesKeepLoadedTail() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var requests = 0
        var includeNew = false
        val repository = object : PicaRepository by unused<PicaRepository>() {
            override suspend fun favorites(page: Int): ComicPage {
                requests++
                return ComicPage(if (page == 2) listOf(ComicSummary("tail", "Tail"))
                    else if (includeNew) listOf(ComicSummary("new", "New"))
                    else listOf(ComicSummary("one", "One")), page, 2)
            }
        }
        val services = services(repository)
        val session = SessionData(DefaultComponentContext(LifecycleRegistry()), services)
        val store = ComicListStore(services, session, true, SearchSnapshot())
        try {
            store.ensureLoaded(); store.ensureLoaded()
            runCurrent()
            assertEquals(1, requests)
            store.loadMore(); store.loadMore()
            runCurrent()
            assertEquals(2, requests)
            store.ensureLoaded()
            runCurrent()
            assertEquals(2, requests)
            session.confirmFavorite("one", false)
            runCurrent()
            assertEquals(listOf("tail"), (store.state.value.result as LoadState.Data).value.map { it.id })
            includeNew = true
            session.confirmFavorite("new", true)
            runCurrent()
            store.ensureLoaded(); store.ensureLoaded()
            runCurrent()
            assertEquals(3, requests)
            assertEquals(listOf("new", "tail"), (store.state.value.result as LoadState.Data).value.map { it.id })
            assertEquals(2, store.state.value.loadedPage)
        } finally {
            store.onDestroy(); session.close(); Dispatchers.resetMain()
        }
    }

    @Test
    fun supersededSearchCannotOverwriteCurrentResultOrSubmittedCriteria() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pending = CompletableDeferred<ComicPage>()
        val requests = mutableListOf<String>()
        val repository = object : PicaRepository by unused<PicaRepository>() {
            override suspend fun search(keyword: String, categories: List<String>, page: Int): ComicPage {
                requests += keyword
                return if (keyword == "slow") pending.await()
                    else ComicPage(listOf(ComicSummary(keyword, keyword)), 1, 1)
            }
        }
        val services = services(repository)
        val session = SessionData(DefaultComponentContext(LifecycleRegistry()), services)
        val store = ComicListStore(services, session, false, SearchSnapshot(query = "first", keyword = "first"))
        try {
            store.ensureLoaded(); runCurrent()
            store.editQuery("slow"); store.submit(); runCurrent()
            store.editQuery("latest"); store.submit(); runCurrent()
            store.editQuery("unsubmitted")
            pending.complete(ComicPage(listOf(ComicSummary("stale", "Stale")), 1, 1))
            runCurrent()
            assertEquals(listOf("first", "slow", "latest"), requests)
            assertEquals("latest", (store.state.value.result as LoadState.Data).value.single().id)
            assertEquals("latest", store.snapshot().keyword)
            assertEquals("unsubmitted", store.snapshot().query)
            val trimmed = top.ntutn.kica.ui.navigation.SnapshotPolicy.withoutOptional { store.snapshot() }
            assertEquals("latest", trimmed.query)
            assertEquals("latest", trimmed.keyword)
            assertEquals("unsubmitted", store.draft.value.query)
            assertFalse(store.state.value.loadingMore)
        } finally {
            store.onDestroy(); session.close(); Dispatchers.resetMain()
        }
    }

    @Test
    fun failedRestoreReplayKeepsDeepAnchorPendingUntilRangeIsAvailable() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var failSecond = true
        val requests = mutableListOf<Int>()
        val repository = object : PicaRepository by unused<PicaRepository>() {
            override suspend fun search(keyword: String, categories: List<String>, page: Int): ComicPage {
                requests += page
                if (page == 2 && failSecond) error("offline")
                return ComicPage(listOf(ComicSummary("page$page", "Page $page")), page, 3)
            }
        }
        val services = services(repository)
        val session = SessionData(DefaultComponentContext(LifecycleRegistry()), services)
        val store = ComicListStore(services, session, false,
            SearchSnapshot(query = "query", keyword = "query", loadedPageHint = 150))
        try {
            store.ensureLoaded(); runCurrent()
            assertEquals(listOf(1, 2), requests)
            assertTrue(store.state.value.restoringRange)
            assertEquals(150, store.snapshot().loadedPageHint)
            assertFalse(store.state.value.loadingMore)
            failSecond = false
            store.loadMore(); runCurrent()
            assertEquals(listOf(1, 2, 2, 3), requests)
            assertFalse(store.state.value.restoringRange)
            assertEquals(3, store.state.value.loadedPage)
        } finally {
            store.onDestroy(); session.close(); Dispatchers.resetMain()
        }
    }

    @Test
    fun settingsWritesUseLatestProjectionAndKeepDirtyProxyDrafts() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val library = InMemoryLibraryRepository()
        val translation = NavigationTranslation()
        val platform = object : PlatformServices by NavigationPlatform(NavigationCredentials()) {
            override val fileLocationProvider = object : FileLocationProvider {
                override suspend fun defaultDownloadLocation() = "downloads"
                override suspend fun chooseExportLocation() = "export"
                override suspend fun downloadedPages(task: DownloadTask) = emptyList<PageRef>()
            }
        }
        val services = AppServices(unused(), library, unused(), platform, translation)
        val store = SettingsStore(services, SettingsSnapshot())
        try {
            store.ensureLoaded(); runCurrent()
            store.host("draft.example")
            store.port("08080")
            store.update { it.copy(theme = ThemePreference.DARK) }
            runCurrent()
            assertEquals("draft.example", store.settings.value.network.proxyHost)
            assertEquals(8080, store.settings.value.network.proxyPort)
            assertEquals(ThemePreference.DARK, store.settings.value.theme)
            assertEquals("08080", store.draft.value.proxyPort)
            assertFalse(store.saving.value)
            val compact = top.ntutn.kica.ui.navigation.SnapshotPolicy.withoutOptional { store.snapshot() }
            assertEquals(SettingsSnapshot(), compact)
            assertEquals("draft.example", store.draft.value.proxyHost)
            store.enableTranslation(); runCurrent()
            assertTrue(store.settings.value.titleTranslationEnabled)
            assertTrue(translation.enabled.value)
            store.chooseExport(); runCurrent()
            assertEquals("export", store.exportLocation.value)
            assertEquals("downloads", store.downloadLocation.value)
        } finally { store.onDestroy(); Dispatchers.resetMain() }
    }

    @Test
    fun confirmedEntityChangesReplayIntoBothDetailEntries() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var calls = 0
        var succeeds = true
        val repository = object : PicaRepository by unused<PicaRepository>() {
            override suspend fun comic(id: String) = ComicDetail(id, "Comic")
            override suspend fun episodes(comicId: String) = emptyList<Episode>()
            override suspend fun toggleFavorite(comicId: String): Boolean { calls++; return succeeds }
            override suspend fun like(comicId: String) = true
        }
        val services = services(repository)
        val session = SessionData(DefaultComponentContext(LifecycleRegistry()), services)
        val first = DetailStore("same", services, session)
        val second = DetailStore("same", services, session)
        fun comic(store: DetailStore) = (store.state.value as LoadState.Data).value.first
        try {
            first.ensureLoaded(); second.ensureLoaded(); runCurrent()
            first.favorite(); first.favorite(); runCurrent()
            assertEquals(1, calls)
            assertTrue(comic(first).isFavorite)
            assertTrue(comic(second).isFavorite)
            second.like(); runCurrent()
            assertTrue(comic(first).isLiked)
            assertTrue(comic(second).isLiked)
            first.ensureLoaded(); second.ensureLoaded(); runCurrent()
            assertEquals(1, calls)
            succeeds = false
            second.favorite()
            assertFalse(comic(second).isFavorite)
            runCurrent()
            assertTrue(comic(second).isFavorite)
            assertTrue(comic(first).isFavorite)
            assertEquals(2, calls)
        } finally {
            first.onDestroy(); second.onDestroy(); session.close(); Dispatchers.resetMain()
        }
    }
}
