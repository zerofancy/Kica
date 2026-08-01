package top.ntutn.kica.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Icon
import io.github.composefluent.component.Text
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.filled.Heart as FilledHeart
import io.github.composefluent.icons.regular.ArrowDownload
import io.github.composefluent.icons.regular.ArrowExpand
import io.github.composefluent.icons.regular.ArrowLeft
import io.github.composefluent.icons.regular.ArrowSync
import io.github.composefluent.icons.regular.Heart
import io.github.composefluent.icons.regular.Maximize
import io.github.composefluent.icons.regular.Search
import io.github.composefluent.icons.regular.Star
import io.github.composefluent.icons.regular.Tag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import top.ntutn.kica.data.DownloadCoordinator
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.data.PicaRepository
import top.ntutn.kica.data.PlatformServices
import top.ntutn.kica.model.AppRoute
import top.ntutn.kica.model.ComicDetail
import top.ntutn.kica.model.ComicCategory
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.DownloadStatus
import top.ntutn.kica.model.HistoryEntry
import top.ntutn.kica.model.LoadState
import top.ntutn.kica.model.PageRef
import top.ntutn.kica.model.ProxyMode
import top.ntutn.kica.model.RankPeriod
import top.ntutn.kica.model.ReaderMode
import top.ntutn.kica.model.ReadingProgress
import top.ntutn.kica.model.ThemePreference
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.about
import top.ntutn.kica.resources.about_text
import top.ntutn.kica.resources.all_categories
import top.ntutn.kica.resources.author
import top.ntutn.kica.resources.back
import top.ntutn.kica.resources.cache
import top.ntutn.kica.resources.cancel
import top.ntutn.kica.resources.categories
import top.ntutn.kica.resources.clear_cache
import top.ntutn.kica.resources.choose_export_location
import top.ntutn.kica.resources.discover
import top.ntutn.kica.resources.download
import top.ntutn.kica.resources.download_location
import top.ntutn.kica.resources.downloads
import top.ntutn.kica.resources.email
import top.ntutn.kica.resources.empty
import top.ntutn.kica.resources.enter_credentials
import top.ntutn.kica.resources.episodes
import top.ntutn.kica.resources.favorite
import top.ntutn.kica.resources.favorites
import top.ntutn.kica.resources.finished
import top.ntutn.kica.resources.fit_height
import top.ntutn.kica.resources.fit_width
import top.ntutn.kica.resources.fullscreen
import top.ntutn.kica.resources.history
import top.ntutn.kica.resources.like
import top.ntutn.kica.resources.load_failed
import top.ntutn.kica.resources.login
import top.ntutn.kica.resources.login_title
import top.ntutn.kica.resources.logging_in
import top.ntutn.kica.resources.logout
import top.ntutn.kica.resources.network
import top.ntutn.kica.resources.no_description
import top.ntutn.kica.resources.ongoing
import top.ntutn.kica.resources.password
import top.ntutn.kica.resources.pause
import top.ntutn.kica.resources.proxy_direct
import top.ntutn.kica.resources.proxy_host
import top.ntutn.kica.resources.proxy_http
import top.ntutn.kica.resources.proxy_port
import top.ntutn.kica.resources.proxy_socks5
import top.ntutn.kica.resources.proxy_system
import top.ntutn.kica.resources.random_books
import top.ntutn.kica.resources.random_comics
import top.ntutn.kica.resources.rank_24h
import top.ntutn.kica.resources.rank_30d
import top.ntutn.kica.resources.rank_7d
import top.ntutn.kica.resources.rank_knight
import top.ntutn.kica.resources.ranking
import top.ntutn.kica.resources.read
import top.ntutn.kica.resources.reader_double
import top.ntutn.kica.resources.reader_double_ltr
import top.ntutn.kica.resources.reader_double_rtl
import top.ntutn.kica.resources.reader_ltr
import top.ntutn.kica.resources.reader_mode
import top.ntutn.kica.resources.reader_paged
import top.ntutn.kica.resources.reader_rtl
import top.ntutn.kica.resources.reader_vertical
import top.ntutn.kica.resources.recommended
import top.ntutn.kica.resources.retry
import top.ntutn.kica.resources.resume
import top.ntutn.kica.resources.search
import top.ntutn.kica.resources.search_failed
import top.ntutn.kica.resources.search_hint
import top.ntutn.kica.resources.settings
import top.ntutn.kica.resources.shuffle_batch
import top.ntutn.kica.resources.system_proxy
import top.ntutn.kica.resources.theme
import top.ntutn.kica.resources.theme_dark
import top.ntutn.kica.resources.theme_light
import top.ntutn.kica.resources.theme_system
import top.ntutn.kica.resources.unfavorite

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
    when (route) {
        AppRoute.Home -> HomeScreen(picaRepository) { onNavigate(AppRoute.Detail(it.id)) }
        AppRoute.Discover -> DiscoverScreen(picaRepository, onNavigate)
        AppRoute.RandomComics -> RandomComicsScreen(
            repository = picaRepository,
            onBack = onBack,
        ) { onNavigate(AppRoute.Detail(it.id)) }
        AppRoute.Favorites -> FavoritesScreen(picaRepository) { onNavigate(AppRoute.Detail(it.id)) }
        AppRoute.History -> HistoryScreen(libraryRepository) { onNavigate(AppRoute.Detail(it.comic.id)) }
        AppRoute.Downloads -> DownloadsScreen(downloadCoordinator)
        AppRoute.Settings -> SettingsScreen(libraryRepository, platformServices, onLogout)
        is AppRoute.Search -> SearchScreen(
            repository = picaRepository,
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

private class RandomComicsLoader(
    private val repository: PicaRepository,
    private val scope: CoroutineScope,
    private val fallbackError: String,
) {
    var state by mutableStateOf(RandomComicsUiState())
        private set

    fun refresh() {
        if (state.isLoading) return
        state = state.startLoading()
        scope.launch {
            state = runCatching { repository.randomComics() }.fold(
                onSuccess = state::loadSuccess,
                onFailure = { state.loadFailure(it.message ?: fallbackError) },
            )
        }
    }
}

@Composable
private fun rememberRandomComicsLoader(repository: PicaRepository): RandomComicsLoader {
    val fallbackError = stringResource(Res.string.load_failed)
    val scope = rememberCoroutineScope()
    val loader = remember(repository, fallbackError) {
        RandomComicsLoader(repository, scope, fallbackError)
    }
    LaunchedEffect(loader) { loader.refresh() }
    return loader
}

@Composable
private fun HomeScreen(repository: PicaRepository, onComicClick: (ComicSummary) -> Unit) {
    var recommendationsRefresh by remember { mutableIntStateOf(0) }
    val loadFailed = stringResource(Res.string.load_failed)
    val recommendationsState by produceState<LoadState<List<ComicSummary>>>(
        initialValue = LoadState.Loading,
        key1 = recommendationsRefresh,
    ) {
        value = runCatching { repository.recommendations() }
            .fold({ LoadState.Data(it) }, { LoadState.Error(it.message ?: loadFailed) })
    }
    val randomLoader = rememberRandomComicsLoader(repository)
    val randomState = randomLoader.state
    val gridState = rememberLazyGridState()

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(Res.string.recommended))
            }
            when (val value = recommendationsState) {
                is LoadState.Data -> item(span = { GridItemSpan(maxLineSpan) }) {
                    HorizontalComicRow(value.value, onComicClick)
                }
                is LoadState.Error -> item(span = { GridItemSpan(maxLineSpan) }) {
                    ErrorCard(value.message) { recommendationsRefresh++ }
                }
                else -> item(span = { GridItemSpan(maxLineSpan) }) {
                    FluentProgressBar(Modifier.fillMaxWidth())
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(6.dp))
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(Res.string.random_comics)) {
                    RandomRefreshButton(randomLoader)
                }
            }
            if (randomState.items == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    when {
                        randomState.isLoading -> FluentProgressBar(Modifier.fillMaxWidth())
                        randomState.errorMessage != null -> ErrorCard(randomState.errorMessage, randomLoader::refresh)
                    }
                }
            } else {
                if (randomState.isLoading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        FluentProgressBar(Modifier.fillMaxWidth())
                    }
                }
                randomState.errorMessage?.let { message ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        RandomRefreshError(message, randomLoader::refresh)
                    }
                }
                if (randomState.items.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(Res.string.empty),
                            color = FluentTheme.colors.text.text.secondary,
                        )
                    }
                } else {
                    gridItems(
                        items = randomState.items,
                        key = { "random:${it.id}" },
                    ) { comic ->
                        ComicCard(comic, onComicClick)
                    }
                }
            }
        }
        PlatformVerticalScrollbar(gridState, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun RandomComicsScreen(
    repository: PicaRepository,
    onBack: () -> Unit,
    onComicClick: (ComicSummary) -> Unit,
) {
    val loader = rememberRandomComicsLoader(repository)
    val state = loader.state
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    Column(
        modifier = Modifier.fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.key == Key.F5 && event.type == KeyEventType.KeyUp) {
                    loader.refresh()
                    true
                } else {
                    false
                }
            }
            .focusRequester(focusRequester)
            .focusable(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FluentIconButton(onClick = onBack) {
                Icon(Icons.Regular.ArrowLeft, contentDescription = stringResource(Res.string.back))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.random_books), style = FluentTheme.typography.title)
            Spacer(Modifier.weight(1f))
            RandomRefreshButton(loader)
        }
        when (val items = state.items) {
            null -> LoadStateContent(
                state = if (state.isLoading) {
                    LoadState.Loading
                } else {
                    LoadState.Error(state.errorMessage ?: stringResource(Res.string.load_failed))
                },
                modifier = Modifier.weight(1f),
                onRetry = loader::refresh,
            ) { Unit }
            else -> Column(Modifier.weight(1f).fillMaxWidth()) {
                if (state.isLoading) {
                    FluentProgressBar(Modifier.fillMaxWidth())
                }
                state.errorMessage?.let { message ->
                    RandomRefreshError(message, loader::refresh)
                }
                ComicGrid(
                    comics = items,
                    onComicClick = onComicClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RandomRefreshButton(loader: RandomComicsLoader) {
    FluentButton(
        onClick = loader::refresh,
        enabled = !loader.state.isLoading,
    ) {
        if (loader.state.isLoading) {
            FluentProgressRing(Modifier.size(16.dp), size = 16.dp)
        } else {
            Icon(
                Icons.Regular.ArrowSync,
                contentDescription = stringResource(Res.string.shuffle_batch),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(stringResource(Res.string.shuffle_batch))
    }
}

@Composable
private fun RandomRefreshError(message: String, onRetry: () -> Unit) {
    FluentCard(modifier = Modifier.fillMaxWidth(), onClick = onRetry) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = FluentTheme.colors.system.critical,
            )
            Text(stringResource(Res.string.retry), style = FluentTheme.typography.bodyStrong)
        }
    }
}

@Composable
private fun HorizontalComicRow(comics: List<ComicSummary>, onComicClick: (ComicSummary) -> Unit) {
    if (comics.isEmpty()) {
        Text(stringResource(Res.string.empty), color = FluentTheme.colors.text.text.secondary)
        return
    }
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(comics, key = { it.id }) { comic ->
                Box(Modifier.width(160.dp)) { ComicCard(comic, onComicClick) }
            }
        }
        PlatformHorizontalScrollbar(listState)
    }
}

