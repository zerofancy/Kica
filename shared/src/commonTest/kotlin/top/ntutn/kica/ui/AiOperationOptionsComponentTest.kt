package top.ntutn.kica.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.data.SessionCredentialStore
import top.ntutn.kica.model.AppSettings
import top.ntutn.kica.ui.navigation.AiOperationOptionsComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AiOperationOptionsComponentTest {
    @Test
    fun settingsCommitAndPolicyAcknowledgmentAreSeparate() = navigationTest {
        val component = AiOperationOptionsComponent(host().context(), library)
        component.setEnabled(true)
        val request = assertNotNull(component.state.value.pendingPolicy)
        assertTrue(component.state.value.settings.aiOperationEnabled)
        component.policyApplied(request.id + 1)
        assertEquals(request, component.state.value.pendingPolicy)
        component.policyApplied(request.id)
        assertNull(component.state.value.pendingPolicy)
    }

    @Test
    fun activityRecreationRetainsInFlightWriteWithoutRetainingActivityCallbacks() = navigationTest { scheduler ->
        val gate = CompletableDeferred<Unit>()
        val delayed = object : LibraryRepository by library {
            override suspend fun updateSettings(settings: AppSettings) {
                gate.await()
                library.updateSettings(settings)
            }
        }
        val original = host()
        val first = AiOperationOptionsComponent(original.context(), delayed)
        first.setEnabled(true)
        assertTrue(first.state.value.busy)
        val second = AiOperationOptionsComponent(host(instances = original.instances).context(), delayed)
        assertTrue(second.state.value.busy)
        gate.complete(Unit)
        scheduler.runCurrent()
        assertFalse(second.state.value.busy)
        assertNotNull(second.state.value.pendingPolicy)
        assertTrue(second.state.value.settings.aiOperationEnabled)
    }

    @Test
    fun failedSettingsWriteDoesNotPublishPolicyRequest() = navigationTest {
        val failed = object : LibraryRepository by library {
            override suspend fun updateSettings(settings: AppSettings) { error("write failed") }
        }
        val component = AiOperationOptionsComponent(host().context(), failed)
        component.setEnabled(true)
        assertFalse(component.state.value.busy)
        assertNotNull(component.state.value.error)
        assertNull(component.state.value.pendingPolicy)
    }

    @Test
    fun credentialSessionIdentityRotatesEvenWhenSameTokenIsReused() = runTest {
        val credentials = SessionCredentialStore()
        credentials.writeToken("fixture-token")
        val first = assertNotNull(credentials.readSessionId())
        assertEquals(first, credentials.readSessionId())
        credentials.writeToken("fixture-token")
        assertNotEquals(first, credentials.readSessionId())
        credentials.clearToken()
        assertNull(credentials.readSessionId())
    }
}
