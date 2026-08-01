package top.ntutn.kica.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformBackHandlerTest {
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
