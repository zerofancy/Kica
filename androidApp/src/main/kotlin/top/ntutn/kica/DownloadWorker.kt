package top.ntutn.kica

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
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

        val settings = container.library.settings().first()
        val notificationTitle = if (settings.titleTranslationEnabled) {
            runCatching { container.titleTranslation.enable() }
            container.titleTranslation.translate(task.comic.title) ?: task.comic.title
        } else {
            task.comic.title
        }
        setForeground(foregroundInfo(notificationTitle, task.completedPages, task.totalPages))
        return downloadPermits.withPermit {
            try {
                HttpDownloadExecutor(container.pica).execute(task) { updated ->
                    container.library.upsertDownload(updated)
                    setForeground(
                        foregroundInfo(
                            notificationTitle,
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

    @SuppressLint("InlinedApi")
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
        return ForegroundInfo(
            id.hashCode(),
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    companion object {
        const val TASK_ID = "task-id"
        private const val CHANNEL = "kica-downloads"
        private val downloadPermits = Semaphore(3)
    }
}
