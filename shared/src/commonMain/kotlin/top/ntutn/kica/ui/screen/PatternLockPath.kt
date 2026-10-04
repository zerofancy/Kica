package top.ntutn.kica.ui.screen

/** Appends a zero-based dot, including any unselected midpoint on the 3×3 grid. */
internal fun appendPatternDot(pattern: List<Int>, dot: Int): List<Int> {
    if (dot in pattern) return pattern
    val previous = pattern.lastOrNull() ?: return listOf(dot)
    val rowSum = previous / 3 + dot / 3
    val columnSum = previous % 3 + dot % 3
    if (rowSum % 2 == 0 && columnSum % 2 == 0) {
        val midpoint = rowSum / 2 * 3 + columnSum / 2
        if (midpoint !in pattern) return pattern + midpoint + dot
    }
    return pattern + dot
}
