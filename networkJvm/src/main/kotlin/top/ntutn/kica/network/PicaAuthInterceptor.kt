package top.ntutn.kica.network

import okhttp3.Interceptor
import okhttp3.Response

internal class PicaAuthInterceptor(
    private val tokenProvider: () -> String?,
    private val onUnauthorized: () -> Unit = {},
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val relativeUrl = buildString {
            append(request.url.encodedPath.removePrefix("/"))
            request.url.encodedQuery?.let {
                append('?')
                append(it)
            }
        }
        val builder = request.newBuilder()
        PicaProtocol.headers(relativeUrl, request.method).forEach(builder::header)
        tokenProvider()?.takeIf { it.isNotBlank() }?.let { builder.header("authorization", it) }
        return chain.proceed(builder.build()).also { response ->
            if (response.code == 401) onUnauthorized()
        }
    }
}
