package top.ntutn.kica.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.ntutn.kica.data.sha256
import top.ntutn.kica.resources.Res
import top.ntutn.kica.resources.cancel
import top.ntutn.kica.resources.lock_draw_pattern
import top.ntutn.kica.resources.lock_pattern_again
import top.ntutn.kica.resources.lock_pattern_incorrect
import top.ntutn.kica.resources.lock_pattern_mismatch
import top.ntutn.kica.resources.lock_set_pattern
import kotlin.time.Duration.Companion.milliseconds

private const val GRID_SIZE = 3
private const val DOT_COUNT = GRID_SIZE * GRID_SIZE

private fun hitTestDot(point: Offset, centers: List<Offset>, radius: Float): Int? {
    for (i in centers.indices) {
        val c = centers[i]
        val dx = point.x - c.x
        val dy = point.y - c.y
        if (dx * dx + dy * dy <= radius * radius) return i
    }
    return null
}

@Composable
private fun PatternLockGrid(
    errorTrigger: Int,
    onPatternComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val dotRadiusPx = with(density) { 14.dp.toPx() }
    val lineWidthPx = with(density) { 3.dp.toPx() }
    val hitRadiusPx = with(density) { 26.dp.toPx() }

    var pattern by remember { mutableStateOf<List<Int>>(emptyList()) }
    var hoverDot by remember { mutableStateOf<Int?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var errorVisible by remember { mutableStateOf(false) }
    var lastErrorTrigger by remember { mutableIntStateOf(0) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    fun reset() {
        pattern = emptyList()
        hoverDot = null
        isDragging = false
        errorVisible = false
    }

    LaunchedEffect(errorTrigger) {
        if (errorTrigger > 0 && errorTrigger != lastErrorTrigger) {
            lastErrorTrigger = errorTrigger
            errorVisible = true
            delay(800.milliseconds)
            reset()
        }
    }

    val dotCenters = remember(size) {
        if (size == IntSize.Zero) {
            List(DOT_COUNT) { Offset.Zero }
        } else {
            val cellSize = minOf(size.width, size.height).toFloat() / GRID_SIZE
            val gridSize = cellSize * GRID_SIZE
            val gridStartX = (size.width - gridSize) / 2f
            val gridStartY = (size.height - gridSize) / 2f
            List(DOT_COUNT) { i ->
                val col = i % GRID_SIZE
                val row = i / GRID_SIZE
                Offset(
                    gridStartX + (col + 0.5f) * cellSize,
                    gridStartY + (row + 0.5f) * cellSize,
                )
            }
        }
    }

    val activeColor = if (errorVisible) {
        FluentTheme.colors.system.critical
    } else {
        FluentTheme.colors.fillAccent.default
    }
    val inactiveColor = FluentTheme.colors.text.text.secondary
    val bgColor = FluentTheme.colors.system.neutralBackground

    Canvas(
        modifier = modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (errorVisible) return@detectDragGestures
                        isDragging = true
                        val dot = hitTestDot(offset, dotCenters, hitRadiusPx)
                        if (dot != null && dot !in pattern) {
                            pattern = pattern + dot
                        }
                    },
                    onDrag = { change, _ ->
                        if (errorVisible) return@detectDragGestures
                        isDragging = true
                        val dot = hitTestDot(change.position, dotCenters, hitRadiusPx)
                        hoverDot = dot
                        if (dot != null && dot !in pattern) {
                            pattern = pattern + dot
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        hoverDot = null
                        if (pattern.size >= 4) {
                            onPatternComplete(pattern.joinToString(""))
                            scope.launch {
                                delay(300.milliseconds)
                                if (!errorVisible) reset()
                            }
                        } else {
                            reset()
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        hoverDot = null
                        reset()
                    },
                )
            },
    ) {
        val lineColor = activeColor
        val dotRadius = dotRadiusPx

        if (pattern.size >= 2) {
            for (i in 1 until pattern.size) {
                drawLine(
                    color = lineColor,
                    start = dotCenters[pattern[i - 1]],
                    end = dotCenters[pattern[i]],
                    strokeWidth = lineWidthPx,
                    cap = StrokeCap.Round,
                )
            }
        }

        if (isDragging && pattern.isNotEmpty()) {
            val lastCenter = dotCenters[pattern.last()]
            val target = hoverDot?.let { dotCenters[it] } ?: lastCenter
            drawLine(
                color = lineColor.copy(alpha = 0.5f),
                start = lastCenter,
                end = target,
                strokeWidth = lineWidthPx,
                cap = StrokeCap.Round,
            )
        }

        for (i in 0 until DOT_COUNT) {
            val center = dotCenters[i]
            if (i in pattern) {
                drawCircle(color = activeColor, radius = dotRadius, center = center)
                drawCircle(color = bgColor, radius = dotRadius * 0.35f, center = center)
            } else {
                drawCircle(
                    color = inactiveColor,
                    radius = dotRadius,
                    center = center,
                    style = Stroke(width = lineWidthPx),
                )
            }
        }
    }
}

@Composable
internal fun PatternLockScreen(
    patternHash: String,
    onUnlock: () -> Unit,
) {
    var errorCount by remember { mutableIntStateOf(0) }
    val incorrectMsg = stringResource(Res.string.lock_pattern_incorrect)
    val drawPatternMsg = stringResource(Res.string.lock_draw_pattern)

    PlatformBackHandler(enabled = true, onBack = {})

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FluentTheme.colors.system.neutralBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Kica",
                style = FluentTheme.typography.title,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                drawPatternMsg,
                style = FluentTheme.typography.caption,
                color = FluentTheme.colors.text.text.secondary,
            )
            PatternLockGrid(
                errorTrigger = errorCount,
                onPatternComplete = { pattern ->
                    if (sha256(pattern) == patternHash) {
                        onUnlock()
                    } else {
                        errorCount++
                    }
                },
                modifier = Modifier.size(280.dp),
            )
            if (errorCount > 0) {
                Text(
                    incorrectMsg,
                    color = FluentTheme.colors.system.critical,
                    style = FluentTheme.typography.caption,
                )
            }
        }
    }
}

@Composable
internal fun PatternLockSettingsDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var firstPattern by remember { mutableStateOf<String?>(null) }
    var errorCount by remember { mutableIntStateOf(0) }
    val againMsg = stringResource(Res.string.lock_pattern_again)
    val mismatchMsg = stringResource(Res.string.lock_pattern_mismatch)
    val setPatternTitle = stringResource(Res.string.lock_set_pattern)
    val drawPatternMsg = stringResource(Res.string.lock_draw_pattern)

    PlatformBackHandler(enabled = true, onBack = onDismiss)

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        FluentCard(Modifier.width(340.dp)) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (firstPattern == null) setPatternTitle else againMsg,
                    style = FluentTheme.typography.subtitle,
                )
                Text(
                    drawPatternMsg,
                    style = FluentTheme.typography.caption,
                    color = FluentTheme.colors.text.text.secondary,
                )
                PatternLockGrid(
                    errorTrigger = errorCount,
                    onPatternComplete = { pattern ->
                        if (firstPattern == null) {
                            firstPattern = pattern
                        } else {
                            if (pattern == firstPattern) {
                                onConfirm(pattern)
                            } else {
                                firstPattern = null
                                errorCount++
                            }
                        }
                    },
                    modifier = Modifier.size(260.dp),
                )
                if (errorCount > 0) {
                    Text(
                        mismatchMsg,
                        color = FluentTheme.colors.system.critical,
                        style = FluentTheme.typography.caption,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FluentTextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.cancel))
                    }
                }
            }
        }
    }
}