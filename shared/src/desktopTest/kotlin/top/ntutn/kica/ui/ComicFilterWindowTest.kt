package top.ntutn.kica.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.awt.ComposeWindow
import io.github.composefluent.component.Text
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import top.ntutn.kica.data.ComicTagResolver
import top.ntutn.kica.data.InMemoryLibraryRepository
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.ui.theme.KicaFluentTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComicFilterWindowTest {
    @Test
    fun unknownItemsStayHiddenAndRemovingRulesRestoresOriginalItems() {
        assumeTrue(System.getenv("KICA_TAG_WINDOW_TEST") == "1")
        val library = InMemoryLibraryRepository()
        val requests = AtomicInteger()
        val result = CompletableDeferred<List<String>>()
        val latest = AtomicReference<ComicFilterState>()
        val comics = listOf(ComicSummary("one", "One", tags = listOf("Alpha")), ComicSummary("two", "Two"))
        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            window = ComposeWindow().apply {
                setSize(720, 600)
                setContent {
                    val scope = rememberCoroutineScope()
                    val resolver = remember { ComicTagResolver({ requests.incrementAndGet(); result.await() }, library, scope) }
                    KicaFluentTheme {
                        CompositionLocalProvider(LocalTagResolver provides resolver) {
                            val filter = rememberComicFilter(comics, library)
                            SideEffect { latest.set(filter) }
                            Column {
                                ComicFilterNotice(filter, library)
                                Text(filter.visible.joinToString { it.title })
                            }
                        }
                    }
                }
                isVisible = true
            }
        }
        fun awaitState(check: (ComicFilterState) -> Boolean) {
            val deadline = System.nanoTime() + 8_000_000_000L
            do {
                SwingUtilities.invokeAndWait { Snapshot.sendApplyNotifications(); window.repaint() }
                Thread.sleep(50)
            } while (latest.get()?.let(check) != true && System.nanoTime() < deadline)
            assertTrue(latest.get()?.let(check) == true, "Actual filter: ${latest.get()}")
        }
        try {
            awaitState { it.visible.size == 2 }
            assertEquals(0, requests.get())
            runBlocking { library.updateBlockedTags { setOf("alpha") } }
            awaitState { it.visible.isEmpty() && it.pending == 1 && it.hiddenIds == setOf("one") }
            assertEquals(1, requests.get())
            result.complete(listOf("Beta"))
            awaitState { it.visible.map { comic -> comic.id } == listOf("two") && it.pending == 0 }
            runBlocking { library.updateBlockedTags { emptySet() } }
            awaitState { it.visible.map { comic -> comic.id } == listOf("one", "two") }
            assertEquals(1, requests.get())
        } finally { SwingUtilities.invokeAndWait { window.dispose() } }
    }
}
