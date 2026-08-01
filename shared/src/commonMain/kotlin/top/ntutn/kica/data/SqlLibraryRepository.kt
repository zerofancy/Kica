package top.ntutn.kica.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.ntutn.kica.db.KicaDatabase
import top.ntutn.kica.model.AppSettings
import top.ntutn.kica.model.ComicCategory
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.DownloadStatus
import top.ntutn.kica.model.DownloadTask
import top.ntutn.kica.model.Episode
import top.ntutn.kica.model.HistoryEntry
import top.ntutn.kica.model.ReaderMode
import top.ntutn.kica.model.ReadingProgress

class SqlLibraryRepository(
    private val database: KicaDatabase,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) : LibraryRepository {
    private val queries = database.kicaQueries

    override fun history(): Flow<List<HistoryEntry>> =
        queries.selectHistory().asFlow().mapToList(Dispatchers.Default).map { rows ->
            rows.mapNotNull { row ->
                runCatching {
                    HistoryEntry(
                        comic = json.decodeFromString<ComicSummary>(row.comic_json),
                        episodeId = row.episode_id,
                        episodeTitle = row.episode_title,
                        pageIndex = row.page_index.toInt(),
                        updatedAtEpochMillis = row.updated_at,
                    )
                }.getOrNull()
            }
        }

    override fun downloads(): Flow<List<DownloadTask>> =
        queries.selectDownloads().asFlow().mapToList(Dispatchers.Default).map { rows ->
            rows.mapNotNull { row ->
                runCatching {
                    DownloadTask(
                        id = row.id,
                        comic = json.decodeFromString<ComicSummary>(row.comic_json),
                        episode = json.decodeFromString<Episode>(row.episode_json),
                        status = DownloadStatus.valueOf(row.status),
                        completedPages = row.completed_pages.toInt(),
                        totalPages = row.total_pages.toInt(),
                        retryCount = row.retry_count.toInt(),
                        targetLocation = row.target_location,
                        error = row.error,
                    )
                }.getOrNull()
            }
        }

    override fun settings(): Flow<AppSettings> =
        queries.selectSetting(SETTINGS_KEY)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { value ->
                value?.let { runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull() }
                    ?: AppSettings()
            }

    override suspend fun cachedRecommendations(): List<ComicSummary>? =
        queries.selectSetting(RECOMMENDATIONS_CACHE_KEY).executeAsOneOrNull()?.let { value ->
            runCatching { json.decodeFromString<List<ComicSummary>>(value) }.getOrNull()
        }

    override suspend fun cacheRecommendations(recommendations: List<ComicSummary>) {
        queries.upsertSetting(RECOMMENDATIONS_CACHE_KEY, json.encodeToString(recommendations))
    }

    override suspend fun cachedRandomComics(): List<ComicSummary>? =
        queries.selectSetting(RANDOM_COMICS_CACHE_KEY).executeAsOneOrNull()?.let { value ->
            runCatching { json.decodeFromString<List<ComicSummary>>(value) }.getOrNull()
        }

    override suspend fun cacheRandomComics(comics: List<ComicSummary>) {
        queries.upsertSetting(RANDOM_COMICS_CACHE_KEY, json.encodeToString(comics))
    }

    override suspend fun cachedCategories(): List<ComicCategory>? =
        queries.selectSetting(CATEGORIES_CACHE_KEY).executeAsOneOrNull()?.let { value ->
            runCatching { json.decodeFromString<List<ComicCategory>>(value) }.getOrNull()
        }

    override suspend fun cacheCategories(categories: List<ComicCategory>) {
        queries.upsertSetting(CATEGORIES_CACHE_KEY, json.encodeToString(categories))
    }

    override suspend fun readingProgress(comicId: String, episodeId: String): ReadingProgress? =
        queries.selectProgress(comicId, episodeId).executeAsOneOrNull()?.let { row ->
            ReadingProgress(
                comicId = row.comic_id,
                episodeId = row.episode_id,
                pageIndex = row.page_index.toInt(),
                mode = runCatching { ReaderMode.valueOf(row.reader_mode) }.getOrDefault(ReaderMode.VERTICAL),
                updatedAtEpochMillis = row.updated_at,
            )
        }

    override suspend fun saveProgress(progress: ReadingProgress) {
        queries.upsertProgress(
            comic_id = progress.comicId,
            episode_id = progress.episodeId,
            page_index = progress.pageIndex.toLong(),
            reader_mode = progress.mode.name,
            updated_at = progress.updatedAtEpochMillis,
        )
    }

    override suspend fun addHistory(entry: HistoryEntry) {
        queries.upsertHistory(
            comic_id = entry.comic.id,
            comic_json = json.encodeToString(entry.comic),
            episode_id = entry.episodeId,
            episode_title = entry.episodeTitle,
            page_index = entry.pageIndex.toLong(),
            updated_at = entry.updatedAtEpochMillis,
        )
    }

    override suspend fun upsertDownload(task: DownloadTask) {
        queries.upsertDownload(
            id = task.id,
            comic_json = json.encodeToString(task.comic),
            episode_json = json.encodeToString(task.episode),
            status = task.status.name,
            completed_pages = task.completedPages.toLong(),
            total_pages = task.totalPages.toLong(),
            retry_count = task.retryCount.toLong(),
            target_location = task.targetLocation,
            error = task.error,
        )
    }

    override suspend fun removeDownload(id: String) {
        queries.deleteDownload(id)
    }

    override suspend fun updateSettings(settings: AppSettings) {
        queries.upsertSetting(SETTINGS_KEY, json.encodeToString(settings))
    }

    override suspend fun clearCache() {
        queries.transaction {
            queries.clearCache()
            queries.deleteSetting(RECOMMENDATIONS_CACHE_KEY)
            queries.deleteSetting(RANDOM_COMICS_CACHE_KEY)
            queries.deleteSetting(CATEGORIES_CACHE_KEY)
        }
    }

    private companion object {
        const val SETTINGS_KEY = "app"
        const val RECOMMENDATIONS_CACHE_KEY = "recommendations_cache"
        const val RANDOM_COMICS_CACHE_KEY = "random_comics_cache"
        const val CATEGORIES_CACHE_KEY = "categories_cache"
    }
}
