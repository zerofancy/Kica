package top.ntutn.kica.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.awt.ComposeWindow
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assume.assumeTrue
import top.ntutn.kica.data.InMemoryLibraryRepository
import top.ntutn.kica.data.PicaRepository
import top.ntutn.kica.model.AppRoute
import top.ntutn.kica.model.ComicPage
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.LoadState
import top.ntutn.kica.ui.screen.LocalSearchPages
import top.ntutn.kica.ui.screen.SearchCriteria
import top.ntutn.kica.ui.screen.SearchPageState
import top.ntutn.kica.ui.screen.SearchScreen
import top.ntutn.kica.ui.theme.KicaFluentTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchStateWindowTest {
    @Test
    fun cancelledSearchCanReturnToPreviousCriteriaAndRestoreWithoutReloading() {
        assumeTrue(System.getenv("KICA_TAG_WINDOW_TEST") == "1")
        val requests = CopyOnWriteArrayList<String>()
        val pending = CompletableDeferred<ComicPage>()
        val unused = Proxy.newProxyInstance(PicaRepository::class.java.classLoader,
            arrayOf(PicaRepository::class.java)) { _, method, _ ->
            error("Unexpected call: ${method.name}")
        } as PicaRepository
        val repository = object : PicaRepository by unused {
            override suspend fun search(keyword: String, categories: List<String>, page: Int): ComicPage {
                requests.add(keyword)
                return if (keyword == "second") pending.await()
                else ComicPage(listOf(ComicSummary(keyword, keyword)), page, 1)
            }
        }
        val library = InMemoryLibraryRepository()
        val page = SearchPageState("first", null, true)
        val pages = mutableMapOf(AppRoute.Search("first", fromTag = true).toString() to page)
        val mounted = mutableStateOf(true)
        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            window = ComposeWindow().apply {
                setSize(720, 600)
                setContent {
                    KicaFluentTheme {
                        CompositionLocalProvider(LocalSearchPages provides pages) {
                            if (mounted.value) SearchScreen(repository, library, "first", null, true, {}, {})
                        }
                    }
                }
                isVisible = true
            }
        }
        fun pumpUntil(check: () -> Boolean) {
            val deadline = System.nanoTime() + 8_000_000_000L
            do {
                SwingUtilities.invokeAndWait { Snapshot.sendApplyNotifications(); window.repaint() }
                Thread.sleep(50)
            } while (!check() && System.nanoTime() < deadline)
            assertTrue(check(), "Requests: $requests; result: ${page.result.value}")
        }
        try {
            pumpUntil { page.completed != null }
            SwingUtilities.invokeAndWait { page.criteria.value = SearchCriteria("second", null) }
            pumpUntil { requests.contains("second") && page.result.value == LoadState.Loading }
            SwingUtilities.invokeAndWait { page.criteria.value = SearchCriteria("first", null) }
            pumpUntil { page.completed == (SearchCriteria("first", null) to 0) }
            assertEquals(listOf("first", "second", "first"), requests.toList())
            assertTrue(page.result.value is LoadState.Data)
            val generation = page.generation
            SwingUtilities.invokeAndWait { mounted.value = false }
            pumpUntil { page.generation > generation }
            SwingUtilities.invokeAndWait { mounted.value = true }
            repeat(10) {
                SwingUtilities.invokeAndWait { Snapshot.sendApplyNotifications(); window.repaint() }
                Thread.sleep(50)
            }
            assertEquals(listOf("first", "second", "first"), requests.toList())
            assertTrue(page.result.value is LoadState.Data)
        } finally {
            SwingUtilities.invokeAndWait { window.dispose() }
        }
    }
}
