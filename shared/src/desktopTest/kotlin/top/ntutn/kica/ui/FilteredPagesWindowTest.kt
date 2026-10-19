package top.ntutn.kica.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.awt.ComposeWindow
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import top.ntutn.kica.data.InMemoryLibraryRepository
import top.ntutn.kica.model.AppSettings
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.ui.component.ComicGrid
import top.ntutn.kica.ui.component.ComicGridPage
import top.ntutn.kica.ui.theme.KicaFluentTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilteredPagesWindowTest {
    @Test
    fun automaticallyPassesFilteredPagesButStopsOnErrorsAndRetriesSamePage() {
        assumeTrue(System.getenv("KICA_TAG_WINDOW_TEST") == "1")
        val library = InMemoryLibraryRepository()
        runBlocking { library.updateSettings(AppSettings(blockedCategories = setOf("hidden"))) }
        val requests = CopyOnWriteArrayList<Int>()
        val loaded = AtomicInteger(1)
        val retry = AtomicReference<() -> Unit>()
        val failure = AtomicReference<String?>()
        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            window = ComposeWindow().apply {
                setSize(720, 600)
                setContent {
                    var pages by remember { mutableStateOf(listOf(ComicGridPage(1, listOf("one")))) }
                    var comics by remember {
                        mutableStateOf(listOf(ComicSummary("one", "One", categories = listOf("hidden"))))
                    }
                    var error by remember { mutableStateOf<String?>(null) }
                    val request = {
                        val next = pages.last().number + 1
                        requests.add(next)
                        if (next == 3 && requests.count { it == 3 } == 1) {
                            error = "synthetic failure"
                        } else {
                            val comic = ComicSummary("$next", "Book $next",
                                categories = if (next < 4) listOf("hidden") else emptyList())
                            comics = comics + comic
                            pages = pages + ComicGridPage(next, listOf(comic.id))
                            error = null
                        }
                        loaded.set(pages.last().number)
                        failure.set(error)
                    }
                    SideEffect { retry.set(request) }
                    KicaFluentTheme {
                        CompositionLocalProvider(LocalTagLibrary provides library) {
                            ComicGrid(comics, {}, canLoadMore = pages.last().number < 4,
                                loadMoreError = error, onLoadMore = request, pages = pages)
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
            assertTrue(check(), "Requests: $requests; loaded: ${loaded.get()}")
        }
        try {
            pumpUntil { failure.get() != null }
            assertEquals(listOf(2, 3), requests.toList())
            repeat(5) {
                SwingUtilities.invokeAndWait { Snapshot.sendApplyNotifications(); window.repaint() }
                Thread.sleep(50)
            }
            assertEquals(listOf(2, 3), requests.toList())
            assertEquals(2, loaded.get())
            SwingUtilities.invokeAndWait { retry.get().invoke() }
            pumpUntil { loaded.get() == 4 }
            repeat(5) {
                SwingUtilities.invokeAndWait { Snapshot.sendApplyNotifications(); window.repaint() }
                Thread.sleep(50)
            }
            assertEquals(listOf(2, 3, 3, 4), requests.toList())
        } finally { SwingUtilities.invokeAndWait { window.dispose() } }
    }
}
