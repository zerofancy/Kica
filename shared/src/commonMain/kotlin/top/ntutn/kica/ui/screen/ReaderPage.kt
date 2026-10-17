package top.ntutn.kica.ui.screen

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged
import top.ntutn.kica.model.PageRef

internal enum class PageFit {
    WIDTH,
    HEIGHT,
}

internal const val PRELOAD_AHEAD = 3
internal const val PRELOAD_BEHIND = 1

@Composable
internal fun ZoomablePage(
    page: PageRef,
    modifier: Modifier,
    fit: PageFit,
    onImageMenu: (PageRef, IntOffset, IntOffset) -> Unit,
) {
    var scale by remember(page.index) { mutableFloatStateOf(1f) }
    var pageWindowOrigin by remember(page.index) { mutableStateOf(IntOffset.Zero) }
    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                pageWindowOrigin = IntOffset(pos.x.toInt(), pos.y.toInt())
            }
            .pointerInput(page.index) {
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
            }
            .imageContextMenuGesture { local ->
                onImageMenu(page, pageWindowOrigin, IntOffset(local.x.toInt(), local.y.toInt()))
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
internal expect fun Modifier.imageContextMenuGesture(onTriggered: (androidx.compose.ui.geometry.Offset) -> Unit): Modifier

@Composable
internal fun PagePreloader(pages: List<PageRef>, currentIndexProvider: () -> Int, fit: PageFit) {
    val platformContext = LocalPlatformContext.current
    val imageLoader = remember(platformContext) { SingletonImageLoader.get(platformContext) }
    val currentSnapshot = remember { mutableIntStateOf(currentIndexProvider()) }
    LaunchedEffect(pages) {
        snapshotFlow { currentIndexProvider() }
            .distinctUntilChanged()
            .collect { currentSnapshot.intValue = it }
    }
    val preloadTargets = remember(pages, currentSnapshot.intValue, fit) {
        val cur = currentSnapshot.intValue.coerceIn(pages.indices)
        val lo = (cur - PRELOAD_BEHIND).coerceAtLeast(0)
        val hi = (cur + PRELOAD_AHEAD).coerceAtMost(pages.lastIndex)
        pages.subList(lo, hi + 1)
    }
    LaunchedEffect(preloadTargets, fit, imageLoader, platformContext) {
        for (page in preloadTargets) {
            val target = page.localPath ?: page.imageUrl
            val req = ImageRequest.Builder(platformContext)
                .data(target)
                .build()
            imageLoader.enqueue(req)
        }
    }
}
