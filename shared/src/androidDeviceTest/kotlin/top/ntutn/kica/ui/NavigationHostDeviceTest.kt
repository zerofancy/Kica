package top.ntutn.kica.ui

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.InputDevice
import android.provider.Settings
import org.junit.Assume.assumeTrue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.arkivanov.decompose.defaultComponentContext
import com.arkivanov.essenty.instancekeeper.getOrCreate
import org.junit.Test
import top.ntutn.kica.model.AppRoute
import top.ntutn.kica.model.UserSession
import top.ntutn.kica.ui.navigation.AppServices
import top.ntutn.kica.ui.navigation.RootComponent
import top.ntutn.kica.ui.navigation.SectionId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Library test APK only: no installation, login or data changes to the user's Kica application. */
class NavigationHarnessActivity : ComponentActivity() {
    lateinit var root: RootComponent
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = RootComponent(defaultComponentContext(), servicesFactory())
        setContent { KicaApp(root) }
    }
    companion object {
        lateinit var servicesFactory: () -> AppServices
    }
}

class NavigationHostDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun realActivityRecreationRetainsPageStoreAndIndependentColumnStacks() {
        val fixtures = NavigationFixtures()
        fixtures.pica.session.value = UserSession("instrumentation-fixture", "fixture")
        NavigationHarnessActivity.servicesFactory = { fixtures.services }
        ActivityScenario.launch(NavigationHarnessActivity::class.java).use { scenario ->
            await(scenario) { it.root.main.value.child != null && !it.root.state.value.restoring }
            lateinit var probe: NavigationProbe
            var originalEntry = ""
            scenario.onActivity { activity ->
                val main = activity.root.main.value.child!!.instance
                main.selectSection(SectionId.FAVORITES)
                main.navigate(AppRoute.Detail("device-fixture"))
                val page = main.active.stack.value.active.instance
                originalEntry = page.entryId
                probe = page.instanceKeeper.getOrCreate("device-probe") { NavigationProbe() }
                main.selectSection(SectionId.DISCOVER)
                main.selectSection(SectionId.FAVORITES)
                assertEquals(originalEntry, main.active.stack.value.active.instance.entryId)
            }
            await(scenario) { fixtures.pica.pageCalls.count { it == "comic" } == 1 }
            val calls = fixtures.pica.pageCalls.toList()
            scenario.recreate()
            await(scenario) { it.root.main.value.child != null && !it.root.state.value.restoring }
            scenario.onActivity { activity ->
                val main = activity.root.main.value.child!!.instance
                assertEquals(SectionId.FAVORITES, main.activeSection)
                val page = main.active.stack.value.active.instance
                assertEquals(originalEntry, page.entryId)
                assertSame(probe, page.instanceKeeper.getOrCreate("device-probe") { NavigationProbe() })
                assertEquals(0, probe.destroyed)
                assertEquals(calls, fixtures.pica.pageCalls)
                assertEquals(1, fixtures.pica.restoreCalls)
            }
        }
        instrumentation.waitForIdleSync()
    }

    @Test
    fun actualSystemBackPopsOneEntryAndReopeningCreatesANewInstance() {
        val fixtures = NavigationFixtures()
        fixtures.pica.session.value = UserSession("instrumentation-fixture", "fixture")
        NavigationHarnessActivity.servicesFactory = { fixtures.services }
        ActivityScenario.launch(NavigationHarnessActivity::class.java).use { scenario ->
            await(scenario) { it.root.main.value.child != null && !it.root.state.value.restoring }
            lateinit var probe: NavigationProbe
            var originalEntry = ""
            scenario.onActivity {
                val main = it.root.main.value.child!!.instance
                main.navigate(AppRoute.Detail("device-fixture"))
                val page = main.active.stack.value.active.instance
                originalEntry = page.entryId
                probe = page.instanceKeeper.getOrCreate("device-probe") { NavigationProbe() }
            }
            await(scenario) {
                fixtures.pica.pageCalls.count { call -> call == "comic" } == 1 &&
                    it.onBackPressedDispatcher.hasEnabledCallbacks()
            }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            await(scenario) { !it.root.main.value.child!!.instance.active.canPop }
            scenario.onActivity {
                val main = it.root.main.value.child!!.instance
                assertEquals(1, probe.destroyed)
                assertEquals(AppRoute.Home, main.active.stack.value.active.instance.route)
                assertFalse(main.canGoBack)
                main.navigate(AppRoute.Detail("device-fixture"))
                assertNotEquals(originalEntry, main.active.stack.value.active.instance.entryId)
            }
        }
        instrumentation.waitForIdleSync()
    }

    @Test
    fun actualEdgeBackGesturePopsOnlyOnePageWhenGestureNavigationIsEnabled() {
        assumeTrue("Device is not using gesture navigation",
            Settings.Secure.getInt(instrumentation.targetContext.contentResolver, "navigation_mode", -1) == 2)
        val fixtures = NavigationFixtures()
        fixtures.pica.session.value = UserSession("gesture-fixture", "fixture")
        NavigationHarnessActivity.servicesFactory = { fixtures.services }
        ActivityScenario.launch(NavigationHarnessActivity::class.java).use { scenario ->
            await(scenario) { it.root.main.value.child != null && !it.root.state.value.restoring }
            var width = 0
            var height = 0
            scenario.onActivity {
                val main = it.root.main.value.child!!.instance
                main.navigate(AppRoute.Detail("gesture-fixture"))
                width = it.window.decorView.width
                height = it.window.decorView.height
            }
            await(scenario) { it.onBackPressedDispatcher.hasEnabledCallbacks() }
            val down = SystemClock.uptimeMillis()
            fun inject(action: Int, x: Float) {
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, height * 0.55f, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) }
                finally { event.recycle() }
            }
            inject(MotionEvent.ACTION_DOWN, 1f)
            for (step in 1..16) {
                SystemClock.sleep(16)
                inject(MotionEvent.ACTION_MOVE, width * 0.45f * step / 16f)
            }
            inject(MotionEvent.ACTION_UP, width * 0.45f)
            await(scenario) { !it.root.main.value.child!!.instance.active.canPop }
            scenario.onActivity {
                assertEquals(AppRoute.Home, it.root.main.value.child!!.instance.active.stack.value.active.instance.route)
            }
        }
    }

    private fun await(scenario: ActivityScenario<NavigationHarnessActivity>, condition: (NavigationHarnessActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        var satisfied = false
        while (!satisfied && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { satisfied = condition(it) }
            if (!satisfied) SystemClock.sleep(32)
        }
        assertTrue(satisfied, "Activity navigation did not settle within 10 seconds")
    }
}
