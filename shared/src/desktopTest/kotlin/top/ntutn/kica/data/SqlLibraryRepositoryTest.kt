package top.ntutn.kica.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            assertEquals(2L, KicaDatabase.Schema.version)

            repository.clearCache()
            assertNull(repository.cachedRecommendations())
            assertNull(repository.cachedRandomComics())
            assertNull(repository.cachedCategories())
        } finally {
            driver.close()
        }
    }

    @Test
    fun oldSettingsDefaultTranslationToDisabled() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            KicaDatabase.Schema.create(driver)
            driver.execute(
                identifier = null,
                sql = "INSERT INTO app_setting(key, value) VALUES ('app', '{}')",
                parameters = 0,
            ).value
            val settings = SqlLibraryRepository(KicaDatabase(driver)).settings().first()
            assertFalse(settings.titleTranslationEnabled)
        } finally {
            driver.close()
        }
    }

    @Test
    fun desktopMigratesVersionOneAndPreservesExistingData() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            listOf(
                "CREATE TABLE history_entry (comic_id TEXT NOT NULL PRIMARY KEY, comic_json TEXT NOT NULL, episode_id TEXT NOT NULL, episode_title TEXT NOT NULL, page_index INTEGER NOT NULL, updated_at INTEGER NOT NULL)",
                "CREATE TABLE reading_progress (comic_id TEXT NOT NULL, episode_id TEXT NOT NULL, page_index INTEGER NOT NULL, reader_mode TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY (comic_id, episode_id))",
                "CREATE TABLE download_task (id TEXT NOT NULL PRIMARY KEY, comic_json TEXT NOT NULL, episode_json TEXT NOT NULL, status TEXT NOT NULL, completed_pages INTEGER NOT NULL, total_pages INTEGER NOT NULL, retry_count INTEGER NOT NULL, target_location TEXT NOT NULL, error TEXT)",
                "CREATE TABLE app_setting (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)",
                "INSERT INTO app_setting(key, value) VALUES ('app', '{}')",
                "PRAGMA user_version = 1",
            ).forEach { sql -> driver.execute(null, sql, 0).value }

            val database = createKicaDatabase(driver, isNewDatabase = false)
            val repository = SqlLibraryRepository(database)

            assertFalse(repository.settings().first().titleTranslationEnabled)
            val cache = SqlTitleTranslationCache(database)
            val key = TitleTranslationCacheKey("原文", "zh-CN", "model", 1)
            cache.put(key, "译文", 1)
            assertEquals("译文", cache.get(key, 2))
        } finally {
            driver.close()
        }
    }
}
