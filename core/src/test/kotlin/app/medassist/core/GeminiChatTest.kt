package app.medassist.core

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeminiChatTest {
    private val chat = GeminiChat("test-key")

    @Test
    fun errorNotesSkippedAndTurnsMerged() {
        val history = listOf(
            ChatMessage(Role.USER, "пакет 1", listOf(Attachment("a.pdf", "a.pdf", SupportedFiles.PDF, "https://x/files/1")), 1),
            ChatMessage(Role.ASSISTANT, "Ошибка сети", timestamp = 2, isError = true),
            ChatMessage(Role.USER, "ещё раз", timestamp = 3),
            ChatMessage(Role.ASSISTANT, "Checkpoint", timestamp = 4),
            ChatMessage(Role.USER, "ВСЕ ФАЙЛЫ ЗАГРУЖЕНЫ", timestamp = 5),
        )
        val req = chat.buildRequest("system", history)
        val contents = req["contents"]!!.jsonArray
        assertEquals(3, contents.size)
        assertEquals("model", contents[1].jsonObject["role"]!!.jsonPrimitive.content)
        val firstParts = contents[0].jsonObject["parts"]!!.jsonArray
        assertEquals(3, firstParts.size)
        assertEquals("https://x/files/1", firstParts[0].jsonObject["file_data"]!!.jsonObject["file_uri"]!!.jsonPrimitive.content)
    }

    @Test
    fun missingUploadFails() {
        val history = listOf(ChatMessage(Role.USER, "", listOf(Attachment("b.jpg", "b.jpg", "image/jpeg")), 1))
        assertFailsWith<ChatException> { chat.buildRequest("system", history) }
    }

    @Test
    fun parsesStreamChunkAndSkipsThoughts() {
        val c = GeminiChat.parseChunk(
            """{"candidates":[{"content":{"parts":[{"text":"думаю","thought":true},{"text":"Ответ"}]},"finishReason":"STOP"}]}"""
        )
        assertEquals("Ответ", c.text)
        assertEquals("STOP", c.finishReason)
    }

    @Test
    fun classifiesDailyLimit() {
        val e = GeminiChat.toError(429, """{"error":{"message":"Quota exceeded","details":[{"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier"}]}}""")
        assertEquals(ChatException.Kind.DAILY_LIMIT, e.kind)
    }
}
