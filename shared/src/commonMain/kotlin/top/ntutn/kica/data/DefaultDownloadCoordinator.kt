package top.ntutn.kica.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.DownloadStatus
import top.ntutn.kica.model.DownloadTask
import top.ntutn.kica.model.Episode

class DefaultDownloadCoordinator(
    private val library: LibraryRepository,
) : DownloadCoordinator {
    private val mutableTasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    override val tasks: StateFlow<List<DownloadTask>> = mutableTasks.asStateFlow()

    override suspend fun restore() {
        mutableTasks.value = library.downloads().first()
            .map { task ->
                if (task.status == DownloadStatus.RUNNING) {
                    task.copy(status = DownloadStatus.QUEUED)
                } else {
                    task
                }
            }
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
        replace(task)
        return task
    }

    override suspend fun pause(id: String) = transition(id, DownloadStatus.PAUSED)

    override suspend fun resume(id: String) = transition(id, DownloadStatus.QUEUED)

    override suspend fun cancel(id: String) = transition(id, DownloadStatus.CANCELLED)

    override suspend fun retry(id: String) {
        val current = mutableTasks.value.firstOrNull { it.id == id } ?: return
        replace(current.copy(status = DownloadStatus.QUEUED, retryCount = 0, error = null))
    }

    private suspend fun transition(id: String, status: DownloadStatus) {
        val current = mutableTasks.value.firstOrNull { it.id == id } ?: return
        replace(current.copy(status = status))
    }

    private suspend fun replace(task: DownloadTask) {
        mutableTasks.value = mutableTasks.value.filterNot { it.id == task.id } + task
        library.upsertDownload(task)
    }
}

