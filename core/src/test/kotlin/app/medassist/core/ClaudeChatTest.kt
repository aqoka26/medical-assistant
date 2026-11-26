package app.medassist.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClaudeChatTest {
    private val chat = ClaudeChat("test-key")

    @Test
    fun errorNotesAreSkippedAndUserTurnsMerged() {
        val history = listOf(
            ChatMessage(Role.USER, "пакет 1", listOf(Attachment("a.pdf", "a.pdf", SupportedFiles.PDF, "file_1")), 1),
            ChatMessage(Role.ASSISTANT, "Ошибка сети", timestamp = 2, isError = true),
            ChatMessage(Role.USER, "пакет 1 ещё раз", timestamp = 3),
            ChatMessage(Role.ASSISTANT, "Checkpoint", timestamp = 4),
            ChatMessage(Role.USER, "ВСЕ ФАЙЛЫ ЗАГРУЖЕНЫ", timestamp = 5),
        )
        val params = chat.buildParams("system", history)
        val messages = params.messages()
        assertEquals(3, messages.size)
        // first user turn: document + text + text
        assertEquals(3, messages[0].content().asContentBlockParams().size)
        assertEquals("Checkpoint", messages[1].content().string().get())
    }

    @Test
    fun attachmentWithoutUploadFails() {
        val history = listOf(
            ChatMessage(Role.USER, "", listOf(Attachment("b.jpg", "b.jpg", "image/jpeg")), 1),
        )
        assertFailsWith<ChatException> { chat.buildParams("system", history) }
    }
}
