package top.ntutn.kica.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.ntutn.kica.ui.component.shouldRestoreAnchor
import top.ntutn.kica.ui.component.shouldRestoreInitialPosition
import top.ntutn.kica.ui.component.anchorIndex

class ScrollAnchorTest {
    @Test
    fun missingSnapshotNeverOverridesAnExistingOrProgrammaticScroll() {
        assertFalse(shouldRestoreAnchor(null, 16 to 0, 16 to 0, false))
        assertFalse(shouldRestoreAnchor(null, 0 to 0, 49 to 180, false))
        assertFalse(shouldRestoreAnchor("saved", 0 to 0, 49 to 180, false))
        assertFalse(shouldRestoreAnchor("saved", 16 to 0, 16 to 0, true))
        assertTrue(shouldRestoreAnchor("saved", 16 to 0, 16 to 0, false))
        assertTrue(shouldRestoreAnchor("deep-key", 150 to 0, 0 to 0, false))
    }

    @Test
    fun temporaryPlaceholdersRestoreTheActualInitialIndexNotADefaultZero() {
        assertTrue(shouldRestoreInitialPosition(null, 16 to 20, 0 to 0, false))
        assertFalse(shouldRestoreInitialPosition(null, 0 to 0, 0 to 0, false))
        assertFalse(shouldRestoreInitialPosition(null, 16 to 20, 49 to 180, false))
        assertFalse(shouldRestoreInitialPosition(null, 16 to 20, 0 to 0, true))
        assertFalse(shouldRestoreInitialPosition("business-key", 16 to 20, 0 to 0, false))
    }

    @Test
    fun businessKeyWinsWhenInsertedItemsMoveTheSavedIndex() {
        assertEquals(2, anchorIndex("target", 1, listOf("new", "before", "target", "after")))
    }

    @Test
    fun removedOrFilteredItemFallsBackToNearestLegalIndex() {
        assertEquals(1, anchorIndex("removed", 9, listOf("a", "b")))
        assertEquals(0, anchorIndex("removed", -1, listOf("a", "b")))
        assertEquals(0, anchorIndex("removed", 9, emptyList()))
    }
}
