package top.ntutn.kica.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.arkivanov.essenty.lifecycle.doOnResume
import top.ntutn.kica.model.AppRoute
import top.ntutn.kica.model.RankPeriod
import top.ntutn.kica.ui.navigation.PageComponent
import top.ntutn.kica.ui.screen.*
import top.ntutn.kica.ui.store.*

@Composable
internal fun BrowseRouteContent(
    page: PageComponent,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val actions = remember(page) { page.store { TagActionStore(page.services.library) } }
    androidx.compose.runtime.CompositionLocalProvider(LocalTagActions provides actions) {
        BrowsePageContent(page, onNavigate, onBack, onLogout)
    }
}

@Composable
private fun BrowsePageContent(
    page: PageComponent,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val services = page.services
    val library = services.library
    val random = page.session.random
    when (val route = page.route) {
        AppRoute.Home -> {
            val store = remember(page) { page.store { HomeStore(services) }.also {
                if (page.boundStores.add("session-random")) page.lifecycle.doOnResume { random.ensureLoaded() }
            } }
            HomeScreen(store, library, page.session.random) { onNavigate(AppRoute.Detail(it.id)) }
        }
        AppRoute.Discover -> {
            val store = remember(page) { page.store {
                DiscoverStore(services, page.stateKeeper.consume("period", PeriodSnapshot.serializer())?.period ?: RankPeriod.HOURS_24)
            }.also { store ->
                if (page.stateKeeper.isRegistered("period")) page.stateKeeper.unregister("period")
                page.stateKeeper.register("period", PeriodSnapshot.serializer()) { PeriodSnapshot(store.selected.value) }
            } }
            DiscoverScreen(store, library, onNavigate)
        }
        AppRoute.RandomComics -> {
            remember(page) { if (page.boundStores.add("session-random")) page.lifecycle.doOnResume { random.ensureLoaded() } }
            RandomComicsScreen(page.session.random, library, onBack) { onNavigate(AppRoute.Detail(it.id)) }
        }
        AppRoute.Favorites -> {
            val store = remember(page) { page.store {
                ComicListStore(services, page.session, true,
                    page.stateKeeper.consume("favorites", SearchSnapshot.serializer()) ?: SearchSnapshot())
            }.also { store ->
                if (page.stateKeeper.isRegistered("favorites")) page.stateKeeper.unregister("favorites")
                page.stateKeeper.register("favorites", SearchSnapshot.serializer()) { store.snapshot() }
            } }
            FavoritesScreen(store, library) { onNavigate(AppRoute.Detail(it.id)) }
        }
        AppRoute.History, AppRoute.Downloads -> {
            val store = remember(page) { page.store {
                LibraryStore(services, page.stateKeeper.consume("library", LibrarySnapshot.serializer()) ?: LibrarySnapshot())
            }.also { store ->
                if (page.stateKeeper.isRegistered("library")) page.stateKeeper.unregister("library")
                page.stateKeeper.register("library", LibrarySnapshot.serializer()) { store.options.value }
            } }
            if (route == AppRoute.History) HistoryScreen(store, library) { onNavigate(AppRoute.Detail(it.comic.id)) }
            else DownloadsScreen(store, library)
        }
        AppRoute.Settings -> {
            val store = remember(page) { page.store {
                SettingsStore(services, page.stateKeeper.consume("settings", SettingsSnapshot.serializer()) ?: SettingsSnapshot(),
                    page.session.tagResolver)
            }.also { store ->
                if (page.stateKeeper.isRegistered("settings")) page.stateKeeper.unregister("settings")
                page.stateKeeper.register("settings", SettingsSnapshot.serializer()) { store.snapshot() }
            } }
            SettingsScreen(store, library, services.platform, onLogout)
        }
        is AppRoute.Search -> {
            val store = remember(page) { page.store {
                val snapshot = page.stateKeeper.consume("search", SearchSnapshot.serializer()) ?: SearchSnapshot(
                    route.initialQuery, route.category, route.fromTag,
                    route.initialQuery.trim().takeIf { it.isNotEmpty() }, route.category)
                ComicListStore(services, page.session, false, snapshot)
            }.also { store ->
                if (page.stateKeeper.isRegistered("search")) page.stateKeeper.unregister("search")
                page.stateKeeper.register("search", SearchSnapshot.serializer()) { store.snapshot() }
            } }
            SearchScreen(store, library, route.initialQuery, route.category, route.fromTag, onBack) {
                onNavigate(AppRoute.Detail(it.id))
            }
        }
        is AppRoute.Detail -> {
            val store = remember(page) { page.store { DetailStore(route.comicId, services, page.session) } }
            DetailScreen(store, services.platform, library,
                onTagSearch = { onNavigate(AppRoute.Search(initialQuery = it, fromTag = true)) },
                onBack = onBack, onRead = { onNavigate(AppRoute.Reader(route.comicId, it)) })
        }
        is AppRoute.Reader -> Unit
    }
}

@kotlinx.serialization.Serializable
private data class PeriodSnapshot(val period: RankPeriod)
