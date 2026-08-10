package top.ntutn.kica.data

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import top.ntutn.kica.model.NetworkSettings

class TitleTranslationServiceTest {
    @Test
    fun parsesJsonFromPlainOrFencedModelOutput() {
        assertEquals("中文标题", parseTitleTranslationJson("{\"translation\":\"中文标题\"}"))
        assertEquals(
            "中文标题",
            parseTitleTranslationJson("```json\n{\"translation\":\"中文标题\"}\n```"),
        )
        assertEquals("", parseTitleTranslationJson("not json"))
        assertEquals("中文标题", parseTitleTranslationOutput("译文：中文标题"))
    }

    @Test
    fun rejectsPlaceholdersEnglishAndRemainingKanaForJapaneseTitles() {
        assertEquals(false, isAcceptableTitleTranslation("子どもの体育", "简体中文标题"))
        assertEquals(false, isAcceptableTitleTranslation("子どもの体育", "Children's PE"))
        assertEquals(false, isAcceptableTitleTranslation("子どもの体育", "请重新翻译这个原标题"))
        assertEquals(false, isAcceptableTitleTranslation("子どもの体育", "子どもの体育"))
        assertEquals(true, isAcceptableTitleTranslation("子どもの体育", "儿童体育"))
        assertEquals(true, isAcceptableTitleTranslation("中文标题", "中文标题"))
    }

    @Test
    fun disabledServiceNeverInvokesModel() = runTest {
        val engine = FakeEngine()
        val service = service(engine)
        assertNull(service.translate("原文"))
        assertEquals(0, engine.calls.get())
        service.close()
    }

    @Test
    fun deduplicatesSameTitleAndSerializesDifferentTitles() = runTest {
        val engine = FakeEngine()
        val service = service(engine)
        service.enable()

        val same = List(8) { async { service.translate("同じ") } }.awaitAll()
        assertEquals(List(8) { "译:同じ" }, same)
        assertEquals(1, engine.calls.get())

        listOf("一", "二", "三").map { title ->
            async { service.translate(title) }
        }.awaitAll()
        assertEquals(1, engine.maxConcurrent.get())
        service.close()
    }

    @Test
    fun engineFailureFallsBackToOriginalByReturningNull() = runTest {
        val service = service(FakeEngine(fail = true))
        service.enable()
        assertNull(service.translate("坏 JSON"))
        service.close()
    }

    private fun service(engine: TitleTranslationEngine) = DefaultTitleTranslationService(
        modelStore = FakeModelStore(),
        cache = MemoryCache(),
        engine = engine,
    )

    private class FakeEngine(private val fail: Boolean = false) : TitleTranslationEngine {
        val calls = AtomicInteger()
        val maxConcurrent = AtomicInteger()
        private val concurrent = AtomicInteger()

        override fun load(modelPath: String) = true

        override fun translate(source: String): String {
            calls.incrementAndGet()
            val active = concurrent.incrementAndGet()
            maxConcurrent.updateAndGet { maxOf(it, active) }
            try {
                Thread.sleep(30)
                if (fail) error("invalid JSON")
                return "译:$source"
            } finally {
                concurrent.decrementAndGet()
            }
        }

        override fun cancel() = Unit
        override fun shutdown() = Unit
    }

    private class FakeModelStore : TitleTranslationModelStore {
        override suspend fun isModelAvailable() = true
        override suspend fun ensureModel(onProgress: (Long, Long) -> Unit) = "model.gguf"
        override fun cancelDownload() = Unit
        override fun updateNetworkSettings(settings: NetworkSettings) = Unit
    }

    private class MemoryCache : TitleTranslationCache {
        private val values = ConcurrentHashMap<TitleTranslationCacheKey, String>()
        override suspend fun get(key: TitleTranslationCacheKey, nowEpochMillis: Long) = values[key]
        override suspend fun put(key: TitleTranslationCacheKey, translatedText: String, nowEpochMillis: Long) {
            values[key] = translatedText
        }
        override suspend fun clear() = values.clear()
    }
}
