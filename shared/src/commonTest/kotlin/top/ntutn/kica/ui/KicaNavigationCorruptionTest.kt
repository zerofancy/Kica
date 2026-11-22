package top.ntutn.kica.ui

import com.arkivanov.decompose.router.pages.Pages
import com.arkivanov.essenty.statekeeper.SerializableContainer
import com.arkivanov.essenty.statekeeper.StateKeeper
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import top.ntutn.kica.model.AppRoute
import top.ntutn.kica.model.UserSession
import top.ntutn.kica.ui.navigation.NavigationRestore
import top.ntutn.kica.ui.navigation.NavigationSnapshotV1
import top.ntutn.kica.ui.navigation.NavigationStateKeeper
import top.ntutn.kica.ui.navigation.PageConfig
import top.ntutn.kica.ui.navigation.RestoringStateKeeper
import top.ntutn.kica.ui.navigation.SectionComponent
import top.ntutn.kica.ui.navigation.SectionId
import top.ntutn.kica.ui.navigation.SectionPagesSnapshot
import top.ntutn.kica.ui.navigation.SessionConfig
import top.ntutn.kica.ui.navigation.restoreOrNull

class KicaNavigationCorruptionTest {
    @Test
    fun validBase64WithInvalidCompressedDispatcherPayloadFallsBackToHome() = navigationTest {
        val source = host()
        source.keeper.register("kica-navigation-v1", NavigationSnapshotV1.serializer()) {
            NavigationSnapshotV1(data = "\"AAAA\"")
        }
        val recoveredHost = host(source.save())
        val recovered = main(recoveredHost, NavigationStateKeeper(recoveredHost.keeper).dispatcher)
        assertEquals(SectionId.HOME, recovered.activeSection)
        assertEquals(AppRoute.Home, recovered.active.stack.value.active.instance.route)
    }

    @Test
    fun lazyRouterSavedStateTypeCorruptionFallsBackBeforeConfigurationCallbacks() = navigationTest {
        pica.session.value = UserSession("fixture", "fixture@example.invalid")
        val source = host()
        val wrapped = NavigationStateKeeper(source.keeper)
        wrapped.dispatcher.register("authenticated-session-v1", String.serializer()) { "not-a-slot-saved-state" }
        val recovered = root(host(source.save()))
        assertEquals(AppRoute.Home, recovered.main.value.child!!.instance.active.stack.value.active.instance.route)

        val pageSource = host()
        pageSource.keeper.register("sections-v1", String.serializer()) { "not-a-pages-saved-state" }
        val recoveredMain = main(host(pageSource.save()))
        assertEquals(SectionId.HOME, recoveredMain.activeSection)

        val stackSource = host()
        stackSource.keeper.register("pages-v1", String.serializer()) { "not-a-stack-saved-state" }
        val stackHost = host(stackSource.save())
        val section = SectionComponent(stackHost.context(), SectionId.FAVORITES, services, recoveredMain.session)
        assertEquals(AppRoute.Favorites, section.stack.value.active.instance.route)
        assertEquals(emptyList(), section.stack.value.backStack)
    }

    @Test
    fun validNavigationWithMismatchedChildCountOrCorruptChildMapFallsBackSafely() = navigationTest {
        val sections = NavigationRestore.saveSections(Pages(listOf(SectionId.HOME, SectionId.DISCOVER), 1))
        val badChild = Json.decodeFromString(SerializableContainer.serializer(), "\"AAAA\"")
        val frames = listOf(
            RouterFrame(sections, emptyList()),
            RouterFrame(NavigationRestore.saveSections(Pages(listOf(SectionId.HOME), 0)),
                listOf(ChildFrame("home-child", badChild))),
            RouterFrame(NavigationRestore.saveSections(Pages(listOf(SectionId.HOME, SectionId.SETTINGS), 1)),
                listOf(ChildFrame("duplicate", null), ChildFrame("duplicate", null))),
        )
        for (frame in frames) {
            val source = host()
            source.keeper.register("sections-v1", RouterFrame.serializer()) { frame }
            val recovered = main(host(source.save()))
            assertEquals(SectionId.HOME, recovered.activeSection)
            assertEquals(AppRoute.Home, recovered.active.stack.value.active.instance.route)
        }
    }

