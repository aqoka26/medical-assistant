package app.medassist.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Account(val login: String, val token: String, val dailyLimit: Int, val usedToday: Int)

data class Usage(val login: String, val usedToday: Int, val dailyLimit: Int)

/** Login and registration against the owner's proxy server (server/worker.js). */
class AuthClient(private val baseUrl: String, private val http: OkHttpClient = defaultHttp) {

    fun register(login: String, password: String, invite: String? = null): Account =
        post("/auth/register", buildJsonObject {
            put("login", login.trim())
            put("password", password)
            if (!invite.isNullOrBlank()) put("invite", invite.trim())
        }).toAccount()

    fun login(login: String, password: String): Account =
        post("/auth/login", buildJsonObject {
            put("login", login.trim())
            put("password", password)
        }).toAccount()

    fun me(token: String): Usage {
        val req = Request.Builder().url("$baseUrl/auth/me").header("Authorization", "Bearer $token").get().build()
        val o = execute(req)
        return Usage(o.str("login"), o.int("usedToday"), o.int("dailyLimit"))
    }

    fun logout(token: String) {
        val req = Request.Builder().url("$baseUrl/auth/logout").header("Authorization", "Bearer $token")
            .post("{}".toRequestBody(JSON)).build()
        runCatching { execute(req) }
    }

    private fun post(path: String, body: JsonObject): JsonObject {
        val req = Request.Builder().url(baseUrl + path).post(body.toString().toRequestBody(JSON)).build()
        return execute(req)
    }

    private fun execute(req: Request): JsonObject {
        val response = try {
            http.newCall(req).execute()
        } catch (e: IOException) {
            throw ChatException("Нет связи с сервером. Проверьте интернет.", cause = e)
        }
        response.use {
            val text = it.body?.string().orEmpty()
            val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: JsonObject(emptyMap())
            if (!it.isSuccessful) {
                val msg = obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                val kind = if (it.code == 401) ChatException.Kind.AUTH else ChatException.Kind.OTHER
                throw ChatException(msg ?: "Ошибка сервера (${it.code}).", kind)
            }
            return obj
        }
    }

    private fun JsonObject.toAccount() = Account(str("login"), str("token"), int("dailyLimit"), int("usedToday"))
    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun JsonObject.int(k: String) = this[k]?.jsonPrimitive?.intOrNull ?: 0

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }
        private val defaultHttp = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
