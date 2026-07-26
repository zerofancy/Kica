package top.ntutn.kica.network

import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import top.ntutn.kica.model.NetworkSettings
import top.ntutn.kica.model.ProxyMode

internal object NetworkFactory {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun service(
        tokenProvider: () -> String?,
        settings: NetworkSettings = NetworkSettings(),
        baseUrl: String = PicaProtocol.BASE_URL,
        onUnauthorized: () -> Unit = {},
    ): PicaService {
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(settings.requestTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(settings.requestTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(settings.requestTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .addInterceptor(PicaAuthInterceptor(tokenProvider, onUnauthorized))
            .addInterceptor(SafeHttpLoggingInterceptor())

        when (settings.proxyMode) {
            ProxyMode.DIRECT -> clientBuilder.proxy(Proxy.NO_PROXY)
            ProxyMode.SYSTEM -> Unit
            ProxyMode.HTTP, ProxyMode.SOCKS5 -> {
                if (settings.proxyHost.isNotBlank() && settings.proxyPort in 1..65535) {
                    val type = if (settings.proxyMode == ProxyMode.HTTP) Proxy.Type.HTTP else Proxy.Type.SOCKS
                    clientBuilder.proxy(Proxy(type, InetSocketAddress(settings.proxyHost, settings.proxyPort)))
                }
            }
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(clientBuilder.build())
            .addConverterFactory(json.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
            .build()
            .create(PicaService::class.java)
    }
}
