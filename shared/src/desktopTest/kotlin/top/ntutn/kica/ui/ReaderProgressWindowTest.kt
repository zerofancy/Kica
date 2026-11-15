package top.ntutn.kica.ui

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import java.awt.Color
import java.awt.Robot
import java.awt.event.KeyEvent
import java.awt.event.InputEvent
import java.awt.image.BufferedImage
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import org.junit.Assume.assumeTrue
import top.ntutn.kica.data.InMemoryLibraryRepository
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.data.PicaRepository
import top.ntutn.kica.data.PlatformServices
import top.ntutn.kica.model.AppRoute
import top.ntutn.kica.model.ComicDetail
import top.ntutn.kica.model.Episode
import top.ntutn.kica.model.PageRef
import top.ntutn.kica.model.ReaderMode
import top.ntutn.kica.model.ReadingProgress
import top.ntutn.kica.ui.screen.ReaderScreen
import top.ntutn.kica.ui.screen.LocalReaderSessions
import top.ntutn.kica.ui.screen.ReaderSessionMemory
import top.ntutn.kica.ui.screen.ReaderEntryState
import top.ntutn.kica.ui.theme.KicaFluentTheme

/** Real desktop modal/focus/save verification. Opt in with KICA_READER_WINDOW_TEST=1. */
class ReaderProgressWindowTest {
    @Test
    fun choicesGateWritesRetainModesAndDoNotInterruptChapterChanges() {
        assumeTrue(System.getenv("KICA_READER_WINDOW_TEST") == "1")
        for (mode in ReaderMode.entries) {
            for (startOver in listOf(false, true)) {
                Fixture(mode, page = 3).use { fixture ->
                    fixture.awaitDialog()
                    fixture.assertNoWrites()
                    fixture.key(KeyEvent.VK_END)
                    fixture.assertNoWrites()
                    if (startOver) fixture.key(KeyEvent.VK_TAB, shift = true)
                    fixture.key(KeyEvent.VK_ENTER)
                    val expected = if (startOver) 0 else if (mode.name.startsWith("DOUBLE")) 2 else 3
                    fixture.awaitPage("e1", expected)
                    assertEquals(mode, fixture.latest.get().mode)
                    fixture.key(KeyEvent.VK_PAGE_DOWN, ctrl = true)
                    fixture.awaitPage("e2", 0)
                    fixture.key(KeyEvent.VK_PAGE_UP, ctrl = true)
                    fixture.awaitPage("e1", 4)
                    assertEquals(1, fixture.readRequests.get(), "Internal chapter changes must not read historical positions again")
                }
            }
        }
    }

    @Test
    fun firstPageSkipsDialogAndEscapeCancelsLaterProgressWithoutWrites() {
        assumeTrue(System.getenv("KICA_READER_WINDOW_TEST") == "1")
        Fixture(ReaderMode.PAGED_RIGHT_TO_LEFT, page = 0).use { fixture ->
            fixture.awaitPage("e1", 0)
            assertEquals(1, fixture.readRequests.get())
            assertEquals(ReaderMode.PAGED_RIGHT_TO_LEFT, fixture.latest.get().mode)
        }
        for (action in listOf("escape", "button", "outside")) {
            Fixture(ReaderMode.DOUBLE_RIGHT_TO_LEFT, page = 1).use { fixture ->
                fixture.awaitDialog()
                when (action) {
                    "escape" -> fixture.key(KeyEvent.VK_ESCAPE)
                    "button" -> {
                        repeat(2) { fixture.key(KeyEvent.VK_TAB, shift = true) }
                        fixture.key(KeyEvent.VK_ENTER)
                    }
                    else -> {
                        fixture.robot.mouseMove(fixture.window.x + 50, fixture.window.y + 100)
                        fixture.robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                        fixture.robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                    }
                }
                await("one cancellation via $action") { fixture.back.get() == 1 }
                fixture.assertNoWrites()
                assertEquals(1, runBlocking { fixture.memory.readingProgress("comic", "e1")?.pageIndex })
                assertEquals(1, fixture.back.get())
            }
        }
    }

    @Test
    fun failedOrEmptyContentDoesNotResetProgressAfterStartOver() {
        assumeTrue(System.getenv("KICA_READER_WINDOW_TEST") == "1")
        for (fail in listOf(false, true)) {
            Fixture(ReaderMode.VERTICAL, page = 3, empty = !fail, fail = fail).use { fixture ->
                fixture.awaitDialog()
                fixture.key(KeyEvent.VK_TAB, shift = true)
                fixture.key(KeyEvent.VK_ENTER)
                await("content request") { fixture.pageRequests.get() > 0 }
                fixture.robot.delay(400)
                fixture.assertNoWrites()
                assertEquals(3, runBlocking { fixture.memory.readingProgress("comic", "e1")?.pageIndex })
            }
        }
    }

