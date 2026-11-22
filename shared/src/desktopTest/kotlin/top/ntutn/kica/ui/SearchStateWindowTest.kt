package top.ntutn.kica.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.awt.ComposeWindow
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import top.ntutn.kica.data.*
import top.ntutn.kica.model.ComicPage
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.LoadState
import top.ntutn.kica.ui.navigation.AppServices
import top.ntutn.kica.ui.navigation.SessionData
import top.ntutn.kica.ui.screen.SearchScreen
import top.ntutn.kica.ui.store.ComicListStore
import top.ntutn.kica.ui.store.SearchSnapshot
import top.ntutn.kica.ui.theme.KicaFluentTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchStateWindowTest {
    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader,
        arrayOf(T::class.java)) { _, method, _ -> error("Unexpected call: ${method.name}") } as T

    @Test
    fun supersededSearchAndRemountKeepTheSameStore() {
        assumeTrue(System.getenv("KICA_TAG_WINDOW_TEST") == "1")
        val requests = CopyOnWriteArrayList<String>()
        val pending = CompletableDeferred<ComicPage>()
        val repository = object : PicaRepository by unused<PicaRepository>() {
            override suspend fun search(keyword: String, categories: List<String>, page: Int): ComicPage {
                requests.add(keyword)
                return if (keyword == "second") pending.await()
                else ComicPage(listOf(ComicSummary(keyword, keyword)), page, 1)
            }
        }
        val library = InMemoryLibraryRepository()
        val services = AppServices(repository, library, unused(), unused(), unused())
        val session = SessionData(DefaultComponentContext(LifecycleRegistry()), services)
        val store = ComicListStore(services, session, false, SearchSnapshot(query = "first", keyword = "first", fromTag = true))
        val mounted = mutableStateOf(true)
        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            store.ensureLoaded()
            window = ComposeWindow().apply {
                setSize(720, 600)
                setContent { KicaFluentTheme {
                    if (mounted.value) SearchScreen(store, library, "first", null, true, {}, {})
                } }
                isVisible = true
            }
        }
        fun pumpUntil(check: () -> Boolean) {
            val deadline = System.nanoTime() + 8_000_000_000L
            do {
                SwingUtilities.invokeAndWait { Snapshot.sendApplyNotifications(); window.repaint() }
                Thread.sleep(50)
            } while (!check() && System.nanoTime() < deadline)
            assertTrue(check(), "Requests: $requests; result: ${store.state.value.result}")
        }
        try {
            pumpUntil { store.state.value.result is LoadState.Data }
            SwingUtilities.invokeAndWait { store.editQuery("second"); store.submit() }
            pumpUntil { requests.contains("second") }
            SwingUtilities.invokeAndWait { store.editQuery("first"); store.submit() }
            pumpUntil { requests.size == 3 && store.state.value.result is LoadState.Data }
            assertEquals(listOf("first", "second", "first"), requests.toList())
            SwingUtilities.invokeAndWait { mounted.value = false }
            SwingUtilities.invokeAndWait { mounted.value = true; store.ensureLoaded() }
            repeat(10) {
                SwingUtilities.invokeAndWait { Snapshot.sendApplyNotifications(); window.repaint() }
                Thread.sleep(50)
            }
            assertEquals(listOf("first", "second", "first"), requests.toList())
            assertTrue(store.state.value.result is LoadState.Data)
        } finally {
            SwingUtilities.invokeAndWait { store.onDestroy(); window.dispose() }
            runBlocking { session.close() }
        }
    }
}
