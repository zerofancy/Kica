package top.ntutn.kica.ui

import top.ntutn.kica.data.FileLocationProvider
import top.ntutn.kica.model.DownloadTask
import top.ntutn.kica.model.PageRef

/** Local image fixtures never invoke a native folder picker or retain a window. */
internal object ReaderTestFiles : FileLocationProvider {
    override suspend fun defaultDownloadLocation(): String = ""
    override suspend fun chooseExportLocation(): String? = null
    override suspend fun downloadedPages(task: DownloadTask): List<PageRef> = emptyList()
}
