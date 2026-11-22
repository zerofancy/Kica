package top.ntutn.kica.ui.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.pages.Pages
import com.arkivanov.essenty.statekeeper.SerializableContainer
import com.arkivanov.essenty.statekeeper.StateKeeper
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import top.ntutn.kica.model.AppRoute

/** Corrupt persisted input may be discarded; cancellation is never a successful fallback. */
internal inline fun <T> restoreOrNull(block: () -> T): T? = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

/** Covers the router's lazy outer SavedState decode before its restore callback is invoked. */
internal class RestoringStateKeeper(private val delegate: StateKeeper) : StateKeeper by delegate {
    override fun <T : Any> consume(key: String, strategy: DeserializationStrategy<T>): T? = restoreOrNull {
        val value = delegate.consume(key, strategy) ?: return@restoreOrNull null
        if (key in ROUTERS) {
            // Validate a serialized COPY: containers are consume-once, and config recreation
            // may still hold Decompose's private SavedState objects rather than encoded bytes.
            @Suppress("UNCHECKED_CAST")
            val serializer = strategy as SerializationStrategy<T>
            val copy = Json.decodeFromString(RouterSnapshot.serializer(), Json.encodeToString(serializer, value))
            validateRouter(key, copy)
        }
        value
    }

    private fun validateRouter(key: String, saved: RouterSnapshot) {
        val count = when (key) {
            "authenticated-session-v1" -> if (NavigationRestore.session(saved.navState) == null) 0 else 1
            "sections-v1" -> requireNotNull(NavigationRestore.sections(saved.navState)).items.size
            else -> {
                val configs = requireNotNull(saved.navState.consume(ListSerializer(PageConfig.serializer())))
                val section = requireNotNull(configs.firstOrNull()?.destination?.let(SectionId::of))
                requireNotNull(NavigationRestore.stack(section, NavigationRestore.saveStack(configs))).size
            }
        }
        require(saved.childState.size == count)
        require(saved.childState.all { it.key.isNotBlank() })
        require(saved.childState.map { it.key }.distinct().size == count)
        // DefaultChildItemFactory constructs a child dispatcher before calling our factory.
        // Prove those compressed child maps are decodable before letting Decompose consume them.
        saved.childState.forEach { child -> child.savedState?.let { StateKeeperDispatcher(it) } }
    }

    companion object {
        private val ROUTERS = setOf("authenticated-session-v1", "sections-v1", "pages-v1")
    }
}

/** Pinned Decompose 3.6.2 router framing; no framework class is reflected or instantiated. */
@Serializable
private data class RouterSnapshot(val navState: SerializableContainer, val childState: List<RouterChildSnapshot>)

@Serializable
private data class RouterChildSnapshot(val key: String, val savedState: SerializableContainer?)

internal fun ComponentContext.restorationContext(): ComponentContext = object : ComponentContext by this {
    override val stateKeeper: StateKeeper = RestoringStateKeeper(this@restorationContext.stateKeeper)
}

/** Same field layout as Decompose's serializable pages, with explicit Kica invariants. */
@Serializable
internal data class SectionPagesSnapshot(val items: List<SectionId>, val selectedIndex: Int)

internal object NavigationRestore {
    fun saveSession(config: SessionConfig?): SerializableContainer = SerializableContainer(config, SessionConfig.serializer())

    fun session(container: SerializableContainer): SessionConfig? = restoreOrNull {
        container.consume(SessionConfig.serializer())?.also { config ->
            require(config.sessionKey.length == 64 && config.sessionKey.all { it in '0'..'9' || it in 'a'..'f' })
        }
    }

    fun saveSections(pages: Pages<SectionId>): SerializableContainer = SerializableContainer(
        SectionPagesSnapshot(pages.items, pages.selectedIndex), SectionPagesSnapshot.serializer())

    fun sections(container: SerializableContainer): Pages<SectionId>? = restoreOrNull {
        val saved = container.consume(SectionPagesSnapshot.serializer()) ?: return@restoreOrNull null
        require(saved.items.isNotEmpty() && saved.items.first() == SectionId.HOME)
        require(saved.items.distinct().size == saved.items.size && saved.selectedIndex in saved.items.indices)
        Pages(saved.items, saved.selectedIndex)
    }

    fun saveStack(configs: List<PageConfig>): SerializableContainer =
        SerializableContainer(configs, ListSerializer(PageConfig.serializer()))

    fun stack(section: SectionId, container: SerializableContainer): List<PageConfig>? = restoreOrNull {
        val configs = container.consume(ListSerializer(PageConfig.serializer())) ?: return@restoreOrNull null
        require(configs.isNotEmpty() && configs.first().destination == section.route)
        require(configs.all { it.entryId.isNotBlank() } && configs.map { it.entryId }.distinct().size == configs.size)
        require(configs.drop(1).all { SectionId.of(it.destination) == null })
        require(configs.all { config -> when (val route = config.destination) {
            is AppRoute.Detail -> route.comicId.isNotBlank()
            is AppRoute.Reader -> route.comicId.isNotBlank() && route.episodeId.isNotBlank()
            else -> true
        } })
        configs
    }
}
