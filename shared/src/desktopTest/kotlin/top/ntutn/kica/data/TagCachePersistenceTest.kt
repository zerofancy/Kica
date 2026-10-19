package top.ntutn.kica.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import top.ntutn.kica.db.KicaDatabase
import top.ntutn.kica.model.*
import kotlin.test.*

class TagCachePersistenceTest {
    @Test
    fun versionTwoUpgradePreservesLibraryAndRules() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            KicaDatabase.Schema.create(driver)
            val old = SqlLibraryRepository(KicaDatabase(driver))
            val comic = ComicSummary("one", "Book")
            old.updateSettings(AppSettings(blockedCategories = setOf("category")))
            old.addHistory(HistoryEntry(comic, "episode", "Chapter", 4, 100))
            old.saveProgress(ReadingProgress("one", "episode", 4, ReaderMode.VERTICAL, 100))
            old.upsertDownload(DownloadTask("task", comic, Episode("episode", "one", 1, "Chapter")))
            driver.execute(null, "DROP TABLE comic_tag_cache", 0)
            driver.execute(null, "PRAGMA user_version = 2", 0)
            val upgraded = SqlLibraryRepository(createKicaDatabase(driver, isNewDatabase = false))
            assertNull(upgraded.history().first().single().comic.tags)
            assertEquals(4, upgraded.readingProgress("one", "episode")?.pageIndex)
            assertEquals("task", upgraded.downloads().first().single().id)
            assertEquals(setOf("category"), upgraded.settings().first().blockedCategories)
            assertTrue(upgraded.settings().first().blockedTags.isEmpty())
            upgraded.saveTagMetadata(TagMetadata("one", emptyList(), 123))
            assertEquals(TagMetadata("one", emptyList(), 123), upgraded.tagMetadata("one"))
        } finally { driver.close() }
    }

    @Test
    fun cacheIsBoundedAndClearingItPreservesSettings() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            KicaDatabase.Schema.create(driver)
            val library = SqlLibraryRepository(KicaDatabase(driver))
            library.updateSettings(AppSettings(blockedCategories = setOf("keep"), titleTranslationEnabled = true))
            library.updateBlockedTags { it + "travel" }
            repeat(2001) { index -> library.saveTagMetadata(TagMetadata("comic-$index", listOf("tag"), index.toLong())) }
            assertEquals(2000L, driver.executeQuery(null, "SELECT COUNT(*) FROM comic_tag_cache", { cursor ->
                cursor.next()
                app.cash.sqldelight.db.QueryResult.Value(cursor.getLong(0)!!)
            }, 0).value)
            assertNotNull(library.tagMetadata("comic-2000"))
            library.clearCache()
            assertNull(library.tagMetadata("comic-2000"))
            assertEquals(setOf("travel"), library.settings().first().blockedTags)
            assertEquals(setOf("keep"), library.settings().first().blockedCategories)
            assertTrue(library.settings().first().titleTranslationEnabled)
        } finally { driver.close() }
    }
}
