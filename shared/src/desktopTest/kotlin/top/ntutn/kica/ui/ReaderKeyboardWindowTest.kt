package top.ntutn.kica.ui

import androidx.compose.ui.awt.ComposeWindow
import java.awt.Color
import java.awt.KeyboardFocusManager
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
import org.junit.Assume.assumeTrue
import top.ntutn.kica.data.*
import top.ntutn.kica.model.*
import top.ntutn.kica.ui.screen.ReaderScreen
import top.ntutn.kica.ui.theme.KicaFluentTheme

/** Opt in with KICA_READER_WINDOW_TEST=1 on a desktop. Uses synthetic local images and no account. */
class ReaderKeyboardWindowTest {
    @Test
    fun realWindowKeyboardNavigationAndModalPriority() {
        assumeTrue("Requires an active desktop and explicit opt-in", System.getenv("KICA_READER_WINDOW_TEST") == "1")
        val robot = Robot().apply { autoDelay = 35 }
        val reports = File("build/reports/reader-keyboard").apply { mkdirs() }
        val images = (0..4).map { index ->
            File(reports, "page-$index.png").apply {
                val image = BufferedImage(600, 1200, BufferedImage.TYPE_INT_RGB)
                image.createGraphics().apply {
                    color = listOf(Color(0x315B8A), Color(0x774D93), Color(0x397B68), Color(0xA56237), Color(0x8B4050))[index]
                    fillRect(0, 0, 600, 1200)
                    color = Color.WHITE
                    font = font.deriveFont(60f)
                    drawString("PAGE ${index + 1}", 120, 180)
                    drawString("BOTTOM", 120, 1130)
                    dispose()
                }
                ImageIO.write(image, "png", this)
            }.absolutePath
        }
        for (mode in ReaderMode.entries) {
            val latest = AtomicReference<ReadingProgress>()
            val back = AtomicInteger()
            val saves = AtomicInteger()
            val memory = InMemoryLibraryRepository()
            runBlocking { memory.saveProgress(ReadingProgress("comic", "e1", 0, mode, 0)) }
            val library = object : LibraryRepository by memory {
                override suspend fun saveProgress(progress: ReadingProgress) {
                    memory.saveProgress(progress)
                    latest.set(progress)
                }
            }
            val repository = proxy<PicaRepository> { name, _ -> when (name) {
                "comic" -> ComicDetail("comic", "Keyboard fixture")
                "episodes" -> (1..3).map { Episode("e$it", "comic", it, "Chapter $it", 5) }
                "pages" -> images.mapIndexed { index, path -> PageRef(index, "", "Page ${index + 1}", path) }
                else -> error("Unexpected repository call: $name")
            } }
            val platform = proxy<PlatformServices> { name, _ -> when (name) {
                "isDesktop" -> true
                "getPlatformName" -> "Keyboard fixture"
                "getSaepAvailable" -> false
                "copyImage" -> true
                "saveImage" -> { saves.incrementAndGet(); true }
                else -> error("Unexpected platform call: $name")
            } }
            lateinit var window: ComposeWindow
            onEdt {
                window = ComposeWindow().apply {
                    title = "Kica keyboard validation — $mode"
                    setSize(1200, 800)
                    setLocation(50, 50)
                    setContent {
                        KicaFluentTheme {
                            PlatformBackHandler(true) { back.incrementAndGet() }
                            ReaderScreen("comic", "e1", repository, library, platform) { back.incrementAndGet() }
                        }
                    }
                    isVisible = true
                    toFront()
                    requestFocus()
                }
            }
            fun awaitPage(episode: String, page: Int) = await("$mode expected $episode/$page, actual ${latest.get()}") {
                latest.get()?.let { it.episodeId == episode && it.pageIndex == page && it.mode == mode } == true
            }
            fun key(code: Int, ctrl: Boolean = false, shift: Boolean = false) {
                if (shift) robot.keyPress(KeyEvent.VK_SHIFT)
                if (ctrl) robot.keyPress(KeyEvent.VK_CONTROL)
                robot.keyPress(code); robot.keyRelease(code)
                if (ctrl) robot.keyRelease(KeyEvent.VK_CONTROL)
                if (shift) robot.keyRelease(KeyEvent.VK_SHIFT)
                robot.delay(600)
            }
            val lastPage = 4
            try {
                await("window focus") { onEdt { window.isFocused } }
                awaitPage("e1", 0)
                robot.delay(250)
                if (mode != ReaderMode.VERTICAL) {
                    val step = if (mode == ReaderMode.DOUBLE_LEFT_TO_RIGHT || mode == ReaderMode.DOUBLE_RIGHT_TO_LEFT) 2 else 1
                    // Repeated KEY_PRESSED events while physically held must remain one move.
                    repeat(5) { robot.keyPress(KeyEvent.VK_PAGE_DOWN); robot.delay(100) }
                    awaitPage("e1", step)
                    robot.delay(300)
                    assertEquals(step, latest.get().pageIndex)
                    robot.keyRelease(KeyEvent.VK_PAGE_DOWN)
                    key(KeyEvent.VK_HOME)
                    awaitPage("e1", 0)
                    val forward = if (mode == ReaderMode.PAGED_RIGHT_TO_LEFT || mode == ReaderMode.DOUBLE_RIGHT_TO_LEFT) KeyEvent.VK_LEFT else KeyEvent.VK_RIGHT
                    key(forward)
                    awaitPage("e1", step)
                }
                key(KeyEvent.VK_END)
                awaitPage("e1", lastPage)
                ImageIO.write(robot.createScreenCapture(onEdt { window.bounds }), "png", File(reports, "$mode.png"))
                if (mode == ReaderMode.VERTICAL) {
                    robot.keyPress(KeyEvent.VK_DOWN)
                    awaitPage("e2", 0)
                    repeat(50) { robot.keyPress(KeyEvent.VK_DOWN) }
                    robot.delay(200)
                    assertEquals(0, latest.get().pageIndex, "Held small-scroll key must stay blocked after changing chapter")
                    robot.keyRelease(KeyEvent.VK_DOWN)
                } else key(KeyEvent.VK_PAGE_DOWN)
                awaitPage("e2", 0)
                key(KeyEvent.VK_PAGE_UP, ctrl = true)
                awaitPage("e1", lastPage)
                key(KeyEvent.VK_HOME)
                awaitPage("e1", 0)

                val bodyPixel = robot.getPixelColor(window.x + window.width / 2, window.y + window.height / 2)
                key(KeyEvent.VK_F1)
                ImageIO.write(robot.createScreenCapture(onEdt { window.bounds }), "png", File(reports, "$mode-help.png"))
                await("help opened") { robot.getPixelColor(window.x + window.width / 2, window.y + window.height / 2) != bodyPixel }
                key(KeyEvent.VK_PAGE_DOWN)
                assertEquals(0, latest.get().pageIndex, "Help must block reading navigation")
                key(KeyEvent.VK_ESCAPE)
                await("help closed") { robot.getPixelColor(window.x + window.width / 2, window.y + window.height / 2) == bodyPixel }
                assertEquals(0, back.get())
                if (mode == ReaderMode.VERTICAL) {
                    val toolbarPixel = robot.getPixelColor(window.x + 140, window.y + 80)
                    key(KeyEvent.VK_TAB, shift = true)
                    key(KeyEvent.VK_SPACE)
                    assertTrue(robot.getPixelColor(window.x + 140, window.y + 80) != toolbarPixel, "Space must activate focused hide-toolbar button")
                    assertEquals(0, latest.get().pageIndex)
                    key(KeyEvent.VK_ESCAPE)
                    assertEquals(toolbarPixel, robot.getPixelColor(window.x + 140, window.y + 80))
                    key(KeyEvent.VK_TAB, shift = true)
                    key(KeyEvent.VK_TAB, shift = true)
                    key(KeyEvent.VK_F1)
                    key(KeyEvent.VK_ESCAPE)
                    key(KeyEvent.VK_ENTER)
                    await("toolbar help button focus restored") { robot.getPixelColor(window.x + window.width / 2, window.y + window.height / 2) != bodyPixel }
                    key(KeyEvent.VK_ESCAPE)
                    // Right click restores body focus before opening the keyboard-accessible menu.
                    robot.mouseMove(window.x + 300, window.y + 250)
                    robot.mousePress(InputEvent.BUTTON3_DOWN_MASK)
                    robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK)
                    robot.delay(300)
                    key(KeyEvent.VK_END)
                    assertEquals(0, latest.get().pageIndex, "Menu must block reading commands")
                    key(KeyEvent.VK_DOWN)
                    key(KeyEvent.VK_ENTER)
                    await("menu save activated by keyboard") { saves.get() == 1 }
                    key(KeyEvent.VK_END)
                    awaitPage("e1", lastPage)
                    key(KeyEvent.VK_HOME)
                    awaitPage("e1", 0)
                    onEdt { window.setSize(600, 400) }
                    robot.delay(400)
                    key(KeyEvent.VK_F1)
                    ImageIO.write(robot.createScreenCapture(onEdt { window.bounds }), "png", File(reports, "narrow-help.png"))
                    key(KeyEvent.VK_ESCAPE)
                    // Tab must bring the offscreen toolbar buttons into the visible viewport.
                    key(KeyEvent.VK_TAB, shift = true)
                    ImageIO.write(robot.createScreenCapture(onEdt { window.bounds }), "png", File(reports, "narrow-toolbar-focus.png"))
                    onEdt { window.setSize(1200, 800) }
                    robot.mouseMove(window.x + 300, window.y + 250)
                    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                    key(KeyEvent.VK_HOME)
                    awaitPage("e1", 0)
                }
                if (mode == ReaderMode.VERTICAL) {
                    key(KeyEvent.VK_F)
                    key(KeyEvent.VK_TAB)
                    key(KeyEvent.VK_SPACE)
                    key(KeyEvent.VK_END)
                    awaitPage("e1", lastPage)
                    key(KeyEvent.VK_HOME)
                    awaitPage("e1", 0)
                }
                key(KeyEvent.VK_F)
                if (mode == ReaderMode.VERTICAL) {
                    val hiddenPixel = robot.getPixelColor(window.x + window.width / 2, window.y + window.height / 2)
                    key(KeyEvent.VK_F1)
                    await("help opened with toolbar hidden") { robot.getPixelColor(window.x + window.width / 2, window.y + window.height / 2) != hiddenPixel }
                    key(KeyEvent.VK_ESCAPE)
                    await("help closed with toolbar still hidden") { robot.getPixelColor(window.x + window.width / 2, window.y + window.height / 2) == hiddenPixel }
                }
                // One held Escape restores the toolbar; repeated presses must not return the page.
                repeat(5) { robot.keyPress(KeyEvent.VK_ESCAPE); robot.delay(80) }
                assertEquals(0, back.get())
                robot.keyRelease(KeyEvent.VK_ESCAPE)
                key(KeyEvent.VK_ESCAPE)
                await("page returned") { back.get() == 1 }
                assertTrue(onEdt { KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow === window })
            } catch (error: Throwable) {
                ImageIO.write(robot.createScreenCapture(onEdt { window.bounds }), "png", File(reports, "$mode-failure.png"))
                throw error
            } finally {
                robot.keyRelease(KeyEvent.VK_PAGE_DOWN)
                robot.keyRelease(KeyEvent.VK_DOWN)
                robot.keyRelease(KeyEvent.VK_ESCAPE)
                onEdt { window.dispose() }
            }
        }
    }

    private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            when (method.name) {
                "toString" -> "Reader keyboard fixture"
                "hashCode" -> 1
                "equals" -> false
                else -> call(method.name, args ?: emptyArray())
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
