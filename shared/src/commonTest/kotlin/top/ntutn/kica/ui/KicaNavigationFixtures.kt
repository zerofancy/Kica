package top.ntutn.kica.ui

import com.arkivanov.decompose.DecomposeSettings
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.InstanceKeeperDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.create
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.statekeeper.SerializableContainer
import com.arkivanov.essenty.statekeeper.StateKeeper
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import top.ntutn.kica.data.*
import top.ntutn.kica.model.*
import top.ntutn.kica.ui.navigation.AppServices
import top.ntutn.kica.ui.navigation.MainComponent
import top.ntutn.kica.ui.navigation.RootComponent

/** Headless shared logic tests use one controlled Main scheduler, not a real Swing EDT. */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalDecomposeApi::class)
internal fun navigationTest(block: suspend NavigationFixtures.(TestScope) -> Unit) = runTest {
    val settings = DecomposeSettings.settings
    Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
    // Desktop extensions install SwingMainThreadChecker via ServiceLoader. It cannot recognize
    // a commonTest dispatcher; restore the real production policy even when an assertion fails.
    DecomposeSettings.settings = settings.copy(mainThreadCheckEnabled = false)
    val fixtures = NavigationFixtures()
    try {
        fixtures.block(this)
    } finally {
        try {
            fixtures.close()
            runCurrent()
        } finally {
            DecomposeSettings.settings = settings
            Dispatchers.resetMain()
        }
    }
}

internal class NavigationFixtures {
    val credentials = NavigationCredentials()
    val pica = NavigationRepository(credentials)
    private val memoryLibrary = InMemoryLibraryRepository()
    var progressWriteGate: CompletableDeferred<Unit>? = null
    var settingsReadFailures = 0
    var settingsSubscriptions = 0
    val library: LibraryRepository = object : LibraryRepository by memoryLibrary {
        override fun settings(): Flow<AppSettings> = flow {
            settingsSubscriptions++
            if (settingsReadFailures > 0) { settingsReadFailures--; error("Fixture settings read failure") }
            emitAll(memoryLibrary.settings())
        }
        override suspend fun saveProgress(progress: ReadingProgress) {
            progressWriteGate?.await()
            memoryLibrary.saveProgress(progress)
        }
    }
    val downloads = NavigationDownloads()
    val translation = NavigationTranslation()
    val services = AppServices(pica, library, downloads, NavigationPlatform(credentials), translation)
    private val hosts = mutableListOf<NavigationHost>()
    private val mains = mutableListOf<MainComponent>()
    private val roots = mutableListOf<RootComponent>()

    fun host(saved: SerializableContainer? = null, instances: InstanceKeeperDispatcher = InstanceKeeperDispatcher(),
        resumed: Boolean = true): NavigationHost = NavigationHost(saved, instances, resumed).also { hosts += it }

    fun main(host: NavigationHost = host(), keeper: StateKeeper = host.keeper): MainComponent =
        MainComponent(host.context(keeper), services).also { mains += it }

    fun root(host: NavigationHost = host()): RootComponent =
        RootComponent(host.context(), services).also { roots += it }

    suspend fun close() {
        roots.forEach { it.close() }
        mains.forEach { it.close() }
        hosts.forEach { it.lifecycle.destroy() }
        hosts.map { it.instances }.distinct().forEach { it.destroy() }
    }
}

internal class NavigationHost(saved: SerializableContainer?, val instances: InstanceKeeperDispatcher, resumed: Boolean) {
    val lifecycle = LifecycleRegistry().apply { if (resumed) resume() else create() }
    val keeper = StateKeeperDispatcher(saved)
    fun context(stateKeeper: StateKeeper = keeper) = DefaultComponentContext(lifecycle, stateKeeper, instances)
    fun save(): SerializableContainer = Json.decodeFromString(SerializableContainer.serializer(),
        Json.encodeToString(SerializableContainer.serializer(), keeper.save()))
}

internal class NavigationProbe : InstanceKeeper.Instance {
    var destroyed = 0
    override fun onDestroy() { destroyed++ }
}

internal class NavigationCredentials : CredentialStore {
    var marker = "initial-login"
    var readGate: CompletableDeferred<Unit>? = null
    var readFailures = 0
    var readCalls = 0
    var ignoreReadCancellation = false
    override suspend fun readToken(): String? = null
    override suspend fun writeToken(token: String) = Unit
    override suspend fun clearToken() = Unit
    override suspend fun readSessionId(): String {
        readCalls++
        val captured = marker
        if (readFailures > 0) { readFailures--; error("Fixture identity failure") }
        val gate = readGate
        if (ignoreReadCancellation) withContext(NonCancellable) { gate?.await() } else gate?.await()
        return captured
    }
}

