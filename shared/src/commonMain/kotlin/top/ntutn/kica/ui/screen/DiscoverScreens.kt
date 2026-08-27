package top.ntutn.kica.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Icon
import io.github.composefluent.component.Text
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.ArrowSync
import io.github.composefluent.icons.regular.Search
import io.github.composefluent.icons.regular.Settings
import io.github.composefluent.icons.regular.Tag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.data.PicaRepository
import top.ntutn.kica.model.AppRoute
import top.ntutn.kica.model.AppSettings
import top.ntutn.kica.model.ComicCategory
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.LoadState
import top.ntutn.kica.model.RankPeriod
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.categories
import top.ntutn.kica.resources.category_filter
import top.ntutn.kica.resources.category_filter_clear
import top.ntutn.kica.resources.category_filter_description
import top.ntutn.kica.resources.category_filter_done
import top.ntutn.kica.resources.category_filter_select_all
import top.ntutn.kica.resources.discover
import top.ntutn.kica.resources.empty
import top.ntutn.kica.resources.load_failed
import top.ntutn.kica.resources.random_books
import top.ntutn.kica.resources.rank_24h
import top.ntutn.kica.resources.rank_30d
import top.ntutn.kica.resources.rank_7d
import top.ntutn.kica.resources.rank_knight
import top.ntutn.kica.resources.ranking
import top.ntutn.kica.resources.search
import top.ntutn.kica.ui.filterBlockedCategories
import top.ntutn.kica.ui.filterBlockedSummaries
import top.ntutn.kica.ui.PlatformBackHandler
import top.ntutn.kica.ui.PlatformVerticalScrollbar
import top.ntutn.kica.ui.component.ComicCard
import top.ntutn.kica.ui.component.ErrorCard
import top.ntutn.kica.ui.component.FluentCard
import top.ntutn.kica.ui.component.FluentChip
import top.ntutn.kica.ui.component.FluentIconButton
import top.ntutn.kica.ui.component.FluentPrimaryButton
import top.ntutn.kica.ui.component.FluentProgressBar
import top.ntutn.kica.ui.component.FluentTextButton
import top.ntutn.kica.ui.component.SectionTitle
import androidx.compose.runtime.collectAsState

@Composable
internal fun DiscoverScreen(
    repository: PicaRepository,
    library: LibraryRepository,
    onNavigate: (AppRoute) -> Unit,
) {
    var selected by remember { mutableStateOf(RankPeriod.HOURS_24) }
    var refresh by remember { mutableIntStateOf(0) }
    var showCategoryFilter by remember { mutableStateOf(false) }
    val loadFailed = stringResource(Res.string.load_failed)
    val settings by library.settings().collectAsState(initial = AppSettings())
    val blocked = settings.blockedCategories
    val categoriesState by produceState<LoadState<List<ComicCategory>>>(LoadState.Loading, refresh) {
        val cached = runCatching { library.cachedCategories() }.getOrNull()
        if (cached != null) {
            value = LoadState.Data(cached, fromCache = true)
        }
        runCatching { repository.categories() }
            .onSuccess { categories ->
                value = LoadState.Data(categories)
                runCatching { library.cacheCategories(categories) }
            }
            .onFailure { error ->
                if (cached == null) {
                    value = LoadState.Error(error.message ?: loadFailed)
                }
            }
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
                SectionTitle(stringResource(Res.string.categories)) {
                    FluentIconButton(onClick = { showCategoryFilter = true }) {
                        Icon(
                            imageVector = Icons.Regular.Settings,
                            contentDescription = stringResource(Res.string.category_filter),
                        )
                    }
                }
            }
            item(key = "random-comics-entry") {
                RandomComicsEntryCard(onClick = { onNavigate(AppRoute.RandomComics) })
            }
            when (val categoryValue = categoriesState) {
                is LoadState.Data -> {
                    val visible = categoryValue.value.filterBlockedCategories(blocked)
                    if (visible.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                stringResource(Res.string.empty),
                                color = FluentTheme.colors.text.text.secondary,
                            )
                        }
                    } else {
                        gridItems(
                            items = visible,
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
                    val visible = rankingValue.value.filterBlockedSummaries(blocked)
                    if (visible.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                stringResource(Res.string.empty),
                                color = FluentTheme.colors.text.text.secondary,
                            )
                        }
                    } else {
                        gridItems(visible, key = { "ranking:${it.id}" }) { comic ->
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

    if (showCategoryFilter) {
        CategoryFilterDialog(
            allCategories = (categoriesState as? LoadState.Data)?.value.orEmpty(),
            library = library,
            onDismiss = { showCategoryFilter = false },
        )
    }
}

@Composable
private fun CategoryFilterDialog(
    allCategories: List<ComicCategory>,
    library: LibraryRepository,
    onDismiss: () -> Unit,
) {
    val settingsSnapshot by library.settings().collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val empty = stringResource(Res.string.empty)
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = remember(settingsSnapshot.theme, systemDark) {
        when (settingsSnapshot.theme) {
            top.ntutn.kica.model.ThemePreference.LIGHT -> false
            top.ntutn.kica.model.ThemePreference.DARK -> true
            top.ntutn.kica.model.ThemePreference.SYSTEM -> systemDark
        }
    }
    val panelBackground = if (useDarkTheme) Color(0xFF202020) else Color(0xFFFAFAFA)
    val panelBorder = if (useDarkTheme) Color(0xFF4A4A4A) else Color(0xFFC8C8C8)
    Dialog(onDismissRequest = onDismiss) {
        PlatformBackHandler(enabled = true, onBack = onDismiss)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .background(
                    color = panelBackground,
                    shape = RoundedCornerShape(8.dp),
                )
                .border(
                    width = 1.dp,
                    color = panelBorder,
                    shape = RoundedCornerShape(8.dp),
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(Res.string.category_filter), style = FluentTheme.typography.subtitle)
                Text(
                    stringResource(Res.string.category_filter_description),
                    style = FluentTheme.typography.caption,
                    color = FluentTheme.colors.text.text.secondary,
                )
                val updateBlocked: (Set<String>) -> Unit = { next ->
                    scope.launch {
                        val latest = library.settings().first()
                        library.updateSettings(latest.copy(blockedCategories = next))
                    }
                }
                val scrollState = rememberScrollState()
                Box(Modifier.fillMaxWidth().height(360.dp)) {
                    if (allCategories.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(empty, color = FluentTheme.colors.text.text.secondary)
                        }
                    } else {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(end = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            allCategories.forEach { category ->
                                val selected = category.title in settingsSnapshot.blockedCategories
                                FluentChip(
                                    selected = selected,
                                    onClick = {
                                        val next = settingsSnapshot.blockedCategories.toMutableSet()
                                        if (selected) next.remove(category.title)
                                        else next.add(category.title)
                                        updateBlocked(next)
                                    },
                                    label = { Text(category.title) },
                                )
                            }
                        }
                        PlatformVerticalScrollbar(
                            state = scrollState,
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FluentTextButton(
                        onClick = {
                            updateBlocked(allCategories.map { it.title }.toSet())
                        },
                    ) { Text(stringResource(Res.string.category_filter_select_all)) }
                    FluentTextButton(
                        onClick = { updateBlocked(emptySet()) },
                    ) { Text(stringResource(Res.string.category_filter_clear)) }
                    Spacer(Modifier.weight(1f))
                    FluentPrimaryButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.category_filter_done))
                    }
                }
            }
        }
    }
}

@Composable
internal fun RandomComicsEntryCard(onClick: () -> Unit) {
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
internal fun CategoryCoverCard(
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
