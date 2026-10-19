package top.ntutn.kica.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.dp
import java.awt.Robot
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import top.ntutn.kica.data.InMemoryLibraryRepository
import top.ntutn.kica.data.PlatformServices
import top.ntutn.kica.ui.screen.ComicTags
import top.ntutn.kica.ui.theme.KicaFluentTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComicTagsWindowTest {
    @Test
    fun tagsCanBeSearchedAndMenusConsumeEscape() {
        assumeTrue(System.getenv("KICA_TAG_WINDOW_TEST") == "1")
        val search = AtomicReference<String>()
        val copied = AtomicReference<String>()
        val backs = AtomicInteger()
        val library = InMemoryLibraryRepository()
        val platform = Proxy.newProxyInstance(PlatformServices::class.java.classLoader,
            arrayOf(PlatformServices::class.java)) { _, method, args ->
            when (method.name) {
                "copyText" -> { copied.set(args[0] as String); true }
                "isDesktop" -> true
                "hashCode" -> 1
                "equals" -> false
                "toString" -> "Tag window fixture"
                else -> error("Unexpected call: ${method.name}")
            }
        } as PlatformServices
        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            window = ComposeWindow().apply {
                title = "Kica tag validation"
                setSize(720, 600)
                setLocation(60, 60)
                setContent {
                    KicaFluentTheme {
                        PlatformBackHandler(true) { backs.incrementAndGet() }
                        Column(Modifier.fillMaxSize().padding(20.dp)) {
                            ComicTags("comic", listOf("Alpha", "Beta") + (3..12).map { "标签 $it" },
                                library, platform, search::set)
                        }
                    }
                }
                isVisible = true
                toFront()
                requestFocus()
            }
        }
        val robot = Robot().apply { autoDelay = 80 }
        val repaint = javax.swing.Timer(30) { window.repaint() }
        SwingUtilities.invokeAndWait { repaint.start() }
        val reports = File("build/reports/comic-tags").apply { mkdirs() }
        fun snapshot(name: String) {
            robot.delay(400)
            ImageIO.write(robot.createScreenCapture(window.bounds), "png", File(reports, "$name.png"))
        }
        fun click(mouse: Int, horizontal: Int, vertical: Int) {
            robot.mouseMove(window.x + window.insets.left + horizontal, window.y + window.insets.top + vertical)
            robot.mousePress(mouse)
            robot.mouseRelease(mouse)
            robot.delay(250)
            SwingUtilities.invokeAndWait { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
            robot.delay(400)
        }
        fun escape() {
            robot.keyPress(KeyEvent.VK_ESCAPE); robot.keyRelease(KeyEvent.VK_ESCAPE)
            robot.delay(250)
            SwingUtilities.invokeAndWait { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
            robot.delay(400)
        }
        try {
            robot.delay(1800)
            snapshot("all-tags")
            click(InputEvent.BUTTON1_DOWN_MASK, 50, 75)
            assertEquals("Alpha", search.get())
            click(InputEvent.BUTTON1_DOWN_MASK, 650, 36)
            snapshot("tag-selector")
            escape()
            assertEquals(0, backs.get())
            click(InputEvent.BUTTON3_DOWN_MASK, 50, 75)
            snapshot("tag-actions")
            click(InputEvent.BUTTON1_DOWN_MASK, 150, 294)
            assertEquals("Alpha", copied.get())
            click(InputEvent.BUTTON3_DOWN_MASK, 50, 75)
            click(InputEvent.BUTTON1_DOWN_MASK, 150, 322)
            assertEquals(setOf("Alpha"), runBlocking { library.settings().first().blockedTags })
            snapshot("blocked-tag")
            click(InputEvent.BUTTON3_DOWN_MASK, 50, 75)
            escape()
            assertEquals(0, backs.get())
            robot.keyPress(KeyEvent.VK_SHIFT)
            robot.keyPress(KeyEvent.VK_F10); robot.keyRelease(KeyEvent.VK_F10)
            robot.keyRelease(KeyEvent.VK_SHIFT)
            robot.delay(300)
            SwingUtilities.invokeAndWait { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
            robot.delay(300)
            click(InputEvent.BUTTON1_DOWN_MASK, 150, 322)
            assertTrue(runBlocking { library.settings().first().blockedTags.isEmpty() })
            SwingUtilities.invokeAndWait { window.setSize(400, 600) }
            snapshot("narrow-tags")
            escape()
            assertEquals(1, backs.get())
        } finally {
            SwingUtilities.invokeAndWait { repaint.stop() }
            SwingUtilities.invokeAndWait { window.dispose() }
        }
    }
}
