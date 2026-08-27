package top.ntutn.kica.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.data.DownloadCoordinator
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.data.PicaRepository
import top.ntutn.kica.data.PlatformServices
import top.ntutn.kica.model.AppRoute
import top.ntutn.kica.model.ComicDetail
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.ReaderMode
import top.ntutn.kica.model.ReadingProgress
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.email
import top.ntutn.kica.resources.enter_credentials
import top.ntutn.kica.resources.load_failed
import top.ntutn.kica.resources.logging_in
import top.ntutn.kica.resources.login
import top.ntutn.kica.resources.login_title
import top.ntutn.kica.resources.password
import top.ntutn.kica.ui.component.FluentCard
import top.ntutn.kica.ui.component.FluentPrimaryButton
import top.ntutn.kica.ui.component.FluentProgressRing
import top.ntutn.kica.ui.component.FluentTextField
import top.ntutn.kica.ui.screen.DetailScreen
import top.ntutn.kica.ui.screen.DiscoverScreen
import top.ntutn.kica.ui.screen.DownloadsScreen
import top.ntutn.kica.ui.screen.FavoritesScreen
import top.ntutn.kica.ui.screen.HistoryScreen
import top.ntutn.kica.ui.screen.HomeScreen
import top.ntutn.kica.ui.screen.RandomComicsScreen
import top.ntutn.kica.ui.screen.ReaderScreen
import top.ntutn.kica.ui.screen.SearchScreen
import top.ntutn.kica.ui.screen.SettingsScreen
import top.ntutn.kica.ui.state.RandomComicsUiState


