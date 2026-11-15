package top.ntutn.kica.ui.screen

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.HistoryEntry
import top.ntutn.kica.model.ReadingProgress

/** A single FIFO consumer keeps earlier, slower writes from overwriting newer positions. */
internal class ReaderProgressWriter(
    scope: CoroutineScope,
    library: LibraryRepository,
    onFailure: () -> Unit,
) {
    private data class Write(val progress: ReadingProgress, val comic: ComicSummary?, val title: String)
    private val writes = Channel<Write>(Channel.UNLIMITED)
    private val worker = scope.launch {
        for ((progress, comic, title) in writes) {
            try {
                library.saveProgress(progress)
                comic?.let {
                    library.addHistory(HistoryEntry(
                        comic = it,
                        episodeId = progress.episodeId,
                        episodeTitle = title,
                        pageIndex = progress.pageIndex,
                        updatedAtEpochMillis = progress.updatedAtEpochMillis,
                    ))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                onFailure()
            }
        }
    }

    fun save(progress: ReadingProgress, comic: ComicSummary?, title: String) {
        writes.trySend(Write(progress, comic, title))
    }

    fun close() {
        writes.close()
        worker.cancel()
    }
}
