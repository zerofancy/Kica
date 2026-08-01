package top.ntutn.kica.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import top.ntutn.kica.model.ComicCategory
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.HistoryEntry
import top.ntutn.kica.model.ReaderMode
import top.ntutn.kica.model.ReadingProgress

class LibraryRepositoryTest {
    @Test
    fun homeAndCategoryDataAreCachedAndCleared() = runTest {
        val library = InMemoryLibraryRepository()
        val comics = listOf(ComicSummary(id = "comic", title = "Comic"))
        val categories = listOf(ComicCategory(id = "action", title = "Action"))

        assertNull(library.cachedRecommendations())
        assertNull(library.cachedRandomComics())
        assertNull(library.cachedCategories())
        library.cacheRecommendations(comics)
        library.cacheRandomComics(comics)
        library.cacheCategories(categories)
        assertEquals(comics, library.cachedRecommendations())
        assertEquals(comics, library.cachedRandomComics())
        assertEquals(categories, library.cachedCategories())

        library.clearCache()
        assertNull(library.cachedRecommendations())
        assertNull(library.cachedRandomComics())
        assertNull(library.cachedCategories())
    }

    @Test
    fun progressAndNewestHistorySurviveRepositoryReads() = runTest {
        val library = InMemoryLibraryRepository()
        val progress = ReadingProgress(
            comicId = "comic",
            episodeId = "episode",
            pageIndex = 14,
            mode = ReaderMode.PAGED_RIGHT_TO_LEFT,
            updatedAtEpochMillis = 1234,
        )
        library.saveProgress(progress)
        library.addHistory(
            HistoryEntry(
                comic = ComicSummary("comic", "Comic"),
                episodeId = "episode",
                episodeTitle = "Episode",
                pageIndex = 14,
                updatedAtEpochMillis = 1234,
            ),
        )

        assertEquals(progress, library.readingProgress("comic", "episode"))
        assertEquals(14, library.history().first().single().pageIndex)
        assertNull(library.readingProgress("missing", "episode"))
    }
}
