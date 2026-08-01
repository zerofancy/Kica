package top.ntutn.kica.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import top.ntutn.kica.data.CredentialStore
import top.ntutn.kica.data.PicaRepository
import top.ntutn.kica.model.ComicDetail
import top.ntutn.kica.model.ComicCategory
import top.ntutn.kica.model.ComicPage
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.Episode
import top.ntutn.kica.model.NetworkSettings
import top.ntutn.kica.model.PageRef
import top.ntutn.kica.model.RankPeriod
import top.ntutn.kica.model.UserSession

class RealPicaRepository(
    private val credentialStore: CredentialStore,
    initialSettings: NetworkSettings = NetworkSettings(),
    private val baseUrl: String = PicaProtocol.BASE_URL,
) : PicaRepository {
    private val mutableSession = MutableStateFlow<UserSession?>(null)
    override val session: StateFlow<UserSession?> = mutableSession.asStateFlow()
    private var service = createService(initialSettings)

    private fun createService(settings: NetworkSettings): PicaService =
        NetworkFactory.service(
            tokenProvider = { mutableSession.value?.token },
            settings = settings,
            baseUrl = baseUrl,
            onUnauthorized = {
                mutableSession.value = null
                runBlocking { credentialStore.clearToken() }
            },
        )

    override suspend fun restoreSession() {
        credentialStore.readToken()?.takeIf { it.isNotBlank() }?.let {
            mutableSession.value = UserSession(it, email = "", displayName = "Kica")
        }
    }

    override suspend fun updateNetworkSettings(settings: NetworkSettings) {
        service = createService(settings)
    }

    override suspend fun login(email: String, password: String): UserSession {
        val envelope = service.login(LoginBody(email, password)).requireSuccess()
        val token = envelope.data.string("token")
            ?: throw PicaApiException("The server did not return a login token.")
        credentialStore.writeToken(token)
        return UserSession(token = token, email = email).also { mutableSession.value = it }
    }

    override suspend fun logout() {
        credentialStore.clearToken()
        mutableSession.value = null
    }

    override suspend fun recommendations(): List<ComicSummary> {
        val data = service.collections().requireSuccess().data
        return data.array("collections")
            .flatMap { collection -> collection.obj()?.array("comics").orEmpty() }
            .mapNotNull(JsonElement::comicSummary)
    }

    override suspend fun randomComics(): List<ComicSummary> =
        service.random().requireSuccess().data.array("comics").mapNotNull(JsonElement::comicSummary)

    override suspend fun categories(): List<ComicCategory> =
        service.categories().requireSuccess().data.array("categories").mapNotNull { element ->
            val item = element.obj()
            if (item.bool("isWeb")) return@mapNotNull null
            val title = item?.string("title") ?: element.primitiveString()
            title?.let {
                ComicCategory(
                    id = item?.string("_id").orEmpty(),
                    title = it,
                    description = item?.string("description").orEmpty(),
                    coverUrl = item?.obj("thumb").imageUrl(),
                )
            }
        }

    override suspend fun ranking(period: RankPeriod): List<ComicSummary> {
        if (period == RankPeriod.KNIGHT) {
            return service.knightRanking().requireSuccess().data
                .array("comics")
                .mapNotNull(JsonElement::comicSummary)
        }
        val wireValue = when (period) {
            RankPeriod.HOURS_24 -> "H24"
            RankPeriod.DAYS_7 -> "D7"
            RankPeriod.DAYS_30 -> "D30"
            RankPeriod.KNIGHT -> error("Handled above")
        }
        return service.ranking(wireValue).requireSuccess().data
            .array("comics")
            .mapNotNull(JsonElement::comicSummary)
    }

    override suspend fun search(keyword: String, categories: List<String>, page: Int): ComicPage {
        val comics = service.search(page, SearchBody(categories, keyword)).requireSuccess().data.obj("comics")
        val items = comics.array("docs").mapNotNull(JsonElement::comicSummary)
        val currentPage = comics.int("page") ?: page
        val totalPages = comics.int("pages")
            ?: if (items.isEmpty()) currentPage else currentPage + 1
        return ComicPage(
            items = items,
            page = currentPage,
            totalPages = totalPages.coerceAtLeast(currentPage),
        )
    }

    override suspend fun favorites(page: Int): List<ComicSummary> {
        val comics = service.favorites(page = page).requireSuccess().data.obj("comics")
        return comics.array("docs").mapNotNull(JsonElement::comicSummary)
    }

    override suspend fun comic(id: String): ComicDetail {
        val value = service.comic(id).requireSuccess().data.obj("comic")
        return value?.comicDetail() ?: throw PicaApiException("Comic data is missing.")
    }

    override suspend fun episodes(comicId: String): List<Episode> {
        val result = mutableListOf<Episode>()
        var page = 1
        var pages = 1
        do {
            val value = service.episodes(comicId, page).requireSuccess().data.obj("eps")
            pages = value.int("pages") ?: 1
            value.array("docs").forEach { element ->
                val item = element.obj() ?: return@forEach
                val order = item.int("order") ?: (result.size + 1)
                result += Episode(
                    id = order.toString(),
                    comicId = comicId,
                    order = order,
                    title = item.string("title") ?: order.toString(),
                )
            }
            page++
        } while (page <= pages)
        return result.sortedBy { it.order }
    }

    override suspend fun pages(comicId: String, episodeId: String): List<PageRef> {
        val result = mutableListOf<PageRef>()
        var page = 1
        var pages = 1
        do {
            val value = service.pages(comicId, episodeId, page).requireSuccess().data.obj("pages")
            pages = value.int("pages") ?: 1
            value.array("docs").forEach { element ->
                val media = element.obj()?.obj("media") ?: return@forEach
                val image = media.imageUrl()
                if (image.isNotBlank()) {
                    result += PageRef(
                        index = result.size,
                        imageUrl = image,
                        originalName = media.string("originalName").orEmpty(),
                    )
                }
            }
            page++
        } while (page <= pages)
        return result
    }

    override suspend fun toggleFavorite(comicId: String): Boolean {
        service.toggleFavorite(comicId).requireSuccess()
        return true
    }

    override suspend fun like(comicId: String): Boolean {
        service.like(comicId).requireSuccess()
        return true
    }
}

