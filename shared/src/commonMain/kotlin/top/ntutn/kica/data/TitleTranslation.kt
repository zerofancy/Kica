package top.ntutn.kica.data

import com.llamatik.library.platform.LlamaBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

object TitleTranslationModel {
    const val FILE_NAME = "Qwen2.5-0.5B-Instruct-Q8_0.gguf"
    const val DOWNLOAD_URL =
        "https://shared-files.ntutn.top/20260811/Qwen2.5-0.5B-Instruct-Q8_0.gguf"
    const val SIZE_BYTES = 531_068_224L
    const val SHA256 = "673eb9fba744c9686488be1ddbb0fbe07b9cf18373bc87c4b1fcab063a7d2aae"
    const val ID = "$FILE_NAME:$SHA256"
    const val TARGET_LANGUAGE = "zh-CN"
    const val PROMPT_VERSION = 4L
}

sealed interface TitleTranslationState {
    data object Disabled : TitleTranslationState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : TitleTranslationState
    data object LoadingModel : TitleTranslationState
    data object Ready : TitleTranslationState
    data class Error(val message: String) : TitleTranslationState
}

interface TitleTranslationModelStore {
    suspend fun isModelAvailable(): Boolean
    suspend fun ensureModel(onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit): String
    fun cancelDownload()
    fun updateNetworkSettings(settings: top.ntutn.kica.model.NetworkSettings)
}

interface TitleTranslationService {
    val enabled: StateFlow<Boolean>
    val state: StateFlow<TitleTranslationState>

    suspend fun isModelAvailable(): Boolean
    suspend fun enable()
    suspend fun disable()
    fun cancelPreparation()
    suspend fun translate(originalTitle: String): String?
    suspend fun clearCache()
    fun updateNetworkSettings(settings: top.ntutn.kica.model.NetworkSettings)
    fun close()
}

interface TitleTranslationEngine {
    fun load(modelPath: String): Boolean
    fun translate(source: String): String
    fun cancel()
    fun shutdown()
}

class LlamatikTitleTranslationEngine : TitleTranslationEngine {
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(modelPath: String): Boolean {
        LlamaBridge.updateGenerateParams(
            temperature = 0f,
            maxTokens = 64,
            topP = 1f,
            topK = 1,
            repeatPenalty = 1.05f,
            contextLength = 512,
            numThreads = 4,
            useMmap = true,
            flashAttention = false,
            batchSize = 128,
            gpuLayers = 0,
        )
        return LlamaBridge.initGenerateModel(modelPath)
    }

    override fun translate(source: String): String {
        val firstMessages = listOf(
            "system" to SYSTEM_PROMPT,
            "user" to "请把下面的作品标题翻译成简体中文。\n原标题：$source",
        )
        // Llamatik 1.9.1 的 Windows generateJson JNI 路径会让原生 C++ 异常越过 JNI，
        // 进而直接终止 JVM。这里使用稳定的普通生成入口，并严格校验返回的单行标题。
        val first = generateTranslation(firstMessages)
        if (isAcceptableTitleTranslation(source, first)) return first

        val retryMessages = listOf(
            "system" to SYSTEM_PROMPT,
            "user" to "上次结果不是简体中文。请重新翻译，不能输出日语或英语。" +
                "忽略标题中的“中国翻訳”等标签。\n原标题：$source\n上次结果：$first",
        )
        val retried = generateTranslation(retryMessages)
        require(isAcceptableTitleTranslation(source, retried)) {
            "模型返回了无效标题"
        }
        return retried
    }

    override fun cancel() = LlamaBridge.nativeCancelGenerate()

    override fun shutdown() = LlamaBridge.shutdown()

    private fun generateTranslation(messages: List<Pair<String, String>>): String {
        val prompt = LlamaBridge.applyChatTemplate(messages, addAssistantPrefix = true)
            ?: qwenFallbackPrompt(messages)
        return parseTitleTranslationOutput(LlamaBridge.generate(prompt), json)
    }

    private fun qwenFallbackPrompt(messages: List<Pair<String, String>>): String = buildString {
        messages.forEach { (role, content) ->
            append("<|im_start|>")
            append(role)
            append('\n')
            append(content)
            append("<|im_end|>\n")
        }
        append("<|im_start|>assistant\n")
    }

    private companion object {
        const val SYSTEM_PROMPT =
            "你是作品标题翻译器。把用户提供的原标题翻译为简体中文；如果原标题已经是简体中文则原样返回。" +
                "原标题中的“中国翻訳”、DL版等标签不代表正文已经翻译。保留专有名词、数字和标点，" +
                "禁止把日文翻成英文，不解释、不复述指令、不补充信息、不使用 JSON。只输出实际译文标题。"
    }
}

