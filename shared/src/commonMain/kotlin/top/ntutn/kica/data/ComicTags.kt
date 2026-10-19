package top.ntutn.kica.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.AppSettings
import kotlin.time.Clock

fun tagKey(tag: String): String = tag.trim().lowercase()

fun cleanTags(tags: List<String>): List<String> =
    tags.map(String::trim).filter(String::isNotEmpty).distinctBy(::tagKey)

data class TagMetadata(
    val comicId: String,
    val tags: List<String>,
    val verifiedAt: Long,
)

data class ResolvedTags(val tags: List<String>?, val stale: Boolean = false)

enum class ComicVisibility { VISIBLE, BLOCKED, UNKNOWN }

fun comicVisibility(comic: ComicSummary, settings: AppSettings, tags: List<String>? = comic.tags): ComicVisibility {
    if (comic.categories.any(settings.blockedCategories::contains)) return ComicVisibility.BLOCKED
    if (settings.blockedTags.isEmpty()) return ComicVisibility.VISIBLE
    if (tags == null) return ComicVisibility.UNKNOWN
    val keys = settings.blockedTags.map(::tagKey).toSet()
    return if (tags.any { tagKey(it) in keys }) ComicVisibility.BLOCKED else ComicVisibility.VISIBLE
}

class ComicTagResolver(
    private val fetchTags: suspend (String) -> List<String>?,
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    constructor(repository: PicaRepository, library: LibraryRepository, scope: CoroutineScope) :
        this({ repository.comic(it).tags }, library, scope)
    private data class Flight(val result: Deferred<ResolvedTags>, var users: Int)
    private val mutex = Mutex()
    private val permits = Semaphore(3)
    private val flights = mutableMapOf<String, Flight>()
    private val mutableUpdates = MutableStateFlow<Map<String, TagMetadata>>(emptyMap())
    val updates = mutableUpdates.asStateFlow()

    fun clearMemoryCache() { mutableUpdates.value = emptyMap() }

    suspend fun rememberTags(comicId: String, tags: List<String>?) {
        if (tags == null) return
        val metadata = TagMetadata(comicId, cleanTags(tags), now())
        library.saveTagMetadata(metadata)
        mutableUpdates.update { current ->
            (current + (comicId to metadata)).entries.sortedByDescending { it.value.verifiedAt }
                .take(2000).associate { it.toPair() }
        }
    }

    suspend fun resolve(comic: ComicSummary): ResolvedTags {
        val flight = mutex.withLock {
            flights[comic.id]?.also { it.users++ } ?: Flight(
                scope.async { load(comic) }, 1,
            ).also { flights[comic.id] = it }
        }
        return try {
            flight.result.await()
        } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                mutex.withLock {
                    flight.users--
                    if (flight.users == 0) {
                        if (flights[comic.id] === flight) flights.remove(comic.id)
                        flight.result.cancel()
                    }
                }
            }
        }
    }

    private suspend fun load(comic: ComicSummary): ResolvedTags {
        val cached = cancellableResult { library.tagMetadata(comic.id) }.getOrNull()
        if (cached != null && now() - cached.verifiedAt in 0 until CACHE_AGE) {
            return ResolvedTags(cached.tags)
        }
        if (cached == null && comic.tags != null) return ResolvedTags(cleanTags(comic.tags))
        return try {
            permits.withPermit {
                val tags = fetchTags(comic.id) ?: return@withPermit ResolvedTags(null)
                rememberTags(comic.id, tags)
                ResolvedTags(cleanTags(tags))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (cached == null) ResolvedTags(null) else ResolvedTags(cached.tags, stale = true)
        }
    }

    companion object {
        const val CACHE_AGE = 7L * 24 * 60 * 60 * 1000
    }
}

suspend fun <T> cancellableResult(block: suspend () -> T): Result<T> = try {
    val result = block()
    kotlinx.coroutines.currentCoroutineContext().ensureActive()
    Result.success(result)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}
