package app.medassist.core

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.core.MultipartField
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.files.FileUploadParams
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.DocumentBlockParam
import com.anthropic.models.messages.FileDocumentSource
import com.anthropic.models.messages.FileImageSource
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import java.io.ByteArrayInputStream
import java.time.Duration

data class ReplyResult(
    val text: String,
    val stopReason: String?,
)

class ChatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Talks to Claude. The whole conversation is rebuilt from [ChatMessage]s on every turn and
 * is append-only, so the prompt cache stays warm and earlier turns never change.
 */
class ClaudeChat(apiKey: String, private val model: String = DEFAULT_MODEL) {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        // Long analyses of many documents can take several minutes.
        .timeout(Duration.ofMinutes(20))
        .build()

    /** Uploads a file once; the returned id is stored and reused in later requests. */
    fun upload(bytes: ByteArray, fileName: String, mimeType: String): String = wrapErrors {
        val field = MultipartField.builder<java.io.InputStream>()
            .value(ByteArrayInputStream(bytes))
            .filename(fileName)
            .contentType(mimeType)
            .build()
        client.files().upload(FileUploadParams.builder().file(field).build()).id()
    }

    fun deleteRemote(fileId: String) {
        runCatching { client.files().delete(fileId) }
    }

    /**
     * Sends the conversation and streams the answer. Every attachment must already have a
     * [Attachment.remoteId]. [onText] receives the answer text as it grows.
     */
    fun reply(
        systemPrompt: String,
        history: List<ChatMessage>,
        onText: (String) -> Unit,
    ): ReplyResult = wrapErrors {
        val params = buildParams(systemPrompt, history)
        val text = StringBuilder()
        var stopReason: String? = null
        client.messages().createStreaming(params).use { stream ->
            stream.stream().forEach { event ->
                event.contentBlockDelta().flatMap { it.delta().text() }.ifPresent {
                    text.append(it.text())
                    onText(text.toString())
                }
                event.messageDelta().ifPresent { delta ->
                    delta.delta().stopReason().ifPresent { stopReason = it.asString() }
                }
            }
        }
        ReplyResult(text.toString(), stopReason)
    }

    internal fun buildParams(systemPrompt: String, history: List<ChatMessage>): MessageCreateParams {
        val oneHour = CacheControlEphemeral.builder().ttl(CacheControlEphemeral.Ttl.TTL_1H).build()
        val builder = MessageCreateParams.builder()
            .model(model)
            .maxTokens(64_000L)
            .systemOfTextBlockParams(
                listOf(TextBlockParam.builder().text(systemPrompt).cacheControl(oneHour).build())
            )
            // Thinking is always on for this model; effort sets how hard it works.
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.HIGH).build())
            // Caches the conversation so far, so each new turn only pays full price for new input.
            .cacheControl(oneHour)
            // If a safety filter declines, the API retries on a recommended model automatically.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))

        for (turn in toTurns(history)) {
            when (turn.role) {
                Role.USER -> builder.addUserMessageOfContentBlockParams(turn.blocks)
                Role.ASSISTANT -> builder.addAssistantMessage(turn.text)
            }
        }
        return builder.build()
    }

    private class Turn(val role: Role, val blocks: List<ContentBlockParam>, val text: String)

    /** Drops error notes and merges back-to-back user messages into one turn. */
    private fun toTurns(history: List<ChatMessage>): List<Turn> {
        val turns = mutableListOf<Turn>()
        for (msg in history) {
            if (msg.isError) continue
            if (msg.role == Role.ASSISTANT) {
                if (msg.text.isBlank()) continue
                turns += Turn(Role.ASSISTANT, emptyList(), msg.text)
                continue
            }
            val blocks = userBlocks(msg)
            val last = turns.lastOrNull()
            if (last != null && last.role == Role.USER) {
                turns[turns.lastIndex] = Turn(Role.USER, last.blocks + blocks, "")
            } else {
                turns += Turn(Role.USER, blocks, "")
            }
        }
        return turns
    }

    private fun userBlocks(msg: ChatMessage): List<ContentBlockParam> {
        val blocks = mutableListOf<ContentBlockParam>()
        for (a in msg.attachments) {
            val id = a.remoteId ?: throw ChatException("Файл «${a.displayName}» ещё не загружен.")
            blocks += when {
                a.mimeType in SupportedFiles.IMAGES -> ContentBlockParam.ofImage(
                    ImageBlockParam.builder().source(FileImageSource.builder().fileId(id).build()).build()
                )
                else -> ContentBlockParam.ofDocument(
                    DocumentBlockParam.builder()
                        .source(FileDocumentSource.builder().fileId(id).build())
                        .title(a.displayName)
                        .build()
                )
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
        if (text.isNotBlank()) blocks += ContentBlockParam.ofText(text)
        return blocks
    }

    private inline fun <T> wrapErrors(block: () -> T): T = try {
        block()
    } catch (e: ChatException) {
        throw e
    } catch (e: UnauthorizedException) {
        throw ChatException("Ключ API не подходит. Проверьте его в настройках.", e)
    } catch (e: RateLimitException) {
        throw ChatException("Слишком много запросов. Подождите минуту и попробуйте снова.", e)
    } catch (e: AnthropicServiceException) {
        throw ChatException("Ошибка сервиса Claude (${e.statusCode()}): ${e.message}", e)
    } catch (e: AnthropicIoException) {
        throw ChatException("Нет связи с сервером. Проверьте интернет и попробуйте снова.", e)
    }

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"

        fun describeStop(stopReason: String?): String? = when (stopReason) {
            StopReason.MAX_TOKENS.asString() -> "Ответ оборван из-за длины. Напишите «продолжай»."
            StopReason.REFUSAL.asString() -> "Claude отказался отвечать на этот запрос. Попробуйте переформулировать."
            else -> null
        }
    }
}
