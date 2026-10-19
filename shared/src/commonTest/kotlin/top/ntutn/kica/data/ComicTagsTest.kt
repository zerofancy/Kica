package top.ntutn.kica.data

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import top.ntutn.kica.model.AppSettings
import top.ntutn.kica.model.ComicSummary
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ComicTagsTest {
    @Test
    fun matchingUsesWholeTagsAndPreservesUnknown() {
        val comic = ComicSummary("one", "Book")
        val rules = AppSettings(blockedTags = setOf(" 科幻 ", "TRAVEL"))
        assertEquals(ComicVisibility.UNKNOWN, comicVisibility(comic, rules))
        assertEquals(ComicVisibility.VISIBLE, comicVisibility(comic, rules, emptyList()))
        assertEquals(ComicVisibility.VISIBLE, comicVisibility(comic, rules, listOf("科幻冒险")))
        assertEquals(ComicVisibility.BLOCKED, comicVisibility(comic, rules, listOf("travel")))
        assertEquals(ComicVisibility.VISIBLE, comicVisibility(comic, rules, listOf("科幻冒險")))
        assertEquals(ComicVisibility.BLOCKED, comicVisibility(comic.copy(categories = listOf("hidden")),
            rules.copy(blockedCategories = setOf("hidden"))))
        assertEquals(ComicVisibility.VISIBLE, comicVisibility(comic, AppSettings()))
        assertEquals(listOf("旅行", "Travel"), cleanTags(listOf(" 旅行 ", "", "旅行", "Travel", "travel")))
    }

    @Test
    fun legacyJsonDoesNotTurnUnknownTagsIntoEmptyTags() {
        val old = Json.decodeFromString<ComicSummary>("""{"id":"one","title":"Book"}""")
        assertNull(old.tags)
        assertEquals(emptySet(), Json.decodeFromString<AppSettings>("{}").blockedTags)
        assertEquals(emptyList(), Json.decodeFromString<ComicSummary>("""{"id":"one","title":"Book","tags":[]}""").tags)
    }

    @Test
    fun currentCacheWinsAndUnknownIsNeverCachedAsEmpty() = runTest {
        val library = InMemoryLibraryRepository()
        library.saveTagMetadata(TagMetadata("one", listOf("cached"), 100))
        var requests = 0
        val resolver = ComicTagResolver({ requests++; null }, library, this) { 101 }
        assertEquals(listOf("cached"), resolver.resolve(ComicSummary("one", "Book", tags = listOf("list"))).tags)
        assertEquals(0, requests)
        assertNull(resolver.resolve(ComicSummary("unknown", "Book")).tags)
        assertNull(library.tagMetadata("unknown"))
        assertEquals(1, requests)
    }

    @Test
    fun expiredCacheIsRefreshedAndOfflineFallbackIsMarked() = runTest {
        val library = InMemoryLibraryRepository()
        library.saveTagMetadata(TagMetadata("one", listOf("old"), 0))
        var offline = true
        val resolver = ComicTagResolver({ if (offline) error("offline") else emptyList() }, library, this) {
            ComicTagResolver.CACHE_AGE + 1
        }
        assertEquals(ResolvedTags(listOf("old"), stale = true), resolver.resolve(ComicSummary("one", "Book")))
        assertEquals(0L, library.tagMetadata("one")!!.verifiedAt)
        offline = false
        assertEquals(ResolvedTags(emptyList()), resolver.resolve(ComicSummary("one", "Book")))
        assertEquals(emptyList(), library.tagMetadata("one")!!.tags)
    }

    @Test
    fun sharedRequestsSurviveOneConsumerCancellationAndStopAfterLastConsumer() = runTest {
        val library = InMemoryLibraryRepository()
        val release = CompletableDeferred<Unit>()
        var requests = 0
        var stopped = 0
        val resolver = ComicTagResolver({
            requests++
            try { release.await(); listOf("travel") } finally { stopped++ }
        }, library, this)
        val comic = ComicSummary("one", "Book")
        val first = async { resolver.resolve(comic) }
        val second = async { resolver.resolve(comic) }
        runCurrent()
        assertEquals(1, requests)
        first.cancelAndJoin()
        assertEquals(0, stopped)
        release.complete(Unit)
        assertEquals(listOf("travel"), second.await().tags)
        assertEquals(1, stopped)
        val waiting = CompletableDeferred<Unit>()
        var cancelled = false
        val other = ComicTagResolver({ try { waiting.await(); null } finally { cancelled = true } }, library, this)
        val consumer = launch { other.resolve(comic.copy(id = "two")) }
        runCurrent()
        consumer.cancelAndJoin()
        runCurrent()
        assertTrue(cancelled)
        assertNull(library.tagMetadata("two"))
    }

    @Test
    fun globalRequestConcurrencyNeverExceedsThree() = runTest {
        val release = CompletableDeferred<Unit>()
        var active = 0
        var peak = 0
        val resolver = ComicTagResolver({
            active++; peak = maxOf(peak, active)
            try { release.await(); listOf("tag") } finally { active-- }
        }, InMemoryLibraryRepository(), this)
        val requests = (1..12).map { index -> async { resolver.resolve(ComicSummary("$index", "Book")) } }
        runCurrent()
        assertEquals(3, peak)
        release.complete(Unit)
        requests.awaitAll()
        assertEquals(3, peak)
        assertEquals(0, active)
    }

    @Test
    fun editingRulesPreservesSettingsAndCacheClearPreservesRules() = runTest {
        val library = InMemoryLibraryRepository()
        library.updateSettings(AppSettings(blockedCategories = setOf("category"), titleTranslationEnabled = true))
        library.updateBlockedTags { it + "travel" }
        library.saveTagMetadata(TagMetadata("one", listOf("travel"), 100))
        library.clearCache()
        assertNull(library.tagMetadata("one"))
        val settings = library.settings().first()
        assertEquals(setOf("travel"), settings.blockedTags)
        assertEquals(setOf("category"), settings.blockedCategories)
        assertTrue(settings.titleTranslationEnabled)
    }
}
