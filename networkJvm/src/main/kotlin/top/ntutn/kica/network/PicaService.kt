package top.ntutn.kica.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
internal data class ApiEnvelope(
    val code: Int = 0,
    val message: String? = null,
    val error: String? = null,
    val data: JsonObject? = null,
)

@Serializable
internal data class LoginBody(
    val email: String,
    val password: String,
)

@Serializable
internal data class SearchBody(
    val categories: List<String> = emptyList(),
    val keyword: String = "",
    val sort: String = "dd",
)

internal interface PicaService {
    @POST("auth/sign-in")
    suspend fun login(@Body body: LoginBody): ApiEnvelope

    @GET("collections")
    suspend fun collections(): ApiEnvelope

    @GET("comics/random")
    suspend fun random(): ApiEnvelope

    @GET("categories")
    suspend fun categories(): ApiEnvelope

    @GET("comics/leaderboard")
    suspend fun ranking(
        @Query("tt") period: String,
        @Query("ct") contentType: String = "VC",
    ): ApiEnvelope

    @GET("comics/knight-leaderboard")
    suspend fun knightRanking(): ApiEnvelope

    @POST("comics/advanced-search")
    suspend fun search(
        @Query("page") page: Int,
        @Body body: SearchBody,
    ): ApiEnvelope

    @GET("users/favourite")
    suspend fun favorites(
        @Query("s") sort: String = "dd",
        @Query("page") page: Int,
    ): ApiEnvelope

    @GET("comics/{id}")
    suspend fun comic(@Path("id") id: String): ApiEnvelope

    @GET("comics/{id}/eps")
    suspend fun episodes(
        @Path("id") id: String,
        @Query("page") page: Int,
    ): ApiEnvelope

    @GET("comics/{id}/order/{episode}/pages")
    suspend fun pages(
        @Path("id") id: String,
        @Path("episode") episode: String,
        @Query("page") page: Int,
    ): ApiEnvelope

    @POST("comics/{id}/favourite")
    suspend fun toggleFavorite(@Path("id") id: String): ApiEnvelope

    @POST("comics/{id}/like")
    suspend fun like(@Path("id") id: String): ApiEnvelope
}
