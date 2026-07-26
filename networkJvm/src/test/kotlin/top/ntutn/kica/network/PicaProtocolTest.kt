package top.ntutn.kica.network

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import top.ntutn.kica.data.SessionCredentialStore

class PicaProtocolTest {
    @Test
    fun signatureMatchesPinnedProtocolVector() {
        val headers = PicaProtocol.headers(
            relativeUrl = "auth/sign-in",
            method = "POST",
            clock = Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC),
            nonce = "0123456789abcdef",
        )

        assertEquals("1700000000", headers["time"])
        assertEquals("0123456789abcdef", headers["nonce"])
        assertEquals(
            "4e44a120ced4a5195ce9f15c2b5c311cf58657104f2691dbd21612405a84ad07",
            headers["signature"],
        )
    }

    @Test
    fun interceptorAddsProtocolAndAuthorizationHeaders() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            val client = OkHttpClient.Builder()
                .addInterceptor(PicaAuthInterceptor(tokenProvider = { "session-token" }))
                .build()

            client.newCall(
                Request.Builder().url(server.url("/comics/random?page=2")).build(),
            ).execute().close()

            val request = assertNotNull(server.takeRequest())
            assertEquals("session-token", request.getHeader("authorization"))
            assertEquals(PicaProtocol.API_KEY, request.getHeader("api-key"))
            assertFalse(request.getHeader("signature").isNullOrBlank())
        }
    }

    @Test
    fun repositoryMapsLoginAndClearsSessionAfter401() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("content-type", "application/json")
                    .setBody("""{"code":200,"data":{"token":"test-token"}}"""),
            )
            server.enqueue(
                MockResponse().setResponseCode(401)
                    .setHeader("content-type", "application/json")
                    .setBody("""{"code":401,"message":"expired"}"""),
            )
            val credentials = SessionCredentialStore()
            val repository = RealPicaRepository(
                credentialStore = credentials,
                baseUrl = server.url("/").toString(),
            )

            assertEquals("test-token", repository.login("reader@example.com", "password").token)
            val loginRequest = assertNotNull(server.takeRequest())
            assertEquals("POST", loginRequest.method)
            assertEquals("/auth/sign-in", loginRequest.path)
            assertEquals("application/json; charset=UTF-8", loginRequest.getHeader("content-type"))
            assertEquals(
                """{"email":"reader@example.com","password":"password"}""",
                loginRequest.body.readUtf8(),
            )
            assertEquals(PicaProtocol.ACCEPT, loginRequest.getHeader("accept"))
            assertEquals(PicaProtocol.APP_CHANNEL, loginRequest.getHeader("app-channel"))
            assertEquals(PicaProtocol.APP_VERSION, loginRequest.getHeader("app-version"))
            assertEquals(PicaProtocol.BUILD_VERSION, loginRequest.getHeader("app-build-version"))
            assertEquals(PicaProtocol.PLATFORM, loginRequest.getHeader("app-platform"))
            assertEquals(PicaProtocol.IMAGE_QUALITY, loginRequest.getHeader("image-quality"))
            assertEquals(PicaProtocol.USER_AGENT, loginRequest.getHeader("user-agent"))
            assertFalse(loginRequest.getHeader("signature").isNullOrBlank())
            assertEquals("test-token", credentials.readToken())
            runCatching { repository.recommendations() }

            assertEquals(null, repository.session.value)
            assertEquals(null, credentials.readToken())
        }
    }

    @Test
    fun loginReportsLogicalApiErrors() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("content-type", "application/json")
                    .setBody("""{"code":400,"error":"1004","message":"invalid credentials"}"""),
            )
            val repository = RealPicaRepository(
                credentialStore = SessionCredentialStore(),
                baseUrl = server.url("/").toString(),
            )

            val failure = assertFailsWith<PicaApiException> {
                repository.login("reader@example.com", "password")
            }

            assertTrue(failure.message.orEmpty().contains("code 400"))
            assertTrue(failure.message.orEmpty().contains("error 1004"))
            assertTrue(failure.message.orEmpty().contains("invalid credentials"))
        }
    }

    @Test
    fun repositoryMapsRecommendationDtos() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("content-type", "application/json")
                    .setBody(
                        """
                        {
                          "code": 200,
                          "data": {
                            "collections": [{
                              "comics": [{
                                "_id": "comic-1",
                                "title": "Example",
                                "author": "Author",
                                "categories": ["Action"],
                                "finished": true,
                                "thumb": {"fileServer": "https://img.example", "path": "cover.jpg"}
                              }]
                            }]
                          }
                        }
                        """.trimIndent(),
                    ),
            )
            val repository = RealPicaRepository(
                credentialStore = SessionCredentialStore(),
                baseUrl = server.url("/").toString(),
            )

            val item = repository.recommendations().single()

            assertEquals("comic-1", item.id)
            assertEquals("Example", item.title)
            assertEquals("https://img.example/static/cover.jpg", item.coverUrl)
            assertTrue(item.finished)
        }
    }

    @Test
    fun repositoryCollectsAndSortsPaginatedEpisodes() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setHeader("content-type", "application/json").setBody(
                    """{"code":200,"data":{"eps":{"pages":2,"docs":[{"order":2,"title":"Second"}]}}}""",
                ),
            )
            server.enqueue(
                MockResponse().setHeader("content-type", "application/json").setBody(
                    """{"code":200,"data":{"eps":{"pages":2,"docs":[{"order":1,"title":"First"}]}}}""",
                ),
            )
            val repository = RealPicaRepository(
                credentialStore = SessionCredentialStore(),
                baseUrl = server.url("/").toString(),
            )

            val episodes = repository.episodes("comic")

            assertEquals(listOf(1, 2), episodes.map { it.order })
            assertEquals(listOf("First", "Second"), episodes.map { it.title })
            assertTrue(server.takeRequest().path.orEmpty().contains("page=1"))
            assertTrue(server.takeRequest().path.orEmpty().contains("page=2"))
        }
    }
}
