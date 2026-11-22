package top.ntutn.kica.ui

import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.arkivanov.essenty.lifecycle.Lifecycle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import top.ntutn.kica.model.AppRoute
import top.ntutn.kica.ui.navigation.MainComponent
import top.ntutn.kica.ui.navigation.PageConfig
import top.ntutn.kica.ui.navigation.SectionId

class KicaNavigationTest {
    @Test
    fun onlyHomeExistsInitiallyAndVisitingSectionsDoesNotFetchPageData() = navigationTest {
        val main = main()
        assertEquals(listOf(SectionId.HOME), main.sections.value.items.map { it.configuration })
        assertEquals(emptyList(), pica.pageCalls)
        for (id in SectionId.entries) {
            main.selectSection(id)
            assertEquals(id, main.activeSection)
            assertEquals(id.route, main.active.stack.value.active.instance.route)
        }
        assertEquals(SectionId.entries.toList(), main.sections.value.items.map { it.configuration })
        assertEquals(emptyList(), pica.pageCalls, "Factories must not launch hidden-page requests")
    }

    @Test
    fun allSixSectionsRetainTheirDeepStacksAndDistantSectionsAreNotDestroyed() = navigationTest {
        val main = main()
        val retained = SectionId.entries.associateWith { id ->
            main.selectSection(id)
            main.navigate(AppRoute.Detail("comic-$id"))
            main.active
        }
        for ((id, original) in retained) {
            main.selectSection(id)
            assertSame(original, main.active)
            assertEquals(AppRoute.Detail("comic-$id"), main.active.stack.value.active.instance.route)
            assertEquals(1, main.active.stack.value.backStack.size)
            assertEquals(Lifecycle.State.RESUMED, main.active.lifecycle.state)
            for ((otherId, other) in retained) {
                if (otherId != id) assertEquals(Lifecycle.State.CREATED, other.lifecycle.state)
            }
            val entry = main.active.stack.value.active.instance
            main.selectSection(id)
            assertSame(entry, main.active.stack.value.active.instance, "Selecting the active section is a no-op")
        }
        assertEquals(emptyList(), pica.pageCalls)
    }

    @Test
    fun equalRoutesAtDifferentStackDepthsHaveIndependentEntryIds() = navigationTest {
        val main = main()
        main.navigate(AppRoute.Detail("same-comic"))
        val first = main.active.stack.value.active.instance
        main.navigate(AppRoute.Detail("same-comic"))
        assertSame(first, main.active.stack.value.active.instance, "Duplicate delivery of one adjacent intent is ignored")
        main.navigate(AppRoute.Search("fixture-tag", fromTag = true))
        main.navigate(AppRoute.Detail("same-comic"))
        val second = main.active.stack.value.active.instance
        assertEquals(first.route, second.route)
        assertNotEquals(first.entryId, second.entryId)
        assertEquals(3, main.active.stack.value.backStack.size)
        main.back()
        main.back()
        assertSame(first, main.active.stack.value.active.instance)
    }

    @Test
    fun poppingDestroysOnlyThePoppedEntryAndReopeningCreatesNewIdentity() = navigationTest {
        val main = main()
        val home = main.active.stack.value.active.instance
        val homeProbe = home.instanceKeeper.getOrCreate("probe") { NavigationProbe() }
        main.navigate(AppRoute.Detail("comic"))
        val first = main.active.stack.value.active.instance
        val probe = first.instanceKeeper.getOrCreate("probe") { NavigationProbe() }
        main.back()
        assertEquals(1, probe.destroyed)
        assertEquals(0, homeProbe.destroyed)
        assertSame(home, main.active.stack.value.active.instance)
        main.navigate(AppRoute.Detail("comic"))
        val second = main.active.stack.value.active.instance
        assertNotEquals(first.entryId, second.entryId)
        assertNotEquals(probe, second.instanceKeeper.getOrCreate("probe") { NavigationProbe() })
        main.back()
        main.back()
        assertEquals(1, probe.destroyed, "Destroy must not be delivered twice")
        assertFalse(main.canGoBack)
    }

    @Test
    fun backAtAnotherSectionRootReturnsToTheExistingDeepHomeStack() = navigationTest {
        val main = main()
        main.navigate(AppRoute.Search("retained-home-query"))
        val homePage = main.active.stack.value.active.instance
        main.selectSection(SectionId.SETTINGS)
        assertTrue(main.canGoBack)
        main.back()
        assertEquals(SectionId.HOME, main.activeSection)
        assertSame(homePage, main.active.stack.value.active.instance)
        assertTrue(main.canGoBack)
        main.back()
        assertFalse(main.canGoBack)
        main.back()
        assertEquals(AppRoute.Home, main.active.stack.value.active.instance.route)
    }

    @Test
    fun serializedStateKeeperRestoresEveryVisitedSectionStackAndEntryIdentity() = navigationTest {
        val originalHost = host()
        val original = main(originalHost)
        original.navigate(AppRoute.Detail("same"))
        original.navigate(AppRoute.Search("tag", fromTag = true))
        original.navigate(AppRoute.Detail("same"))
        original.selectSection(SectionId.FAVORITES)
        original.navigate(AppRoute.Reader("comic", "episode"))
        original.selectSection(SectionId.DISCOVER)
        val expected = original.configurations()
        val restored = main(host(originalHost.save()))
        assertEquals(SectionId.DISCOVER, restored.activeSection)
        assertEquals(expected, restored.configurations())
        restored.selectSection(SectionId.FAVORITES)
        assertEquals(AppRoute.Reader("comic", "episode"), restored.active.stack.value.active.instance.route)
        assertEquals(emptyList(), pica.pageCalls, "Restoring hidden pages does not eagerly rebuild their data")
    }

    private fun MainComponent.configurations(): List<Pair<SectionId, List<PageConfig>>> =
        sections.value.items.map { child ->
            child.configuration to child.instance!!.stack.value.let { stack ->
                stack.backStack.map { it.configuration } + stack.active.configuration
            }
        }
}