internal fun parseTitleTranslationJson(response: String, json: Json = Json): String {
    val trimmed = response.trim()
    val objectText = trimmed.indexOf('{').takeIf { it >= 0 }?.let { start ->
        trimmed.lastIndexOf('}').takeIf { it >= start }?.let { end -> trimmed.substring(start, end + 1) }
    } ?: return ""
    return runCatching {
        json.parseToJsonElement(objectText)
            .jsonObject["translation"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            .orEmpty()
    }.getOrDefault("")
}

internal fun parseTitleTranslationOutput(response: String, json: Json = Json): String {
    parseTitleTranslationJson(response, json).takeIf { it.isNotEmpty() }?.let { return it }
    return response.trim()
        .removePrefix("```text").removePrefix("```").removeSuffix("```")
        .trim()
        .lineSequence().firstOrNull().orEmpty()
        .removePrefix("译文：").removePrefix("翻译：")
        .trim().trim('"', '\'', '“', '”')
}

internal fun isAcceptableTitleTranslation(source: String, translated: String): Boolean {
    if (translated.isEmpty() || translated.length > 256) return false
    if (translated in setOf("简体中文标题", "实际译文", "译文")) return false
    if (listOf("上次结果", "上一次结果", "请重新翻译", "原标题", "不能输出日语").any(translated::contains)) {
        return false
    }
    val sourceHasKana = source.any { it in '\u3040'..'\u30ff' || it in '\uff66'..'\uff9f' }
    if (!sourceHasKana) return true
    val translationHasKana = translated.any { it in '\u3040'..'\u30ff' || it in '\uff66'..'\uff9f' }
    val translationHanCount = translated.count { it in '\u3400'..'\u9fff' }
    return !translationHasKana && translationHanCount >= 2
}

class DefaultTitleTranslationService(
    private val modelStore: TitleTranslationModelStore,
    private val cache: TitleTranslationCache,
    private val engine: TitleTranslationEngine = LlamatikTitleTranslationEngine(),
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : TitleTranslationService {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val preparationMutex = Mutex()
    private val modelMutex = Mutex()
    private val inFlightMutex = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<String?>>()
    private val recentFailures = mutableMapOf<String, Long>()

    private val mutableEnabled = MutableStateFlow(false)
    override val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()
    private val mutableState = MutableStateFlow<TitleTranslationState>(TitleTranslationState.Disabled)
    override val state: StateFlow<TitleTranslationState> = mutableState.asStateFlow()
    private var modelLoaded = false

    override suspend fun isModelAvailable(): Boolean = modelStore.isModelAvailable()

    override suspend fun enable() = preparationMutex.withLock {
        if (mutableEnabled.value && mutableState.value == TitleTranslationState.Ready) return@withLock
        mutableEnabled.value = true
        try {
            val modelPath = modelStore.ensureModel { downloaded, total ->
                mutableState.value = TitleTranslationState.Downloading(downloaded, total)
            }
            if (!mutableEnabled.value) throw CancellationException("Title translation was disabled")
            mutableState.value = TitleTranslationState.LoadingModel
            modelMutex.withLock {
                if (!modelLoaded) {
                    check(engine.load(modelPath)) { "无法加载标题翻译模型" }
                    modelLoaded = true
                }
            }
            mutableState.value = TitleTranslationState.Ready
        } catch (error: CancellationException) {
            mutableEnabled.value = false
            mutableState.value = TitleTranslationState.Disabled
            throw error
        } catch (error: Throwable) {
            mutableEnabled.value = false
            mutableState.value = TitleTranslationState.Error(error.message ?: "标题翻译初始化失败")
            throw error
        }
    }

    override suspend fun disable() {
        mutableEnabled.value = false
        modelStore.cancelDownload()
        mutableState.value = TitleTranslationState.Disabled
        if (modelLoaded) {
            engine.cancel()
            modelMutex.withLock {
                if (modelLoaded) {
                    engine.shutdown()
                    modelLoaded = false
                }
            }
        }
    }

    override fun cancelPreparation() {
        mutableEnabled.value = false
        modelStore.cancelDownload()
        mutableState.value = TitleTranslationState.Disabled
    }

    override suspend fun translate(originalTitle: String): String? {
        val source = originalTitle.trim()
        if (!mutableEnabled.value || source.isEmpty()) return null
        val key = cacheKey(source)
        cache.get(key, nowEpochMillis())?.let { return it }
        if (mutableState.value != TitleTranslationState.Ready) return null

        val now = nowEpochMillis()
        val failedAt = inFlightMutex.withLock { recentFailures[source] }
        if (failedAt != null && now - failedAt < FAILURE_COOLDOWN_MILLIS) return null

        val task = inFlightMutex.withLock {
            inFlight[source] ?: serviceScope.async {
                runCatching { translateWithModel(source) }
                    .onFailure {
                        inFlightMutex.withLock { recentFailures[source] = nowEpochMillis() }
                    }
                    .getOrNull()
                    ?.also { cache.put(key, it, nowEpochMillis()) }
            }.also { inFlight[source] = it }
        }
        return try {
            task.await()
        } finally {
            if (task.isCompleted) {
                inFlightMutex.withLock {
                    if (inFlight[source] === task) inFlight.remove(source)
                }
            }
        }
    }

    override suspend fun clearCache() = cache.clear()

    override fun updateNetworkSettings(settings: top.ntutn.kica.model.NetworkSettings) {
        modelStore.updateNetworkSettings(settings)
    }

    override fun close() {
        modelStore.cancelDownload()
        if (modelLoaded) {
            engine.cancel()
            engine.shutdown()
            modelLoaded = false
        }
        serviceScope.cancel()
    }

    private suspend fun translateWithModel(source: String): String = modelMutex.withLock {
        check(mutableEnabled.value && modelLoaded) { "标题翻译模型未就绪" }
        engine.translate(source)
    }

    private fun cacheKey(source: String) = TitleTranslationCacheKey(
        sourceText = source,
        targetLanguage = TitleTranslationModel.TARGET_LANGUAGE,
        modelId = TitleTranslationModel.ID,
        promptVersion = TitleTranslationModel.PROMPT_VERSION,
    )

    private companion object {
        const val FAILURE_COOLDOWN_MILLIS = 5L * 60 * 1_000
    }
}
