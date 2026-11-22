package top.ntutn.kica.ui.store

import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import top.ntutn.kica.data.cancellableResult
import top.ntutn.kica.data.TitleTranslationState
import top.ntutn.kica.model.*
import top.ntutn.kica.ui.navigation.AppServices

@Serializable
internal data class LibrarySnapshot(val visibleLimit: Int = 30, val showHidden: Boolean = false)

internal class LibraryStore(private val services: AppServices, snapshot: LibrarySnapshot) : PageStore() {
    private val mutableHistory = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history = mutableHistory.asStateFlow()
    private val mutableTasks = MutableStateFlow(services.downloads.tasks.value)
    val tasks = mutableTasks.asStateFlow()
    private val mutableOptions = MutableStateFlow(snapshot)
    val options = mutableOptions.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private var started = false
    override fun ensureLoaded() {
        if (started) return
        started = true
        scope.launch { services.library.history().collect { mutableHistory.value = it } }
        scope.launch { services.downloads.tasks.collect { mutableTasks.value = it } }
    }
    fun verifyMore() { mutableOptions.value = mutableOptions.value.copy(visibleLimit = mutableOptions.value.visibleLimit + 30) }
    fun toggleHidden() { mutableOptions.value = mutableOptions.value.copy(showHidden = !mutableOptions.value.showHidden) }
    fun pause(id: String) = command { services.downloads.pause(id) }
    fun resume(id: String) = command { services.downloads.resume(id) }
    fun cancel(id: String) = command { services.downloads.cancel(id) }
    private fun command(action: suspend () -> Unit) { scope.launch {
        cancellableResult { action() }.onFailure { mutableError.value = it.message }
    } }
}

@Serializable
internal data class SettingsSnapshot(
    val proxyHost: String = "", val proxyPort: String = "", val hostDirty: Boolean = false,
    val portDirty: Boolean = false,
)

internal class SettingsStore(
    private val services: AppServices,
    snapshot: SettingsSnapshot,
    private val tagResolver: top.ntutn.kica.data.ComicTagResolver? = null,
) : PageStore() {
    private val mutableSettings = MutableStateFlow(AppSettings())
    val settings = mutableSettings.asStateFlow()
    private val mutableDraft = MutableStateFlow(snapshot)
    val draft = mutableDraft.asStateFlow()
    private val mutableTranslation = MutableStateFlow<TitleTranslationState>(services.translation.state.value)
    val translation = mutableTranslation.asStateFlow()
    private val mutableModelAvailable = MutableStateFlow(false)
    val modelAvailable = mutableModelAvailable.asStateFlow()
    private val mutableDownloadLocation = MutableStateFlow("")
    val downloadLocation = mutableDownloadLocation.asStateFlow()
    private val mutableExportLocation = MutableStateFlow<String?>(null)
    val exportLocation = mutableExportLocation.asStateFlow()
    private val mutableSaving = MutableStateFlow(false)
    val saving = mutableSaving.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutex = Mutex()
    private var started = false
    override fun ensureLoaded() {
        if (started) return
        started = true
        scope.launch { services.library.settings().collect {
            mutableSettings.value = it
            mutableDraft.value = mutableDraft.value.copy(
                proxyHost = if (mutableDraft.value.hostDirty) mutableDraft.value.proxyHost else it.network.proxyHost,
                proxyPort = if (mutableDraft.value.portDirty) mutableDraft.value.proxyPort else it.network.proxyPort.takeIf { it > 0 }?.toString().orEmpty())
        } }
        scope.launch { services.translation.state.collect {
            mutableTranslation.value = it
            mutableModelAvailable.value = services.translation.isModelAvailable()
        } }
        execute { mutableDownloadLocation.value = services.platform.fileLocationProvider.defaultDownloadLocation() }
    }
    fun update(transform: (AppSettings) -> AppSettings) = execute {
        mutex.withLock {
            mutableSaving.value = true
            try { services.library.updateSettings(transform(services.library.settings().first())) }
            finally { mutableSaving.value = false }
        }
    }
    fun snapshot(): SettingsSnapshot {
        val current = draft.value
        if (!top.ntutn.kica.ui.navigation.SnapshotPolicy.includeOptional) return SettingsSnapshot()
        return if (current.proxyHost.length <= 1_024) current
            else current.copy(proxyHost = "", hostDirty = false)
    }
    fun host(value: String) {
        mutableDraft.value = mutableDraft.value.copy(proxyHost = value, hostDirty = true)
        update { it.copy(network = it.network.copy(proxyHost = value)) }
    }
    fun port(value: String) {
        if (!value.all(Char::isDigit) || value.length > 5) return
        mutableDraft.value = mutableDraft.value.copy(proxyPort = value, portDirty = true)
        update { it.copy(network = it.network.copy(proxyPort = value.toIntOrNull() ?: 0)) }
    }
    fun enableTranslation() = execute {
        services.translation.enable()
        update { it.copy(titleTranslationEnabled = true) }
    }
    fun disableTranslation() = execute {
        update { it.copy(titleTranslationEnabled = false) }
        services.translation.disable()
    }
    fun cancelTranslation() = services.translation.cancelPreparation()
    fun chooseExport() = execute { mutableExportLocation.value = services.platform.fileLocationProvider.chooseExportLocation() }
    fun clearCache() = execute {
        services.library.clearCache()
        tagResolver?.clearMemoryCache()
        services.translation.clearCache()
    }
    private fun execute(action: suspend () -> Unit) { scope.launch {
        cancellableResult { action() }.onFailure { mutableError.value = it.message }
    } }
}
