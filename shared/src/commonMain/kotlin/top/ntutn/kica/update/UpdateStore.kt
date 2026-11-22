package top.ntutn.kica.update

import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.ntutn.updater.BrowserOpenResult
import top.ntutn.updater.DownloadPhase
import top.ntutn.updater.DownloadProgress
import top.ntutn.updater.DownloadResult
import top.ntutn.updater.DownloadedFile
import top.ntutn.updater.UpdateCheckResult
import top.ntutn.updater.UpdateRelease
import top.ntutn.updater.isAllowedWebUrl

/**
 * Root-retained UI state. All actions are serialized on [dispatcher]; SDK progress may arrive
 * on any thread. Resources belong to the application runtime, not a page or this Store.
 * [parentScope] supplies test/application cancellation, never an Activity/Window callback.
 */
class UpdateStore(
    private val runtime: UpdateRuntime,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    parentScope: CoroutineScope? = null,
    private val clock: () -> Instant = { Clock.System.now() },
) : InstanceKeeper.Instance {
    val info: UpdateAppInfo get() = runtime.info
    private val supervisor = SupervisorJob(parentScope?.coroutineContext?.get(Job))
    private val scope = CoroutineScope((parentScope?.coroutineContext ?: dispatcher) + supervisor + dispatcher)
    private val mutableState = MutableStateFlow(UpdateState(autoCheckEnabled = runtime.autoCheckEnabled.value))
    val state: StateFlow<UpdateState> = mutableState.asStateFlow()
    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var pendingExport: Pair<UpdateRelease, DownloadedFile>? = null
    private var manualCheck = false
    private var checkDismissed = false
    private val preferenceMutex = Mutex()
    private var checkRetryAt: Instant? = null
    private val dismissedReleases = mutableSetOf<ReleaseKey>()

    init {
        scope.launch {
            runtime.autoCheckEnabled.collect { enabled ->
                mutableState.update { it.copy(autoCheckEnabled = enabled) }
            }
        }
    }

    fun automaticCheck() = action {
        if (info.enabled && runtime.autoCheckEnabled.value && !state.value.downloading &&
            runtime.claimAutomaticCheck()
        ) startCheck(manual = false)
    }

    fun check() = action { startCheck(manual = true) }

    private fun startCheck(manual: Boolean) {
        // Retain a verified file/release until explicit redownload; a recheck must not hide recovery UI.
        if (state.value.downloading || downloadJob != null || pendingExport != null ||
            state.value.download is UpdateDownloadState.Completed
        ) {
            if (manual) showNow()
            return
        }
        if (manual) {
            checkDismissed = false
            manualCheck = true
            mutableState.update { it.copy(dialogVisible = true) }
        }
        if (checkJob != null) return // A manual action upgrades the same in-flight silent request.
        if (!info.enabled || runtime.checker == null) {
            if (manual) mutableState.update {
                it.copy(feedback = UpdateFeedback.Disabled(info.disabledReason ?: "此安装版本不支持更新检查。"))
            }
            manualCheck = false
            return
        }
        if (coolingDown(checkRetryAt)) {
            manualCheck = false
            return
        }
        manualCheck = manual
        checkDismissed = false
        mutableState.update { it.copy(checking = true, feedback = null) }
        checkJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = try {
                    runtime.checker!!.check()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                currentCoroutineContext().ensureActive()
                applyCheck(result)
            } finally {
                checkJob = null
                manualCheck = false
                mutableState.update { it.copy(checking = false) }
            }
        }
        checkJob?.start()
    }

    private fun applyCheck(result: UpdateCheckResult?) {
        checkRetryAt = (result as? UpdateCheckResult.Failed)?.error?.retryAt
        when (result) {
            is UpdateCheckResult.Available -> {
                // Reject unsafe handoff URLs even for a custom/injected checker.
                if (!isAllowedWebUrl(result.release.downloadUrl)) {
                    setCheckFailure(failure(UpdateFailureKind.UnsafeUrl))
                    return
                }
                if (checkDismissed) dismissedReleases += result.release.key()
                val previous = state.value.available
                mutableState.update {
                    it.copy(
                        available = result.release,
                        feedback = null,
                        download = if (previous == result.release) it.download else UpdateDownloadState.Idle,
                        dialogVisible = it.dialogVisible || (!checkDismissed &&
                            (manualCheck || result.release.key() !in dismissedReleases)),
                    )
                }
            }
            is UpdateCheckResult.UpToDate -> setCheckFeedback(UpdateFeedback.UpToDate)
            UpdateCheckResult.NoMatchingPackage -> setCheckFeedback(UpdateFeedback.NoMatchingPackage)
            is UpdateCheckResult.Failed -> setCheckFailure(result.error.toFailure())
            null -> setCheckFailure(failure())
        }
    }

    private fun setCheckFailure(error: UpdateFailure) = setCheckFeedback(UpdateFeedback.Failure(error))

    private fun setCheckFeedback(feedback: UpdateFeedback) {
        mutableState.update { it.copy(available = null, feedback = feedback, dialogVisible = it.dialogVisible || manualCheck) }
    }

    fun download() = action { startDownload() }
    fun retryDownload() = action { startDownload() }

    /** User requested a fresh copy (e.g. the completed file was removed externally). */
    fun redownload() = action {
        if (state.value.checking || downloadJob != null ||
            state.value.download !is UpdateDownloadState.Completed
        ) return@action
        pendingExport = null
        mutableState.update { it.copy(download = UpdateDownloadState.Idle) }
        startDownload() // Never deletes an exported user-owned file.
    }

    private fun startDownload() {
        val snapshot = state.value
        val release = snapshot.available ?: return
        if (!info.enabled || snapshot.checking || downloadJob != null ||
            snapshot.download is UpdateDownloadState.Completed
        ) return
        val error = (snapshot.download as? UpdateDownloadState.Failed)?.error
        if (coolingDown(error?.retryAt)) return
        val downloader = runtime.downloader
        if (downloader == null) {
            mutableState.update { it.copy(download = UpdateDownloadState.Failed(failure(UpdateFailureKind.Unavailable))) }
            return
        }
        mutableState.update {
            it.copy(dialogVisible = true, download = UpdateDownloadState.Downloading(DownloadProgress(0, release.packageSize)))
        }
        downloadJob = scope.launch(start = CoroutineStart.LAZY) {
            var receivingProgress = true
            var unexported: DownloadedFile? = null
            try {
                val task = currentCoroutineContext()[Job]
                val savedFile = pendingExport?.takeIf { it.first == release }?.second
                val result = if (savedFile != null) DownloadResult.Completed(savedFile)
                else downloader.download(release) { progress ->
                    scope.launch {
                        if (receivingProgress && task?.isActive == true && state.value.downloading) {
                            mutableState.update {
                                it.copy(download = if (progress.phase == DownloadPhase.Finalizing) {
                                    UpdateDownloadState.Finalizing(progress)
                                } else UpdateDownloadState.Downloading(progress))
                            }
                        }
                    }
                }
                when (result) {
                    is DownloadResult.Completed -> {
                        receivingProgress = false
                        unexported = result.file
                        pendingExport = release to result.file
                        currentCoroutineContext().ensureActive()
                        mutableState.update {
                            it.copy(download = UpdateDownloadState.Finalizing(
                                DownloadProgress(result.file.sizeBytes, result.file.sizeBytes, DownloadPhase.Finalizing),
                            ))
                        }
                        // Publishing to user Downloads is a commit: cancellation cannot discard its handle.
                        // Stay on the Store dispatcher and save Completed before leaving the protected phase.
                        withContext(NonCancellable) {
                            val file = runtime.exportDownload(result.file)
                            unexported = null
                            pendingExport = null
                            mutableState.update { it.copy(download = UpdateDownloadState.Completed(file)) }
                        }
                    }
                    is DownloadResult.Failed -> mutableState.update {
                        it.copy(download = UpdateDownloadState.Failed(result.error.toFailure()))
                    }
                }
            } catch (cancelled: CancellationException) {
                mutableState.update {
                    if (it.download is UpdateDownloadState.Completed) it
                    else it.copy(download = UpdateDownloadState.Idle)
                }
                throw cancelled // SDK cancellation includes partial-file cleanup.
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(download = UpdateDownloadState.Failed(failure(
                        if (unexported != null) UpdateFailureKind.FileIo else UpdateFailureKind.Unknown,
                    )))
                }
            } finally {
                // Preserve verified SDK files on export failure for retry; never delete user Downloads.
                receivingProgress = false
                downloadJob = null
            }
        }
        downloadJob?.start()
    }

    /** Cancels SDK transfer/verification; final export is a non-cancellable commit to user Downloads. */
    fun cancelDownload() = action { downloadJob?.cancel() }
    fun dismiss() = action {
        if (checkJob != null) {
            checkDismissed = true
            manualCheck = false
        }
        state.value.available?.let { dismissedReleases += it.key() }
        mutableState.update { it.copy(dialogVisible = false) }
    }
    fun show() = action { showNow() }
    private fun showNow() { mutableState.update { it.copy(dialogVisible = true) } }

    fun browserDownload() = action {
        val snapshot = state.value
        val release = snapshot.available ?: return@action
        if (!info.enabled || snapshot.browserOpening || snapshot.downloading) return@action
        val previous = (snapshot.feedback as? UpdateFeedback.Failure)?.error
        if (coolingDown(previous?.retryAt)) return@action
        mutableState.update { it.copy(browserOpening = true) }
        scope.launch {
            try {
                val result = if (!isAllowedWebUrl(release.downloadUrl)) {
                    UpdateFeedback.Failure(failure(UpdateFailureKind.UnsafeUrl))
                } else when (val handoff = runtime.browser?.open(release.downloadUrl)) {
                    BrowserOpenResult.Dispatched -> UpdateFeedback.BrowserDispatched
                    is BrowserOpenResult.Failed -> UpdateFeedback.Failure(handoff.reason.toFailure())
                    null -> UpdateFeedback.Failure(failure(UpdateFailureKind.Unavailable))
                }
                mutableState.update { it.copy(feedback = result) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(feedback = UpdateFeedback.Failure(failure())) }
            } finally {
                mutableState.update { it.copy(browserOpening = false) }
            }
        }
    }

    fun setAutoCheckEnabled(enabled: Boolean) = action {
        try {
            preferenceMutex.withLock { runtime.setAutoCheckEnabled(enabled) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.update { it.copy(feedback = UpdateFeedback.Failure(failure())) }
        }
    }

    private fun coolingDown(retryAt: Instant?): Boolean = retryAt != null && clock() < retryAt
    private fun action(block: suspend () -> Unit) { scope.launch { block() } }

    override fun onDestroy() { supervisor.cancel() }

    /** Gracefully finish cancellation/SDK cleanup; never closes the app-owned shared runtime. */
    suspend fun close() { supervisor.cancelAndJoin() }

    private data class ReleaseKey(val version: Long, val distribution: String?, val pkg: String?)
    private fun UpdateRelease.key() = ReleaseKey(version.code, distributionId, packageId)
}
