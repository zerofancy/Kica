package top.ntutn.kica.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.DownloadStatus
import top.ntutn.kica.model.DownloadTask
import top.ntutn.kica.model.Episode

class DefaultDownloadCoordinatorTest {
    private val comic = ComicSummary(id = "comic", title = "Comic")
    private val episode = Episode(id = "episode", comicId = comic.id, order = 1, title = "Episode")

    @Test
    fun enqueuePauseResumeCancelAndRetryArePersisted() = runTest {
        val library = InMemoryLibraryRepository()
        val coordinator = DefaultDownloadCoordinator(library)

        val task = coordinator.enqueue(comic, episode, "downloads")
        assertEquals(DownloadStatus.QUEUED, task.status)

        coordinator.pause(task.id)
        assertEquals(DownloadStatus.PAUSED, coordinator.tasks.value.single().status)
        coordinator.resume(task.id)
        assertEquals(DownloadStatus.QUEUED, coordinator.tasks.value.single().status)
        coordinator.cancel(task.id)
        assertEquals(DownloadStatus.CANCELLED, coordinator.tasks.value.single().status)
        coordinator.retry(task.id)
        assertEquals(DownloadStatus.QUEUED, coordinator.tasks.value.single().status)
    }

    @Test
    fun restoreConvertsInterruptedWorkToQueued() = runTest {
        val library = InMemoryLibraryRepository()
        library.upsertDownload(
            DownloadTask(
                id = "comic:episode",
                comic = comic,
                episode = episode,
                status = DownloadStatus.RUNNING,
            ),
        )
        val coordinator = DefaultDownloadCoordinator(library)

        coordinator.restore()

        assertEquals(DownloadStatus.QUEUED, coordinator.tasks.value.single().status)
    }
}
