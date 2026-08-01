package top.ntutn.kica.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import top.ntutn.kica.model.AppRoute

class KicaNavigationTest {
    @Test
    fun nonHomeRootDestinationsKeepHomeAsTheirBackTarget() {
        val destinations = listOf(
            AppRoute.Discover,
            AppRoute.Favorites,
            AppRoute.History,
            AppRoute.Downloads,
            AppRoute.Settings,
        )

        destinations.forEach { destination ->
            assertEquals(
                listOf(AppRoute.Home, destination),
                rootNavigationStackFor(destination),
            )
        }
    }

    @Test
    fun navigatingHomeDoesNotDuplicateHome() {
        assertEquals(
            listOf(AppRoute.Home),
            rootNavigationStackFor(AppRoute.Home),
        )
    }
}
