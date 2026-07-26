package top.ntutn.kica

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.ntutn.kica.data.DownloadCoordinator
import top.ntutn.kica.data.LibraryRepository
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.DownloadStatus
import top.ntutn.kica.model.DownloadTask
import top.ntutn.kica.model.Episode

internal class AndroidDownloadCoordinator(
    context: Context,
    private val library: LibraryRepository,
) : DownloadCoordinator {
    private val workManager = WorkManager.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob())
    private val mutableTasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    override val tasks: StateFlow<List<DownloadTask>> = mutableTasks.asStateFlow()

    init {
        scope.launch {
            library.downloads().collectLatest { mutableTasks.value = it }
        }
    }

    override suspend fun restore() {
        library.downloads().first().forEach { task ->
            when (task.status) {
                DownloadStatus.RUNNING, DownloadStatus.QUEUED -> {
                    val queued = task.copy(status = DownloadStatus.QUEUED)
                    library.upsertDownload(queued)
                    schedule(queued.id)
                }
                else -> Unit
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
        library.upsertDownload(task)
        schedule(task.id)
        return task
    }

    override suspend fun pause(id: String) {
        workManager.cancelUniqueWork(workName(id))
        transition(id, DownloadStatus.PAUSED)
    }

    override suspend fun resume(id: String) {
        transition(id, DownloadStatus.QUEUED)
        schedule(id)
    }

    override suspend fun cancel(id: String) {
        workManager.cancelUniqueWork(workName(id))
        transition(id, DownloadStatus.CANCELLED)
    }

    override suspend fun retry(id: String) = resume(id)

    private suspend fun transition(id: String, status: DownloadStatus) {
        mutableTasks.value.firstOrNull { it.id == id }?.let {
            library.upsertDownload(it.copy(status = status, error = null))
        }
    }

    private fun schedule(id: String) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(Data.Builder().putString(DownloadWorker.TASK_ID, id).build())
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .addTag(workName(id))
            .build()
        workManager.enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    private fun workName(id: String): String = "kica-download-$id"
}

