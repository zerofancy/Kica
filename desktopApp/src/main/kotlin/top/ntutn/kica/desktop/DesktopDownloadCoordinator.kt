package top.ntutn.kica.desktop

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import top.ntutn.kica.data.DownloadCoordinator
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.DownloadStatus
import top.ntutn.kica.model.DownloadTask
import top.ntutn.kica.model.Episode
import top.ntutn.kica.network.HttpDownloadExecutor

internal class DesktopDownloadCoordinator(
    private val library: LibraryRepository,
    private val executor: HttpDownloadExecutor,
    concurrency: Int = 5,
) : DownloadCoordinator, AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob())
    private val semaphore = Semaphore(concurrency)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val mutableTasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    override val tasks: StateFlow<List<DownloadTask>> = mutableTasks.asStateFlow()

    override suspend fun restore() {
        mutableTasks.value = library.downloads().first().map {
            if (it.status == DownloadStatus.RUNNING) it.copy(status = DownloadStatus.QUEUED) else it
        }
        mutableTasks.value.filter { it.status == DownloadStatus.QUEUED }.forEach(::start)
    }

    override suspend fun enqueue(
        comic: ComicSummary,
        episode: Episode,
        targetLocation: String,
    ): DownloadTask {
        val task = DownloadTask(
            id = "${comic.id}:${episode.id}",
            comic = comic,
            episode = episode,
            targetLocation = targetLocation,
        )
        update(task)
        start(task)
        return task
    }

    override suspend fun pause(id: String) {
        jobs.remove(id)?.cancel()
        transition(id, DownloadStatus.PAUSED)
    }

    override suspend fun resume(id: String) {
        val task = mutableTasks.value.firstOrNull { it.id == id } ?: return
        val queued = task.copy(status = DownloadStatus.QUEUED, error = null)
        update(queued)
        start(queued)
    }

    override suspend fun cancel(id: String) {
        jobs.remove(id)?.cancel()
        transition(id, DownloadStatus.CANCELLED)
    }

    override suspend fun retry(id: String) = resume(id)

    private fun start(task: DownloadTask) {
        if (jobs[task.id]?.isActive == true) return
        jobs[task.id] = scope.launch {
            semaphore.withPermit {
                try {
                    executor.execute(task) { update(it) }
                } catch (error: Throwable) {
                    update(
                        task.copy(
                            status = DownloadStatus.FAILED,
                            error = error.message,
                            retryCount = task.retryCount + 1,
                        ),
                    )
                } finally {
                    jobs.remove(task.id)
                }
            }
        }
    }

    private suspend fun transition(id: String, status: DownloadStatus) {
        mutableTasks.value.firstOrNull { it.id == id }?.let { update(it.copy(status = status)) }
    }

    private suspend fun update(task: DownloadTask) {
        mutableTasks.value = mutableTasks.value.filterNot { it.id == task.id } + task
        library.upsertDownload(task)
    }

    override fun close() {
        scope.cancel()
    }
}

