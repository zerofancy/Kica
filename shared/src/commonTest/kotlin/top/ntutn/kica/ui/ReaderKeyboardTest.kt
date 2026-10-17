package top.ntutn.kica.ui

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.ntutn.kica.model.ReaderMode
import top.ntutn.kica.ui.screen.ReaderCommand
import top.ntutn.kica.ui.screen.ReaderKeyState
import top.ntutn.kica.ui.screen.readerAcceptsRepeat
import top.ntutn.kica.ui.screen.readerChapterDirection
import top.ntutn.kica.ui.screen.readerCommand

class ReaderKeyboardTest {
    @Test
    fun arrowsFollowReadingDirectionAndSpaceAlwaysAdvances() {
        for (mode in ReaderMode.entries) {
            val vertical = mode == ReaderMode.VERTICAL
            val rtl = mode == ReaderMode.PAGED_RIGHT_TO_LEFT || mode == ReaderMode.DOUBLE_RIGHT_TO_LEFT
            assertEquals(ReaderCommand.FORWARD, readerCommand(Key.Spacebar, mode))
            assertEquals(ReaderCommand.BACKWARD, readerCommand(Key.Spacebar, mode, shift = true))
            assertEquals(ReaderCommand.FORWARD, readerCommand(Key.PageDown, mode))
            assertEquals(ReaderCommand.BACKWARD, readerCommand(Key.PageUp, mode))
            assertEquals(if (vertical) ReaderCommand.SMALL_FORWARD else ReaderCommand.FORWARD, readerCommand(Key.DirectionDown, mode))
            assertEquals(if (vertical) ReaderCommand.SMALL_BACKWARD else ReaderCommand.BACKWARD, readerCommand(Key.DirectionUp, mode))
            assertEquals(if (vertical) null else if (rtl) ReaderCommand.BACKWARD else ReaderCommand.FORWARD, readerCommand(Key.DirectionRight, mode))
            assertEquals(if (vertical) null else if (rtl) ReaderCommand.FORWARD else ReaderCommand.BACKWARD, readerCommand(Key.DirectionLeft, mode))
        }
    }

    @Test
    fun modifiedKeysDoNotStealSystemOrControlShortcuts() {
        val mode = ReaderMode.VERTICAL
        assertEquals(ReaderCommand.NEXT_CHAPTER, readerCommand(Key.PageDown, mode, ctrl = true))
        assertEquals(ReaderCommand.PREVIOUS_CHAPTER, readerCommand(Key.PageUp, mode, ctrl = true))
        for (key in listOf(Key.F, Key.F1, Key.Spacebar, Key.MoveHome, Key.MoveEnd, Key.DirectionDown)) {
            assertNull(readerCommand(key, mode, ctrl = true))
            assertNull(readerCommand(key, mode, alt = true))
            assertNull(readerCommand(key, mode, meta = true))
        }
        assertNull(readerCommand(Key.PageDown, mode, ctrl = true, shift = true))
        assertNull(readerCommand(Key.Tab, mode))
        assertNull(readerCommand(Key.Enter, mode))
        assertNull(readerCommand(Key.Escape, mode))
        assertEquals(ReaderCommand.START, readerCommand(Key.MoveHome, mode))
        assertEquals(ReaderCommand.END, readerCommand(Key.MoveEnd, mode))
    }

    @Test
    fun boundaryRequiresFreshPressAndHomeEndNeverChangeChapter() {
        assertNull(readerChapterDirection(ReaderCommand.FORWARD, false, false, false))
        assertEquals(true, readerChapterDirection(ReaderCommand.FORWARD, false, true, false))
        assertEquals(false, readerChapterDirection(ReaderCommand.BACKWARD, true, false, false))
        for (command in ReaderCommand.entries) {
            assertNull(readerChapterDirection(command, true, true, repeated = true))
        }
        assertNull(readerChapterDirection(ReaderCommand.START, true, true, false))
        assertNull(readerChapterDirection(ReaderCommand.END, true, true, false))
        assertEquals(true, readerChapterDirection(ReaderCommand.NEXT_CHAPTER, false, false, false))
        assertEquals(false, readerChapterDirection(ReaderCommand.PREVIOUS_CHAPTER, false, false, false))
    }

    @Test
    fun heldKeySurvivesChapterChangeUntilReleaseAndOnlySmallScrollRepeats() {
        val state = ReaderKeyState()
        assertFalse(state.press(Key.PageDown))
        assertTrue(state.press(Key.PageDown))
        state.blockHeldKeys()
        assertTrue(state.isBlocked(Key.PageDown))
        state.release(Key.PageDown)
        assertFalse(state.isBlocked(Key.PageDown))
        assertFalse(state.press(Key.PageDown))
        state.press(Key.DirectionDown)
        state.blockHeldKeys()
        assertTrue(state.isBlocked(Key.DirectionDown))
        assertTrue(state.press(Key.DirectionDown))
        state.clear()
        assertFalse(state.isBlocked(Key.DirectionDown))
        assertFalse(state.press(Key.PageDown))
        assertEquals(setOf(ReaderCommand.SMALL_FORWARD, ReaderCommand.SMALL_BACKWARD), ReaderCommand.entries.filter(::readerAcceptsRepeat).toSet())
    }
}