@Composable
fun LoginScreen(
    onLogin: (String, String, (String?) -> Unit) -> Unit,
) {
    var emailValue by remember { mutableStateOf("") }
    var passwordValue by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val enterCredentials = stringResource(Res.string.enter_credentials)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        FluentCard(modifier = Modifier.width(400.dp).padding(20.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(Res.string.login_title), style = FluentTheme.typography.title)
                FluentTextField(
                    value = emailValue,
                    onValueChange = { emailValue = it },
                    label = { Text(stringResource(Res.string.email)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                FluentTextField(
                    value = passwordValue,
                    onValueChange = { passwordValue = it },
                    label = { Text(stringResource(Res.string.password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = FluentTheme.colors.system.critical) }
                FluentPrimaryButton(
                    onClick = {
                        if (emailValue.isBlank() || passwordValue.isBlank()) {
                            error = enterCredentials
                        } else {
                            busy = true
                            error = null
                            onLogin(emailValue.trim(), passwordValue) {
                                busy = false
                                error = it
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) {
                        FluentProgressRing(Modifier.size(18.dp), size = 18.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(if (busy) Res.string.logging_in else Res.string.login))
                }
            }
        }
    }
}

@Composable
fun RouteContent(
    route: AppRoute,
    picaRepository: PicaRepository,
    libraryRepository: LibraryRepository,
    downloadCoordinator: DownloadCoordinator,
    platformServices: PlatformServices,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    RouteContent(
        route = route,
        picaRepository = picaRepository,
        libraryRepository = libraryRepository,
        randomComicsLoader = rememberRandomComicsLoader(picaRepository, libraryRepository),
        downloadCoordinator = downloadCoordinator,
        platformServices = platformServices,
        onNavigate = onNavigate,
        onBack = onBack,
        onLogout = onLogout,
    )
}

@Composable
internal fun RouteContent(
    route: AppRoute,
    picaRepository: PicaRepository,
    libraryRepository: LibraryRepository,
    randomComicsLoader: RandomComicsLoader,
    downloadCoordinator: DownloadCoordinator,
    platformServices: PlatformServices,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    when (route) {
        AppRoute.Home -> HomeScreen(
            repository = picaRepository,
            library = libraryRepository,
            randomLoader = randomComicsLoader,
        ) { onNavigate(AppRoute.Detail(it.id)) }
        AppRoute.Discover -> DiscoverScreen(picaRepository, libraryRepository, onNavigate)
        AppRoute.RandomComics -> RandomComicsScreen(
            loader = randomComicsLoader,
            library = libraryRepository,
            onBack = onBack,
        ) { onNavigate(AppRoute.Detail(it.id)) }
        AppRoute.Favorites -> FavoritesScreen(
            repository = picaRepository,
            library = libraryRepository,
        ) { onNavigate(AppRoute.Detail(it.id)) }
        AppRoute.History -> HistoryScreen(libraryRepository) { onNavigate(AppRoute.Detail(it.comic.id)) }
        AppRoute.Downloads -> DownloadsScreen(downloadCoordinator, libraryRepository)
        AppRoute.Settings -> SettingsScreen(libraryRepository, platformServices, onLogout)
        is AppRoute.Search -> SearchScreen(
            repository = picaRepository,
            library = libraryRepository,
            initialQuery = route.initialQuery,
            initialCategory = route.category,
            onBack = onBack,
        ) {
            onNavigate(AppRoute.Detail(it.id))
        }
        is AppRoute.Detail -> DetailScreen(
            comicId = route.comicId,
            repository = picaRepository,
            downloads = downloadCoordinator,
            platformServices = platformServices,
            onBack = onBack,
            onRead = { onNavigate(AppRoute.Reader(route.comicId, it)) },
        )
        is AppRoute.Reader -> ReaderScreen(
            comicId = route.comicId,
            episodeId = route.episodeId,
            repository = picaRepository,
            library = libraryRepository,
            platformServices = platformServices,
            onBack = onBack,
        )
    }
}

internal class RandomComicsLoader(
    private val fetchRandomComics: suspend () -> List<ComicSummary>,
    private val readCache: suspend () -> List<ComicSummary>?,
    private val writeCache: suspend (List<ComicSummary>) -> Unit,
    private val scope: CoroutineScope,
    private val fallbackError: String,
) {
    private var didLoadOnce = false

    var state by mutableStateOf(RandomComicsUiState())
        private set

    fun loadOnce() {
        if (didLoadOnce) return
        didLoadOnce = true
        load(useCache = true)
    }

    fun refresh() {
        if (state.isLoading) return
        load(useCache = false)
    }

    private fun load(useCache: Boolean) {
        state = state.startLoading()
        scope.launch {
            if (useCache) {
                runCatching { readCache() }.getOrNull()?.let { cached ->
                    state = state.loadSuccess(cached).startLoading()
                }
            }
            runCatching { fetchRandomComics() }
                .onSuccess { comics ->
                    state = state.loadSuccess(comics)
                    runCatching { writeCache(comics) }
                }
                .onFailure { error ->
                    state = state.loadFailure(error.message ?: fallbackError)
                }
        }
    }
}

@Composable
internal fun rememberRandomComicsLoader(
    repository: PicaRepository,
    library: LibraryRepository,
): RandomComicsLoader {
    val fallbackError = stringResource(Res.string.load_failed)
    val scope = rememberCoroutineScope()
    val loader = remember(repository, library, fallbackError) {
        RandomComicsLoader(
            fetchRandomComics = repository::randomComics,
            readCache = library::cachedRandomComics,
            writeCache = library::cacheRandomComics,
            scope = scope,
            fallbackError = fallbackError,
        )
    }
    LaunchedEffect(loader) { loader.loadOnce() }
    return loader
}

internal fun ComicDetail.toSummary() = ComicSummary(
    id = id,
    title = title,
    author = author,
    coverUrl = coverUrl,
    categories = categories,
    finished = finished,
    likes = likes,
    views = views,
)

internal fun progress(
    comicId: String,
    episodeId: String,
    page: Int,
    mode: ReaderMode,
) = ReadingProgress(
    comicId = comicId,
    episodeId = episodeId,
    pageIndex = page,
    mode = mode,
    updatedAtEpochMillis = kotlin.time.Clock.System.now().toEpochMilliseconds(),
)
