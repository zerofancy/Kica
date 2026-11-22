package top.ntutn.kica.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Icon
import io.github.composefluent.component.Text
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.filled.Heart as FilledHeart
import io.github.composefluent.icons.filled.Star as FilledStar
import io.github.composefluent.icons.regular.ArrowDownload
import io.github.composefluent.icons.regular.ArrowLeft
import io.github.composefluent.icons.regular.Heart
import io.github.composefluent.icons.regular.Star
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.data.PlatformServices
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.author
import top.ntutn.kica.resources.back
import top.ntutn.kica.resources.categories
import top.ntutn.kica.resources.download
import top.ntutn.kica.resources.episodes
import top.ntutn.kica.resources.favorite
import top.ntutn.kica.resources.finished
import top.ntutn.kica.resources.like
import top.ntutn.kica.resources.no_description
import top.ntutn.kica.resources.ongoing
import top.ntutn.kica.resources.read
import top.ntutn.kica.resources.unfavorite
import top.ntutn.kica.resources.unlike
import top.ntutn.kica.ui.component.FluentButton
import top.ntutn.kica.ui.component.FluentCard
import top.ntutn.kica.ui.component.FluentChip
import top.ntutn.kica.ui.component.FluentIconButton
import top.ntutn.kica.ui.component.FluentScaffold
import top.ntutn.kica.ui.component.SectionTitle
import top.ntutn.kica.ui.state.translatedTitle
import top.ntutn.kica.ui.component.LoadStateContent
import top.ntutn.kica.ui.PlatformVerticalScrollbar

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DetailScreen(
    store: top.ntutn.kica.ui.store.DetailStore,
    platformServices: PlatformServices,
    library: top.ntutn.kica.data.LibraryRepository,
    onTagSearch: (String) -> Unit,
    onBack: () -> Unit,
    onRead: (String) -> Unit,
) {
    val state by store.state.collectAsState()
    val favoriteBusy by store.favoriteBusy.collectAsState()
    val likeBusy by store.likeBusy.collectAsState()
    FluentScaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FluentIconButton(onClick = onBack) {
                    Icon(Icons.Regular.ArrowLeft, contentDescription = stringResource(Res.string.back))
                }
            }
        },
    ) { padding ->
        LoadStateContent(state, Modifier.padding(padding), onRetry = { store.refresh() }) { (comic, episodeItems) ->
            val scrollState = rememberScrollState()
            val displayTitle = translatedTitle(comic.title)
            val isFavorite = comic.isFavorite
            val isLiked = comic.isLiked
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(scrollState).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        // Leave enough room beside the 180 dp cover for both action labels.
                        val compact = maxWidth < 600.dp
                        val tagsAndActions: @Composable () -> Unit = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(Res.string.categories))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    comic.categories.forEach { FluentChip(false, {}, { Text(it) }) }
                                }
                                ComicTags(comic.id, comic.tags, library, platformServices, onTagSearch)
                                Row(
                                    modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FluentButton(
                                        onClick = store::favorite,
                                        enabled = !favoriteBusy,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(
                                            if (isFavorite) Icons.Filled.FilledStar else Icons.Regular.Star,
                                            contentDescription = null,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            stringResource(if (isFavorite) Res.string.unfavorite else Res.string.favorite),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    FluentButton(
                                        onClick = store::like,
                                        enabled = !likeBusy,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(
                                            if (isLiked) Icons.Filled.FilledHeart else Icons.Regular.Heart,
                                            contentDescription = null,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            stringResource(if (isLiked) Res.string.unlike else Res.string.like),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                AsyncImage(
                                    model = comic.coverUrl,
                                    contentDescription = displayTitle,
                                    modifier = Modifier.width(180.dp).aspectRatio(0.72f),
                                    contentScale = ContentScale.Crop,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(displayTitle, style = FluentTheme.typography.title)
                                    if (displayTitle != comic.title) {
                                        Text(
                                            comic.title,
                                            style = FluentTheme.typography.caption,
                                            color = FluentTheme.colors.text.text.secondary,
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text("${stringResource(Res.string.author)}：${comic.author}")
                                    Text(stringResource(if (comic.finished) Res.string.finished else Res.string.ongoing))

                                    if (!compact) {
                                        Spacer(Modifier.height(12.dp))
                                        tagsAndActions()
                                    }
                                }
                            }
                            if (compact) {
                                tagsAndActions()
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
                                    onClick = { store.download(episode) },
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
