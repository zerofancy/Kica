package top.ntutn.kica.ui

import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import top.ntutn.kica.model.AppRoute
import top.ntutn.kica.model.AppSettings
import top.ntutn.kica.model.UserSession
import top.ntutn.kica.model.ReaderMode
import top.ntutn.kica.model.ReadingProgress
import top.ntutn.kica.ui.navigation.SectionId

@OptIn(ExperimentalCoroutinesApi::class)
class KicaRootComponentTest {
    @Test
    fun updateInstallationRequiresRecoveredUnlockedResumedRoot() = navigationTest { clock ->
        library.updateSettings(AppSettings(lockEnabled = true, lockPasswordHash = "test-hash"))
        val host = host()
        val root = root(host)
        assertFalse(root.canInstallUpdate(), "Recovery must not allow installation")
        clock.runCurrent()
        assertFalse(root.canInstallUpdate(), "A locked root must not allow installation")
        root.unlock()
        clock.runCurrent()
        assertTrue(root.canInstallUpdate(), "Anonymous users may update after recovery and unlock")
        host.lifecycle.stop()
        assertFalse(root.canInstallUpdate(), "A background host must not allow installation")
        host.lifecycle.resume()
        clock.runCurrent()
        assertTrue(root.canInstallUpdate())
        root.close()
        assertFalse(root.canInstallUpdate(), "Shutdown must synchronously prevent new installation")
    }

    @Test
    fun authenticationMustResolveBeforeCreatingAnInteractiveMainTree() = navigationTest { clock ->
        val gate = CompletableDeferred<Unit>()
        pica.restoreGate = gate
        val root = root()
        assertTrue(root.state.value.restoring)
        assertNull(root.main.value.child)
        assertEquals(emptyList(), pica.pageCalls)
        gate.complete(Unit)
        clock.runCurrent()
        assertFalse(root.state.value.restoring)
        assertNull(root.state.value.sessionKey)
        assertNull(root.main.value.child)
        root.login("fixture@example.invalid", "not-a-real-password")
        clock.runCurrent()
        assertNotNull(root.state.value.sessionKey)
        assertNotNull(root.main.value.child)
        assertEquals(1, pica.restoreCalls)
        assertEquals(1, pica.loginCalls)
        assertEquals(1, downloads.restoreCalls)
    }

    @Test
    fun lockStopsInteractionWithoutDestroyingPagesAndUnlockRestoresSameEntry() = navigationTest { clock ->
        pica.session.value = UserSession("fixture", "fixture@example.invalid")
        library.updateSettings(AppSettings(lockEnabled = true, lockPasswordHash = "non-secret-test-hash"))
        val host = host()
        val root = root(host)
        clock.runCurrent()
        assertTrue(root.state.value.locked)
        val main = assertNotNull(root.main.value.child).instance
        val home = main.active.stack.value.active.instance
        val probe = home.instanceKeeper.getOrCreate("probe") { NavigationProbe() }
        assertEquals(Lifecycle.State.CREATED, home.lifecycle.state)
        root.unlock()
        clock.runCurrent()
        assertFalse(root.state.value.locked)
        assertSame(home, main.active.stack.value.active.instance)
        assertEquals(Lifecycle.State.RESUMED, home.lifecycle.state)
        main.navigate(AppRoute.Detail("comic"))
        val detail = main.active.stack.value.active.instance
        host.lifecycle.stop()
        assertEquals(Lifecycle.State.CREATED, detail.lifecycle.state)
        host.lifecycle.resume()
        clock.runCurrent()
        assertSame(detail, main.active.stack.value.active.instance)
        assertEquals(0, probe.destroyed)
        assertEquals(emptyList(), pica.pageCalls)
        assertEquals(1, pica.restoreCalls)
    }

    @Test
    fun configurationRecreationKeepsInstanceStoresAndDoesNotRestoreAuthenticationAgain() = navigationTest { clock ->
        pica.session.value = UserSession("fixture", "fixture@example.invalid")
        library.updateSettings(AppSettings(lockEnabled = true, lockPasswordHash = "test-hash"))
        val oldHost = host()
        val oldRoot = root(oldHost)
        oldRoot.unlock()
        clock.runCurrent()
        val oldMain = assertNotNull(oldRoot.main.value.child).instance
        oldMain.selectSection(SectionId.HISTORY)
        oldMain.navigate(AppRoute.Detail("comic"))
        val oldPage = oldMain.active.stack.value.active.instance
        val retained = oldPage.instanceKeeper.getOrCreate("probe") { NavigationProbe() }
        val saved = oldHost.save()
        oldHost.lifecycle.destroy() // Host retains its explicit InstanceKeeperDispatcher.
        assertEquals(0, retained.destroyed)
        val recreated = root(host(saved, oldHost.instances))
        clock.runCurrent()
        val newMain = assertNotNull(recreated.main.value.child).instance
        val newPage = newMain.active.stack.value.active.instance
        assertSame(oldRoot.state, recreated.state)
        assertEquals(oldPage.entryId, newPage.entryId)
        assertEquals(SectionId.HISTORY, newMain.activeSection)
        assertSame(retained, newPage.instanceKeeper.getOrCreate("probe") { NavigationProbe() })
        assertFalse(recreated.state.value.locked, "Configuration changes must not masquerade as cold recovery")
        assertEquals(1, pica.restoreCalls)
        assertEquals(1, downloads.restoreCalls)
        assertEquals(0, retained.destroyed)
    }

