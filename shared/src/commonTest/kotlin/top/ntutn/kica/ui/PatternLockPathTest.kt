package top.ntutn.kica.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import top.ntutn.kica.ui.screen.appendPatternDot

class PatternLockPathTest {
    // Use the user-facing 1–9 numbering in test cases.
    private fun draw(vararg dots: Int): List<Int> =
        dots.fold(emptyList<Int>()) { pattern, dot ->
            appendPatternDot(pattern, dot - 1)
        }.map { it + 1 }

    @Test
    fun skippedDotsMatchExplicitPath() {
        assertEquals(listOf(1, 2, 3, 6, 9), draw(1, 3, 9))
        assertEquals(draw(1, 2, 3, 6, 9), draw(1, 3, 9))
    }

    @Test
    fun fillsHorizontalVerticalAndDiagonalMidpointsInBothDirections() {
        val lines = listOf(
            listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9),
            listOf(1, 4, 7), listOf(2, 5, 8), listOf(3, 6, 9),
            listOf(1, 5, 9), listOf(3, 5, 7),
        )
        for (line in lines) {
            assertEquals(line, draw(line.first(), line.last()))
            assertEquals(line.reversed(), draw(line.last(), line.first()))
        }
    }

    @Test
    fun doesNotRepeatSelectedMidpointOrEndpoint() {
        assertEquals(listOf(2, 1, 3), draw(2, 1, 3))
        assertEquals(listOf(5, 1, 9), draw(5, 1, 9))
        assertEquals(listOf(1, 2, 3, 6, 9), draw(1, 3, 1, 9, 9))
    }

    @Test
    fun leavesAdjacentAndNonAlignedMovesUnchanged() {
        assertEquals(listOf(1, 2, 6, 7), draw(1, 2, 6, 7))
        assertEquals(emptyList(), draw())
        assertEquals(listOf(5), draw(5))
    }
}
