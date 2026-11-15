package top.ntutn.kica.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import top.ntutn.kica.data.InMemoryLibraryRepository
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.ReaderMode
import top.ntutn.kica.model.ReadingProgress
import top.ntutn.kica.ui.screen.ReaderProgressWriter

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderProgressWriterTest {
    private fun progress(page: Int, episode: String = "e1") =
        ReadingProgress("comic", episode, page, ReaderMode.VERTICAL, page.toLong())

    @Test
    fun delayedWritesStayOrderedAndHistoryMatchesTheLatestPosition() = runTest {
        val memory = InMemoryLibraryRepository()
        val saved = mutableListOf<Int>()
        val library = object : LibraryRepository by memory {
            override suspend fun saveProgress(progress: ReadingProgress) {
                if (progress.pageIndex == 0) delay(100)
                saved += progress.pageIndex
                memory.saveProgress(progress)
            }
        }
        val writer = ReaderProgressWriter(backgroundScope, library) { error("Unexpected failure") }
        val comic = ComicSummary("comic", "Fixture")
        writer.save(progress(0), comic, "Chapter 1")
        writer.save(progress(3), comic, "Chapter 1")
        // Use the test scope's clock; background consumers otherwise do not keep advanceUntilIdle alive.
        delay(200)
        assertEquals(listOf(0, 3), saved)
        assertEquals(3, memory.readingProgress("comic", "e1")?.pageIndex)
        assertEquals(3, memory.history().first().single().pageIndex)
        writer.close()
    }

    @Test
    fun closingCancelsPendingWritesAndRejectsLateCallbacks() = runTest {
        val memory = InMemoryLibraryRepository()
        val library = object : LibraryRepository by memory {
            override suspend fun saveProgress(progress: ReadingProgress) {
                delay(100)
                memory.saveProgress(progress)
            }
        }
        val writer = ReaderProgressWriter(backgroundScope, library) { error("Unexpected failure") }
        writer.save(progress(0), null, "")
        runCurrent()
        writer.close()
        writer.save(progress(8), null, "")
        advanceUntilIdle()
        assertNull(memory.readingProgress("comic", "e1"))
        assertEquals(emptyList(), memory.history().first())
    }

    @Test
    fun saveFailureDoesNotKillFollowingWrites() = runTest {
        val memory = InMemoryLibraryRepository()
        var failures = 0
        val library = object : LibraryRepository by memory {
            override suspend fun saveProgress(progress: ReadingProgress) {
                if (progress.pageIndex == 1) error("Storage failure")
                memory.saveProgress(progress)
            }
        }
        val writer = ReaderProgressWriter(backgroundScope, library) { failures++ }
        writer.save(progress(1), null, "")
        writer.save(progress(2), null, "")
        runCurrent()
        assertEquals(1, failures)
        assertEquals(2, memory.readingProgress("comic", "e1")?.pageIndex)
        writer.close()
    }
}
