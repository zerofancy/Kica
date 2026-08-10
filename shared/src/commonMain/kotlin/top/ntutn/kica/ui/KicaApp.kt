package top.ntutn.kica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Mica
import io.github.composefluent.component.Icon
import io.github.composefluent.component.NavigationDisplayMode
import io.github.composefluent.component.NavigationView
import io.github.composefluent.component.Text
import io.github.composefluent.component.menuItem
import io.github.composefluent.component.rememberNavigationState
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.ArrowDownload
import io.github.composefluent.icons.regular.Heart
import io.github.composefluent.icons.regular.History
import io.github.composefluent.icons.regular.Home
import io.github.composefluent.icons.regular.Navigation
import io.github.composefluent.icons.regular.Settings
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.data.DownloadCoordinator
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.data.PicaRepository
import top.ntutn.kica.data.PlatformServices
import top.ntutn.kica.data.TitleTranslationService
import top.ntutn.kica.model.AppRoute
import top.ntutn.kica.model.ThemePreference
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.discover
import top.ntutn.kica.resources.downloads
import top.ntutn.kica.resources.favorites
import top.ntutn.kica.resources.history
import top.ntutn.kica.resources.home
import top.ntutn.kica.resources.login_failed
import top.ntutn.kica.resources.settings

private data class RootDestination(
    val route: AppRoute,
    val label: StringResource,
    val icon: ImageVector,
)

private val rootDestinations = listOf(
    RootDestination(AppRoute.Home, Res.string.home, Icons.Regular.Home),
    RootDestination(AppRoute.Discover, Res.string.discover, Icons.Regular.Navigation),
    RootDestination(AppRoute.Favorites, Res.string.favorites, Icons.Regular.Heart),
    RootDestination(AppRoute.History, Res.string.history, Icons.Regular.History),
    RootDestination(AppRoute.Downloads, Res.string.downloads, Icons.Regular.ArrowDownload),
    RootDestination(AppRoute.Settings, Res.string.settings, Icons.Regular.Settings),
)

@Composable
fun KicaApp(
    picaRepository: PicaRepository,
    libraryRepository: LibraryRepository,
    downloadCoordinator: DownloadCoordinator,
    platformServices: PlatformServices,
    titleTranslationService: TitleTranslationService,
) {
    val session by picaRepository.session.collectAsState()
    val settings by libraryRepository.settings().collectAsState(initial = top.ntutn.kica.model.AppSettings())
    val scope = rememberCoroutineScope()
    val backStack = remember { mutableStateListOf<AppRoute>(AppRoute.Home) }
    val navigateBack = {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    PlatformBackHandler(
        enabled = session != null && backStack.size > 1,
        onBack = navigateBack,
    )

    LaunchedEffect(Unit) {
        picaRepository.restoreSession()
        downloadCoordinator.restore()
    }
    LaunchedEffect(settings.network) {
        picaRepository.updateNetworkSettings(settings.network)
        titleTranslationService.updateNetworkSettings(settings.network)
    }
    LaunchedEffect(settings.titleTranslationEnabled) {
        if (settings.titleTranslationEnabled) {
            runCatching { titleTranslationService.enable() }
        } else {
            titleTranslationService.disable()
        }
    }

    val readerActive = session != null && backStack.lastOrNull() is AppRoute.Reader
    CompositionLocalProvider(LocalTitleTranslationService provides titleTranslationService) {
        KicaFluentTheme(
            preference = settings.theme,
            forceDarkSystemBars = readerActive,
        ) {
        val loginFailed = stringResource(Res.string.login_failed)
        Mica(Modifier.fillMaxSize()) {
            val contentModifier = if (readerActive) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            }
            Box(
                contentModifier,
            ) {
                if (session == null) {
                    LoginScreen(
                        onLogin = { email, password, onResult ->
                            scope.launch {
                                runCatching { picaRepository.login(email, password) }
                                    .onSuccess { onResult(null) }
                                    .onFailure { onResult(it.message ?: loginFailed) }
                            }
                        },
                    )
                } else {
                    val randomComicsLoader = rememberRandomComicsLoader(
                        repository = picaRepository,
                        library = libraryRepository,
                    )
                    val route = backStack.last()
                    val navigate: (AppRoute) -> Unit = { target ->
                        if (target in rootDestinations.map { it.route }) {
                            backStack.clear()
                            backStack.addAll(rootNavigationStackFor(target))
                        } else if (backStack.lastOrNull() != target) {
                            backStack.add(target)
                        }
                    }
                    val routeContent: @Composable () -> Unit = {
                        RouteContent(
                            route = route,
                            picaRepository = picaRepository,
                            libraryRepository = libraryRepository,
                            randomComicsLoader = randomComicsLoader,
                            downloadCoordinator = downloadCoordinator,
                            platformServices = platformServices,
                            onNavigate = navigate,
                            onBack = navigateBack,
                            onLogout = {
                                scope.launch {
                                    picaRepository.logout()
                                    backStack.clear()
                                    backStack.add(AppRoute.Home)
                                }
                            },
                        )
                    }
                    if (route is AppRoute.Reader) {
                        routeContent()
                    } else {
                        MainShell(
                            route = route,
                            onNavigate = navigate,
                            content = routeContent,
                        )
                    }
                }
            }
        }
        }
    }
}

internal fun rootNavigationStackFor(target: AppRoute): List<AppRoute> =
    if (target == AppRoute.Home) listOf(AppRoute.Home) else listOf(AppRoute.Home, target)

@Composable
private fun MainShell(
    route: AppRoute,
    onNavigate: (AppRoute) -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = classifyWindow(maxWidth.value.toInt())
        val displayMode = when (layout) {
            WindowLayout.PHONE -> NavigationDisplayMode.LeftCollapsed
            WindowLayout.TABLET -> NavigationDisplayMode.LeftCompact
            WindowLayout.DESKTOP -> NavigationDisplayMode.Left
        }
        val navigationState = rememberNavigationState(
            initialExpanded = layout == WindowLayout.DESKTOP,
        )
        PlatformBackHandler(
            enabled = layout != WindowLayout.DESKTOP && navigationState.expanded,
            onBack = { navigationState.expanded = false },
        )
        val selectDestination: (AppRoute) -> Unit = { destination ->
            onNavigate(destination)
            if (layout != WindowLayout.DESKTOP) navigationState.expanded = false
        }

        NavigationView(
            modifier = Modifier.fillMaxSize(),
            displayMode = displayMode,
            state = navigationState,
            contentPadding = if (layout == WindowLayout.PHONE) {
                PaddingValues(top = 48.dp)
            } else {
                PaddingValues()
            },
            title = {
                Text(
                    text = "Kica",
                    style = FluentTheme.typography.subtitle,
                )
            },
            menuItems = {
                rootDestinations.dropLast(1).forEach { destination ->
                    menuItem(
                        selected = route == destination.route,
                        onClick = { selectDestination(destination.route) },
                        text = { Text(stringResource(destination.label)) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        key = destination.route,
                    )
                }
            },
            footerItems = {
                rootDestinations.lastOrNull()?.let { destination ->
                    menuItem(
                        selected = route == destination.route,
                        onClick = { selectDestination(destination.route) },
                        text = { Text(stringResource(destination.label)) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        key = destination.route,
                    )
                }
            },
            pane = content,
        )
    }
}
