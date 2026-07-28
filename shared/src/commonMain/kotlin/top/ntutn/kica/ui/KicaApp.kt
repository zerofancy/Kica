package top.ntutn.kica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.data.DownloadCoordinator
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.data.PicaRepository
import top.ntutn.kica.data.PlatformServices
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
    RootDestination(AppRoute.Home, Res.string.home, Icons.Rounded.Home),
    RootDestination(AppRoute.Discover, Res.string.discover, Icons.Rounded.Explore),
    RootDestination(AppRoute.Favorites, Res.string.favorites, Icons.Rounded.Favorite),
    RootDestination(AppRoute.History, Res.string.history, Icons.Rounded.History),
    RootDestination(AppRoute.Downloads, Res.string.downloads, Icons.Rounded.Download),
    RootDestination(AppRoute.Settings, Res.string.settings, Icons.Rounded.Settings),
)

@Composable
fun KicaApp(
    picaRepository: PicaRepository,
    libraryRepository: LibraryRepository,
    downloadCoordinator: DownloadCoordinator,
    platformServices: PlatformServices,
) {
    val session by picaRepository.session.collectAsState()
    val settings by libraryRepository.settings().collectAsState(initial = top.ntutn.kica.model.AppSettings())
    val scope = rememberCoroutineScope()
    val backStack = remember { mutableStateListOf<AppRoute>(AppRoute.Home) }

    LaunchedEffect(Unit) {
        picaRepository.restoreSession()
        downloadCoordinator.restore()
    }
    LaunchedEffect(settings.network) {
        picaRepository.updateNetworkSettings(settings.network)
    }

    KicaFluentTheme(settings.theme) {
        val loginFailed = stringResource(Res.string.login_failed)
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
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
                val route = backStack.last()
                val navigate: (AppRoute) -> Unit = { target ->
                    if (target in rootDestinations.map { it.route }) {
                        backStack.clear()
                    }
                    if (backStack.lastOrNull() != target) backStack.add(target)
                }
                val routeContent: @Composable () -> Unit = {
                    RouteContent(
                        route = route,
                        picaRepository = picaRepository,
                        libraryRepository = libraryRepository,
                        downloadCoordinator = downloadCoordinator,
                        platformServices = platformServices,
                        onNavigate = navigate,
                        onBack = {
                            if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                        },
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

@Composable
private fun MainShell(
    route: AppRoute,
    onNavigate: (AppRoute) -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = classifyWindow(maxWidth.value.toInt())
        if (layout == WindowLayout.PHONE) {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet {
                        Text(
                            "Kica",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(20.dp),
                        )
                        rootDestinations.forEach { destination ->
                            NavigationDrawerItem(
                                selected = route == destination.route,
                                onClick = {
                                    onNavigate(destination.route)
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(stringResource(destination.label)) },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                },
            ) {
                Scaffold(
                    topBar = {
                        Row {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Rounded.Menu, contentDescription = null)
                            }
                        }
                    },
                    bottomBar = {
                        NavigationBar {
                            rootDestinations.take(5).forEach { destination ->
                                NavigationBarItem(
                                    selected = route == destination.route,
                                    onClick = { onNavigate(destination.route) },
                                    icon = { Icon(destination.icon, contentDescription = null) },
                                    label = { Text(stringResource(destination.label)) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        content()
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = if (layout == WindowLayout.DESKTOP) Modifier.width(220.dp) else Modifier,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    rootDestinations.forEach { destination ->
                        NavigationRailItem(
                            selected = route == destination.route,
                            onClick = { onNavigate(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = if (layout == WindowLayout.DESKTOP) {
                                { Text(stringResource(destination.label)) }
                            } else {
                                null
                            },
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    content()
                }
            }
        }
    }
}
