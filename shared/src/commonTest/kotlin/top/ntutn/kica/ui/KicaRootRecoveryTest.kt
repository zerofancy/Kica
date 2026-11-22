package top.ntutn.kica.ui

import com.arkivanov.essenty.lifecycle.Lifecycle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import top.ntutn.kica.data.sha256
import top.ntutn.kica.model.AppRoute
import top.ntutn.kica.model.AppSettings
import top.ntutn.kica.model.UserSession

@OptIn(ExperimentalCoroutinesApi::class)
class KicaRootRecoveryTest {
    @Test
    fun unknownSafetySettingsFinishRestoringButFailClosedUntilBackoffRecovery() = navigationTest { clock ->
        pica.session.value = UserSession("fixture", "fixture@example.invalid")
        library.updateSettings(AppSettings(lockEnabled = true, lockPasswordHash = "fixture-lock"))
        settingsReadFailures = Int.MAX_VALUE
        val root = root()
        clock.runCurrent()
        assertFalse(root.state.value.restoring)
        assertNotNull(root.state.value.serviceError)
        assertNull(root.state.value.sessionKey)
        assertNull(root.main.value.child, "Unknown safety preferences cannot silently bypass the application lock")
        root.login("fixture@example.invalid", "not-a-real-password")
        assertEquals(0, pica.loginCalls)
        val attempts = settingsSubscriptions
        clock.advanceTimeBy(499)
        clock.runCurrent()
        assertEquals(attempts, settingsSubscriptions, "A failing settings source must not spin")
        settingsReadFailures = 0
        clock.advanceTimeBy(1)
        clock.runCurrent()
        assertTrue(root.state.value.locked)
        assertNull(root.state.value.serviceError)
        val page = assertNotNull(root.main.value.child).instance.active.stack.value.active.instance
        assertEquals(Lifecycle.State.CREATED, page.lifecycle.state)
        assertEquals(1, pica.restoreCalls)
    }

    @Test
    fun networkSetupFailureDoesNotHideSettingsAndExplicitRetryKeepsTheEntry() = navigationTest { clock ->
        pica.session.value = UserSession("fixture", "fixture@example.invalid")
        pica.networkFailures = Int.MAX_VALUE
        val root = root()
        clock.runCurrent()
        assertFalse(root.state.value.restoring)
        assertNotNull(root.state.value.serviceError)
        val main = assertNotNull(root.main.value.child).instance
        main.navigate(AppRoute.Settings)
        val settings = main.active.stack.value.active.instance
        pica.networkFailures = 0
        root.retryServices()
        clock.runCurrent()
        assertNull(root.state.value.serviceError)
        assertSame(settings, assertNotNull(root.main.value.child).instance.active.stack.value.active.instance)
        assertEquals(1, pica.restoreCalls, "Retrying network configuration must not restore an already valid session again")
    }

    @Test
    fun laterNetworkApplyFailureDoesNotKillTheSettingsObserver() = navigationTest { clock ->
        pica.session.value = UserSession("fixture", "fixture@example.invalid")
        val root = root()
        clock.runCurrent()
        val main = assertNotNull(root.main.value.child).instance
        val initial = root.state.value.settings
        val key = root.state.value.sessionKey
        pica.networkFailures = 1
        library.updateSettings(initial.copy(preventScreenshots = !initial.preventScreenshots))
        clock.runCurrent()
        assertNotNull(root.state.value.serviceError)
        assertEquals(key, root.state.value.sessionKey)
        library.updateSettings(initial)
        clock.runCurrent()
        assertNull(root.state.value.serviceError)
        assertEquals(initial, root.state.value.settings)
        assertSame(main, assertNotNull(root.main.value.child).instance)
    }

    @Test
    fun credentialIdentityFailureIsRetryableWithoutReusingAnOldAccountTree() = navigationTest { clock ->
        pica.session.value = UserSession("fixture", "first@example.invalid")
        val root = root()
        clock.runCurrent()
        val oldPage = assertNotNull(root.main.value.child).instance.active.stack.value.active.instance
        credentials.marker = "next-account-login"
        credentials.readFailures = Int.MAX_VALUE
        pica.session.value = UserSession("other-fixture", "other@example.invalid")
        clock.runCurrent()
        assertNull(root.state.value.sessionKey)
        assertNull(root.main.value.child)
        assertNotNull(root.state.value.serviceError)
        credentials.readFailures = 0
        root.retryServices()
        clock.runCurrent()
        assertNull(root.state.value.serviceError)
        assertEquals(sha256(credentials.marker), root.state.value.sessionKey)
        val fresh = assertNotNull(root.main.value.child).instance.active.stack.value.active.instance
        assertTrue(oldPage.entryId != fresh.entryId)
    }

    @Test
    fun suspendedOldCredentialReadCannotResurrectLogoutOrReplaceANewerLogin() = navigationTest { clock ->
        pica.session.value = UserSession("fixture", "first@example.invalid")
        val root = root()
        clock.runCurrent()
        val gate = CompletableDeferred<Unit>()
        credentials.readGate = gate
        credentials.ignoreReadCancellation = true
        credentials.marker = "stale-intermediate-marker"
        pica.session.value = UserSession("intermediate", "intermediate@example.invalid")
        clock.runCurrent()
        assertNull(root.state.value.sessionKey, "Previous account is hidden while the replacement identity is unresolved")
        root.logout()
        clock.runCurrent()
        assertNull(root.state.value.sessionKey)
        credentials.readGate = null // Only the already suspended read holds the old gate.
        root.login("new@example.invalid", "not-a-real-password")
        clock.runCurrent()
        val expected = sha256(credentials.marker)
        assertEquals(expected, root.state.value.sessionKey)
        gate.complete(Unit)
        clock.runCurrent()
        assertEquals(expected, root.state.value.sessionKey)
        assertNotNull(root.main.value.child)
        assertEquals(1, pica.logoutCalls)
        assertEquals(1, pica.loginCalls)
    }
}
