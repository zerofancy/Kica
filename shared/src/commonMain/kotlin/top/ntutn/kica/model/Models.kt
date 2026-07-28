package top.ntutn.kica.model

import kotlinx.serialization.Serializable

@Serializable
data class UserSession(
    val token: String,
    val email: String,
    val displayName: String = email,
)

@Serializable
data class ComicSummary(
    val id: String,
    val title: String,
    val author: String = "",
    val coverUrl: String = "",
    val categories: List<String> = emptyList(),
    val finished: Boolean = false,
    val likes: Int = 0,
    val views: Int = 0,
)

@Serializable
data class ComicCategory(
    val id: String = "",
    val title: String,
    val description: String = "",
    val coverUrl: String = "",
)

@Serializable
data class ComicDetail(
    val id: String,
    val title: String,
    val author: String = "",
    val description: String = "",
    val coverUrl: String = "",
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val finished: Boolean = false,
    val episodeCount: Int = 0,
    val likes: Int = 0,
    val views: Int = 0,
    val comments: Int = 0,
    val isFavorite: Boolean = false,
    val isLiked: Boolean = false,
)

@Serializable
data class Episode(
    val id: String,
    val comicId: String,
    val order: Int,
    val title: String,
    val pageCount: Int = 0,
)

@Serializable
data class PageRef(
    val index: Int,
    val imageUrl: String,
    val originalName: String = "",
    val localPath: String? = null,
)

@Serializable
data class ReadingProgress(
    val comicId: String,
    val episodeId: String,
    val pageIndex: Int,
    val mode: ReaderMode = ReaderMode.VERTICAL,
    val updatedAtEpochMillis: Long,
)

@Serializable
enum class ReaderMode {
    VERTICAL,
    PAGED_LEFT_TO_RIGHT,
    PAGED_RIGHT_TO_LEFT,
    DOUBLE_LEFT_TO_RIGHT,
    DOUBLE_RIGHT_TO_LEFT,
}

@Serializable
data class HistoryEntry(
    val comic: ComicSummary,
    val episodeId: String,
    val episodeTitle: String,
    val pageIndex: Int,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class DownloadTask(
    val id: String,
    val comic: ComicSummary,
    val episode: Episode,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val completedPages: Int = 0,
    val totalPages: Int = episode.pageCount,
    val retryCount: Int = 0,
    val targetLocation: String = "",
    val error: String? = null,
)

@Serializable
enum class DownloadStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
}

@Serializable
enum class RankPeriod {
    HOURS_24,
    DAYS_7,
    DAYS_30,
    KNIGHT,
}

@Serializable
enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

@Serializable
enum class ProxyMode {
    DIRECT,
    SYSTEM,
    HTTP,
    SOCKS5,
}

@Serializable
data class NetworkSettings(
    val proxyMode: ProxyMode = ProxyMode.SYSTEM,
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val requestTimeoutSeconds: Int = 10,
)

@Serializable
data class AppSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val readerMode: ReaderMode = ReaderMode.VERTICAL,
    val desktopDownloadConcurrency: Int = 5,
    val androidDownloadConcurrency: Int = 3,
    val network: NetworkSettings = NetworkSettings(),
)

sealed interface LoadState<out T> {
    data object Idle : LoadState<Nothing>
    data object Loading : LoadState<Nothing>
    data class Data<T>(val value: T, val fromCache: Boolean = false) : LoadState<T>
    data class Error(val message: String, val canRetry: Boolean = true) : LoadState<Nothing>
}

sealed interface AppRoute {
    data object Home : AppRoute
    data object Discover : AppRoute
    data object Favorites : AppRoute
    data object History : AppRoute
    data object Downloads : AppRoute
    data object Settings : AppRoute
    data class Search(
        val initialQuery: String = "",
        val category: String? = null,
    ) : AppRoute
    data class Detail(val comicId: String) : AppRoute
    data class Reader(val comicId: String, val episodeId: String) : AppRoute
}
