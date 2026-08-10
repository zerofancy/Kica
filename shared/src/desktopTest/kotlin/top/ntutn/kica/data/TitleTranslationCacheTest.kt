package top.ntutn.kica.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import top.ntutn.kica.db.KicaDatabase

class TitleTranslationCacheTest {
    @Test
    fun expiresEntriesByCreationTime() = withCache(maxAgeMillis = 100) { cache ->
        val key = key("title")
        cache.put(key, "标题", 1_000)
        assertEquals("标题", cache.get(key, 1_099))
        assertNull(cache.get(key, 1_100))
    }

    @Test
    fun evictsLeastRecentlyUsedEntryByLogicalSize() = withCache(maxSizeBytes = 80) { cache ->
        val first = key("a")
        val second = key("b")
        val third = key("c")
        cache.put(first, "一", 1)
        cache.put(second, "二", 2)
        assertEquals("一", cache.get(first, 3))
        cache.put(third, "三", 4)

        assertEquals("一", cache.get(first, 5))
        assertNull(cache.get(second, 5))
        assertEquals("三", cache.get(third, 5))
    }

    @Test
    fun cacheKeySeparatesModelLanguageAndPromptVersion() = withCache { cache ->
        cache.put(key("same"), "译文", 1)
        assertNull(cache.get(key("same", language = "en"), 2))
        assertNull(cache.get(key("same", model = "other"), 2))
        assertNull(cache.get(key("same", promptVersion = 2), 2))
    }

    @Test
    fun clearRemovesEveryTranslation() = withCache { cache ->
        val key = key("title")
        cache.put(key, "标题", 1)
        cache.clear()
        assertNull(cache.get(key, 2))
    }

    private fun withCache(
        maxAgeMillis: Long = SqlTitleTranslationCache.DEFAULT_MAX_AGE_MILLIS,
        maxSizeBytes: Long = SqlTitleTranslationCache.DEFAULT_MAX_SIZE_BYTES,
        block: suspend (SqlTitleTranslationCache) -> Unit,
    ) = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            KicaDatabase.Schema.create(driver)
            block(SqlTitleTranslationCache(KicaDatabase(driver), maxAgeMillis, maxSizeBytes))
        } finally {
            driver.close()
        }
    }

    private fun key(
        source: String,
        language: String = "z",
        model: String = "m",
        promptVersion: Long = 1,
    ) = TitleTranslationCacheKey(source, language, model, promptVersion)
}