    @Test
    fun lazyConfigurationCompressionErrorsAreCaughtAtEveryRestoreBoundary() {
        fun corrupted() = Json.decodeFromString(SerializableContainer.serializer(), "\"AAAA\"")
        assertNull(NavigationRestore.session(corrupted()))
        assertNull(NavigationRestore.sections(corrupted()))
        assertNull(NavigationRestore.stack(SectionId.HOME, corrupted()))
    }

    @Test
    fun invalidSectionsNeverReachDecomposeWithAnEmptyDuplicateOrInvalidSelection() {
        val invalid = listOf(
            SectionPagesSnapshot(emptyList(), 0),
            SectionPagesSnapshot(listOf(SectionId.HOME), -1),
            SectionPagesSnapshot(listOf(SectionId.HOME), 1),
            SectionPagesSnapshot(listOf(SectionId.HOME, SectionId.HOME), 0),
            SectionPagesSnapshot(listOf(SectionId.DISCOVER), 0),
            SectionPagesSnapshot(listOf(SectionId.DISCOVER, SectionId.HOME), 0),
        )
        for (snapshot in invalid) {
            assertNull(NavigationRestore.sections(SerializableContainer(snapshot, SectionPagesSnapshot.serializer())))
        }
        val valid = SectionPagesSnapshot(listOf(SectionId.HOME, SectionId.SETTINGS), 1)
        val restored = NavigationRestore.sections(SerializableContainer(valid, SectionPagesSnapshot.serializer()))!!
        assertEquals(valid.items, restored.items)
        assertEquals(1, restored.selectedIndex)
    }

    @Test
    fun invalidStacksRejectWrongRootsDuplicateEntryIdentityAndInvalidTargets() {
        val home = PageConfig("home", AppRoute.Home)
        val invalid = listOf(
            emptyList(),
            listOf(PageConfig("not-root", AppRoute.Detail("comic"))),
            listOf(PageConfig("wrong-root", AppRoute.Settings)),
            listOf(home, PageConfig("home", AppRoute.Detail("comic"))),
            listOf(home, PageConfig("", AppRoute.Detail("comic"))),
            listOf(home, PageConfig("nested-root", AppRoute.Favorites)),
            listOf(home, PageConfig("invalid-target", AppRoute.Reader("comic", ""))),
        )
        for (stack in invalid) assertNull(NavigationRestore.stack(SectionId.HOME, NavigationRestore.saveStack(stack)))
        val valid = listOf(home, PageConfig("first", AppRoute.Detail("same")),
            PageConfig("search", AppRoute.Search("tag")), PageConfig("second", AppRoute.Detail("same")))
        assertEquals(valid, NavigationRestore.stack(SectionId.HOME, NavigationRestore.saveStack(valid)))
    }

    @Test
    fun invalidSessionKeysAreDiscardedWithoutChangingValidMarkerIdentity() {
        for (key in listOf("", "token-like-invalid-string", "g".repeat(64))) {
            assertNull(NavigationRestore.session(NavigationRestore.saveSession(SessionConfig(key))))
        }
        val valid = SessionConfig("a0".repeat(32))
        assertEquals(valid, NavigationRestore.session(NavigationRestore.saveSession(valid)))
    }

    @Test
    fun cancellationIsNotMistakenForCorruptPersistedData() {
        assertFailsWith<CancellationException> { restoreOrNull<Unit> { throw CancellationException("fixture") } }
        val cancelled = object : StateKeeper by StateKeeperDispatcher() {
            override fun <T : Any> consume(key: String, strategy: DeserializationStrategy<T>): T? =
                throw CancellationException("fixture")
        }
        assertFailsWith<CancellationException> { RestoringStateKeeper(cancelled).consume("test", String.serializer()) }
        assertFailsWith<CancellationException> { NavigationStateKeeper(cancelled) }
    }

    @Serializable
    private data class RouterFrame(val navState: SerializableContainer, val childState: List<ChildFrame>)

    @Serializable
    private data class ChildFrame(val key: String, val savedState: SerializableContainer?)
}
