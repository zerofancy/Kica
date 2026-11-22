package top.ntutn.kica.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.ntutn.kica.ui.modal.LocalModalInteractionState
import top.ntutn.kica.ui.modal.ModalInteraction
import top.ntutn.kica.ui.modal.ModalInteractionState
import top.ntutn.kica.ui.modal.ModalOwner

/** Tests the exact registration boundary used by both native window wrappers without opening windows. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class ModalInteractionCompositionTest {
    @Test
    fun nestedScopesInheritOwnerAndRecompositionDoesNotDuplicateRegistration() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val registry = ModalInteractionState()
        val owner = ModalOwner()
        val nested = mutableStateOf(false)
        val revision = mutableStateOf(0)
        val scene = ImageComposeScene(300, 200, coroutineContext = backgroundScope.coroutineContext) {
            CompositionLocalProvider(LocalModalInteractionState provides registry) {
                @Suppress("UNUSED_VARIABLE") val observedRevision = revision.value
                ModalInteraction(owner) {
                    if (nested.value) ModalInteraction { }
                }
            }
        }
        fun frame() { Snapshot.sendApplyNotifications(); scene.render().close() }
        try {
            frame()
            assertEquals(1, registry.activeCount)
            nested.value = true
            frame()
            assertEquals(2, registry.activeCount)
            assertFalse(registry.hasOtherInteractions(owner), "Nested popup inherits its dialog owner")
            repeat(5) { revision.value++; frame() }
            assertEquals(2, registry.activeCount)
            nested.value = false
            frame()
            assertEquals(1, registry.activeCount)
        } finally {
            scene.close()
            runCurrent()
            Dispatchers.resetMain()
        }
        assertEquals(0, registry.activeCount, "Disposing the host releases every remaining window")
    }

    @Test
    fun switchingProviderOrOwnerReleasesOldRegistration() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val first = ModalInteractionState()
        val second = ModalInteractionState()
        val provider = mutableStateOf(first)
        val oldOwner = ModalOwner()
        val newOwner = ModalOwner()
        val owner = mutableStateOf(oldOwner)
        val scene = ImageComposeScene(300, 200, coroutineContext = backgroundScope.coroutineContext) {
            CompositionLocalProvider(LocalModalInteractionState provides provider.value) {
                ModalInteraction(owner.value) { }
            }
        }
        fun frame() { Snapshot.sendApplyNotifications(); scene.render().close() }
        try {
            frame()
            assertEquals(1, first.activeCount)
            provider.value = second
            frame()
            assertEquals(0, first.activeCount)
            assertEquals(1, second.activeCount)
            owner.value = newOwner
            frame()
            assertEquals(1, second.activeCount)
            assertTrue(second.hasOtherInteractions(oldOwner))
            assertFalse(second.hasOtherInteractions(newOwner))
        } finally {
            scene.close()
            runCurrent()
            Dispatchers.resetMain()
        }
        assertEquals(0, second.activeCount)
    }

    @Test
    fun featureWaitsForBusinessModalAndDoesNotBlockItsOwnDialog() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val registry = ModalInteractionState()
        val owner = ModalOwner()
        val businessOpen = mutableStateOf(true)
        val featureRequested = mutableStateOf(false)
        val scene = ImageComposeScene(300, 200, coroutineContext = backgroundScope.coroutineContext) {
            CompositionLocalProvider(LocalModalInteractionState provides registry) {
                if (businessOpen.value) ModalInteraction { }
                if (featureRequested.value && !registry.hasOtherInteractions(owner)) {
                    ModalInteraction(owner) { }
                }
            }
        }
        fun frames() { repeat(3) { Snapshot.sendApplyNotifications(); scene.render().close(); runCurrent() } }
        try {
            frames()
            featureRequested.value = true
            frames()
            assertEquals(1, registry.activeCount, "Business modal must keep priority")
            assertTrue(registry.hasOtherInteractions(owner))
            businessOpen.value = false
            frames()
            assertEquals(1, registry.activeCount, "Pending feature appears after business modal closes")
            assertFalse(registry.hasOtherInteractions(owner))
            repeat(5) { frames(); assertEquals(1, registry.activeCount, "No self-blocking show/hide loop") }
            featureRequested.value = false
            frames()
            assertEquals(0, registry.activeCount)
        } finally {
            scene.close()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}
