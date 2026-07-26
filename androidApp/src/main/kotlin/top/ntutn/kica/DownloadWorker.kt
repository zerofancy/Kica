package top.ntutn.kica

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import top.ntutn.kica.model.DownloadStatus
import top.ntutn.kica.network.HttpDownloadExecutor

internal class DownloadWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(TASK_ID) ?: return Result.failure()
        val container = (applicationContext as KicaApplication).container
        val task = container.library.downloads().first().firstOrNull { it.id == id }
            ?: return Result.failure()
        if (task.status == DownloadStatus.CANCELLED || task.status == DownloadStatus.PAUSED) {
            return Result.success()
        }

        setForeground(foregroundInfo(task.comic.title, task.completedPages, task.totalPages))
        return downloadPermits.withPermit {
            try {
                HttpDownloadExecutor(container.pica).execute(task) { updated ->
                    container.library.upsertDownload(updated)
                    setForeground(
                        foregroundInfo(
                            updated.comic.title,
                            updated.completedPages,
                            updated.totalPages,
                        ),
                    )
                }
                Result.success()
            } catch (error: Throwable) {
                val failed = task.copy(
                    status = DownloadStatus.FAILED,
                    retryCount = task.retryCount + 1,
                    error = error.message,
                )
                container.library.upsertDownload(failed)
                if (failed.retryCount < 5) Result.retry() else Result.failure()
            }
        }
    }

    private fun foregroundInfo(title: String, completed: Int, total: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Kica downloads", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("$completed / $total")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(total.coerceAtLeast(0), completed.coerceAtLeast(0), total <= 0)
            .build()
        return ForegroundInfo(id.hashCode(), notification)
    }

    companion object {
        const val TASK_ID = "task-id"
        private const val CHANNEL = "kica-downloads"
        private val downloadPermits = Semaphore(3)
    }
}