    @Test
    fun temporaryLockUnmountKeepsDecisionAndCurrentChapter() {
        assumeTrue(System.getenv("KICA_READER_WINDOW_TEST") == "1")
        Fixture(ReaderMode.PAGED_LEFT_TO_RIGHT, page = 3).use { fixture ->
            fixture.awaitDialog()
            fixture.key(KeyEvent.VK_ENTER)
            fixture.awaitPage("e1", 3)
            fixture.key(KeyEvent.VK_PAGE_DOWN, ctrl = true)
            fixture.awaitPage("e2", 0)
            onEdt { fixture.mounted.value = false }
            fixture.robot.delay(200)
            fixture.latest.set(null)
            onEdt { fixture.mounted.value = true }
            fixture.awaitPage("e2", 0)
            assertEquals(1, fixture.readRequests.get(), "Unlock must not re-read or re-prompt")
        }
    }

    @Test
    fun lockWhileChangingChapterRetainsPendingTargetWithoutWritingIt() {
        assumeTrue(System.getenv("KICA_READER_WINDOW_TEST") == "1")
        Fixture(ReaderMode.PAGED_LEFT_TO_RIGHT, page = 0).use { fixture ->
            fixture.awaitPage("e1", 0)
            fixture.blockedEpisode.set("e2")
            fixture.key(KeyEvent.VK_PAGE_DOWN, ctrl = true)
            await("target chapter loading") { fixture.blockedRequests.get() == 1 }
            assertEquals(3, runBlocking { fixture.memory.readingProgress("comic", "e2")?.pageIndex })
            onEdt { fixture.mounted.value = false }
            fixture.robot.delay(200)
            fixture.latest.set(null)
            onEdt { fixture.mounted.value = true }
            await("unlock resumes pending target") { fixture.blockedRequests.get() == 2 }
            assertEquals(1, fixture.readRequests.get())
            fixture.chapterGate.complete(Unit)
            fixture.awaitPage("e2", 0)
        }
    }

    @Test
    fun smallWindowLongTitleKeepsStartOverReachable() {
        assumeTrue(System.getenv("KICA_READER_WINDOW_TEST") == "1")
        Fixture(ReaderMode.PAGED_RIGHT_TO_LEFT, page = 3, narrow = true).use { fixture ->
            fixture.awaitDialog()
            fixture.assertNoWrites()
            fixture.key(KeyEvent.VK_TAB, shift = true)
            fixture.key(KeyEvent.VK_ENTER)
            fixture.awaitPage("e1", 0)
        }
    }

