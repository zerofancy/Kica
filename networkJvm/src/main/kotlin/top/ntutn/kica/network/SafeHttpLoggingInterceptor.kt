package top.ntutn.kica.network

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import org.slf4j.LoggerFactory

internal class SafeHttpLoggingInterceptor : Interceptor {
    private val logger = LoggerFactory.getLogger("top.ntutn.kica.network.Http")

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startedAt = System.nanoTime()
        logger.info(
            "--> {} {} headers={} body={}",
            request.method,
            request.url,
            request.safeHeaders(),
            request.safeBody(),
        )

        return try {
            chain.proceed(request).also { response ->
                val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                if (response.isSuccessful) {
                    logger.info("<-- {} {} ({} ms)", response.code, request.url, elapsedMillis)
                } else {
                    val responseBody = response.peekBody(MAX_ERROR_BODY_BYTES).string()
                    logger.warn(
                        "<-- {} {} ({} ms) body={}",
                        response.code,
                        request.url,
                        elapsedMillis,
                        SecretRedactor.redact(responseBody),
                    )
                }
            }
        } catch (exception: Exception) {
            val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
            logger.warn(
                "<-- FAILED {} {} ({} ms): {}",
                request.method,
                request.url,
                elapsedMillis,
                SecretRedactor.redact(exception.message.orEmpty()),
            )
            throw exception
        }
    }

    private fun Request.safeHeaders(): Map<String, String> =
        headers.names()
            .sorted()
            .associateWith { name ->
                if (name.lowercase() in REDACTED_HEADERS) {
                    SecretRedactor.REDACTED
                } else {
                    header(name).orEmpty()
                }
            }

    private fun Request.safeBody(): String {
        val requestBody = body ?: return "<none>"
        if (requestBody.isDuplex() || requestBody.isOneShot()) return "<streaming>"

        return runCatching {
            val buffer = Buffer()
            requestBody.writeTo(buffer)
            val charset = requestBody.contentType()?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
            SecretRedactor.redact(buffer.readString(charset))
        }.getOrElse {
            "<unavailable>"
        }
    }

    private companion object {
        const val MAX_ERROR_BODY_BYTES = 64L * 1024L
        val REDACTED_HEADERS = setOf("api-key", "authorization", "cookie", "proxy-authorization", "signature")
    }
}
