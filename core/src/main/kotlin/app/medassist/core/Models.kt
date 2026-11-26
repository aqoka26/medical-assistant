package app.medassist.core

import kotlinx.serialization.Serializable

@Serializable
enum class Role { USER, ASSISTANT }

/**
 * A file the user attached. The bytes live on the phone ([localName] inside the app's
 * private folder). Gemini keeps uploaded files for 48 hours only, so [remoteId] (the file
 * URI) is refreshed by uploading again when [uploadedAt] is too old.
 */
@Serializable
data class Attachment(
    val localName: String,
    val displayName: String,
    val mimeType: String,
    val remoteId: String? = null,
    val uploadedAt: Long = 0,
)

@Serializable
data class ChatMessage(
    val role: Role,
    val text: String,
    val attachments: List<Attachment> = emptyList(),
    val timestamp: Long,
    /** Error notes are shown in the chat but never sent to the model. */
    val isError: Boolean = false,
)

@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val messages: List<ChatMessage> = emptyList(),
)

object SupportedFiles {
    const val PDF = "application/pdf"
    val IMAGES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
    val TEXT = setOf("text/plain", "text/markdown", "text/csv")

    fun isSupported(mime: String): Boolean = mime == PDF || mime in IMAGES || mime in TEXT
}
