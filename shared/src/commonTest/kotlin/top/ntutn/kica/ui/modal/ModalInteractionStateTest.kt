package top.ntutn.kica.ui.modal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModalInteractionStateTest {
    @Test
    fun nestedAndSiblingLeasesDisposeIndependentlyInAnyOrder() {
        val state = ModalInteractionState()
        assertFalse(state.hasActiveInteractions)
        val parent = state.register()
        val child = state.register()
        val sibling = state.register()
        assertEquals(3, state.activeCount)
        parent.dispose()
        child.dispose()
        child.dispose() // Disposal after host teardown must be harmless.
        assertEquals(1, state.activeCount)
        assertTrue(state.hasActiveInteractions)
        sibling.dispose()
        sibling.dispose()
        assertEquals(0, state.activeCount)
        assertFalse(state.hasActiveInteractions)
    }

    @Test
    fun ownerIgnoresOnlyItsOwnWindowsNotOtherFeaturesOrUnownedModals() {
        val state = ModalInteractionState()
        val feature = ModalOwner()
        val other = ModalOwner()
        val ownDialog = state.register(feature)
        val ownPopup = state.register(feature)
        assertTrue(state.hasActiveInteractions)
        assertFalse(state.hasOtherInteractions(feature), "A feature must not block itself")
        assertTrue(state.hasOtherInteractions(other))
        val unrelated = state.register(other)
        assertTrue(state.hasOtherInteractions(feature))
        unrelated.dispose()
        assertFalse(state.hasOtherInteractions(feature))
        val business = state.register()
        assertTrue(state.hasOtherInteractions(feature))
        business.dispose()
        ownPopup.dispose()
        ownDialog.dispose()
        assertFalse(state.hasOtherInteractions(feature))
    }

    @Test
    fun disposalFromAnOldRegistryCannotReleaseNewRegistryEntries() {
        val old = ModalInteractionState()
        val replacement = ModalInteractionState()
        val owner = ModalOwner()
        val oldLease = old.register(owner)
        val newLease = replacement.register(owner)
        oldLease.dispose()
        assertEquals(0, old.activeCount)
        assertEquals(1, replacement.activeCount)
        newLease.dispose()
        assertEquals(0, replacement.activeCount)
    }
}