class PicaApiException(message: String) : IllegalStateException(SecretRedactor.redact(message))

private val apiLogger = LoggerFactory.getLogger("top.ntutn.kica.network.Api")

private fun ApiEnvelope.requireSuccess(): ApiEnvelope {
    if (code in 200..299 || code == 0 && data != null) return this
    val safeError = SecretRedactor.redact(error.orEmpty())
    val safeMessage = SecretRedactor.redact(message.orEmpty())
    apiLogger.warn(
        "PicACG API error code={} error={} message={}",
        code,
        safeError.ifBlank { "<none>" },
        safeMessage.ifBlank { "<none>" },
    )
    throw PicaApiException(
        buildString {
            append("PicACG request failed with code ")
            append(code)
            error?.takeIf { it.isNotBlank() }?.let {
                append(" (error ")
                append(it)
                append(')')
            }
            message?.takeIf { it.isNotBlank() }?.let {
                append(": ")
                append(it)
            }
        },
    )
}

private fun JsonElement.obj(): JsonObject? = this as? JsonObject

private fun JsonElement.primitiveString(): String? =
    runCatching { jsonPrimitive.contentOrNull }.getOrNull()

private fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject

private fun JsonObject?.array(key: String): List<JsonElement> =
    (this?.get(key) as? JsonArray)?.toList().orEmpty()

private fun JsonObject?.string(key: String): String? =
    this?.get(key)?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }

private fun JsonObject?.int(key: String): Int? =
    this?.get(key)?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }

private fun JsonObject?.bool(key: String): Boolean =
    this?.get(key)?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() } ?: false

private fun JsonElement.comicSummary(): ComicSummary? {
    val item = obj() ?: return null
    val id = item.string("_id") ?: return null
    return ComicSummary(
        id = id,
        title = item.string("title").orEmpty(),
        author = item.string("author").orEmpty(),
        coverUrl = item.obj("thumb").imageUrl(),
        categories = item.array("categories").mapNotNull(JsonElement::primitiveString),
        finished = item.bool("finished"),
        likes = item.int("totalLikes") ?: item.int("likesCount") ?: 0,
        views = item.int("totalViews") ?: 0,
    )
}

private fun JsonObject.comicDetail(): ComicDetail? {
    val id = string("_id") ?: return null
    return ComicDetail(
        id = id,
        title = string("title").orEmpty(),
        author = string("author").orEmpty(),
        description = string("description").orEmpty(),
        coverUrl = obj("thumb").imageUrl(),
        categories = array("categories").mapNotNull(JsonElement::primitiveString),
        tags = array("tags").mapNotNull(JsonElement::primitiveString),
        finished = bool("finished"),
        episodeCount = int("epsCount") ?: 0,
        likes = int("totalLikes") ?: int("likesCount") ?: 0,
        views = int("totalViews") ?: 0,
        comments = int("commentsCount") ?: 0,
        isFavorite = bool("isFavourite"),
        isLiked = bool("isLiked"),
    )
}

private fun JsonObject?.imageUrl(): String {
    val server = this.string("fileServer").orEmpty().trimEnd('/')
    val path = this.string("path").orEmpty().trimStart('/')
    if (server.isBlank()) return ""
    if (path.isBlank()) return server
    return if (server.endsWith("/static")) "$server/$path" else "$server/static/$path"
}
