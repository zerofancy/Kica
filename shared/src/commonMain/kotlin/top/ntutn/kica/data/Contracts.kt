package top.ntutn.kica.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import top.ntutn.kica.model.AppSettings
import top.ntutn.kica.model.ComicDetail
import top.ntutn.kica.model.ComicCategory
import top.ntutn.kica.model.ComicPage
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.DownloadTask
import top.ntutn.kica.model.Episode
import top.ntutn.kica.model.HistoryEntry
import top.ntutn.kica.model.NetworkSettings
import top.ntutn.kica.model.PageRef
import top.ntutn.kica.model.RankPeriod
import top.ntutn.kica.model.ReadingProgress
import top.ntutn.kica.model.UserSession

interface PicaRepository {
    val session: StateFlow<UserSession?>

    suspend fun restoreSession()
    suspend fun updateNetworkSettings(settings: NetworkSettings)
    suspend fun login(email: String, password: String): UserSession
    suspend fun logout()
    suspend fun recommendations(): List<ComicSummary>
    suspend fun randomComics(): List<ComicSummary>
    suspend fun categories(): List<ComicCategory>
    suspend fun ranking(period: RankPeriod): List<ComicSummary>
    suspend fun search(keyword: String, categories: List<String> = emptyList(), page: Int = 1): ComicPage
    suspend fun favorites(page: Int = 1): ComicPage
    suspend fun comic(id: String): ComicDetail
    suspend fun episodes(comicId: String): List<Episode>
    suspend fun pages(comicId: String, episodeId: String): List<PageRef>
    suspend fun toggleFavorite(comicId: String): Boolean
    suspend fun like(comicId: String): Boolean
}

interface LibraryRepository {
    fun history(): Flow<List<HistoryEntry>>
    fun downloads(): Flow<List<DownloadTask>>
    fun settings(): Flow<AppSettings>
    suspend fun cachedRecommendations(): List<ComicSummary>?
    suspend fun cacheRecommendations(recommendations: List<ComicSummary>)
    suspend fun cachedRandomComics(): List<ComicSummary>?
    suspend fun cacheRandomComics(comics: List<ComicSummary>)
    suspend fun cachedCategories(): List<ComicCategory>?
    suspend fun cacheCategories(categories: List<ComicCategory>)
    suspend fun readingProgress(comicId: String, episodeId: String): ReadingProgress?
    suspend fun saveProgress(progress: ReadingProgress)
    suspend fun addHistory(entry: HistoryEntry)
    suspend fun upsertDownload(task: DownloadTask)
    suspend fun removeDownload(id: String)
    suspend fun updateSettings(settings: AppSettings)
    suspend fun clearCache()
}

interface DownloadCoordinator {
    val tasks: StateFlow<List<DownloadTask>>
    suspend fun restore()
    suspend fun enqueue(comic: ComicSummary, episode: Episode, targetLocation: String = ""): DownloadTask
    suspend fun pause(id: String)
    suspend fun resume(id: String)
    suspend fun cancel(id: String)
    suspend fun retry(id: String)
}

interface CredentialStore {
    suspend fun readToken(): String?
    suspend fun writeToken(token: String)
    suspend fun clearToken()
}

interface FileLocationProvider {
    suspend fun defaultDownloadLocation(): String
    suspend fun chooseExportLocation(): String?
    suspend fun downloadedPages(task: DownloadTask): List<PageRef>
}

interface PlatformServices {
    val platformName: String
    val isDesktop: Boolean
    val credentialStore: CredentialStore
    val fileLocationProvider: FileLocationProvider
    suspend fun shareFile(path: String): Boolean
    suspend fun openExternalUrl(url: String): Boolean
    suspend fun copyImage(page: PageRef): Boolean
    suspend fun saveImage(page: PageRef): Boolean
}
