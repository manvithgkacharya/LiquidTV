package com.liquidtv.app.data.sonix

import com.liquidtv.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SonixApiResult<out T> {
    data class Success<T>(val data: T) : SonixApiResult<T>
    data class Unauthorized(val errorCode: String) : SonixApiResult<Nothing>
    data class Forbidden(val errorCode: String) : SonixApiResult<Nothing>
    data class Failure(val message: String, val httpCode: Int = 0) : SonixApiResult<Nothing>
}

interface SonixControlApi {
    suspend fun callAction(
        action: String,
        payload: JsonObject,
        deviceToken: String?,
    ): SonixApiResult<JsonObject>
}

@Singleton
class HttpSonixControlApi(
    private val okHttpClient: OkHttpClient,
    private val baseUrl: String,
    private val anonKey: String,
) : SonixControlApi {

    @Inject
    constructor(okHttpClient: OkHttpClient) : this(
        okHttpClient = okHttpClient,
        baseUrl = BuildConfig.SONIX_SUPABASE_URL,
        anonKey = BuildConfig.SONIX_SUPABASE_ANON_KEY,
    )

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun callAction(
        action: String,
        payload: JsonObject,
        deviceToken: String?,
    ): SonixApiResult<JsonObject> = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trim().trimEnd('/')
        if (cleanBase.isEmpty() || anonKey.isBlank()) {
            return@withContext SonixApiResult.Failure("إعدادات الخدمة غير مكتملة")
        }

        val bodyJson = buildJsonObject {
            put("action", action)
            payload.forEach { (key, value) ->
                if (key != "action") put(key, value)
            }
        }

        val requestBuilder = Request.Builder()
            .url("$cleanBase/functions/v1/sonix-device-api")
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")

        if (!deviceToken.isNullOrBlank()) {
            requestBuilder.header("x-sonix-device-token", deviceToken.trim())
        }

        try {
            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                val parsed = runCatching {
                    if (rawBody.isNotBlank()) json.parseToJsonElement(rawBody).jsonObject else null
                }.getOrNull()
                val errorCode = parsed?.get("error")?.jsonPrimitive?.content.orEmpty()

                return@withContext when {
                    response.isSuccessful && parsed != null -> SonixApiResult.Success(parsed)
                    response.code == 401 -> SonixApiResult.Unauthorized(errorCode.ifBlank { "invalid_device_credentials" })
                    response.code == 403 -> SonixApiResult.Forbidden(errorCode.ifBlank { "access_denied" })
                    else -> {
                        val msg = errorCode.ifBlank { "server_error_${response.code}" }
                        SonixApiResult.Failure(msg, response.code)
                    }
                }
            }
        } catch (e: Exception) {
            runCatching { android.util.Log.w("SonixControlApi", "API call failed", e) }
            SonixApiResult.Failure("network_error")
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
