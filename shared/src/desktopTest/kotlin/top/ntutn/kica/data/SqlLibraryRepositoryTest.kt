package top.ntutn.kica.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import top.ntutn.kica.db.KicaDatabase
import top.ntutn.kica.model.ComicCategory
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.HistoryEntry
import top.ntutn.kica.model.ReaderMode
import top.ntutn.kica.model.ReadingProgress

class SqlLibraryRepositoryTest {
    @Test
    fun versionOneSchemaPersistsHistoryAndProgress() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            KicaDatabase.Schema.create(driver)
            val repository = SqlLibraryRepository(KicaDatabase(driver))
            val progress = ReadingProgress(
                comicId = "comic",
                episodeId = "episode",
                pageIndex = 8,
                mode = ReaderMode.VERTICAL,
                updatedAtEpochMillis = 999,
            )

            repository.saveProgress(progress)
            repository.addHistory(
                HistoryEntry(
                    comic = ComicSummary("comic", "Comic"),
                    episodeId = "episode",
                    episodeTitle = "Episode",
                    pageIndex = 8,
                    updatedAtEpochMillis = 999,
                ),
            )
            val categories = listOf(ComicCategory(id = "action", title = "Action"))
            val comics = listOf(ComicSummary(id = "cached", title = "Cached"))
            repository.cacheRecommendations(comics)
            repository.cacheRandomComics(comics)
            repository.cacheCategories(categories)

            assertEquals(progress, repository.readingProgress("comic", "episode"))
            assertEquals("Comic", repository.history().first().single().comic.title)
            assertEquals(comics, repository.cachedRecommendations())
            assertEquals(comics, repository.cachedRandomComics())
            assertEquals(categories, repository.cachedCategories())
            assertEquals(1L, KicaDatabase.Schema.version)

            repository.clearCache()
            assertNull(repository.cachedRecommendations())
            assertNull(repository.cachedRandomComics())
            assertNull(repository.cachedCategories())
        } finally {
            driver.close()
        }
    }
}
