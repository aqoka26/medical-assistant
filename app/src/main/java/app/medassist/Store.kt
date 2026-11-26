package app.medassist

import android.content.Context
import app.medassist.core.Conversation
import app.medassist.core.LabResult
import app.medassist.core.Profile
import app.medassist.core.Reminder
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Keeps chats, attached files and settings in the app's private storage on the phone. */
class Store(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val conversationsDir = File(context.filesDir, "conversations").apply { mkdirs() }
    val attachmentsDir = File(context.filesDir, "attachments").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value.trim()).apply()

    var model: String
        get() = when (val saved = prefs.getString("model", null)) {
            // Names used by older versions of the app that Google does not serve.
            null, "", "gemini-3.8-flash" -> app.medassist.core.GeminiChat.DEFAULT_MODEL
            "gemini-3.5-flash-lite" -> app.medassist.core.GeminiChat.LITE_MODEL
            else -> saved
        }
        set(value) = prefs.edit().putString("model", value.trim()).apply()

    var authToken: String?
        get() = prefs.getString("auth_token", null)
        set(value) = prefs.edit().putString("auth_token", value).apply()

    var authLogin: String?
        get() = prefs.getString("auth_login", null)
        set(value) = prefs.edit().putString("auth_login", value).apply()

    /** The owner's server answered that accounts are not switched on. */
    var accountsOff: Boolean
        get() = prefs.getBoolean("accounts_off", false)
        set(value) = prefs.edit().putBoolean("accounts_off", value).apply()

    var lockEnabled: Boolean
        get() = prefs.getBoolean("lock_enabled", false)
        set(value) = prefs.edit().putBoolean("lock_enabled", value).apply()

    var disclaimerAccepted: Boolean
        get() = prefs.getBoolean("disclaimer_accepted", false)
        set(value) = prefs.edit().putBoolean("disclaimer_accepted", value).apply()

    var currentConversationId: String?
        get() = prefs.getString("current_conversation", null)
        set(value) = prefs.edit().putString("current_conversation", value).apply()

    fun loadAll(): List<Conversation> =
        conversationsDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f -> runCatching { json.decodeFromString<Conversation>(f.readText()) }.getOrNull() }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()

    fun save(conversation: Conversation) {
        val target = File(conversationsDir, "${conversation.id}.json")
        val tmp = File(conversationsDir, "${conversation.id}.json.tmp")
        tmp.writeText(json.encodeToString(Conversation.serializer(), conversation))
        tmp.renameTo(target)
    }

    fun delete(conversation: Conversation) {
        File(conversationsDir, "${conversation.id}.json").delete()
        conversation.messages.flatMap { it.attachments }.forEach { File(attachmentsDir, it.localName).delete() }
    }

    fun attachmentFile(localName: String) = File(attachmentsDir, localName)

    // ---------- family profiles, lab values, reminders ----------

    private val dataDir = context.filesDir

    private fun <T> readList(name: String, serializer: KSerializer<T>): List<T> =
        runCatching { json.decodeFromString(ListSerializer(serializer), File(dataDir, name).readText()) }.getOrNull()
            ?: emptyList()

    private fun <T> writeList(name: String, serializer: KSerializer<T>, items: List<T>) {
        val tmp = File(dataDir, "$name.tmp")
        tmp.writeText(json.encodeToString(ListSerializer(serializer), items))
        tmp.renameTo(File(dataDir, name))
    }

    var profiles: List<Profile>
        get() = readList("profiles.json", Profile.serializer()).let { saved ->
            if (saved.none { it.id == Profile.ME }) listOf(Profile.DEFAULT) + saved else saved
        }
        set(value) = writeList("profiles.json", Profile.serializer(), value)

    var currentProfileId: String
        get() = prefs.getString("current_profile", Profile.ME) ?: Profile.ME
        set(value) = prefs.edit().putString("current_profile", value).apply()

    var labs: List<LabResult>
        get() = readList("labs.json", LabResult.serializer())
        set(value) = writeList("labs.json", LabResult.serializer(), value)

    var reminders: List<Reminder>
        get() = readList("reminders.json", Reminder.serializer())
        set(value) = writeList("reminders.json", Reminder.serializer(), value)
}
