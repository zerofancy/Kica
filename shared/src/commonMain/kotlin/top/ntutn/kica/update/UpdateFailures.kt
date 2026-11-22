package top.ntutn.kica.update

import top.ntutn.updater.BrowserOpenError
import top.ntutn.updater.DownloadError
import top.ntutn.updater.DownloadErrorKind
import top.ntutn.updater.UpdateError
import top.ntutn.updater.UpdateErrorKind

internal fun UpdateError.toFailure(): UpdateFailure {
    val kind = when (kind) {
        UpdateErrorKind.ServiceConfiguration -> UpdateFailureKind.Configuration
        UpdateErrorKind.AmbiguousLatest -> UpdateFailureKind.AmbiguousRelease
        UpdateErrorKind.ServiceUnavailable -> UpdateFailureKind.ServiceUnavailable
        UpdateErrorKind.Network -> UpdateFailureKind.Network
        UpdateErrorKind.Timeout -> UpdateFailureKind.Timeout
        UpdateErrorKind.Http -> UpdateFailureKind.Http
        UpdateErrorKind.InvalidResponse -> UpdateFailureKind.InvalidResponse
        UpdateErrorKind.UnsafeDownloadUrl -> UpdateFailureKind.UnsafeUrl
        UpdateErrorKind.Closed -> UpdateFailureKind.Unavailable
    }
    return failure(kind).copy(retryAt = retryAt, httpStatus = httpStatus, serviceCode = serviceCode)
}

internal fun DownloadError.toFailure(): UpdateFailure {
    val kind = when (kind) {
        DownloadErrorKind.Network -> UpdateFailureKind.Network
        DownloadErrorKind.Timeout -> UpdateFailureKind.Timeout
        DownloadErrorKind.Http -> UpdateFailureKind.Http
        DownloadErrorKind.UnsafeUrl -> UpdateFailureKind.UnsafeUrl
        DownloadErrorKind.InsufficientSpace -> UpdateFailureKind.InsufficientSpace
        DownloadErrorKind.FileIo -> UpdateFailureKind.FileIo
        DownloadErrorKind.Busy -> UpdateFailureKind.Busy
        DownloadErrorKind.Closed -> UpdateFailureKind.Unavailable
        DownloadErrorKind.ChecksumMismatch -> UpdateFailureKind.ChecksumMismatch
        DownloadErrorKind.TooManyRedirects, DownloadErrorKind.UnsupportedContent,
        DownloadErrorKind.EmptyFile, DownloadErrorKind.SizeMismatch,
        DownloadErrorKind.TooLarge -> UpdateFailureKind.InvalidFile
    }
    return failure(kind).copy(retryAt = retryAt, httpStatus = httpStatus)
}

internal fun BrowserOpenError.toFailure(): UpdateFailure = failure(
    if (this == BrowserOpenError.UnsafeUrl) UpdateFailureKind.UnsafeUrl else UpdateFailureKind.Unavailable,
)

internal fun failure(kind: UpdateFailureKind = UpdateFailureKind.Unknown): UpdateFailure = UpdateFailure(
    kind = kind,
    message = when (kind) {
        UpdateFailureKind.Configuration -> "更新配置异常，请联系维护者。"
        UpdateFailureKind.AmbiguousRelease -> "发布数据冲突，请稍后重试或联系维护者。"
        UpdateFailureKind.ServiceUnavailable -> "更新服务暂不可用，请稍后重试。"
        UpdateFailureKind.Network -> "网络连接失败，请重试。"
        UpdateFailureKind.Timeout -> "更新请求超时，请重试。"
        UpdateFailureKind.Http -> "更新请求失败，请稍后重试。"
        UpdateFailureKind.InvalidResponse -> "更新信息异常，无法继续。"
        UpdateFailureKind.UnsafeUrl -> "更新下载地址不安全，已阻止操作。"
        UpdateFailureKind.Unavailable -> "此更新操作暂不可用。"
        UpdateFailureKind.FileIo -> "无法保存更新文件，请检查下载目录后重试。"
        UpdateFailureKind.InsufficientSpace -> "存储空间不足，请清理后重试。"
        UpdateFailureKind.InvalidFile -> "更新文件无效或大小不符，请重新下载。"
        UpdateFailureKind.ChecksumMismatch -> "更新文件校验失败，请重新下载。"
        UpdateFailureKind.Busy -> "已有更新任务进行中，请稍后重试。"
        UpdateFailureKind.Unknown -> "更新操作失败，请重试。"
    },
)
