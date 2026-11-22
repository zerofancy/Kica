package top.ntutn.kica.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import top.ntutn.updater.DownloadError
import top.ntutn.updater.DownloadErrorKind
import top.ntutn.updater.UpdateError
import top.ntutn.updater.UpdateErrorKind

class UpdateFailuresTest {
    @Test
    fun diagnosticsKeepStatusAndCodeWithoutRemoteMessageOrUrl() {
        val retry = Instant.parse("2026-11-22T14:00:00Z")
        val failure = UpdateError(UpdateErrorKind.Http, 429, "RATE_LIMITED", retry).toFailure()
        assertEquals(429, failure.httpStatus)
        assertEquals("RATE_LIMITED", failure.serviceCode)
        assertEquals(retry, failure.retryAt)
        assertEquals("更新请求失败，请稍后重试。", failure.message)
    }

    @Test
    fun downloadDiagnosticsKeepRetryAndStatus() {
        val retry = Instant.parse("2026-11-22T14:00:00Z")
        val failure = DownloadError(DownloadErrorKind.Http, 429, retry).toFailure()
        assertEquals(429, failure.httpStatus)
        assertEquals(retry, failure.retryAt)
        assertEquals(null, failure.serviceCode)
    }
}
