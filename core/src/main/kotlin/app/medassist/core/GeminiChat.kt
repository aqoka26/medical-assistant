package app.medassist.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.booleanOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ReplyResult(val text: String, val finishReason: String?, val model: String)

class ChatException(message: String, val kind: Kind = Kind.OTHER, cause: Throwable? = null) :
    Exception(message, cause) {
    enum class Kind { DAILY_LIMIT, MINUTE_LIMIT, FILE_GONE, AUTH, USER_LIMIT, OTHER }
}

/**
 * Talks to the Google Gemini API (free tier works). The conversation is rebuilt from
 * [ChatMessage]s on every turn; files go through the Gemini Files API.
 */
class GeminiChat(
    private val apiKey: String?,
    private val baseUrl: String = BASE,
    private val appToken: String? = null,
    private val authToken: String? = null,
    private val http: OkHttpClient = defaultClient,
) {
    /** With a proxy the key stays on the server; the app only sends its app token. */
    private fun Request.Builder.auth(): Request.Builder = apply {
        if (!apiKey.isNullOrBlank()) header("x-goog-api-key", apiKey)
        if (!appToken.isNullOrBlank()) header("x-app-token", appToken)
        if (!authToken.isNullOrBlank()) header("Authorization", "Bearer $authToken")
    }


    /** Uploads a file (resumable upload, two steps) and returns its file URI. */
    fun upload(bytes: ByteArray, displayName: String, mimeType: String): String {
        val startBody = buildJsonObject { putJsonObject("file") { put("display_name", displayName) } }
        val start = Request.Builder()
            .url("$baseUrl/upload/v1beta/files")
            .auth()
            .header("X-Goog-Upload-Protocol", "resumable")
            .header("X-Goog-Upload-Command", "start")
            .header("X-Goog-Upload-Header-Content-Length", bytes.size.toString())
            .header("X-Goog-Upload-Header-Content-Type", mimeType)
            .post(startBody.toString().toRequestBody(JSON_TYPE))
            .build()
        val uploadUrl = call(start) { it.header("x-goog-upload-url") }
            ?: throw ChatException("Сервер не принял файл «$displayName».")

        val finish = Request.Builder()
            .url(uploadUrl)
            .auth()
            .header("X-Goog-Upload-Offset", "0")
            .header("X-Goog-Upload-Command", "upload, finalize")
            .post(bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        val body = call(finish) { it.body!!.string() }
        return json.parseToJsonElement(body).jsonObject["file"]?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull
            ?: throw ChatException("Сервер не вернул ссылку на файл «$displayName».")
    }

    /** Checks the key by listing models. */
    fun checkKey() {
        val req = Request.Builder().url("$baseUrl/v1beta/models").auth().get().build()
        call(req) { }
    }

    /** Sends the conversation and streams the answer; [onText] gets the answer so far. */
    fun reply(
        model: String,
        systemPrompt: String,
        history: List<ChatMessage>,
        onText: (String) -> Unit,
    ): ReplyResult {
        val body = buildRequest(systemPrompt, history).toString()
        val req = Request.Builder()
            .url("$baseUrl/v1beta/models/$model:streamGenerateContent?alt=sse")
            .auth()
            .post(body.toRequestBody(JSON_TYPE))
            .build()
        return call(req) { response ->
            val text = StringBuilder()
            var finish: String? = null
            var blocked: String? = null
            val source = response.body!!.source()
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val chunk = parseChunk(line.removePrefix("data:").trim())
                if (chunk.text.isNotEmpty()) {
                    text.append(chunk.text)
                    onText(text.toString())
                }
                chunk.finishReason?.let { finish = it }
                chunk.blockReason?.let { blocked = it }
            }
            if (blocked != null && text.isEmpty()) {
                throw ChatException("Gemini заблокировал запрос фильтром безопасности ($blocked). Попробуйте переформулировать.")
            }
            ReplyResult(text.toString(), finish, model)
        }
    }

    fun buildRequest(systemPrompt: String, history: List<ChatMessage>): JsonObject = buildJsonObject {
        putJsonObject("systemInstruction") {
            putJsonArray("parts") { add(buildJsonObject { put("text", systemPrompt) }) }
        }
        put("contents", buildContents(history))
        putJsonObject("generationConfig") {
            put("maxOutputTokens", 65_536)
        }
    }

    private fun buildContents(history: List<ChatMessage>): JsonArray {
        // Error notes are not sent; back-to-back messages of one role are merged into one turn.
        val turns = mutableListOf<Pair<Role, MutableList<JsonObject>>>()
        for (msg in history) {
            if (msg.isError) continue
            val parts = if (msg.role == Role.USER) userParts(msg) else listOf(textPart(msg.text))
            if (parts.isEmpty() || (msg.role == Role.ASSISTANT && msg.text.isBlank())) continue
            val last = turns.lastOrNull()
            if (last != null && last.first == msg.role) last.second += parts else turns += msg.role to parts.toMutableList()
        }
        return buildJsonArray {
            for ((role, parts) in turns) {
                add(buildJsonObject {
                    put("role", if (role == Role.USER) "user" else "model")
                    put("parts", JsonArray(parts))
                })
            }
        }
    }

    private fun userParts(msg: ChatMessage): List<JsonObject> {
        val parts = mutableListOf<JsonObject>()
        for (a in msg.attachments) {
            val uri = a.remoteId ?: throw ChatException("Файл «${a.displayName}» ещё не загружен.")
            parts += buildJsonObject {
                putJsonObject("file_data") {
                    put("mime_type", a.mimeType)
                    put("file_uri", uri)
                }
            }
        }
        val text = buildString {
            if (msg.attachments.isNotEmpty()) {
                append("[Прикреплённые файлы: ")
                append(msg.attachments.joinToString(", ") { it.displayName })
                append("]")
                if (msg.text.isNotBlank()) append("\n\n")
            }
            append(msg.text)
        }
        if (text.isNotBlank()) parts += textPart(text)
        return parts
    }

    private fun textPart(text: String) = buildJsonObject { put("text", text) }

    private inline fun <T> call(request: Request, handle: (Response) -> T): T {
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw ChatException("Нет связи с сервером Google. Проверьте интернет и попробуйте снова.", cause = e)
        }
        response.use {
            if (!it.isSuccessful) throw toError(it.code, it.body?.string().orEmpty())
            return try {
                handle(it)
            } catch (e: IOException) {
                throw ChatException("Связь оборвалась во время ответа. Нажмите «Повторить».", cause = e)
            }
        }
    }

    companion object {
        const val BASE = "https://generativelanguage.googleapis.com"
        const val DEFAULT_MODEL = "gemini-flash-latest"
        const val LITE_MODEL = "gemini-flash-lite-latest"

        /** Gemini Files API keeps files for 48 hours; refresh a bit earlier. */
        const val FILE_LIFETIME_MS = 46L * 60 * 60 * 1000

        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }

        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .build()

        data class Chunk(val text: String, val finishReason: String?, val blockReason: String?)

        fun parseChunk(data: String): Chunk {
            val obj = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
                ?: return Chunk("", null, null)
            val candidate = obj["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            val text = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray
                ?.map { it.jsonObject }
                // "thought" parts are the model's private reasoning, not the answer.
                ?.filter { it["thought"]?.jsonPrimitive?.booleanOrNull != true }
                ?.mapNotNull { it["text"]?.jsonPrimitive?.contentOrNull }
                ?.joinToString("")
                .orEmpty()
            val finish = candidate?.get("finishReason")?.jsonPrimitive?.contentOrNull
            val block = obj["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull
            return Chunk(text, finish, block)
        }

        fun toError(code: Int, body: String): ChatException {
            val message = runCatching {
                json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
            }.getOrNull().orEmpty()
            val lower = (message + body).lowercase()
            return when {
                // Errors from the owner's proxy server (accounts and per-user limits).
                "user_daily_limit" in lower -> ChatException(message, ChatException.Kind.USER_LIMIT)
                "unauthenticated" in lower -> ChatException(message.ifBlank { "Войдите в аккаунт." }, ChatException.Kind.AUTH)
                "\"blocked\"" in lower -> ChatException(message, ChatException.Kind.AUTH)
                code == 429 && "perday" in lower -> ChatException(
                    "Исчерпан бесплатный дневной лимит Gemini. Он обновится в полночь по тихоокеанскому времени (около 11:00 по Москве).",
                    ChatException.Kind.DAILY_LIMIT,
                )
                code == 429 -> ChatException(
                    "Слишком много запросов за минуту (бесплатный лимит). Подождите минуту и нажмите «Повторить».",
                    ChatException.Kind.MINUTE_LIMIT,
                )
                "location is not supported" in lower || "not available in your country" in lower -> ChatException(
                    "Gemini API недоступен в вашей стране. Нужен VPN в поддерживаемую страну.",
                )
                code == 400 && "api key not valid" in lower || code == 401 -> ChatException(
                    "Ключ API не подходит. Проверьте его в настройках.",
                )
                code == 403 && ("file" in lower || "permission" in lower) -> ChatException(
                    "Файл на сервере устарел, загружаю заново.", ChatException.Kind.FILE_GONE,
                )
                code == 404 -> ChatException("Модель не найдена ($message). Выберите другую модель в настройках.")
                else -> ChatException("Ошибка Gemini ($code): ${message.ifBlank { body.take(300) }}")
            }
        }

        fun describeFinish(reason: String?): String? = when (reason) {
            "MAX_TOKENS" -> "Ответ оборван из-за длины. Напишите «продолжай»."
            "SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII" ->
                "Gemini остановил ответ фильтром безопасности. Попробуйте переформулировать."
            "RECITATION" -> "Gemini остановил ответ (повтор защищённого текста). Попробуйте переформулировать."
            else -> null
        }
    }
}
