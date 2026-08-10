package top.ntutn.kica.network

import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class JvmTitleTranslationModelStoreTest {
    @Test
    fun downloadsAndVerifiesModel() = runTest {
        val content = "small-model".encodeToByteArray()
        withServerAndDirectory { server, directory ->
            server.enqueue(MockResponse().setBody(okio.Buffer().write(content)))
            val store = store(server, directory, content)

            val path = store.ensureModel { _, _ -> }

            assertContentEquals(content, java.io.File(path).readBytes())
            assertTrue(store.isModelAvailable())
            assertTrue(directory.resolve("test.gguf.sha256").isFile)
        }
    }

    @Test
    fun resumesPartFileWithHttpRange() = runTest {
        val content = "resume-model".encodeToByteArray()
        withServerAndDirectory { server, directory ->
            directory.resolve("test.gguf.part").writeBytes(content.copyOfRange(0, 4))
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setBody(okio.Buffer().write(content.copyOfRange(4, content.size))),
            )
            val store = store(server, directory, content)

            val path = store.ensureModel { _, _ -> }

            assertEquals("bytes=4-", server.takeRequest().getHeader("Range"))
            assertContentEquals(content, java.io.File(path).readBytes())
        }
    }

    @Test
    fun rejectsWrongChecksumAndRemovesPartFile() = runTest {
        val content = "corrupt".encodeToByteArray()
        withServerAndDirectory { server, directory ->
            server.enqueue(MockResponse().setBody(okio.Buffer().write(content)))
            val store = JvmTitleTranslationModelStore(
                modelDirectory = directory,
                maxRetries = 1,
                downloadUrl = server.url("/model").toString(),
                modelFileName = "test.gguf",
                expectedSizeBytes = content.size.toLong(),
                expectedSha256 = "0".repeat(64),
            )

            assertFailsWith<IllegalStateException> { store.ensureModel { _, _ -> } }
            assertFalse(directory.resolve("test.gguf.part").exists())
            assertFalse(store.isModelAvailable())
        }
    }

    private fun store(server: MockWebServer, directory: java.io.File, content: ByteArray) =
        JvmTitleTranslationModelStore(
            modelDirectory = directory,
            maxRetries = 1,
            downloadUrl = server.url("/model").toString(),
            modelFileName = "test.gguf",
            expectedSizeBytes = content.size.toLong(),
            expectedSha256 = sha256(content),
        )

    private suspend fun withServerAndDirectory(
        block: suspend (MockWebServer, java.io.File) -> Unit,
    ) {
        val server = MockWebServer()
        val directory = createTempDirectory("kica-model-test").toFile()
        try {
            server.start()
            block(server, directory)
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
