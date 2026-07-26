package top.ntutn.kica.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.ntutn.kica.model.AppSettings
import top.ntutn.kica.model.DownloadTask
import top.ntutn.kica.model.HistoryEntry
import top.ntutn.kica.model.ReadingProgress

class InMemoryLibraryRepository : LibraryRepository {
    private val historyState = MutableStateFlow<List<HistoryEntry>>(emptyList())
    private val downloadState = MutableStateFlow<List<DownloadTask>>(emptyList())
    private val settingsState = MutableStateFlow(AppSettings())
    private val progress = mutableMapOf<Pair<String, String>, ReadingProgress>()

    override fun history(): Flow<List<HistoryEntry>> = historyState.asStateFlow()

    override fun downloads(): Flow<List<DownloadTask>> = downloadState.asStateFlow()

    override fun settings(): Flow<AppSettings> = settingsState.asStateFlow()

    override suspend fun readingProgress(comicId: String, episodeId: String): ReadingProgress? =
        progress[comicId to episodeId]

    override suspend fun saveProgress(progress: ReadingProgress) {
        this.progress[progress.comicId to progress.episodeId] = progress
    }

    override suspend fun addHistory(entry: HistoryEntry) {
        historyState.value = listOf(entry) + historyState.value
            .filterNot { it.comic.id == entry.comic.id }
            .take(199)
    }

    override suspend fun upsertDownload(task: DownloadTask) {
        downloadState.value = downloadState.value
            .filterNot { it.id == task.id } + task
    }

    override suspend fun removeDownload(id: String) {
        downloadState.value = downloadState.value.filterNot { it.id == id }
    }

    override suspend fun updateSettings(settings: AppSettings) {
        settingsState.value = settings
    }

    override suspend fun clearCache() = Unit
}

