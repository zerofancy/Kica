package top.ntutn.kica.ui.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.children.ChildNavState
import com.arkivanov.decompose.router.pages.Pages
import com.arkivanov.decompose.router.pages.PagesNavigation
import com.arkivanov.decompose.router.pages.childPages
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import top.ntutn.kica.model.AppRoute

internal class MainComponent(
    context: ComponentContext,
    val services: AppServices,
) : ComponentContext by context.restorationContext() {
    val session = SessionData(context, services)
    private val navigation = PagesNavigation<SectionId>()
    val sections = childPages(
        source = navigation,
        initialPages = { Pages(items = listOf(SectionId.HOME), selectedIndex = 0) },
        savePages = NavigationRestore::saveSections,
        restorePages = NavigationRestore::sections,
        key = "sections-v1",
        pageStatus = { index, pages ->
            if (index == pages.selectedIndex) ChildNavState.Status.RESUMED else ChildNavState.Status.CREATED
        },
        handleBackButton = false,
    ) { section, childContext -> SectionComponent(childContext, section, services, session) }

    val active: SectionComponent get() = sections.value.let { it.items[it.selectedIndex].instance!! }
    val activeSection: SectionId get() = active.id
    val canGoBack: Boolean get() = active.canPop || activeSection != SectionId.HOME

    fun selectSection(id: SectionId) {
        navigation.navigate(
            transformer = { pages ->
                val index = pages.items.indexOf(id)
                if (index >= 0) pages.copy(selectedIndex = index)
                else Pages(pages.items + id, pages.items.size)
            },
            onComplete = { _, _ -> },
        )
    }

    fun navigate(route: AppRoute) {
        val section = SectionId.of(route)
        if (section != null) selectSection(section) else active.open(route)
    }

    fun back() {
        if (active.canPop) active.pop()
        else if (activeSection != SectionId.HOME) selectSection(SectionId.HOME)
    }

    suspend fun close() = session.close()
}

internal class SectionComponent(
    context: ComponentContext,
    val id: SectionId,
    services: AppServices,
    session: SessionData,
) : ComponentContext by context.restorationContext() {
    private val navigation = StackNavigation<PageConfig>()
    val stack = childStack(
        source = navigation,
        initialStack = { listOf(PageConfig.create(id.route)) },
        saveStack = NavigationRestore::saveStack,
        restoreStack = { NavigationRestore.stack(id, it) },
        key = "pages-v1",
        handleBackButton = false,
    ) { config, childContext -> PageComponent(childContext, config, services, session) }

    val canPop: Boolean get() = stack.value.backStack.isNotEmpty()
    @OptIn(com.arkivanov.decompose.DelicateDecomposeApi::class)
    fun open(route: AppRoute) {
        // One click delivered twice must not create two adjacent entries. Repeated routes deeper
        // in the stack remain valid and receive a fresh identity.
        if (stack.value.active.configuration.destination != route) navigation.push(PageConfig.create(route))
    }
    fun pop() { if (canPop) navigation.pop() }
}
