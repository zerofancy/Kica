package top.ntutn.kica.update

import kotlin.time.Instant
import top.ntutn.updater.DownloadProgress
import top.ntutn.updater.DownloadedFile
import top.ntutn.updater.UpdateRelease

/** UI renders this state only when its foreground/unlocked/non-reader gate allows it. */
data class UpdateState(
    val checking: Boolean = false,
    val available: UpdateRelease? = null,
    val feedback: UpdateFeedback? = null,
    val dialogVisible: Boolean = false,
    val download: UpdateDownloadState = UpdateDownloadState.Idle,
    val browserOpening: Boolean = false,
    val autoCheckEnabled: Boolean = true,
) {
    val downloading: Boolean
        get() = download is UpdateDownloadState.Downloading || download is UpdateDownloadState.Finalizing
}

sealed interface UpdateFeedback {
    data class Disabled(val reason: String) : UpdateFeedback
    data object UpToDate : UpdateFeedback
    data object NoMatchingPackage : UpdateFeedback
    data class Failure(val error: UpdateFailure) : UpdateFeedback
    /** The browser accepted the handoff; this does not mean downloaded or installed. */
    data object BrowserDispatched : UpdateFeedback
}

/** All messages are local constants, never exception messages, service bodies or URLs. */
data class UpdateFailure(
    val kind: UpdateFailureKind,
    val message: String,
    val retryAt: Instant? = null,
    val httpStatus: Int? = null,
    val serviceCode: String? = null,
)

enum class UpdateFailureKind {
    Configuration, AmbiguousRelease, ServiceUnavailable, Network, Timeout, Http,
    InvalidResponse, UnsafeUrl, Unavailable, FileIo, InsufficientSpace,
    InvalidFile, ChecksumMismatch, Busy, Unknown,
}

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(val progress: DownloadProgress) : UpdateDownloadState
    data class Finalizing(val progress: DownloadProgress) : UpdateDownloadState
    /** Export to the platform's final location has finished, not installation. */
    data class Completed(val file: DownloadedFile) : UpdateDownloadState
    data class Failed(val error: UpdateFailure) : UpdateDownloadState
}