    @Test
    fun processRecoveryRestoresMatchingSessionButReinitializesTheApplicationLock() = navigationTest { clock ->
        pica.session.value = UserSession("fixture", "fixture@example.invalid")
        library.updateSettings(AppSettings(lockEnabled = true, lockPasswordHash = "test-hash"))
        val originalHost = host()
        val original = root(originalHost)
        original.unlock()
        clock.runCurrent()
        val main = assertNotNull(original.main.value.child).instance
        main.navigate(AppRoute.Reader("comic", "episode"))
        val entryId = main.active.stack.value.active.instance.entryId
        val recovered = root(host(originalHost.save())) // A fresh keeper means a real cold process.
        clock.runCurrent()
        assertTrue(recovered.state.value.locked, "An unlocked flag must never be serialized")
        val page = assertNotNull(recovered.main.value.child).instance.active.stack.value.active.instance
        assertEquals(entryId, page.entryId)
        assertEquals(AppRoute.Reader("comic", "episode"), page.route)
        assertEquals(Lifecycle.State.CREATED, page.lifecycle.state)
        assertEquals(2, pica.restoreCalls)
        assertEquals(emptyList(), pica.pageCalls)
    }

    @Test
    fun credentialOnlyRepositoryRecoveryKeepsTheSameSessionNavigation() = navigationTest { clock ->
        val originalHost = host()
        val original = root(originalHost)
        original.login("fixture@example.invalid", "not-a-real-password")
        clock.runCurrent()
        val main = assertNotNull(original.main.value.child).instance
        main.navigate(AppRoute.Detail("retained-comic"))
        val entryId = main.active.stack.value.active.instance.entryId
        val sessionKey = original.state.value.sessionKey
        val saved = originalHost.save()
        // RealPicaRepository.restoreSession reconstructs UserSession with email="" from token.
        // The credential marker still identifies exactly the same authenticated login.
        pica.restoredEmail = ""
        val recovered = root(host(saved))
        clock.runCurrent()
        assertEquals(sessionKey, recovered.state.value.sessionKey)
        val recoveredPage = assertNotNull(recovered.main.value.child).instance.active.stack.value.active.instance
        assertEquals(entryId, recoveredPage.entryId)
        assertEquals(AppRoute.Detail("retained-comic"), recoveredPage.route)
    }

    @Test
    fun changedAccountAndSameAccountNewLoginBothDiscardOldNavigation() = navigationTest { clock ->
        pica.session.value = UserSession("fixture", "first@example.invalid")
        val originalHost = host()
        val original = root(originalHost)
        clock.runCurrent()
        val main = assertNotNull(original.main.value.child).instance
        main.navigate(AppRoute.Detail("old-account-comic"))
        val snapshot = originalHost.save()
        val previousSession = original.state.value.sessionKey
        credentials.marker = "different-explicit-login"
        val sameAccountNewLogin = root(host(snapshot))
        clock.runCurrent()
        assertNotEquals(previousSession, sameAccountNewLogin.state.value.sessionKey)
        assertEquals(AppRoute.Home, assertNotNull(sameAccountNewLogin.main.value.child).instance.active.stack.value.active.instance.route)
        credentials.marker = "other-account-explicit-login"
        pica.session.value = UserSession("other-fixture", "other@example.invalid")
        val anotherAccount = root(host(snapshot))
        clock.runCurrent()
        assertEquals(AppRoute.Home, assertNotNull(anotherAccount.main.value.child).instance.active.stack.value.active.instance.route)
    }

    @Test
    fun logoutDuringPendingWriteSurvivesHostConfigurationRecreation() = navigationTest { clock ->
        pica.session.value = UserSession("fixture", "fixture@example.invalid")
        val originalHost = host()
        val original = root(originalHost)
        clock.runCurrent()
        val main = assertNotNull(original.main.value.child).instance
        main.navigate(AppRoute.Reader("comic", "episode"))
        val gate = CompletableDeferred<Unit>()
        progressWriteGate = gate
        assertTrue(main.session.progressWriter.save(ReadingProgress("comic", "episode", 2, ReaderMode.VERTICAL, 1), null, ""))
        clock.runCurrent()
        original.logout()
        clock.runCurrent()
        val saved = originalHost.save()
        originalHost.lifecycle.destroy()
        val recreated = root(host(saved, originalHost.instances))
        gate.complete(Unit)
        clock.runCurrent()
        assertNull(recreated.state.value.sessionKey, "A retained logout must not resurrect a closed Main session")
        assertNull(recreated.main.value.child)
        assertEquals(1, pica.logoutCalls)
        assertEquals(2, library.readingProgress("comic", "episode")?.pageIndex)
    }

    @Test
    fun logoutDestroysTheOldEntryTreeAndSameAccountLoginStartsFresh() = navigationTest { clock ->
        pica.session.value = UserSession("fixture", "fixture@example.invalid")
        val root = root()
        clock.runCurrent()
        val main = assertNotNull(root.main.value.child).instance
        main.navigate(AppRoute.Detail("old-comic"))
        val oldPage = main.active.stack.value.active.instance
        val probe = oldPage.instanceKeeper.getOrCreate("probe") { NavigationProbe() }
        val sessionKey = root.state.value.sessionKey
        root.logout()
        clock.runCurrent()
        assertNull(root.state.value.sessionKey)
        assertNull(root.main.value.child)
        assertEquals(1, probe.destroyed)
        root.login("fixture@example.invalid", "not-a-real-password")
        clock.runCurrent()
        val freshMain = assertNotNull(root.main.value.child).instance
        assertNotEquals(sessionKey, root.state.value.sessionKey)
        assertNotEquals(oldPage.entryId, freshMain.active.stack.value.active.instance.entryId)
        assertEquals(AppRoute.Home, freshMain.active.stack.value.active.instance.route)
        assertEquals(1, probe.destroyed)
    }
}
