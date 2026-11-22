package top.ntutn.kica.network

import java.lang.reflect.Proxy
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import top.ntutn.kica.data.PicaRepository
import top.ntutn.kica.model.ComicSummary
import top.ntutn.kica.model.DownloadStatus
import top.ntutn.kica.model.DownloadTask
import top.ntutn.kica.model.Episode
import top.ntutn.kica.model.PageRef
import top.ntutn.kica.model.UserSession

class HttpDownloadExecutorTest {
    @Test
    fun writesDownloadedPageUnderTargetPathWithSpaces() = runTest {
        val directory = createTempDirectory("kica-download-path-test-")
        val client = OkHttpClient()
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(MockResponse().setBody("page-content"))
            val task = task(directory.resolve("downloads with spaces").toString())
            val repository = repository(listOf(PageRef(0, server.url("/page.jpg").toString(), "page.jpg")))
            val progress = mutableListOf<DownloadTask>()

            val completed = HttpDownloadExecutor(repository, client, maxRetries = 1).execute(task) { progress += it }

            val page = directory.resolve("downloads with spaces/Comic fixture/Chapter 1/0001.jpg")
            assertEquals("page-content", Files.readString(page))
            assertFalse(Files.exists(page.resolveSibling("0001.jpg.part")))
            assertEquals(DownloadStatus.COMPLETED, completed.status)
            assertEquals(1, completed.completedPages)
            assertEquals(listOf(DownloadStatus.RUNNING, DownloadStatus.RUNNING, DownloadStatus.COMPLETED),
                progress.map { it.status })
            assertEquals("/page.jpg", server.takeRequest().path)
        } finally {
            server.shutdown()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun reusesExistingNonEmptyPageWithoutNetworkDownload() = runTest {
        val directory = createTempDirectory("kica-download-existing-test-")
        val client = OkHttpClient()
        val server = MockWebServer()
        try {
            server.start()
            val page = directory.resolve("Comic fixture/Chapter 1/0001.jpg")
            Files.createDirectories(page.parent)
            Files.write(page, "existing-page".encodeToByteArray())
            val repository = repository(listOf(PageRef(0, server.url("/page.jpg").toString(), "page.jpg")))

            val result = HttpDownloadExecutor(repository, client, maxRetries = 1).execute(task(directory.toString())) { }

            assertEquals(DownloadStatus.COMPLETED, result.status)
            assertEquals("existing-page", Files.readString(page))
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            directory.toFile().deleteRecursively()
        }
    }

    private fun task(target: String) = DownloadTask(
        id = "task",
        comic = ComicSummary("comic", "Comic: fixture"),
        episode = Episode("chapter", "comic", 1, "Chapter 1", 1),
        targetLocation = target,
    )

    private fun repository(pages: List<PageRef>): PicaRepository {
        val session = MutableStateFlow<UserSession?>(null)
        return Proxy.newProxyInstance(PicaRepository::class.java.classLoader, arrayOf(PicaRepository::class.java)) { _, method, _ ->
            when (method.name) {
                "pages" -> pages
                "getSession" -> session
                else -> error("Unexpected repository method: ${method.name}")
            }
        } as PicaRepository
    }
}
