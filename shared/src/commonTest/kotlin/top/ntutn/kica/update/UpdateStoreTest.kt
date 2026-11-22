package top.ntutn.kica.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import top.ntutn.updater.DownloadError
import top.ntutn.updater.DownloadErrorKind
import top.ntutn.updater.DownloadPhase
import top.ntutn.updater.DownloadProgress
import top.ntutn.updater.DownloadResult
import top.ntutn.updater.UpdateCheckResult
import top.ntutn.updater.UpdateError
import top.ntutn.updater.UpdateErrorKind

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateStoreTest {
    /** Exercise default Main.immediate, replacing Main only inside each test. */
    private fun storeTest(block: suspend TestScope.(FakeUpdateRuntime, UpdateStore) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = FakeUpdateRuntime()
        val store = UpdateStore(runtime)
        try { block(runtime, store) } finally { store.close(); Dispatchers.resetMain() }
    }

    @Test
    fun automaticIsOncePerRuntimeAndManualUpgradesInFlight() = storeTest { runtime, store ->
        val response = CompletableDeferred<UpdateCheckResult>()
        runtime.checkBlock = { response.await() }
        repeat(3) { store.automaticCheck() }
        runCurrent()
        assertEquals(1, runtime.checks)
        assertFalse(store.state.value.dialogVisible)
        store.check()
        store.check()
        runCurrent()
        assertTrue(store.state.value.checking)
        assertTrue(store.state.value.dialogVisible)
        response.complete(UpdateCheckResult.NoMatchingPackage)
        runCurrent()
        assertEquals(1, runtime.checks)
        assertIs<UpdateFeedback.NoMatchingPackage>(store.state.value.feedback)
        assertFalse(store.state.value.checking)
        store.automaticCheck()
        runCurrent()
        assertEquals(1, runtime.checks)
    }

    @Test
    fun silentFailureNeverOpensAndDisabledRuntimeNeverCallsNetwork() = storeTest { runtime, store ->
        runtime.checkBlock = { throw IllegalStateException("https://secret.example/token") }
        store.automaticCheck()
        runCurrent()
        assertFalse(store.state.value.dialogVisible)
        val error = assertIs<UpdateFeedback.Failure>(store.state.value.feedback).error
        assertEquals(UpdateFailureKind.Unknown, error.kind)
        assertFalse(error.message.contains("secret"))
        val disabledRuntime = FakeUpdateRuntime(enabled = false)
        val disabled = UpdateStore(disabledRuntime)
        try {
            disabled.automaticCheck()
            runCurrent()
            assertNull(disabled.state.value.feedback)
            disabled.check()
            runCurrent()
            assertIs<UpdateFeedback.Disabled>(disabled.state.value.feedback)
            assertEquals(0, disabledRuntime.checks)
            assertFalse(disabledRuntime.claimed)
        } finally { disabled.close() }
    }

    @Test
    fun preferenceAndProcessLatchSurviveStoreRecreation() = storeTest { runtime, store ->
        store.setAutoCheckEnabled(false)
        runCurrent()
        store.automaticCheck()
        runCurrent()
        assertEquals(0, runtime.checks)
        assertFalse(runtime.claimed)
        assertFalse(store.state.value.autoCheckEnabled)
        store.setAutoCheckEnabled(true)
        runCurrent()
        store.automaticCheck()
        runCurrent()
        store.close()
        val recreated = UpdateStore(runtime)
        try {
            recreated.automaticCheck()
            runCurrent()
            assertEquals(1, runtime.checks)
            assertEquals(0, runtime.closes)
        } finally { recreated.close() }
    }

    @Test
    fun dismissalOnlyHidesAndManualCheckCanRedisplayRelease() = storeTest { runtime, store ->
        store.automaticCheck()
        runCurrent()
        assertEquals(testRelease, store.state.value.available)
        store.dismiss()
        runCurrent()
        assertFalse(store.state.value.dialogVisible)
        store.show()
        runCurrent()
        assertTrue(store.state.value.dialogVisible)
        store.dismiss()
        store.check()
        runCurrent()
        assertTrue(store.state.value.dialogVisible)
        assertEquals(2, runtime.checks)
    }

    @Test
    fun checkCooldownUsesInjectedClockAndClearsStaleRelease() = runTest {
        var now = Instant.fromEpochSeconds(100)
        val runtime = FakeUpdateRuntime()
        val store = UpdateStore(runtime, StandardTestDispatcher(testScheduler), clock = { now })
        try {
            store.check()
            runCurrent()
            runtime.checkBlock = {
                UpdateCheckResult.Failed(UpdateError(UpdateErrorKind.Http, 429, retryAt = Instant.fromEpochSeconds(200)))
            }
            store.check()
            runCurrent()
            assertNull(store.state.value.available)
            store.download()
            store.check()
            runCurrent()
            assertEquals(2, runtime.checks)
            assertEquals(0, runtime.downloads)
            assertEquals(Instant.fromEpochSeconds(200), assertIs<UpdateFeedback.Failure>(store.state.value.feedback).error.retryAt)
            now = Instant.fromEpochSeconds(200)
            runtime.checkBlock = { UpdateCheckResult.NoMatchingPackage }
            store.check()
            runCurrent()
            assertEquals(3, runtime.checks)
            assertIs<UpdateFeedback.NoMatchingPackage>(store.state.value.feedback)
        } finally { store.close() }
    }

    @Test
    fun downloadUnknownLengthFinalizesThenExportsBeforeCompleted() = storeTest { runtime, store ->
        val finishDownload = CompletableDeferred<Unit>()
        val finishExport = CompletableDeferred<Unit>()
        val exported = FakeUpdateFile("/system/Downloads/update.exe")
        runtime.downloadBlock = { progress ->
            progress(DownloadProgress(12, null))
            finishDownload.await()
            DownloadResult.Completed(FakeUpdateFile())
        }
        runtime.exportBlock = { finishExport.await(); exported }
        store.check()
        runCurrent()
        store.download()
        store.download()
        runCurrent()
        val progress = assertIs<UpdateDownloadState.Downloading>(store.state.value.download).progress
        assertNull(progress.totalBytes)
        assertEquals(12L, progress.downloadedBytes)
        store.dismiss()
        store.check()
        runCurrent()
        assertTrue(store.state.value.dialogVisible) // manual check reopens progress, but doesn't query again
        assertEquals(1, runtime.checks)
        finishDownload.complete(Unit)
        runCurrent()
        assertIs<UpdateDownloadState.Finalizing>(store.state.value.download)
        assertEquals(1, runtime.exports)
        assertEquals(1, runtime.downloads)
        finishExport.complete(Unit)
        runCurrent()
        assertEquals(exported, assertIs<UpdateDownloadState.Completed>(store.state.value.download).file)
    }

    @Test
    fun cancellationCleansSdkPartialAndIgnoresLateProgress() = storeTest { runtime, store ->
        var cleanup = false
        var progressCallback: ((DownloadProgress) -> Unit)? = null
        runtime.downloadBlock = { progress ->
            progressCallback = progress
            try { awaitCancellation() } finally { cleanup = true }
        }
        store.check()
        runCurrent()
        store.download()
        runCurrent()
        store.cancelDownload()
        runCurrent()
        assertTrue(cleanup)
        assertIs<UpdateDownloadState.Idle>(store.state.value.download)
        progressCallback!!(DownloadProgress(90, 100, DownloadPhase.Finalizing))
        runCurrent()
        assertIs<UpdateDownloadState.Idle>(store.state.value.download)
        assertEquals(0, runtime.closes)
    }

    @Test
    fun exportFailureKeepsVerifiedSourceAndRetryDoesNotRedownload() = storeTest { runtime, store ->
        runtime.exportBlock = { throw IllegalStateException("private path") }
        store.check()
        runCurrent()
        store.download()
        runCurrent()
        assertEquals(UpdateFailureKind.FileIo, assertIs<UpdateDownloadState.Failed>(store.state.value.download).error.kind)
        assertEquals(0, runtime.deletes)
        runtime.exportBlock = { FakeUpdateFile("/Downloads/update.exe") }
        store.retryDownload()
        runCurrent()
        assertIs<UpdateDownloadState.Completed>(store.state.value.download)
        assertEquals(1, runtime.downloads)
        assertEquals(2, runtime.exports)
    }

    @Test
    fun downloadCooldownAndBrowserHandoffAreNotInstallation() = runTest {
        var now = Instant.fromEpochSeconds(100)
        val runtime = FakeUpdateRuntime()
        val store = UpdateStore(runtime, StandardTestDispatcher(testScheduler), clock = { now })
        try {
            runtime.downloadBlock = {
                DownloadResult.Failed(DownloadError(DownloadErrorKind.Http, 429, Instant.fromEpochSeconds(200)))
            }
            store.check()
            runCurrent()
            store.download()
            runCurrent()
            store.retryDownload()
            runCurrent()
            assertEquals(1, runtime.downloads)
            store.browserDownload()
            runCurrent()
            assertIs<UpdateFeedback.BrowserDispatched>(store.state.value.feedback)
            assertIs<UpdateDownloadState.Failed>(store.state.value.download)
            assertEquals(1, runtime.browserCalls)
            now = Instant.fromEpochSeconds(200)
            store.retryDownload()
            runCurrent()
            assertEquals(2, runtime.downloads)
        } finally { store.close() }
    }

    @Test
    fun completedCheckReopensRetainedReleaseInsteadOfLosingRecoveryControls() = storeTest { runtime, store ->
        store.check()
        runCurrent()
        store.download()
        runCurrent()
        val completed = assertIs<UpdateDownloadState.Completed>(store.state.value.download)
        store.dismiss()
        runCurrent()
        runtime.checkBlock = { UpdateCheckResult.NoMatchingPackage }
        store.check()
        runCurrent()
        assertTrue(store.state.value.dialogVisible)
        assertEquals(testRelease, store.state.value.available)
        assertEquals(completed, store.state.value.download)
        assertEquals(1, runtime.checks)
    }

    @Test
    fun missingCompletedFileCanBeDownloadedAgainWithoutDeletingUserFile() = storeTest { runtime, store ->
        val missing = object : top.ntutn.updater.DownloadedFile by FakeUpdateFile() {
            override suspend fun exists() = false
        }
        runtime.downloadBlock = { DownloadResult.Completed(missing) }
        store.check()
        runCurrent()
        store.download()
        runCurrent()
        val completed = assertIs<UpdateDownloadState.Completed>(store.state.value.download)
        assertFalse(completed.file.exists())
        runtime.downloadBlock = { DownloadResult.Completed(FakeUpdateFile("/Downloads/new.exe")) }
        store.redownload()
        runCurrent()
        val replacement = assertIs<UpdateDownloadState.Completed>(store.state.value.download)
        assertTrue(replacement.file.exists())
        assertEquals("/Downloads/new.exe", replacement.file.localPath)
        assertEquals(2, runtime.downloads)
        assertEquals(0, runtime.deletes)
    }

    @Test
    fun redownloadDoesNotDeleteExportedUserFile() = storeTest { runtime, store ->
        store.check()
        runCurrent()
        store.download()
        runCurrent()
        assertIs<UpdateDownloadState.Completed>(store.state.value.download)
        store.redownload()
        runCurrent()
        assertIs<UpdateDownloadState.Completed>(store.state.value.download)
        assertEquals(2, runtime.downloads)
        assertEquals(0, runtime.deletes)
    }

    @Test
    fun destructionCancelsCheckWithoutClosingAppRuntimeOrDisplayingFailure() = storeTest { runtime, store ->
        var cancelled = false
        runtime.checkBlock = {
            try { awaitCancellation() } finally { cancelled = true }
        }
        store.check()
        runCurrent()
        store.onDestroy()
        runCurrent()
        assertTrue(cancelled)
        assertFalse(store.state.value.checking)
        assertNull(store.state.value.feedback)
        assertEquals(0, runtime.closes)
    }

    @Test
    fun malformedAndNoPackageResultsNeverBecomeUpToDate() = storeTest { runtime, store ->
        val cases = listOf(
            UpdateCheckResult.NoMatchingPackage,
            UpdateCheckResult.Failed(UpdateError(UpdateErrorKind.AmbiguousLatest, 409)),
            UpdateCheckResult.Failed(UpdateError(UpdateErrorKind.InvalidResponse)),
            UpdateCheckResult.Available(testRelease.copy(downloadUrl = "http://insecure.example/file")),
        )
        for (result in cases) {
            runtime.checkBlock = { result }
            store.check()
            runCurrent()
            assertFalse(store.state.value.feedback is UpdateFeedback.UpToDate)
            assertNull(store.state.value.available)
        }
        assertEquals(UpdateFailureKind.UnsafeUrl, assertIs<UpdateFeedback.Failure>(store.state.value.feedback).error.kind)
    }

    @Test
    fun cancellingDuringExportPreservesCompletedPublishedFile() = storeTest { runtime, store ->
        val finishExport = CompletableDeferred<Unit>()
        val published = FakeUpdateFile("/Downloads/published.exe")
        runtime.exportBlock = { finishExport.await(); published }
        store.check()
        runCurrent()
        store.download()
        runCurrent()
        assertIs<UpdateDownloadState.Finalizing>(store.state.value.download)
        store.cancelDownload()
        runCurrent()
        assertIs<UpdateDownloadState.Finalizing>(store.state.value.download)
        finishExport.complete(Unit)
        runCurrent()
        assertEquals(published, assertIs<UpdateDownloadState.Completed>(store.state.value.download).file)
        assertEquals(0, runtime.deletes)
        store.retryDownload()
        runCurrent()
        assertEquals(1, runtime.downloads)
    }

    @Test
    fun checkDuringPendingExportFailureKeepsExportOnlyRetryReachable() = storeTest { runtime, store ->
        runtime.exportBlock = { throw IllegalStateException() }
        store.check()
        runCurrent()
        store.download()
        runCurrent()
        runtime.checkBlock = { UpdateCheckResult.NoMatchingPackage }
        store.check()
        runCurrent()
        assertEquals(1, runtime.checks)
        assertEquals(testRelease, store.state.value.available)
        runtime.exportBlock = { it }
        store.retryDownload()
        runCurrent()
        assertIs<UpdateDownloadState.Completed>(store.state.value.download)
        assertEquals(1, runtime.downloads)
    }

    @Test
    fun dismissingInFlightManualCheckDoesNotReopenOnResult() = storeTest { runtime, store ->
        val response = CompletableDeferred<UpdateCheckResult>()
        runtime.checkBlock = { response.await() }
        store.check()
        runCurrent()
        store.dismiss()
        runCurrent()
        response.complete(UpdateCheckResult.Available(testRelease))
        runCurrent()
        assertFalse(store.state.value.dialogVisible)
        assertEquals(testRelease, store.state.value.available)
        store.check()
        runCurrent()
        assertTrue(store.state.value.dialogVisible)
    }

    @Test
    fun preferenceWritesRemainInActionOrderWhenFirstWriteSuspends() = storeTest { runtime, store ->
        val firstWrite = CompletableDeferred<Unit>()
        val writes = mutableListOf<Boolean>()
        runtime.preferenceBlock = { enabled ->
            writes += enabled
            if (!enabled) firstWrite.await()
            runtime.autoCheckEnabled.value = enabled
        }
        store.setAutoCheckEnabled(false)
        store.setAutoCheckEnabled(true)
        runCurrent()
        assertEquals(listOf(false), writes)
        firstWrite.complete(Unit)
        runCurrent()
        assertEquals(listOf(false, true), writes)
        assertTrue(store.state.value.autoCheckEnabled)
    }

    @Test
    fun unexpectedBrowserFailureIsSanitizedAndCancellationIsNotFailure() = storeTest { runtime, store ->
        store.check()
        runCurrent()
        runtime.browserBlock = { throw IllegalArgumentException("secret URL") }
        store.browserDownload()
        runCurrent()
        assertEquals(UpdateFailureKind.Unknown, assertIs<UpdateFeedback.Failure>(store.state.value.feedback).error.kind)
        runtime.browserBlock = { throw kotlinx.coroutines.CancellationException() }
        store.browserDownload()
        runCurrent()
        assertFalse(store.state.value.browserOpening)
        assertFalse(assertIs<UpdateFeedback.Failure>(store.state.value.feedback).error.message.contains("secret"))
    }
}