@Composable
private fun DiscoverScreen(repository: PicaRepository, onNavigate: (AppRoute) -> Unit) {
    var selected by remember { mutableStateOf(RankPeriod.HOURS_24) }
    var refresh by remember { mutableIntStateOf(0) }
    val loadFailed = stringResource(Res.string.load_failed)
    val categoriesState by produceState<LoadState<List<ComicCategory>>>(LoadState.Loading, refresh) {
        value = runCatching { repository.categories() }
            .fold({ LoadState.Data(it) }, { LoadState.Error(it.message ?: loadFailed) })
    }
    val rankingState by produceState<LoadState<List<ComicSummary>>>(LoadState.Loading, selected, refresh) {
        value = runCatching { repository.ranking(selected) }
            .fold({ LoadState.Data(it) }, { LoadState.Error(it.message ?: loadFailed) })
    }
    val gridState = rememberLazyGridState()

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(Res.string.discover)) {
                    FluentIconButton(onClick = { onNavigate(AppRoute.Search()) }) {
                        Icon(Icons.Regular.Search, contentDescription = stringResource(Res.string.search))
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(Res.string.categories), style = FluentTheme.typography.subtitle)
            }
            item(key = "random-comics-entry") {
                RandomComicsEntryCard(onClick = { onNavigate(AppRoute.RandomComics) })
            }
            when (val categoryValue = categoriesState) {
                is LoadState.Data -> {
                    if (categoryValue.value.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                stringResource(Res.string.empty),
                                color = FluentTheme.colors.text.text.secondary,
                            )
                        }
                    } else {
                        gridItems(
                            items = categoryValue.value,
                            key = { "category:${it.id.ifBlank { it.title }}" },
                        ) { category ->
                            CategoryCoverCard(
                                category = category,
                                onClick = { onNavigate(AppRoute.Search(category = category.title)) },
                            )
                        }
                    }
                }
                is LoadState.Error -> item(span = { GridItemSpan(maxLineSpan) }) {
                    ErrorCard(categoryValue.message) { refresh++ }
                }
                else -> item(span = { GridItemSpan(maxLineSpan) }) {
                    FluentProgressBar(Modifier.fillMaxWidth())
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(8.dp))
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(Res.string.ranking), style = FluentTheme.typography.subtitle)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RankPeriod.entries.forEach { period ->
                        val label = when (period) {
                            RankPeriod.HOURS_24 -> Res.string.rank_24h
                            RankPeriod.DAYS_7 -> Res.string.rank_7d
                            RankPeriod.DAYS_30 -> Res.string.rank_30d
                            RankPeriod.KNIGHT -> Res.string.rank_knight
                        }
                        FluentChip(
                            selected = selected == period,
                            onClick = { selected = period },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }
            when (val rankingValue = rankingState) {
                is LoadState.Data -> {
                    if (rankingValue.value.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                stringResource(Res.string.empty),
                                color = FluentTheme.colors.text.text.secondary,
                            )
                        }
                    } else {
                        gridItems(rankingValue.value, key = { "ranking:${it.id}" }) { comic ->
                            ComicCard(comic) { onNavigate(AppRoute.Detail(it.id)) }
                        }
                    }
                }
                is LoadState.Error -> item(span = { GridItemSpan(maxLineSpan) }) {
                    ErrorCard(rankingValue.message) { refresh++ }
                }
                else -> item(span = { GridItemSpan(maxLineSpan) }) {
                    FluentProgressBar(Modifier.fillMaxWidth())
                }
            }
        }
        PlatformVerticalScrollbar(gridState, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun RandomComicsEntryCard(onClick: () -> Unit) {
    FluentCard(
        modifier = Modifier.fillMaxWidth().aspectRatio(1.45f),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Brush.linearGradient(categoryCoverPalettes[1])),
        ) {
            Icon(
                imageVector = Icons.Regular.ArrowSync,
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(52.dp),
                tint = Color.White.copy(alpha = 0.9f),
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.62f),
                        ),
                    ),
                ),
            )
            Text(
                text = stringResource(Res.string.random_books),
                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                color = Color.White,
                style = FluentTheme.typography.bodyStrong,
            )
        }
    }
}

