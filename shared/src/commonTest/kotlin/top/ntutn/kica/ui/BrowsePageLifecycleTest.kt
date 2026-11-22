package top.ntutn.kica.ui

import kotlin.test.*
import top.ntutn.kica.model.AppRoute
import top.ntutn.kica.model.RankPeriod
import top.ntutn.kica.ui.store.*

class BrowsePageLifecycleTest {
    @Test
    fun remountBindsExactlyOneResumeObserverPerTypedStore() = navigationTest {
        val main = main()
        val page = main.active.stack.value.active.instance
        val probe = page.store { ResumeProbe() }
        assertEquals(1, probe.resumes)
        assertSame(probe, page.store { ResumeProbe() })
        assertEquals(1, probe.resumes)
        main.navigate(AppRoute.Detail("comic"))
        main.back()
        assertEquals(2, probe.resumes)
        assertEquals(emptyList(), pica.pageCalls)
    }

    @Test
    fun purePushPopAndRemountDoNotReloadHomeOrSharedRandom() = navigationTest {
        val main = main()
        assertEquals(emptyList(), pica.pageCalls)
        val home = main.active.stack.value.active.instance
        val store = home.store { HomeStore(services) }
        main.session.random.ensureLoaded()
        assertEquals(listOf("home", "random"), pica.pageCalls)
        main.navigate(AppRoute.Detail("comic"))
        main.back()
        assertSame(store, home.store { HomeStore(services) })
        main.session.random.ensureLoaded()
        assertEquals(listOf("home", "random"), pica.pageCalls)
    }

    @Test
    fun visitedRankingPeriodsLoadOnlyOnceWithoutExplicitRefresh() = navigationTest {
        val store = DiscoverStore(services, RankPeriod.HOURS_24)
        try {
            store.ensureLoaded()
            store.select(RankPeriod.DAYS_7)
            store.select(RankPeriod.HOURS_24)
            store.ensureLoaded()
            assertEquals(1, pica.pageCalls.count { it == "categories" })
            assertEquals(2, pica.pageCalls.count { it == "ranking" })
        } finally { store.onDestroy() }
    }

    private class ResumeProbe : PageStore() {
        var resumes = 0
        override fun ensureLoaded() { resumes++ }
    }
}