/** Main-data calls are counted independently from restore/settings/auth operations. */
internal class NavigationRepository(private val credentials: NavigationCredentials) : PicaRepository {
    override val session = MutableStateFlow<UserSession?>(null)
    var restoreGate: CompletableDeferred<Unit>? = null
    var logoutGate: CompletableDeferred<Unit>? = null
    var restoreCalls = 0
    var restoredEmail: String? = null
    var loginCalls = 0
    var logoutCalls = 0
    var networkFailures = 0
    var networkApplications = 0
    val pageCalls = mutableListOf<String>()
    override suspend fun restoreSession() {
        restoreCalls++
        restoreGate?.await()
        restoredEmail?.let { email -> session.value = session.value?.copy(email = email) }
    }
    override suspend fun updateNetworkSettings(settings: NetworkSettings) {
        networkApplications++
        if (networkFailures > 0) { networkFailures--; error("Fixture network configuration failure") }
    }
    override suspend fun login(email: String, password: String): UserSession {
        loginCalls++
        credentials.marker = "login-$loginCalls"
        return UserSession("unused-fixture-token", email).also { session.value = it }
    }
    override suspend fun logout() { logoutCalls++; logoutGate?.await(); session.value = null }
    override suspend fun recommendations(): List<ComicSummary> { pageCalls += "home"; return emptyList() }
    override suspend fun randomComics(): List<ComicSummary> { pageCalls += "random"; return emptyList() }
    override suspend fun categories(): List<ComicCategory> { pageCalls += "categories"; return emptyList() }
    override suspend fun ranking(period: RankPeriod): List<ComicSummary> { pageCalls += "ranking"; return emptyList() }
    override suspend fun search(keyword: String, categories: List<String>, page: Int): ComicPage {
        pageCalls += "search"; return ComicPage(emptyList(), page, 1)
    }
    override suspend fun favorites(page: Int): ComicPage { pageCalls += "favorites"; return ComicPage(emptyList(), page, 1) }
    override suspend fun comic(id: String): ComicDetail { pageCalls += "comic"; return ComicDetail(id, "Fixture") }
    override suspend fun episodes(comicId: String): List<Episode> { pageCalls += "episodes"; return emptyList() }
    override suspend fun pages(comicId: String, episodeId: String): List<PageRef> { pageCalls += "pages"; return emptyList() }
    override suspend fun toggleFavorite(comicId: String) = true
    override suspend fun like(comicId: String) = true
}

internal class NavigationDownloads : DownloadCoordinator {
    override val tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    var restoreCalls = 0
    override suspend fun restore() { restoreCalls++ }
    override suspend fun enqueue(comic: ComicSummary, episode: Episode, targetLocation: String): DownloadTask =
        DownloadTask("fixture-download", comic, episode)
    override suspend fun pause(id: String) = Unit
    override suspend fun resume(id: String) = Unit
    override suspend fun cancel(id: String) = Unit
    override suspend fun retry(id: String) = Unit
}

internal class NavigationPlatform(override val credentialStore: CredentialStore) : PlatformServices {
    override val platformName = "Headless fixture"
    override val isDesktop = false
    override val saepAvailable = false
    override val fileLocationProvider = object : FileLocationProvider {
        override suspend fun defaultDownloadLocation() = ""
        override suspend fun chooseExportLocation(): String? = null
        override suspend fun downloadedPages(task: DownloadTask): List<PageRef> = emptyList()
    }
    override suspend fun shareFile(path: String) = false
    override suspend fun openExternalUrl(url: String) = false
    override suspend fun copyImage(page: PageRef) = false
    override suspend fun copyText(text: String) = false
    override suspend fun saveImage(page: PageRef) = false
    override fun openAiOperationOptions() = Unit
}

internal class NavigationTranslation : TitleTranslationService {
    override val enabled = MutableStateFlow(false)
    override val state = MutableStateFlow<TitleTranslationState>(TitleTranslationState.Disabled)
    override suspend fun isModelAvailable() = false
    override suspend fun enable() { enabled.value = true }
    override suspend fun disable() { enabled.value = false }
    override fun cancelPreparation() = Unit
    override suspend fun translate(originalTitle: String): String? = null
    override suspend fun clearCache() = Unit
    override fun updateNetworkSettings(settings: NetworkSettings) = Unit
    override fun close() = Unit
}
