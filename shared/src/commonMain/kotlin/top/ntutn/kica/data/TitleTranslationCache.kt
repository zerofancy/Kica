package top.ntutn.kica.data

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.ntutn.kica.db.KicaDatabase

data class TitleTranslationCacheKey(
    val sourceText: String,
    val targetLanguage: String,
    val modelId: String,
    val promptVersion: Long,
)

interface TitleTranslationCache {
    suspend fun get(key: TitleTranslationCacheKey, nowEpochMillis: Long): String?
    suspend fun put(key: TitleTranslationCacheKey, translatedText: String, nowEpochMillis: Long)
    suspend fun clear()
}

class SqlTitleTranslationCache(
    database: KicaDatabase,
    private val maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
    private val maxSizeBytes: Long = DEFAULT_MAX_SIZE_BYTES,
) : TitleTranslationCache {
    private val queries = database.kicaQueries
    private val mutex = Mutex()

    override suspend fun get(key: TitleTranslationCacheKey, nowEpochMillis: Long): String? = mutex.withLock {
        queries.transactionWithResult {
            queries.deleteExpiredTitleTranslations(nowEpochMillis - maxAgeMillis)
            val entry = queries.selectTitleTranslation(
                source_text = key.sourceText,
                target_language = key.targetLanguage,
                model_id = key.modelId,
                prompt_version = key.promptVersion,
            ).executeAsOneOrNull()
            if (entry != null) {
                queries.touchTitleTranslation(
                    last_accessed_at = nowEpochMillis,
                    source_text = key.sourceText,
                    target_language = key.targetLanguage,
                    model_id = key.modelId,
                    prompt_version = key.promptVersion,
                )
            }
            entry?.translated_text
        }
    }

    override suspend fun put(
        key: TitleTranslationCacheKey,
        translatedText: String,
        nowEpochMillis: Long,
    ) = mutex.withLock {
        queries.transaction {
            queries.deleteExpiredTitleTranslations(nowEpochMillis - maxAgeMillis)
            queries.upsertTitleTranslation(
                source_text = key.sourceText,
                target_language = key.targetLanguage,
                model_id = key.modelId,
                prompt_version = key.promptVersion,
                translated_text = translatedText,
                created_at = nowEpochMillis,
                last_accessed_at = nowEpochMillis,
                size_bytes = cacheEntrySize(key, translatedText),
            )
            var currentSize = queries.selectTitleTranslationCacheSize().executeAsOne()
            while (currentSize > maxSizeBytes) {
                val oldest = queries.selectLeastRecentlyUsedTitleTranslation().executeAsOneOrNull() ?: break
                queries.deleteTitleTranslation(
                    source_text = oldest.source_text,
                    target_language = oldest.target_language,
                    model_id = oldest.model_id,
                    prompt_version = oldest.prompt_version,
                )
                currentSize = queries.selectTitleTranslationCacheSize().executeAsOne()
            }
        }
    }

    override suspend fun clear(): Unit = mutex.withLock {
        queries.clearTitleTranslationCache()
    }

    private fun cacheEntrySize(key: TitleTranslationCacheKey, translatedText: String): Long =
        (key.sourceText.encodeToByteArray().size +
            key.targetLanguage.encodeToByteArray().size +
            key.modelId.encodeToByteArray().size +
            translatedText.encodeToByteArray().size +
            LONG_METADATA_BYTES).toLong()

    companion object {
        const val DEFAULT_MAX_AGE_MILLIS: Long = 14L * 24 * 60 * 60 * 1_000
        const val DEFAULT_MAX_SIZE_BYTES: Long = 10L * 1024 * 1024
        private const val LONG_METADATA_BYTES = 32
    }
}

fun createKicaDatabase(driver: SqlDriver, isNewDatabase: Boolean): KicaDatabase {
    if (isNewDatabase) {
        KicaDatabase.Schema.create(driver).value
        setUserVersion(driver, KicaDatabase.Schema.version)
    } else {
        val storedVersion = readUserVersion(driver).takeIf { it > 0L } ?: 1L
        if (storedVersion < KicaDatabase.Schema.version) {
            KicaDatabase.Schema.migrate(driver, storedVersion, KicaDatabase.Schema.version).value
            setUserVersion(driver, KicaDatabase.Schema.version)
        }
    }
    return KicaDatabase(driver)
}

private fun readUserVersion(driver: SqlDriver): Long =
    driver.executeQuery(
        identifier = null,
        sql = "PRAGMA user_version",
        mapper = { cursor ->
            app.cash.sqldelight.db.QueryResult.Value(
                if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L,
            )
        },
        parameters = 0,
    ).value

private fun setUserVersion(driver: SqlDriver, version: Long) {
    driver.execute(null, "PRAGMA user_version = $version", 0).value
}
