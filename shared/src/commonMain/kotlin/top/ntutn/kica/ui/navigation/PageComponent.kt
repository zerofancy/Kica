package top.ntutn.kica.ui.navigation

import com.arkivanov.decompose.ComponentContext
import kotlinx.serialization.Serializable
import top.ntutn.kica.model.AppRoute
import kotlin.uuid.Uuid

@Serializable
internal data class PageConfig(
    val entryId: String,
    val destination: AppRoute,
) {
    companion object {
        fun create(destination: AppRoute) = PageConfig(Uuid.random().toString(), destination)
    }
}

/** A page identity outlives its Compose subtree, but not its Decompose child context. */
internal class PageComponent(
    context: ComponentContext,
    val config: PageConfig,
    val services: AppServices,
    val session: SessionData,
) : ComponentContext by context.restorationContext() {
    internal val boundStores = mutableSetOf<String>()
    val route: AppRoute get() = config.destination
    val entryId: String get() = config.entryId
}

@Serializable
internal enum class SectionId {
    HOME, DISCOVER, FAVORITES, HISTORY, DOWNLOADS, SETTINGS;

    val route: AppRoute get() = when (this) {
        HOME -> AppRoute.Home
        DISCOVER -> AppRoute.Discover
        FAVORITES -> AppRoute.Favorites
        HISTORY -> AppRoute.History
        DOWNLOADS -> AppRoute.Downloads
        SETTINGS -> AppRoute.Settings
    }

    companion object {
        fun of(route: AppRoute): SectionId? = entries.firstOrNull { it.route == route }
    }
}
