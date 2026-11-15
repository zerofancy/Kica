package top.ntutn.kica.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import top.ntutn.kica.model.ReaderMode
import top.ntutn.kica.model.ReadingProgress
import top.ntutn.kica.ui.screen.ReaderEntrySession
import top.ntutn.kica.ui.screen.ReaderEntryState
import top.ntutn.kica.ui.screen.ReaderStart

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderEntrySessionTest {
    private fun progress(page: Int, mode: ReaderMode = ReaderMode.VERTICAL) =
        ReadingProgress("comic", "chapter", page, mode, 100)

    @Test
    fun noRecordEntersFirstPageWithoutAsking() = runTest {
        val entry = ReaderEntrySession()
        entry.readProgress { null }
        assertEquals(ReaderEntryState.Ready(ReaderStart()), entry.state)
    }

    @Test
    fun firstPageNeverAsksAndRetainsEveryMode() = runTest {
        for (mode in ReaderMode.entries) {
            val entry = ReaderEntrySession()
            entry.readProgress { progress(0, mode) }
            assertEquals(ReaderEntryState.Ready(ReaderStart(0, mode)), entry.state)
        }
    }

    @Test
    fun laterPagesAskIncludingSecondPageOfDoubleMode() = runTest {
        for (mode in ReaderMode.entries) {
            for (page in listOf(1, 4, Int.MAX_VALUE)) {
                val entry = ReaderEntrySession()
                val saved = progress(page, mode)
                entry.readProgress { saved }
                assertEquals(ReaderEntryState.Choice(saved), entry.state)
            }
        }
    }

    @Test
    fun continueAndStartRetainModeAndAcceptOnlyFirstChoice() = runTest {
        for (mode in ReaderMode.entries) {
            for (continueReading in listOf(true, false)) {
                val entry = ReaderEntrySession()
                entry.readProgress { progress(7, mode) }
                entry.choose(continueReading)
                val expected = ReaderEntryState.Ready(ReaderStart(if (continueReading) 7 else 0, mode))
                assertEquals(expected, entry.state)
                entry.choose(!continueReading)
                entry.readProgress { error("A resolved entry must not read again") }
                assertEquals(expected, entry.state)
            }
        }
    }

    @Test
    fun cancellationIsIdempotentAndIgnoresLateReadAndChoice() = runTest {
        val entry = ReaderEntrySession()
        val result = CompletableDeferred<ReadingProgress?>()
        val reading = launch { entry.readProgress { result.await() } }
        runCurrent()
        assertEquals(ReaderEntryState.Checking, entry.state)
        assertTrue(entry.cancel())
        assertEquals(false, entry.cancel())
        result.complete(progress(8))
        reading.join()
        entry.choose(true)
        entry.readProgress { error("Cancelled entry must stay cancelled") }
        assertEquals(ReaderEntryState.Cancelled, entry.state)
    }

    @Test
    fun errorDoesNotBecomeNoRecordAndRetryCanSucceed() = runTest {
        val entry = ReaderEntrySession()
        entry.readProgress { error("Storage unavailable") }
        assertEquals(ReaderEntryState.Failed, entry.state)
        entry.choose(false)
        assertEquals(ReaderEntryState.Failed, entry.state)
        entry.readProgress { progress(3) }
        assertIs<ReaderEntryState.Choice>(entry.state)
    }

    @Test
    fun supersededReadCannotReplaceNewerResult() = runTest {
        val entry = ReaderEntrySession()
        val old = CompletableDeferred<ReadingProgress?>()
        val first = launch { entry.readProgress { old.await() } }
        runCurrent()
        entry.readProgress { progress(2) }
        old.complete(progress(9))
        first.join()
        assertEquals(ReaderEntryState.Choice(progress(2)), entry.state)
    }

    @Test
    fun coroutineCancellationIsNotAnErrorOrAnAutomaticStart() = runTest {
        val entry = ReaderEntrySession()
        assertFailsWith<CancellationException> { entry.readProgress { throw CancellationException() } }
        assertEquals(ReaderEntryState.Checking, entry.state)
    }

    @Test
    fun malformedNegativePositionSkipsChoiceButKeepsCorrectionInformation() = runTest {
        val entry = ReaderEntrySession()
        entry.readProgress { progress(-2, ReaderMode.PAGED_RIGHT_TO_LEFT) }
        val start = assertIs<ReaderEntryState.Ready>(entry.state).start
        assertEquals(-2, start.pageIndex)
        assertEquals(0, start.pageFor(5))
        assertEquals(ReaderMode.PAGED_RIGHT_TO_LEFT, start.mode)
    }

    @Test
    fun positionsClampToNonemptyChapterAndRejectEmptyChapter() {
        assertEquals(0, ReaderStart(0).pageFor(1))
        assertEquals(0, ReaderStart(-1).pageFor(3))
        assertEquals(4, ReaderStart(9).pageFor(5))
        assertEquals(2, ReaderStart(2).pageFor(5))
        assertEquals(4, ReaderStart(Int.MAX_VALUE).pageFor(5))
        assertFailsWith<IllegalArgumentException> { ReaderStart(2).pageFor(0) }
    }

    @Test
    fun temporaryUnmountPreservesReadyDecisionAndInvalidatesSuspendedRead() = runTest {
        val resolved = ReaderEntrySession()
        resolved.readProgress { progress(4) }
        resolved.choose(true)
        resolved.interruptRead()
        resolved.readProgress { error("Unlock must keep the confirmed decision") }
        assertEquals(ReaderEntryState.Ready(ReaderStart(4)), resolved.state)

        val checking = ReaderEntrySession()
        val old = CompletableDeferred<ReadingProgress?>()
        val first = launch { checking.readProgress { old.await() } }
        runCurrent()
        checking.interruptRead()
        old.complete(progress(9))
        first.join()
        assertEquals(ReaderEntryState.Checking, checking.state)
        checking.readProgress { progress(2) }
        assertEquals(ReaderEntryState.Choice(progress(2)), checking.state)
    }

    @Test
    fun reopeningIsANewDecisionUsingTheLatestSavedPosition() = runTest {
        val first = ReaderEntrySession()
        first.readProgress { progress(4) }
        first.choose(false)
        first.cancel()
        val reopened = ReaderEntrySession()
        reopened.readProgress { progress(0) }
        assertIs<ReaderEntryState.Ready>(reopened.state)
        val another = ReaderEntrySession()
        another.readProgress { progress(5) }
        assertIs<ReaderEntryState.Choice>(another.state)
    }
}
