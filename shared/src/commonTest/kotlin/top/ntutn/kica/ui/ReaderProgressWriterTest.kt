package top.ntutn.kica.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import top.ntutn.kica.data.InMemoryLibraryRepository
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.ReaderMode
import top.ntutn.kica.model.ReadingProgress
import top.ntutn.kica.ui.navigation.SessionProgressCoordinator

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
        val writer = SessionProgressCoordinator(backgroundScope, library)
        val comic = ComicSummary("comic", "Fixture")
        assertTrue(writer.save(progress(0), comic, "Chapter 1"))
        assertTrue(writer.save(progress(3), comic, "Chapter 1"))
        assertTrue(writer.drain())
        assertEquals(listOf(0, 3), saved)
        assertEquals(3, memory.readingProgress("comic", "e1")?.pageIndex)
        assertEquals(3, memory.history().first().single().pageIndex)
        assertTrue(writer.close())
    }

    @Test
    fun closingDrainsAlreadyAcceptedWritesAndRejectsLateCallbacks() = runTest {
        val memory = InMemoryLibraryRepository()
        val library = object : LibraryRepository by memory {
            override suspend fun saveProgress(progress: ReadingProgress) {
                delay(100)
                memory.saveProgress(progress)
            }
        }
        val writer = SessionProgressCoordinator(backgroundScope, library)
        writer.save(progress(0), null, "")
        runCurrent()
        assertTrue(writer.close())
        assertFalse(writer.save(progress(8), null, ""))
        assertEquals(0, memory.readingProgress("comic", "e1")?.pageIndex)
    }

    @Test
    fun saveFailureDoesNotKillFollowingWrites() = runTest {
        val memory = InMemoryLibraryRepository()
        val library = object : LibraryRepository by memory {
            override suspend fun saveProgress(progress: ReadingProgress) {
                if (progress.pageIndex == 1) error("Storage failure")
                memory.saveProgress(progress)
            }
        }
        val writer = SessionProgressCoordinator(backgroundScope, library)
        writer.save(progress(1), null, "")
        writer.save(progress(2), null, "")
        assertTrue(writer.drain())
        assertEquals(1, writer.failures.value)
        assertEquals(2, memory.readingProgress("comic", "e1")?.pageIndex)
        writer.close()
    }

    @Test
    fun boundedCloseReportsIncompletePersistence() = runTest {
        val memory = InMemoryLibraryRepository()
        val library = object : LibraryRepository by memory {
            override suspend fun saveProgress(progress: ReadingProgress) { delay(10_000) }
        }
        val writer = SessionProgressCoordinator(backgroundScope, library)
        writer.save(progress(2), null, "")
        assertFalse(writer.close(timeoutMillis = 25))
        assertEquals(1, writer.failures.value)
        assertFalse(writer.save(progress(3), null, ""))
    }
}