@Composable
private fun CategoryCoverCard(
    category: ComicCategory,
    onClick: () -> Unit,
) {
    val paletteIndex = category.title.hashCode().and(Int.MAX_VALUE) % categoryCoverPalettes.size
    val palette = categoryCoverPalettes[paletteIndex]
    var imageFailed by remember(category.coverUrl) { mutableStateOf(false) }
    FluentCard(
        modifier = Modifier.fillMaxWidth().aspectRatio(1.45f),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Brush.linearGradient(palette))
        ) {
            if (category.coverUrl.isNotBlank() && !imageFailed) {
                AsyncImage(
                    model = category.coverUrl,
                    contentDescription = category.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onError = { imageFailed = true },
                )
            }
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.78f),
                        ),
                    ),
                ),
            )
            Box(
                modifier = Modifier.padding(14.dp).size(34.dp)
                    .background(Color.White.copy(alpha = 0.18f), FluentTheme.shapes.control),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Regular.Tag,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
            ) {
                Text(
                    text = category.title,
                    color = Color.White,
                    style = FluentTheme.typography.bodyStrong,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (category.description.isNotBlank()) {
                    Text(
                        text = category.description,
                        color = Color.White.copy(alpha = 0.82f),
                        style = FluentTheme.typography.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private val categoryCoverPalettes = listOf(
    listOf(Color(0xFF005FB8), Color(0xFF4F9CF9)),
    listOf(Color(0xFF6B4AA5), Color(0xFFB37FEB)),
    listOf(Color(0xFF0F7B6C), Color(0xFF4DB6AC)),
    listOf(Color(0xFF9D5D00), Color(0xFFEAA300)),
    listOf(Color(0xFFB1464A), Color(0xFFFF7A85)),
    listOf(Color(0xFF3A6073), Color(0xFF68A0B0)),
)

@Composable
private fun SearchScreen(
    repository: PicaRepository,
    initialQuery: String,
    initialCategory: String?,
    onBack: () -> Unit,
    onComicClick: (ComicSummary) -> Unit,
) {
    var query by remember(initialQuery, initialCategory) { mutableStateOf(initialQuery) }
    var selectedCategory by remember(initialCategory) { mutableStateOf(initialCategory) }
    var criteria by remember(initialQuery, initialCategory) {
        mutableStateOf(
            SearchCriteria(initialQuery.trim(), initialCategory)
                .takeIf { it.keyword.isNotEmpty() || it.category != null },
        )
    }
    var refresh by remember { mutableIntStateOf(0) }
    val searchFailed = stringResource(Res.string.search_failed)
    var state by remember(initialQuery, initialCategory) {
        mutableStateOf<LoadState<List<ComicSummary>>>(
            if (criteria == null) LoadState.Idle else LoadState.Loading,
        )
    }
    var loadedPage by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(0) }
    var loadingMore by remember { mutableStateOf(false) }
    var loadMoreError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(criteria, refresh) {
        val current = criteria
        loadedPage = 0
        totalPages = 0
        loadingMore = false
        loadMoreError = null
        if (current == null) {
            state = LoadState.Idle
        } else {
            state = LoadState.Loading
            state = runCatching {
                repository.search(
                    keyword = current.keyword,
                    categories = current.category?.let(::listOf).orEmpty(),
                    page = 1,
                )
            }.fold(
                onSuccess = { result ->
                    loadedPage = result.page
                    totalPages = result.totalPages
                    LoadState.Data(result.items)
                },
                onFailure = { LoadState.Error(it.message ?: searchFailed) },
            )
        }
    }
    val canLoadMore = state is LoadState.Data && loadedPage < totalPages
    val requestLoadMore: () -> Unit = request@{
        val current = criteria ?: return@request
        if (!canLoadMore || loadingMore) return@request
        val nextPage = loadedPage + 1
        loadingMore = true
        loadMoreError = null
        scope.launch {
            val result = runCatching {
                repository.search(
                    keyword = current.keyword,
                    categories = current.category?.let(::listOf).orEmpty(),
                    page = nextPage,
                )
            }
            if (criteria != current) return@launch
            result.fold(
                onSuccess = { page ->
                    val existing = (state as? LoadState.Data)?.value.orEmpty()
                    state = LoadState.Data((existing + page.items).distinctBy(ComicSummary::id))
                    loadedPage = maxOf(nextPage, page.page)
                    totalPages = page.totalPages
                },
                onFailure = {
                    loadMoreError = it.message ?: searchFailed
                },
            )
            loadingMore = false
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FluentIconButton(onClick = onBack) {
                Icon(Icons.Regular.ArrowLeft, contentDescription = stringResource(Res.string.back))
            }
            FluentTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(Res.string.search_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FluentPrimaryButton(
                onClick = {
                    val keyword = query.trim()
                    if (keyword.isNotEmpty() || selectedCategory != null) {
                        val nextCriteria = SearchCriteria(keyword, selectedCategory)
                        if (criteria == nextCriteria) refresh++ else criteria = nextCriteria
                    }
                },
            ) { Text(stringResource(Res.string.search)) }
        }
        selectedCategory?.let { category ->
            Spacer(Modifier.height(10.dp))
            FluentChip(
                selected = true,
                onClick = {
                    selectedCategory = null
                    criteria = query.trim()
                        .takeIf(String::isNotEmpty)
                        ?.let { SearchCriteria(keyword = it, category = null) }
                },
                label = { Text("${stringResource(Res.string.categories)}：$category") },
            )
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.weight(1f)) {
            when (val value = state) {
                LoadState.Idle -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.search_hint),
                        color = FluentTheme.colors.text.text.secondary,
                    )
                }
                else -> LoadStateContent(
                    state = value,
                    onRetry = { refresh++ },
                ) {
                    ComicGrid(
                        comics = it,
                        onComicClick = onComicClick,
                        loadingMore = loadingMore,
                        canLoadMore = canLoadMore,
                        loadMoreError = loadMoreError,
                        onLoadMore = requestLoadMore,
                    )
                }
            }
        }
    }
}

private data class SearchCriteria(
    val keyword: String,
    val category: String?,
)

@Composable
private fun FavoritesScreen(repository: PicaRepository, onComicClick: (ComicSummary) -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val loadFailed = stringResource(Res.string.load_failed)
    val state by produceState<LoadState<List<ComicSummary>>>(LoadState.Loading, refresh) {
        value = runCatching { repository.favorites() }
            .fold({ LoadState.Data(it) }, { LoadState.Error(it.message ?: loadFailed) })
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        SectionTitle(stringResource(Res.string.favorites))
        Box(Modifier.weight(1f)) {
            LoadStateContent(state, onRetry = { refresh++ }) { ComicGrid(it, onComicClick) }
        }
    }
}

@Composable
private fun HistoryScreen(library: LibraryRepository, onClick: (HistoryEntry) -> Unit) {
    val historyItems by library.history().collectAsState(initial = emptyList())
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        SectionTitle(stringResource(Res.string.history))
        if (historyItems.isEmpty()) {
            EmptyContent(Modifier.weight(1f))
        } else {
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(historyItems, key = { it.comic.id }) { entry ->
                        FluentCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onClick(entry) },
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = entry.comic.coverUrl,
                                    contentDescription = entry.comic.title,
                                    modifier = Modifier.size(64.dp),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(entry.comic.title, style = FluentTheme.typography.bodyStrong)
                                    Text(
                                        "${entry.episodeTitle} · ${entry.pageIndex + 1}",
                                        color = FluentTheme.colors.text.text.secondary,
                                    )
                                }
                            }
                        }
                    }
                }
                PlatformVerticalScrollbar(listState, Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

@Composable
private fun DownloadsScreen(coordinator: DownloadCoordinator) {
    val tasks by coordinator.tasks.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        SectionTitle(stringResource(Res.string.downloads))
        if (tasks.isEmpty()) {
            EmptyContent(Modifier.weight(1f))
        } else {
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(tasks, key = { it.id }) { task ->
                        FluentCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(task.comic.title, style = FluentTheme.typography.bodyStrong)
                                Text(task.episode.title, color = FluentTheme.colors.text.text.secondary)
                                Spacer(Modifier.height(8.dp))
                                FluentProgressBar(
                                    progress = if (task.totalPages <= 0) 0f
                                    else task.completedPages.toFloat() / task.totalPages,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    when (task.status) {
                                        DownloadStatus.RUNNING, DownloadStatus.QUEUED ->
                                            FluentTextButton(onClick = { scope.launch { coordinator.pause(task.id) } }) {
                                                Text(stringResource(Res.string.pause))
                                            }
                                        DownloadStatus.PAUSED, DownloadStatus.FAILED ->
                                            FluentTextButton(onClick = { scope.launch { coordinator.resume(task.id) } }) {
                                                Text(stringResource(Res.string.resume))
                                            }
                                        else -> Unit
                                    }
                                    if (task.status != DownloadStatus.COMPLETED) {
                                        FluentTextButton(onClick = { scope.launch { coordinator.cancel(task.id) } }) {
                                            Text(stringResource(Res.string.cancel))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                PlatformVerticalScrollbar(listState, Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailScreen(
    comicId: String,
    repository: PicaRepository,
    downloads: DownloadCoordinator,
    platformServices: PlatformServices,
    onBack: () -> Unit,
    onRead: (String) -> Unit,
) {
    var refresh by remember { mutableIntStateOf(0) }
    val loadFailed = stringResource(Res.string.load_failed)
    val state by produceState<LoadState<Pair<ComicDetail, List<top.ntutn.kica.model.Episode>>>>(
        LoadState.Loading,
        comicId,
        refresh,
    ) {
        value = runCatching { repository.comic(comicId) to repository.episodes(comicId) }
            .fold({ LoadState.Data(it) }, { LoadState.Error(it.message ?: loadFailed) })
    }
    val scope = rememberCoroutineScope()

    FluentScaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FluentIconButton(onClick = onBack) {
                    Icon(Icons.Regular.ArrowLeft, contentDescription = stringResource(Res.string.back))
                }
            }
        },
    ) { padding ->
        LoadStateContent(state, Modifier.padding(padding), onRetry = { refresh++ }) { (comic, episodeItems) ->
            val scrollState = rememberScrollState()
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(scrollState).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        AsyncImage(
                            model = comic.coverUrl,
                            contentDescription = comic.title,
                            modifier = Modifier.width(180.dp).aspectRatio(0.72f),
                            contentScale = ContentScale.Crop,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(comic.title, style = FluentTheme.typography.title)
                            Spacer(Modifier.height(8.dp))
                            Text("${stringResource(Res.string.author)}：${comic.author}")
                            Text(stringResource(if (comic.finished) Res.string.finished else Res.string.ongoing))
                            Spacer(Modifier.height(12.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                comic.categories.forEach { FluentChip(false, {}, { Text(it) }) }
                                comic.tags.take(8).forEach { FluentChip(false, {}, { Text(it) }) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FluentButton(onClick = { scope.launch { repository.toggleFavorite(comic.id) } }) {
                                    Icon(
                                        if (comic.isFavorite) Icons.Filled.FilledHeart else Icons.Regular.Heart,
                                        contentDescription = null,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(if (comic.isFavorite) Res.string.unfavorite else Res.string.favorite))
                                }
                                FluentButton(onClick = { scope.launch { repository.like(comic.id) } }) {
                                    Icon(Icons.Regular.Star, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(Res.string.like))
                                }
                            }
                        }
                    }
                    Text(comic.description.ifBlank { stringResource(Res.string.no_description) })
                    SectionTitle(stringResource(Res.string.episodes))
                    episodeItems.forEach { episode ->
                        FluentCard(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(episode.title, modifier = Modifier.weight(1f))
                                FluentIconButton(onClick = { onRead(episode.id) }, iconOnly = false) {
                                    Text(stringResource(Res.string.read))
                                }
                                FluentIconButton(
                                    onClick = {
                                        scope.launch {
                                            downloads.enqueue(
                                                comic = comic.toSummary(),
                                                episode = episode,
                                                targetLocation = platformServices.fileLocationProvider.defaultDownloadLocation(),
                                            )
                                        }
                                    },
                                ) {
                                    Icon(Icons.Regular.ArrowDownload, contentDescription = stringResource(Res.string.download))
                                }
                            }
                        }
                    }
                }
                PlatformVerticalScrollbar(
                    scrollState,
                    Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderScreen(
    comicId: String,
    episodeId: String,
    repository: PicaRepository,
    library: LibraryRepository,
    platformServices: PlatformServices,
    onBack: () -> Unit,
) {
    var refresh by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(ReaderMode.VERTICAL) }
    var fit by remember { mutableStateOf(PageFit.WIDTH) }
    var controlsVisible by remember { mutableStateOf(true) }
    val loadFailed = stringResource(Res.string.load_failed)
    val restoredState by produceState(false to null as ReadingProgress?, comicId, episodeId) {
        value = true to library.readingProgress(comicId, episodeId)
    }
    val progressLoaded = restoredState.first
    val restoredProgress = restoredState.second
    val metadata by produceState<Pair<ComicSummary, String>?>(null, comicId, episodeId) {
        value = runCatching {
            val detail = repository.comic(comicId)
            val episodeTitle = repository.episodes(comicId)
                .firstOrNull { it.id == episodeId }
                ?.title
                ?: episodeId
            detail.toSummary() to episodeTitle
        }.getOrNull()
    }
    val pagesState by produceState<LoadState<List<PageRef>>>(LoadState.Loading, comicId, episodeId, refresh) {
        value = runCatching { repository.pages(comicId, episodeId) }.fold(
            onSuccess = { LoadState.Data(it) },
            onFailure = { error ->
                val task = library.downloads().first()
                    .firstOrNull { it.comic.id == comicId && it.episode.id == episodeId }
                val offline = task?.let {
                    platformServices.fileLocationProvider.downloadedPages(it)
                }.orEmpty()
                if (offline.isNotEmpty()) {
                    LoadState.Data(offline, fromCache = true)
                } else {
                    LoadState.Error(error.message ?: loadFailed)
                }
            },
        )
    }
    val scope = rememberCoroutineScope()
    val initialPage = restoredProgress?.pageIndex ?: 0
    val savePage: (Int) -> Unit = { page ->
        scope.launch {
            val saved = progress(comicId, episodeId, page, mode)
            library.saveProgress(saved)
            metadata?.let { (comic, episodeTitle) ->
                library.addHistory(
                    HistoryEntry(
                        comic = comic,
                        episodeId = episodeId,
                        episodeTitle = episodeTitle,
                        pageIndex = page,
                        updatedAtEpochMillis = saved.updatedAtEpochMillis,
                    ),
                )
            }
        }
    }
    LaunchedEffect(restoredProgress) {
        restoredProgress?.let { mode = it.mode }
    }

    val toolbarScrollState = rememberScrollState()
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        if (controlsVisible) {
            Column(Modifier.fillMaxWidth().background(Color(0xCC202020))) {
                Row(
                    Modifier.fillMaxWidth()
                        .horizontalScroll(toolbarScrollState)
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FluentIconButton(onClick = onBack) {
                        Icon(Icons.Regular.ArrowLeft, contentDescription = stringResource(Res.string.back), tint = Color.White)
                    }
                    Text(stringResource(Res.string.reader_mode), color = Color.White)
                    ReaderModeButton(mode == ReaderMode.VERTICAL, Res.string.reader_vertical) {
                        mode = ReaderMode.VERTICAL
                    }
                    ReaderModeButton(mode == ReaderMode.PAGED_LEFT_TO_RIGHT, Res.string.reader_ltr) {
                        mode = ReaderMode.PAGED_LEFT_TO_RIGHT
                    }
                    ReaderModeButton(mode == ReaderMode.PAGED_RIGHT_TO_LEFT, Res.string.reader_rtl) {
                        mode = ReaderMode.PAGED_RIGHT_TO_LEFT
                    }
                    if (platformServices.isDesktop) {
                        ReaderModeButton(mode == ReaderMode.DOUBLE_LEFT_TO_RIGHT, Res.string.reader_double_ltr) {
                            mode = ReaderMode.DOUBLE_LEFT_TO_RIGHT
                        }
                        ReaderModeButton(mode == ReaderMode.DOUBLE_RIGHT_TO_LEFT, Res.string.reader_double_rtl) {
                            mode = ReaderMode.DOUBLE_RIGHT_TO_LEFT
                        }
                    }
                    ReaderModeButton(fit == PageFit.WIDTH, Res.string.fit_width) {
                        fit = PageFit.WIDTH
                    }
                    ReaderModeButton(fit == PageFit.HEIGHT, Res.string.fit_height) {
                        fit = PageFit.HEIGHT
                    }
                    FluentIconButton(onClick = { controlsVisible = false }) {
                        Icon(Icons.Regular.Maximize, contentDescription = stringResource(Res.string.fullscreen), tint = Color.White)
                    }
                }
                PlatformHorizontalScrollbar(toolbarScrollState)
            }
        }
        Box(Modifier.weight(1f)) {
            LoadStateContent(pagesState, onRetry = { refresh++ }) { pages ->
                when {
                    !progressLoaded -> FluentProgressRing(Modifier.align(Alignment.Center))
                    pages.isEmpty() -> EmptyContent()
                    mode == ReaderMode.VERTICAL -> VerticalReader(pages, initialPage, fit, savePage)
                    mode == ReaderMode.DOUBLE_LEFT_TO_RIGHT || mode == ReaderMode.DOUBLE_RIGHT_TO_LEFT ->
                        DoubleReader(pages, initialPage, mode == ReaderMode.DOUBLE_RIGHT_TO_LEFT, fit, savePage)
                    else -> PagedReader(pages, initialPage, mode == ReaderMode.PAGED_RIGHT_TO_LEFT, fit, savePage)
                }
            }
            if (!controlsVisible) {
                FluentIconButton(onClick = { controlsVisible = true }, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Regular.ArrowExpand, contentDescription = stringResource(Res.string.fullscreen), tint = Color.White)
                }
            }
        }
    }
}

private enum class PageFit {
    WIDTH,
    HEIGHT,
}

@Composable
private fun ReaderModeButton(selected: Boolean, label: org.jetbrains.compose.resources.StringResource, onClick: () -> Unit) {
    FluentChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(label)) },
        modifier = Modifier.padding(horizontal = 3.dp),
    )
}

@Composable
private fun VerticalReader(
    pages: List<PageRef>,
    initialPage: Int,
    fit: PageFit,
    onPageChanged: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(initialPage, pages.size) {
        if (pages.isNotEmpty()) listState.scrollToItem(initialPage.coerceIn(pages.indices))
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect(onPageChanged)
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(pages, key = { it.index }) { page ->
                ZoomablePage(page, Modifier.fillMaxWidth().heightIn(min = 480.dp), fit)
            }
        }
        PlatformVerticalScrollbar(listState, Modifier.align(Alignment.CenterEnd))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PagedReader(
    pages: List<PageRef>,
    initialPage: Int,
    reverse: Boolean,
    fit: PageFit,
    onPageChanged: (Int) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val pagerModifier = Modifier.fillMaxSize().mouseWheelPaging(pagerState)
    LaunchedEffect(initialPage, pages.size) {
        if (pages.isNotEmpty()) pagerState.scrollToPage(initialPage.coerceIn(pages.indices))
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect(onPageChanged)
    }
    HorizontalPager(
        state = pagerState,
        reverseLayout = reverse,
        modifier = pagerModifier,
    ) { index ->
        ZoomablePage(pages[index], Modifier.fillMaxSize(), fit)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DoubleReader(
    pages: List<PageRef>,
    initialPage: Int,
    reverse: Boolean,
    fit: PageFit,
    onPageChanged: (Int) -> Unit,
) {
    val pairs = remember(pages) { pages.chunked(2) }
    val pagerState = rememberPagerState(pageCount = { pairs.size })
    val pagerModifier = Modifier.fillMaxSize().mouseWheelPaging(pagerState)
    LaunchedEffect(initialPage, pairs.size) {
        if (pairs.isNotEmpty()) pagerState.scrollToPage((initialPage / 2).coerceIn(pairs.indices))
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect { onPageChanged(it * 2) }
    }
    HorizontalPager(state = pagerState, reverseLayout = reverse, modifier = pagerModifier) { index ->
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
            pairs[index].forEach { page ->
                ZoomablePage(page, Modifier.weight(1f).fillMaxHeight(), fit)
            }
        }
    }
}

@Composable
private fun ZoomablePage(page: PageRef, modifier: Modifier, fit: PageFit) {
    var scale by remember(page.index) { mutableFloatStateOf(1f) }
    Box(
        modifier = modifier.pointerInput(page.index) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } >= 2) {
                        scale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = page.localPath ?: page.imageUrl,
            contentDescription = page.originalName,
            modifier = Modifier.fillMaxSize().scale(scale),
            contentScale = if (fit == PageFit.WIDTH) ContentScale.FillWidth else ContentScale.Fit,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun Modifier.mouseWheelPaging(state: androidx.compose.foundation.pager.PagerState): Modifier {
    val scope = rememberCoroutineScope()
    var paging by remember(state) { mutableStateOf(false) }
    return pointerInput(state, scope) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll) continue

                val scroll = event.changes.firstOrNull()?.scrollDelta ?: continue
                val delta = if (abs(scroll.y) >= abs(scroll.x)) scroll.y else scroll.x
                if (delta != 0f) {
                    event.changes.forEach { it.consume() }
                    if (!paging && state.pageCount > 0) {
                        val direction = if (delta > 0f) 1 else -1
                        val target = (state.currentPage + direction).coerceIn(0, state.pageCount - 1)
                        if (target != state.currentPage) {
                            paging = true
                            scope.launch {
                                try {
                                    state.animateScrollToPage(target)
                                } finally {
                                    paging = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    library: LibraryRepository,
    platformServices: PlatformServices,
    onLogout: () -> Unit,
) {
    val settingsValue by library.settings().collectAsState(initial = top.ntutn.kica.model.AppSettings())
    val scope = rememberCoroutineScope()
    val downloadLocation by produceState("") {
        value = platformServices.fileLocationProvider.defaultDownloadLocation()
    }
    var proxyHost by remember(settingsValue.network.proxyHost) {
        mutableStateOf(settingsValue.network.proxyHost)
    }
    var proxyPort by remember(settingsValue.network.proxyPort) {
        mutableStateOf(settingsValue.network.proxyPort.takeIf { it > 0 }?.toString().orEmpty())
    }
    var exportLocation by remember { mutableStateOf<String?>(null) }
    val settingsScrollState = rememberScrollState()
    val proxyModeScrollState = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(settingsScrollState).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        SectionTitle(stringResource(Res.string.settings))
        SettingCard(stringResource(Res.string.theme)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemePreference.entries.forEach { preference ->
                    val label = when (preference) {
                        ThemePreference.SYSTEM -> Res.string.theme_system
                        ThemePreference.LIGHT -> Res.string.theme_light
                        ThemePreference.DARK -> Res.string.theme_dark
                    }
                    FluentChip(
                        selected = settingsValue.theme == preference,
                        onClick = { scope.launch { library.updateSettings(settingsValue.copy(theme = preference)) } },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        }
        SettingCard(stringResource(Res.string.network)) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(proxyModeScrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProxyMode.entries.forEach { mode ->
                        val label = when (mode) {
                            ProxyMode.DIRECT -> Res.string.proxy_direct
                            ProxyMode.SYSTEM -> Res.string.proxy_system
                            ProxyMode.HTTP -> Res.string.proxy_http
                            ProxyMode.SOCKS5 -> Res.string.proxy_socks5
                        }
                        FluentChip(
                            selected = settingsValue.network.proxyMode == mode,
                            onClick = {
                                scope.launch {
                                    library.updateSettings(
                                        settingsValue.copy(network = settingsValue.network.copy(proxyMode = mode)),
                                    )
                                }
                            },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
                PlatformHorizontalScrollbar(proxyModeScrollState)
            }
            if (settingsValue.network.proxyMode in setOf(ProxyMode.HTTP, ProxyMode.SOCKS5)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FluentTextField(
                        value = proxyHost,
                        onValueChange = { value ->
                            proxyHost = value
                            scope.launch {
                                library.updateSettings(
                                    settingsValue.copy(network = settingsValue.network.copy(proxyHost = value)),
                                )
                            }
                        },
                        label = { Text(stringResource(Res.string.proxy_host)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    FluentTextField(
                        value = proxyPort,
                        onValueChange = { value ->
                            if (value.all(Char::isDigit) && value.length <= 5) {
                                proxyPort = value
                                scope.launch {
                                    library.updateSettings(
                                        settingsValue.copy(
                                            network = settingsValue.network.copy(
                                                proxyPort = value.toIntOrNull() ?: 0,
                                            ),
                                        ),
                                    )
                                }
                            }
                        },
                        label = { Text(stringResource(Res.string.proxy_port)) },
                        singleLine = true,
                        modifier = Modifier.width(120.dp),
                    )
                }
            }
        }
        SettingCard(stringResource(Res.string.download_location)) {
            Text(downloadLocation.ifBlank { platformServices.platformName })
            if (!platformServices.isDesktop) {
                FluentButton(
                    onClick = {
                        scope.launch {
                            exportLocation = platformServices.fileLocationProvider.chooseExportLocation()
                        }
                    },
                ) {
                    Text(stringResource(Res.string.choose_export_location))
                }
                exportLocation?.let { Text(it, style = FluentTheme.typography.caption) }
            }
        }
        SettingCard(stringResource(Res.string.cache)) {
            FluentButton(onClick = { scope.launch { library.clearCache() } }) {
                Text(stringResource(Res.string.clear_cache))
            }
        }
        SettingCard(stringResource(Res.string.about)) {
            Text(stringResource(Res.string.about_text))
        }
        FluentButton(onClick = onLogout) {
            Text(stringResource(Res.string.logout))
        }
        }
        PlatformVerticalScrollbar(settingsScrollState, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    FluentCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = FluentTheme.typography.bodyStrong)
            content()
        }
    }
}

private fun ComicDetail.toSummary() = ComicSummary(
    id = id,
    title = title,
    author = author,
    coverUrl = coverUrl,
    categories = categories,
    finished = finished,
    likes = likes,
    views = views,
)

private fun progress(
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
