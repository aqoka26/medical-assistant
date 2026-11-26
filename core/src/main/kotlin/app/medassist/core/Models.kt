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
    val profileId: String = Profile.ME,
)

object SupportedFiles {
    const val PDF = "application/pdf"
    val IMAGES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
    val TEXT = setOf("text/plain", "text/markdown", "text/csv")

    fun isSupported(mime: String): Boolean = mime == PDF || mime in IMAGES || mime in TEXT
}

/** A family member whose documents are analysed separately. */
@Serializable
data class Profile(
    val id: String,
    val name: String,
    val birthYear: Int? = null,
    /** "м" or "ж", or null when not given. */
    val sex: String? = null,
) {
    fun describe(): String = buildString {
        append(name)
        sex?.let { append(", пол: ").append(if (it == "ж") "женский" else "мужской") }
        birthYear?.let { append(", год рождения: ").append(it) }
    }

    companion object {
        const val ME = "me"
        val DEFAULT = Profile(ME, "Я")
    }
}

/** One lab value taken from an answer, for the dynamics charts. */
@Serializable
data class LabResult(
    val name: String,
    val value: Double,
    val unit: String = "",
    /** Date the sample was taken, YYYY-MM-DD. */
    val date: String,
    val ref: String = "",
    val profileId: String = Profile.ME,
)

@Serializable
data class Reminder(
    val id: Int,
    val text: String,
    val at: Long,
)
