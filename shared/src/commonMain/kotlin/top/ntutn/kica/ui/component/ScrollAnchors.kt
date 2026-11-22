package top.ntutn.kica.ui.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.flow.first

/** A small key/index/offset snapshot, never a serialized list of loaded results. */
@Composable
internal fun PreserveGridAnchor(state: LazyGridState, keys: List<String>, ready: Boolean = true) {
    var itemKey by rememberSaveable { mutableStateOf<String?>(null) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var restored by remember(state) { mutableStateOf(false) }
    val initialPosition = remember(state) { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
    val currentKeys by rememberUpdatedState(keys)
    LaunchedEffect(state, keys, ready) {
        if (!restored && ready && keys.isNotEmpty()) {
            snapshotFlow { state.layoutInfo.totalItemsCount >= keys.size &&
                state.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
            // Respect the caller's current scroll. If temporary placeholders clamped a nonzero
            // initial LazyState to zero, replay that actual initial position, never a default zero.
            if (shouldRestoreAnchor(itemKey, initialPosition,
                    state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset, state.isScrollInProgress)) {
                state.scrollToItem(anchorIndex(itemKey, index, keys), offset.coerceAtLeast(0))
            } else if (shouldRestoreInitialPosition(itemKey, initialPosition,
                    state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset, state.isScrollInProgress)) {
                state.scrollToItem(initialPosition.first.coerceIn(0, keys.lastIndex), initialPosition.second.coerceAtLeast(0))
            }
            val first = state.firstVisibleItemIndex
            itemKey = keys.getOrNull(first)
            index = first
            offset = state.firstVisibleItemScrollOffset
            restored = true
        } else if (restored && ready && keys.isNotEmpty() && itemKey != null && itemKey !in keys && !state.isScrollInProgress) {
            state.scrollToItem(anchorIndex(itemKey, index, keys), offset.coerceAtLeast(0))
        }
    }
    LaunchedEffect(state) {
        snapshotFlow {
            val first = state.firstVisibleItemIndex
            Triple(state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == first }?.key as? String,
                first, state.firstVisibleItemScrollOffset)
        }.collect { (key, first, pixels) ->
            if (restored && key != null && key in currentKeys) { itemKey = key; index = first; offset = pixels }
        }
    }
}

@Composable
internal fun PreserveListAnchor(state: LazyListState, keys: List<String>, ready: Boolean = true) {
    var itemKey by rememberSaveable { mutableStateOf<String?>(null) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var restored by remember(state) { mutableStateOf(false) }
    val initialPosition = remember(state) { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
    val currentKeys by rememberUpdatedState(keys)
    LaunchedEffect(state, keys, ready) {
        if (!restored && ready && keys.isNotEmpty()) {
            snapshotFlow { state.layoutInfo.totalItemsCount >= keys.size &&
                state.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
            if (shouldRestoreAnchor(itemKey, initialPosition,
                    state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset, state.isScrollInProgress)) {
                state.scrollToItem(anchorIndex(itemKey, index, keys), offset.coerceAtLeast(0))
            } else if (shouldRestoreInitialPosition(itemKey, initialPosition,
                    state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset, state.isScrollInProgress)) {
                state.scrollToItem(initialPosition.first.coerceIn(0, keys.lastIndex), initialPosition.second.coerceAtLeast(0))
            }
            val first = state.firstVisibleItemIndex
            itemKey = keys.getOrNull(first)
            index = first
            offset = state.firstVisibleItemScrollOffset
            restored = true
        } else if (restored && ready && keys.isNotEmpty() && itemKey != null && itemKey !in keys && !state.isScrollInProgress) {
            state.scrollToItem(anchorIndex(itemKey, index, keys), offset.coerceAtLeast(0))
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { Triple(state.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index == state.firstVisibleItemIndex }?.key as? String,
            state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) }.collect { (key, first, pixels) ->
            if (restored && key != null && key in currentKeys) { itemKey = key; index = first; offset = pixels }
        }
    }
}

internal fun shouldRestoreAnchor(key: String?, initial: Pair<Int, Int>, current: Pair<Int, Int>, scrolling: Boolean): Boolean =
    key != null && !scrolling && (initial != (0 to 0) || initial == current)

/** Unknown-tag placeholders may clamp a nonzero LazyState before the real list is available. */
internal fun shouldRestoreInitialPosition(key: String?, initial: Pair<Int, Int>, current: Pair<Int, Int>, scrolling: Boolean): Boolean =
    key == null && initial != (0 to 0) && current == (0 to 0) && !scrolling

internal fun anchorIndex(key: String?, fallback: Int, keys: List<String>): Int {
    if (keys.isEmpty()) return 0
    val byKey = key?.let(keys::indexOf) ?: -1
    return if (byKey >= 0) byKey else fallback.coerceIn(0, keys.lastIndex)
}
