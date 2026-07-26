package top.ntutn.kica.network

import java.nio.charset.StandardCharsets
import java.time.Clock
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object PicaProtocol {
    const val BASE_URL = "https://picaapi.picacomic.com/"
    const val API_KEY = "C69BAF41DA5ABD1FFEDC6D2FEA56B"
    const val ACCEPT = "application/vnd.picacomic.com.v1+json"
    const val APP_CHANNEL = "3"
    const val APP_VERSION = "2.2.1.3.3.4"
    const val BUILD_VERSION = "45"
    const val PLATFORM = "android"
    const val IMAGE_QUALITY = "original"
    const val USER_AGENT = "okhttp/3.8.1"
    const val UPDATE_VERSION = "v1.5.4"

    /*
     * This compatibility key is part of the public client protocol implemented
     * by the LGPL-3.0 upstream application. It is not a user credential.
     */
    private const val SIGNING_KEY =
        "~d}\$Q7\$eIni=V)9\\RK/P.RM4;9[7|@/CA}b~OW!3?EV`:<>M7pddUBL5n|0/*Cn"

    fun headers(
        relativeUrl: String,
        method: String,
        clock: Clock = Clock.systemUTC(),
        nonce: String = UUID.randomUUID().toString().replace("-", ""),
    ): Map<String, String> {
        val time = clock.instant().epochSecond.toString()
        val source = relativeUrl + time + nonce + method + API_KEY
        val signature = hmacSha256(source.lowercase(), SIGNING_KEY)
        return buildMap {
            put("api-key", API_KEY)
            put("accept", ACCEPT)
            put("app-channel", APP_CHANNEL)
            put("time", time)
            put("app-uuid", "defaultUuid")
            put("nonce", nonce)
            put("signature", signature)
            put("app-version", APP_VERSION)
            put("image-quality", IMAGE_QUALITY)
            put("app-platform", PLATFORM)
            put("app-build-version", BUILD_VERSION)
            put("user-agent", USER_AGENT)
            put("version", UPDATE_VERSION)
        }
    }

    private fun hmacSha256(source: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(source.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

