package top.ntutn.kica.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CancellationException
import top.ntutn.kica.model.AppRoute
import top.ntutn.kica.model.ReaderMode
import top.ntutn.kica.model.ReadingProgress

/** Retained only while the route stays on the app stack, including temporary app-lock screens. */
internal class ReaderSessionMemory {
    val entry = ReaderEntrySession()
    var episodeId: String? = null
    var position: ReaderStart? = null
}

internal val LocalReaderSessions = staticCompositionLocalOf<MutableMap<AppRoute.Reader, ReaderSessionMemory>?> { null }

internal data class ReaderStart(
    val pageIndex: Int = 0,
    val mode: ReaderMode = ReaderMode.VERTICAL,
    val reportAdjustment: Boolean = true,
) {
    fun pageFor(pageCount: Int): Int {
        require(pageCount > 0)
        return pageIndex.coerceIn(0, pageCount - 1)
    }
}

internal sealed interface ReaderEntryState {
    data object Checking : ReaderEntryState
    data object Failed : ReaderEntryState
    data class Choice(val progress: ReadingProgress) : ReaderEntryState
    data class Ready(val start: ReaderStart) : ReaderEntryState
    data object Cancelled : ReaderEntryState
}

/** One external entry, not one chapter. Internal chapter changes never read this gate again. */
internal class ReaderEntrySession {
    var state: ReaderEntryState by mutableStateOf(ReaderEntryState.Checking)
        private set
    private var generation = 0

    suspend fun readProgress(read: suspend () -> ReadingProgress?) {
        if (state != ReaderEntryState.Checking && state != ReaderEntryState.Failed) return
        val request = ++generation
        state = ReaderEntryState.Checking
        try {
            val progress = read()
            if (request != generation) return
            state = if (progress != null && progress.pageIndex > 0) {
                ReaderEntryState.Choice(progress)
            } else {
                ReaderEntryState.Ready(ReaderStart(progress?.pageIndex ?: 0, progress?.mode ?: ReaderMode.VERTICAL))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (request == generation) state = ReaderEntryState.Failed
        }
    }

    fun choose(continueReading: Boolean) {
        val choice = state as? ReaderEntryState.Choice ?: return
        state = ReaderEntryState.Ready(ReaderStart(
            pageIndex = if (continueReading) choice.progress.pageIndex else 0,
            mode = choice.progress.mode,
        ))
    }

    /** Invalidate a suspended read without forgetting an already confirmed decision. */
    fun interruptRead() { generation++ }

    /** Returns false on duplicate cancellation or after this entry was disposed. */
    fun cancel(): Boolean {
        if (state == ReaderEntryState.Cancelled) return false
        generation++
        state = ReaderEntryState.Cancelled
        return true
    }
}