    private class Fixture(
        private val mode: ReaderMode,
        page: Int,
        empty: Boolean = false,
        fail: Boolean = false,
        narrow: Boolean = false,
    ) : AutoCloseable {
        val robot = Robot().apply { autoDelay = 35 }
        val latest = AtomicReference<ReadingProgress>()
        val back = AtomicInteger()
        val pageRequests = AtomicInteger()
        val readRequests = AtomicInteger()
        val mounted = mutableStateOf(true)
        val blockedEpisode = AtomicReference<String?>()
        val blockedRequests = AtomicInteger()
        val chapterGate = CompletableDeferred<Unit>()
        private val sessions = mutableMapOf<AppRoute.Reader, ReaderSessionMemory>()
        private val saves = AtomicInteger()
        val memory = InMemoryLibraryRepository()
        val window: ComposeWindow
        private val reports = File("build/reports/reader-progress").apply { mkdirs() }

        init {
            val imageFile = File(reports, "fixture.png")
            val image = BufferedImage(600, 1200, BufferedImage.TYPE_INT_RGB)
            image.createGraphics().apply { color = Color(0x315B8A); fillRect(0, 0, 600, 1200); dispose() }
            ImageIO.write(image, "png", imageFile)
            runBlocking {
                memory.saveProgress(ReadingProgress("comic", "e1", page, mode, 0))
                memory.saveProgress(ReadingProgress("comic", "e2", 3, mode, 0))
            }
            val library = object : LibraryRepository by memory {
                override suspend fun readingProgress(comicId: String, episodeId: String): ReadingProgress? {
                    readRequests.incrementAndGet()
                    return memory.readingProgress(comicId, episodeId)
                }
                override suspend fun saveProgress(progress: ReadingProgress) {
                    saves.incrementAndGet()
                    memory.saveProgress(progress)
                    latest.set(progress)
                }
            }
            val baseRepository = proxy<PicaRepository> { name -> when (name) {
                "comic" -> ComicDetail("comic", "Progress fixture")
                "episodes" -> (1..2).map { Episode("e$it", "comic", it, if (narrow) "很长的章节名称".repeat(25) else "Chapter $it", 5) }
                "pages" -> {
                    pageRequests.incrementAndGet()
                    if (fail) error("Synthetic load failure")
                    if (empty) emptyList<PageRef>() else (0..4).map { PageRef(it, "", "Page $it", imageFile.absolutePath) }
                }
                else -> error("Unexpected repository call: $name")
            } }
            val repository = object : PicaRepository by baseRepository {
                override suspend fun pages(comicId: String, episodeId: String): List<PageRef> {
                    if (episodeId == blockedEpisode.get()) {
                        blockedRequests.incrementAndGet()
                        chapterGate.await()
                    }
                    return baseRepository.pages(comicId, episodeId)
                }
            }
            val platform = proxy<PlatformServices> { name -> when (name) {
                "isDesktop" -> true
                "getPlatformName" -> "Progress fixture"
                "getSaepAvailable" -> false
                else -> error("Unexpected platform call: $name")
            } }
            window = onEdt {
                ComposeWindow().apply {
                    title = "Kica progress validation — $mode"
                    if (narrow) setSize(420, 300) else setSize(1000, 760)
                    setLocation(70, 70)
                    setContent {
                        KicaFluentTheme {
                            PlatformBackHandler(true) { back.incrementAndGet() }
                            CompositionLocalProvider(LocalReaderSessions provides sessions) {
                                if (mounted.value) ReaderScreen("comic", "e1", repository, library, platform) { back.incrementAndGet() }
                            }
                        }
                    }
                    isVisible = true
                    toFront()
                    requestFocus()
                }
            }
        }

        fun awaitDialog() {
            await("progress read and window focus") {
                readRequests.get() > 0 && onEdt {
                    sessions.values.singleOrNull()?.entry?.state is ReaderEntryState.Choice &&
                        (window.isFocused || window.ownedWindows.any { it.isShowing && it.isFocused })
                }
            }
            // Compose can host modal dialogs in an in-window layer, not an owned AWT window.
            robot.delay(500)
            ImageIO.write(robot.createScreenCapture(onEdt { window.bounds }), "png", File(reports, "choice.png"))
        }

        fun assertNoWrites() {
            assertEquals(0, saves.get(), "Unresolved/cancelled/failed entry must not save progress")
            assertTrue(runBlocking { memory.history().first().isEmpty() })
        }

        fun key(code: Int, shift: Boolean = false, ctrl: Boolean = false) {
            await("native window focus before key $code") {
                onEdt { window.isFocused || window.ownedWindows.any { it.isShowing && it.isFocused } }
            }
            if (shift) robot.keyPress(KeyEvent.VK_SHIFT)
            if (ctrl) robot.keyPress(KeyEvent.VK_CONTROL)
            robot.keyPress(code)
            robot.keyRelease(code)
            if (ctrl) robot.keyRelease(KeyEvent.VK_CONTROL)
            if (shift) robot.keyRelease(KeyEvent.VK_SHIFT)
            robot.delay(250)
        }

        fun awaitPage(episode: String, page: Int) {
            try {
                await("$mode expected $episode/$page, actual ${latest.get()}") {
                    latest.get()?.let { it.episodeId == episode && it.pageIndex == page } == true
                }
            } catch (error: Throwable) {
                ImageIO.write(robot.createScreenCapture(onEdt { window.bounds }), "png", File(reports, "failure.png"))
                throw error
            }
        }

        override fun close() {
            onEdt {
                window.ownedWindows.forEach { it.dispose() }
                window.dispose()
            }
        }
    }

    companion object {
        private inline fun <reified T> proxy(crossinline call: (String) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
                when (method.name) {
                    "toString" -> "Progress fixture"
                    "hashCode" -> 1
                    "equals" -> false
                    else -> call(method.name)
                }
            } as T

        private fun await(message: String, check: () -> Boolean) {
            val deadline = System.nanoTime() + 8_000_000_000L
            while (!check() && System.nanoTime() < deadline) Thread.sleep(50)
            assertTrue(check(), message)
        }

        private fun <T> onEdt(block: () -> T): T {
            if (SwingUtilities.isEventDispatchThread()) return block()
            val result = AtomicReference<Result<T>>()
            SwingUtilities.invokeAndWait { result.set(runCatching(block)) }
            return result.get().getOrThrow()
        }
    }
}
