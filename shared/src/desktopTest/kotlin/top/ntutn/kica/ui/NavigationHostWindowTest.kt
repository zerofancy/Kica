package top.ntutn.kica.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.awt.ComposeWindow
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assume.assumeTrue
import top.ntutn.kica.model.AppRoute
import top.ntutn.kica.model.UserSession
import top.ntutn.kica.ui.navigation.RootComponent
import top.ntutn.kica.ui.navigation.SectionId
import java.awt.Robot
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real EDT, ComposeWindow and Esc dispatch; headless model tests cannot replace this coverage. */
class NavigationHostWindowTest {
    @Test
    fun actualRootRendersRetainedColumnsAndEscReleasesOnlyTopPage() {
        assumeTrue(System.getenv("KICA_TAG_WINDOW_TEST") == "1")
        val fixtures = NavigationFixtures()
        fixtures.pica.session.value = UserSession("window-fixture", "fixture")
        lateinit var root: RootComponent
        lateinit var window: ComposeWindow
        val lifecycle = LifecycleRegistry()
        SwingUtilities.invokeAndWait {
            lifecycle.resume()
            root = RootComponent(DefaultComponentContext(lifecycle), fixtures.services)
            window = ComposeWindow().apply {
                setSize(1100, 760)
                setContent { KicaApp(root) }
                isVisible = true
                toFront()
                requestFocus()
            }
        }
        fun await(condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            var ready = false
            while (!ready && System.nanoTime() < deadline) {
                SwingUtilities.invokeAndWait {
                    Snapshot.sendApplyNotifications()
                    window.repaint()
                    ready = condition()
                }
                if (!ready) Thread.sleep(32)
            }
            assertTrue(ready, "Window navigation did not settle: ${fixtures.pica.pageCalls}")
        }
        try {
            await { root.main.value.child != null && fixtures.pica.pageCalls.contains("home") }
            var entry = ""
            SwingUtilities.invokeAndWait {
                val main = root.main.value.child!!.instance
                main.selectSection(SectionId.FAVORITES)
                main.navigate(AppRoute.Detail("window-fixture"))
                entry = main.active.stack.value.active.instance.entryId
            }
            await { fixtures.pica.pageCalls.contains("comic") }
            val calls = fixtures.pica.pageCalls.toList()
            SwingUtilities.invokeAndWait {
                val main = root.main.value.child!!.instance
                main.selectSection(SectionId.SETTINGS)
                main.selectSection(SectionId.DISCOVER)
                main.selectSection(SectionId.FAVORITES)
                assertEquals(entry, main.active.stack.value.active.instance.entryId)
            }
            await { root.main.value.child!!.instance.activeSection == SectionId.FAVORITES }
            SwingUtilities.invokeAndWait {
                assertEquals(calls.count { it == "comic" }, fixtures.pica.pageCalls.count { it == "comic" })
                assertEquals(1, fixtures.pica.restoreCalls)
                window.toFront()
                window.requestFocus()
            }
            await { window.isFocused }
            Robot().apply { keyPress(KeyEvent.VK_ESCAPE); keyRelease(KeyEvent.VK_ESCAPE); waitForIdle() }
            await { !root.main.value.child!!.instance.active.canPop }
            SwingUtilities.invokeAndWait {
                val main = root.main.value.child!!.instance
                assertEquals(AppRoute.Favorites, main.active.stack.value.active.instance.route)
                main.back()
                assertEquals(SectionId.HOME, main.activeSection)
                assertFalse(main.canGoBack)
            }
        } finally {
            runBlocking {
                withContext(Dispatchers.Main) {
                    root.close()
                    lifecycle.destroy()
                    window.dispose()
                }
            }
        }
    }
}
