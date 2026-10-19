package top.ntutn.kica.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.awt.ComposeWindow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import top.ntutn.kica.data.*
import top.ntutn.kica.model.AppSettings
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.ui.component.ComicGrid
import top.ntutn.kica.ui.component.ComicGridPage
import top.ntutn.kica.ui.theme.KicaFluentTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaginationScrollWindowTest {
    @Test
    fun appendingWhileCheckingNewTagsKeepsExistingCardsAndScrollAnchor() {
        assumeTrue(System.getenv("KICA_TAG_WINDOW_TEST") == "1")
        val memory = InMemoryLibraryRepository()
        runBlocking { memory.updateSettings(AppSettings(blockedTags = setOf("blocked"))) }
        val appended = AtomicBoolean(false)
        val repeatedLookups = AtomicInteger()
        val oldLookupGate = CompletableDeferred<Unit>()
        val newTags = CompletableDeferred<List<String>>()
        val library = object : LibraryRepository by memory {
            override suspend fun tagMetadata(comicId: String): TagMetadata? {
                if (appended.get() && comicId.startsWith("old-")) {
                    repeatedLookups.incrementAndGet()
                    oldLookupGate.await()
                }
                return memory.tagMetadata(comicId)
            }
        }
        val initial = (0 until 60).map { ComicSummary("old-$it", "Book $it", tags = listOf("safe")) }
        val comics = mutableStateOf(initial)
        val pages = mutableStateOf(listOf(ComicGridPage(1, initial.map { it.id })))
        val loading = mutableStateOf(false)
        val grid = AtomicReference<LazyGridState>()
        val scope = AtomicReference<CoroutineScope>()
        val requestAnchor = AtomicReference<Pair<Int, Int>>()
        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            window = ComposeWindow().apply {
                setSize(720, 600)
                setContent {
                    val currentScope = rememberCoroutineScope()
                    val state = rememberLazyGridState()
                    val resolver = remember { ComicTagResolver({ newTags.await() }, library, currentScope) }
                    SideEffect { grid.set(state); scope.set(currentScope) }
                    KicaFluentTheme {
                        CompositionLocalProvider(LocalTagLibrary provides library, LocalTagResolver provides resolver) {
                            ComicGrid(comics.value, {}, loadingMore = loading.value,
                                canLoadMore = pages.value.size == 1,
                                onLoadMore = {
                                    requestAnchor.set(state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset)
                                    loading.value = true
                                }, pages = pages.value, state = state)
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
            assertTrue(check(), "index=${grid.get()?.firstVisibleItemIndex}, loading=${loading.value}, repeated=${repeatedLookups.get()}")
        }
        fun anchor(): Pair<Int, Int> {
            val value = AtomicReference<Pair<Int, Int>>()
            SwingUtilities.invokeAndWait { value.set(grid.get().firstVisibleItemIndex to grid.get().firstVisibleItemScrollOffset) }
            return value.get()
        }
        try {
            pumpUntil { grid.get()?.layoutInfo?.totalItemsCount == 61 }
            SwingUtilities.invokeAndWait { scope.get().launch { grid.get().scrollToItem(49, 180) } }
            pumpUntil { loading.value && grid.get().firstVisibleItemIndex == 49 }
            val before = anchor()
            assertEquals(requestAnchor.get(), before)
            SwingUtilities.invokeAndWait {
                appended.set(true)
                comics.value = initial + ComicSummary("new", "New")
                pages.value = pages.value + ComicGridPage(2, listOf("new"))
                loading.value = false
            }
            pumpUntil { grid.get().layoutInfo.totalItemsCount == 62 && !loading.value }
            assertEquals(before, anchor())
            assertEquals(0, repeatedLookups.get())
            val firstVisibleKey = AtomicReference<Any>()
            SwingUtilities.invokeAndWait { firstVisibleKey.set(grid.get().layoutInfo.visibleItemsInfo.first().key) }
            assertEquals("comic:old-48", firstVisibleKey.get())
            newTags.complete(listOf("safe"))
            pumpUntil { runBlocking { memory.tagMetadata("new") }?.tags == listOf("safe") }
            repeat(5) {
                SwingUtilities.invokeAndWait { Snapshot.sendApplyNotifications(); window.repaint() }
                Thread.sleep(50)
            }
            assertEquals(before, anchor())
        } finally {
            oldLookupGate.complete(Unit)
            newTags.complete(emptyList())
            SwingUtilities.invokeAndWait { window.dispose() }
        }
    }
}
