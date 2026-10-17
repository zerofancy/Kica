package top.ntutn.kica.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.mouseWheelPaging(
    state: androidx.compose.foundation.pager.PagerState,
    onOverflowForward: () -> Unit = {},
    onOverflowBackward: () -> Unit = {},
): Modifier {
    val scope = rememberCoroutineScope()
    var paging by remember(state) { mutableStateOf(false) }
    var overflowFired by remember(state) { mutableStateOf(false) }
    val forwardState = rememberUpdatedState(onOverflowForward)
    val backwardState = rememberUpdatedState(onOverflowBackward)
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
                        } else if (!overflowFired) {
                            overflowFired = true
                            scope.launch {
                                delay(OVERFLOW_COOLDOWN_MS)
                                overflowFired = false
                            }
                            if (direction > 0) forwardState.value() else backwardState.value()
                        }
                    }
                }
            }
        }
    }
}

private const val OVERFLOW_COOLDOWN_MS = 500L

internal const val CHAPTER_OVERFLOW_THRESHOLD_DP = 40

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.touchChapterOverflow(
    atStart: () -> Boolean,
    atEnd: () -> Boolean,
    isHorizontal: Boolean,
    forwardSign: Int,
    onNext: () -> Unit,
    onPrev: () -> Unit,
): Modifier {
    val threshold = with(LocalDensity.current) { CHAPTER_OVERFLOW_THRESHOLD_DP.dp.toPx() }
    val atStartState = rememberUpdatedState(atStart)
    val atEndState = rememberUpdatedState(atEnd)
    val onNextState = rememberUpdatedState(onNext)
    val onPrevState = rememberUpdatedState(onPrev)
    return pointerInput(threshold, isHorizontal, forwardSign) {
        awaitPointerEventScope {
            var total = 0f
            var fired = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressedCount = event.changes.count { it.pressed }
                if (pressedCount != 1) {
                    // no drag, release, or multi-touch (zoom) -> reset
                    total = 0f
                    fired = false
                    continue
                }
                val change = event.changes.first { it.pressed }
                val delta = if (isHorizontal) {
                    change.position.x - change.previousPosition.x
                } else {
                    change.position.y - change.previousPosition.y
                }
                total += delta
                if (!fired) {
                    val projected = total * forwardSign
                    if (projected > threshold && atEndState.value()) {
                        onNextState.value()
                        fired = true
                    } else if (projected < -threshold && atStartState.value()) {
                        onPrevState.value()
                        fired = true
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.wheelChapterOverflow(
    atStart: () -> Boolean,
    atEnd: () -> Boolean,
    onNext: () -> Unit,
    onPrev: () -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    var overflowFired by remember { mutableStateOf(false) }
    val atStartState = rememberUpdatedState(atStart)
    val atEndState = rememberUpdatedState(atEnd)
    val onNextState = rememberUpdatedState(onNext)
    val onPrevState = rememberUpdatedState(onPrev)
    return pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll) continue
                val scroll = event.changes.firstOrNull()?.scrollDelta ?: continue
                if (scroll.y == 0f || overflowFired) continue
                if (scroll.y > 0f && atEndState.value()) {
                    overflowFired = true
                    scope.launch {
                        delay(OVERFLOW_COOLDOWN_MS)
                        overflowFired = false
                    }
                    onNextState.value()
                } else if (scroll.y < 0f && atStartState.value()) {
                    overflowFired = true
                    scope.launch {
                        delay(OVERFLOW_COOLDOWN_MS)
                        overflowFired = false
                    }
                    onPrevState.value()
                }
            }
        }
    }
}
