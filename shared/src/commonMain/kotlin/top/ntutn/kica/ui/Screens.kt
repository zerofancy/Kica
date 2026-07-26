package top.ntutn.kica.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.data.DownloadCoordinator
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.data.PicaRepository
import top.ntutn.kica.data.PlatformServices
import top.ntutn.kica.model.AppRoute
import top.ntutn.kica.model.ComicDetail
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
import top.ntutn.kica.resources.resume
import top.ntutn.kica.resources.search
import top.ntutn.kica.resources.search_failed
import top.ntutn.kica.resources.search_hint
import top.ntutn.kica.resources.settings
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
        Card(
            modifier = Modifier.width(400.dp).padding(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(Res.string.login_title), style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = emailValue,
                    onValueChange = { emailValue = it },
                    label = { Text(stringResource(Res.string.email)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = passwordValue,
                    onValueChange = { passwordValue = it },
                    label = { Text(stringResource(Res.string.password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
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
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
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
        AppRoute.Favorites -> FavoritesScreen(picaRepository) { onNavigate(AppRoute.Detail(it.id)) }
        AppRoute.History -> HistoryScreen(libraryRepository) { onNavigate(AppRoute.Detail(it.comic.id)) }
        AppRoute.Downloads -> DownloadsScreen(downloadCoordinator)
        AppRoute.Settings -> SettingsScreen(libraryRepository, platformServices, onLogout)
        AppRoute.Search -> SearchScreen(picaRepository, onBack) { onNavigate(AppRoute.Detail(it.id)) }
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

@Composable
private fun HomeScreen(repository: PicaRepository, onComicClick: (ComicSummary) -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val loadFailed = stringResource(Res.string.load_failed)
    val state by produceState<LoadState<Pair<List<ComicSummary>, List<ComicSummary>>>>(
        initialValue = LoadState.Loading,
        key1 = refresh,
    ) {
        value = runCatching { repository.recommendations() to repository.randomComics() }
            .fold({ LoadState.Data(it) }, { LoadState.Error(it.message ?: loadFailed) })
    }
    LoadStateContent(state, onRetry = { refresh++ }) { (recommendedItems, randomItems) ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item { SectionTitle(stringResource(Res.string.recommended)) }
            item { HorizontalComicRow(recommendedItems, onComicClick) }
            item { SectionTitle(stringResource(Res.string.random_comics)) }
            item { HorizontalComicRow(randomItems, onComicClick) }
        }
    }
}

@Composable
private fun HorizontalComicRow(comics: List<ComicSummary>, onComicClick: (ComicSummary) -> Unit) {
    if (comics.isEmpty()) {
        Text(stringResource(Res.string.empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(comics, key = { it.id }) { comic ->
            Box(Modifier.width(160.dp)) { ComicCard(comic, onComicClick) }
        }
    }
}

@Composable
private fun DiscoverScreen(repository: PicaRepository, onNavigate: (AppRoute) -> Unit) {
    var selected by remember { mutableStateOf(RankPeriod.HOURS_24) }
    var refresh by remember { mutableIntStateOf(0) }
    val loadFailed = stringResource(Res.string.load_failed)
    val categoriesState by produceState<LoadState<List<String>>>(LoadState.Loading, refresh) {
        value = runCatching { repository.categories() }
            .fold({ LoadState.Data(it) }, { LoadState.Error(it.message ?: loadFailed) })
    }
    val rankingState by produceState<LoadState<List<ComicSummary>>>(LoadState.Loading, selected, refresh) {
        value = runCatching { repository.ranking(selected) }
            .fold({ LoadState.Data(it) }, { LoadState.Error(it.message ?: loadFailed) })
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        SectionTitle(stringResource(Res.string.discover)) {
            IconButton(onClick = { onNavigate(AppRoute.Search) }) {
                Icon(Icons.Rounded.Search, contentDescription = stringResource(Res.string.search))
            }
        }
        Text(stringResource(Res.string.categories), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        when (val categoryValue = categoriesState) {
            is LoadState.Data -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categoryValue.value) { category ->
                    FilterChip(selected = false, onClick = { onNavigate(AppRoute.Search) }, label = { Text(category) })
                }
            }
            is LoadState.Error -> Text(categoryValue.message, color = MaterialTheme.colorScheme.error)
            else -> LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(Res.string.ranking), style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(RankPeriod.entries) { period ->
                val label = when (period) {
                    RankPeriod.HOURS_24 -> Res.string.rank_24h
                    RankPeriod.DAYS_7 -> Res.string.rank_7d
                    RankPeriod.DAYS_30 -> Res.string.rank_30d
                    RankPeriod.KNIGHT -> Res.string.rank_knight
                }
                FilterChip(
                    selected = selected == period,
                    onClick = { selected = period },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f)) {
            LoadStateContent(rankingState, onRetry = { refresh++ }) {
                ComicGrid(it, { comic -> onNavigate(AppRoute.Detail(comic.id)) })
            }
        }
    }
}

@Composable
private fun SearchScreen(
    repository: PicaRepository,
    onBack: () -> Unit,
    onComicClick: (ComicSummary) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<LoadState<List<ComicSummary>>>(LoadState.Idle) }
    val searchFailed = stringResource(Res.string.search_failed)
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(Res.string.search_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    submitted = query.trim()
                    if (submitted.isNotEmpty()) {
                        state = LoadState.Loading
                        scope.launch {
                            state = runCatching { repository.search(submitted) }
                                .fold({ LoadState.Data(it) }, { LoadState.Error(it.message ?: searchFailed) })
                        }
                    }
                },
            ) { Text(stringResource(Res.string.search)) }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.weight(1f)) {
            when (val value = state) {
                LoadState.Idle -> EmptyContent()
                else -> LoadStateContent(value) { ComicGrid(it, onComicClick) }
            }
        }
    }
}

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
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        SectionTitle(stringResource(Res.string.history))
        if (historyItems.isEmpty()) {
            EmptyContent(Modifier.weight(1f))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(historyItems, key = { it.comic.id }) { entry ->
                    Card(Modifier.fillMaxWidth().clickable { onClick(entry) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = entry.comic.coverUrl,
                                contentDescription = entry.comic.title,
                                modifier = Modifier.size(64.dp),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(entry.comic.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${entry.episodeTitle} · ${entry.pageIndex + 1}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsScreen(coordinator: DownloadCoordinator) {
    val tasks by coordinator.tasks.collectAsState()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        SectionTitle(stringResource(Res.string.downloads))
        if (tasks.isEmpty()) {
            EmptyContent(Modifier.weight(1f))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tasks, key = { it.id }) { task ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(task.comic.title, style = MaterialTheme.typography.titleMedium)
                            Text(task.episode.title, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = {
                                    if (task.totalPages <= 0) 0f
                                    else task.completedPages.toFloat() / task.totalPages
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                when (task.status) {
                                    DownloadStatus.RUNNING, DownloadStatus.QUEUED ->
                                        TextButton(onClick = { scope.launch { coordinator.pause(task.id) } }) {
                                            Text(stringResource(Res.string.pause))
                                        }
                                    DownloadStatus.PAUSED, DownloadStatus.FAILED ->
                                        TextButton(onClick = { scope.launch { coordinator.resume(task.id) } }) {
                                            Text(stringResource(Res.string.resume))
                                        }
                                    else -> Unit
                                }
                                if (task.status != DownloadStatus.COMPLETED) {
                                    TextButton(onClick = { scope.launch { coordinator.cancel(task.id) } }) {
                                        Text(stringResource(Res.string.cancel))
                                    }
                                }
                            }
                        }
                    }
                }
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

    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
                }
            }
        },
    ) { padding ->
        LoadStateContent(state, Modifier.padding(padding), onRetry = { refresh++ }) { (comic, episodeItems) ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
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
                        Text(comic.title, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(8.dp))
                        Text("${stringResource(Res.string.author)}：${comic.author}")
                        Text(stringResource(if (comic.finished) Res.string.finished else Res.string.ongoing))
                        Spacer(Modifier.height(12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            comic.categories.forEach { FilterChip(false, {}, { Text(it) }) }
                            comic.tags.take(8).forEach { FilterChip(false, {}, { Text(it) }) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { scope.launch { repository.toggleFavorite(comic.id) } }) {
                                Icon(
                                    if (comic.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = null,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(if (comic.isFavorite) Res.string.unfavorite else Res.string.favorite))
                            }
                            OutlinedButton(onClick = { scope.launch { repository.like(comic.id) } }) {
                                Icon(Icons.Rounded.ThumbUp, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(Res.string.like))
                            }
                        }
                    }
                }
                Text(comic.description.ifBlank { stringResource(Res.string.no_description) })
                SectionTitle(stringResource(Res.string.episodes))
                episodeItems.forEach { episode ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(episode.title, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onRead(episode.id) }) {
                                Text(stringResource(Res.string.read))
                            }
                            IconButton(
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
                                Icon(Icons.Rounded.Download, contentDescription = stringResource(Res.string.download))
                            }
                        }
                    }
                }
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

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        if (controlsVisible) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Color(0xCC202020))
                    .horizontalScroll(rememberScrollState())
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back), tint = Color.White)
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
                IconButton(onClick = { controlsVisible = false }) {
                    Icon(Icons.Rounded.Fullscreen, contentDescription = stringResource(Res.string.fullscreen), tint = Color.White)
                }
            }
        }
        Box(Modifier.weight(1f)) {
            LoadStateContent(pagesState, onRetry = { refresh++ }) { pages ->
                when {
                    !progressLoaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    pages.isEmpty() -> EmptyContent()
                    mode == ReaderMode.VERTICAL -> VerticalReader(pages, initialPage, fit, savePage)
                    mode == ReaderMode.DOUBLE_LEFT_TO_RIGHT || mode == ReaderMode.DOUBLE_RIGHT_TO_LEFT ->
                        DoubleReader(pages, initialPage, mode == ReaderMode.DOUBLE_RIGHT_TO_LEFT, fit, savePage)
                    else -> PagedReader(pages, initialPage, mode == ReaderMode.PAGED_RIGHT_TO_LEFT, fit, savePage)
                }
            }
            if (!controlsVisible) {
                IconButton(onClick = { controlsVisible = true }, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Rounded.FullscreenExit, contentDescription = stringResource(Res.string.fullscreen), tint = Color.White)
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
    FilterChip(
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
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(pages, key = { it.index }) { page ->
            ZoomablePage(page, Modifier.fillMaxWidth().heightIn(min = 480.dp), fit)
        }
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
    LaunchedEffect(initialPage, pages.size) {
        if (pages.isNotEmpty()) pagerState.scrollToPage(initialPage.coerceIn(pages.indices))
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect(onPageChanged)
    }
    HorizontalPager(
        state = pagerState,
        reverseLayout = reverse,
        modifier = Modifier.fillMaxSize(),
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
    LaunchedEffect(initialPage, pairs.size) {
        if (pairs.isNotEmpty()) pagerState.scrollToPage((initialPage / 2).coerceIn(pairs.indices))
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect { onPageChanged(it * 2) }
    }
    HorizontalPager(state = pagerState, reverseLayout = reverse, modifier = Modifier.fillMaxSize()) { index ->
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
            detectTransformGestures { _, _, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 4f)
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
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
                    FilterChip(
                        selected = settingsValue.theme == preference,
                        onClick = { scope.launch { library.updateSettings(settingsValue.copy(theme = preference)) } },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        }
        SettingCard(stringResource(Res.string.network)) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProxyMode.entries.forEach { mode ->
                    val label = when (mode) {
                        ProxyMode.DIRECT -> Res.string.proxy_direct
                        ProxyMode.SYSTEM -> Res.string.proxy_system
                        ProxyMode.HTTP -> Res.string.proxy_http
                        ProxyMode.SOCKS5 -> Res.string.proxy_socks5
                    }
                    FilterChip(
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
            if (settingsValue.network.proxyMode in setOf(ProxyMode.HTTP, ProxyMode.SOCKS5)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
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
                    OutlinedTextField(
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
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            exportLocation = platformServices.fileLocationProvider.chooseExportLocation()
                        }
                    },
                ) {
                    Text(stringResource(Res.string.choose_export_location))
                }
                exportLocation?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        SettingCard(stringResource(Res.string.cache)) {
            OutlinedButton(onClick = { scope.launch { library.clearCache() } }) {
                Text(stringResource(Res.string.clear_cache))
            }
        }
        SettingCard(stringResource(Res.string.about)) {
            Text(stringResource(Res.string.about_text))
        }
        OutlinedButton(onClick = onLogout) {
            Text(stringResource(Res.string.logout))
        }
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
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
