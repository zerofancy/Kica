package top.ntutn.kica.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowLayoutTest {
    @Test
    fun representativeAcceptanceWidthsUseExpectedNavigation() {
        assertEquals(WindowLayout.PHONE, classifyWindow(360))
        assertEquals(WindowLayout.TABLET, classifyWindow(800))
        assertEquals(WindowLayout.DESKTOP, classifyWindow(880))
        assertEquals(WindowLayout.DESKTOP, classifyWindow(1440))
    }

    @Test
    fun breakpointEdgesAreStable() {
        assertEquals(WindowLayout.PHONE, classifyWindow(599))
        assertEquals(WindowLayout.TABLET, classifyWindow(600))
        assertEquals(WindowLayout.TABLET, classifyWindow(839))
        assertEquals(WindowLayout.DESKTOP, classifyWindow(840))
    }
}
