package top.ntutn.kica.ui.navigation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.HistoryEntry
import top.ntutn.kica.model.ReadingProgress

/** Session-owned FIFO. A page's destruction never cancels an accepted write. */
class SessionProgressCoordinator(scope: CoroutineScope, library: LibraryRepository) {
    private sealed interface Command {
        data class Write(val progress: ReadingProgress, val comic: ComicSummary?, val title: String) : Command
        data class Barrier(val completed: CompletableDeferred<Unit>) : Command
    }
    private val queue = Channel<Command>(Channel.UNLIMITED)
    private val mutableFailures = MutableStateFlow(0)
    val failures: StateFlow<Int> = mutableFailures.asStateFlow()
    private var accepting = true
    private val worker = scope.launch {
        for (command in queue) when (command) {
            is Command.Barrier -> command.completed.complete(Unit)
            is Command.Write -> try {
                library.saveProgress(command.progress)
                command.comic?.let { comic ->
                    library.addHistory(HistoryEntry(
                        comic = comic,
                        episodeId = command.progress.episodeId,
                        episodeTitle = command.title,
                        pageIndex = command.progress.pageIndex,
                        updatedAtEpochMillis = command.progress.updatedAtEpochMillis,
                    ))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableFailures.value++
            }
        }
    }

    /** Called on the UI thread, like navigation and reader layout reports. */
    fun save(progress: ReadingProgress, comic: ComicSummary?, title: String): Boolean =
        accepting && queue.trySend(Command.Write(progress, comic, title)).isSuccess

    /** Waits for writes accepted before this call, without shutting down the session. */
    suspend fun drain(timeoutMillis: Long = 2_000): Boolean {
        if (!accepting) return withTimeoutOrNull(timeoutMillis) { worker.join(); true } ?: false
        val completed = CompletableDeferred<Unit>()
        if (!queue.trySend(Command.Barrier(completed)).isSuccess) return false
        return withTimeoutOrNull(timeoutMillis) { completed.await(); true } ?: false
    }

    /** Stop reports first; callers must invoke this before cancelling the owning scope. */
    suspend fun close(timeoutMillis: Long = 2_000): Boolean {
        accepting = false
        queue.close()
        val drained = withTimeoutOrNull(timeoutMillis) { worker.join(); true } ?: false
        if (!drained) {
            // Explicitly expose an incomplete shutdown, rather than silently claiming persistence.
            mutableFailures.value++
            worker.cancel()
            queue.cancel()
        }
        return drained
    }
}
