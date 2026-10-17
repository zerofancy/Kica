package top.ntutn.kica.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformBackHandlerTest {
    @Test
    fun heldEscapeCannotCloseOverlayAndPopPageTogether() {
        val press = DesktopEscapePress()
        var calls = 0
        repeat(10) { assertTrue(press.press { calls++; true }) }
        assertEquals(1, calls)
        press.release()
        assertTrue(press.press { calls++; true })
        assertEquals(2, calls)
    }

    @Test
    fun inactiveWindowDoesNotHandleEscapeFromAnotherWindow() {
        val registry = DesktopBackHandlerRegistry()
        val inactive = DesktopBackHandlerEntry(true) { error("Inactive window handled Escape") }
        inactive.windowFocused = { false }
        registry.register(inactive)
        assertFalse(registry.handleBack())
    }

    @Test
    fun mostRecentlyRegisteredEnabledHandlerRunsFirst() {
        val calls = mutableListOf<String>()
        val registry = DesktopBackHandlerRegistry()
        val page = DesktopBackHandlerEntry(enabled = true) { calls += "page" }
        val overlay = DesktopBackHandlerEntry(enabled = false) { calls += "overlay" }
        registry.register(page)
        registry.register(overlay)

        assertTrue(registry.handleBack())
        assertEquals(listOf("page"), calls)

        overlay.enabled = true
        assertTrue(registry.handleBack())
        assertEquals(listOf("page", "overlay"), calls)
    }

    @Test
    fun noEnabledHandlerLeavesEscapeUnconsumed() {
        val registry = DesktopBackHandlerRegistry()
        registry.register(DesktopBackHandlerEntry(enabled = false) {})

        assertFalse(registry.handleBack())
    }
}
