package app.medassist

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.medassist.core.Attachment
import app.medassist.core.ChatMessage
import app.medassist.core.ClaudeChat
import app.medassist.core.Conversation
import app.medassist.core.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class Screen { CHAT, HISTORY, SETTINGS }

data class UiState(
    val screen: Screen = Screen.CHAT,
    val conversations: List<Conversation> = emptyList(),
    val current: Conversation? = null,
    val pending: List<Attachment> = emptyList(),
    val sending: Boolean = false,
    val streamingText: String? = null,
    val status: String? = null,
    val notice: String? = null,
    val apiKey: String = "",
    val disclaimerAccepted: Boolean = false,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val importer = FileImporter(app, store)
    private val systemPrompt: String by lazy {
        app.assets.open("system_prompt.md").bufferedReader().use { it.readText() }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val all = store.loadAll()
        val current = all.firstOrNull { it.id == store.currentConversationId }
        _state.value = UiState(
            conversations = all,
            current = current,
            apiKey = store.apiKey,
            disclaimerAccepted = store.disclaimerAccepted,
            screen = if (store.apiKey.isBlank() && store.disclaimerAccepted) Screen.SETTINGS else Screen.CHAT,
        )
    }

    fun show(screen: Screen) = _state.update { it.copy(screen = screen) }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun acceptDisclaimer() {
        store.disclaimerAccepted = true
        _state.update {
            it.copy(disclaimerAccepted = true, screen = if (it.apiKey.isBlank()) Screen.SETTINGS else it.screen)
        }
    }

    fun saveApiKey(key: String) {
        store.apiKey = key
        _state.update { it.copy(apiKey = key.trim(), screen = Screen.CHAT, notice = "Ключ сохранён") }
    }

    fun newConversation() {
        if (_state.value.sending) return
        store.currentConversationId = null
        _state.update { it.copy(current = null, pending = emptyList(), screen = Screen.CHAT, status = null) }
    }

    fun open(conversation: Conversation) {
        if (_state.value.sending) return
        store.currentConversationId = conversation.id
        _state.update { it.copy(current = conversation, pending = emptyList(), screen = Screen.CHAT) }
    }

    fun delete(conversation: Conversation) {
        if (_state.value.sending && _state.value.current?.id == conversation.id) return
        val key = _state.value.apiKey
        viewModelScope.launch(Dispatchers.IO) {
            // Also remove the uploaded copies from Anthropic's file storage.
            if (key.isNotBlank()) {
                val chat = ClaudeChat(key)
                conversation.messages.flatMap { it.attachments }.mapNotNull { it.remoteId }.forEach(chat::deleteRemote)
            }
        }
        store.delete(conversation)
        _state.update { s ->
            s.copy(
                conversations = s.conversations.filterNot { it.id == conversation.id },
                current = if (s.current?.id == conversation.id) null else s.current,
            )
        }
    }

    fun addFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(status = "Подготовка файлов…") }
            val added = mutableListOf<Attachment>()
            val errors = mutableListOf<String>()
            for (uri in uris) {
                runCatching { importer.import(uri) }
                    .onSuccess { added += it }
                    .onFailure { errors += (it.message ?: "Не удалось открыть файл") }
            }
            _state.update {
                it.copy(pending = it.pending + added, status = null, notice = errors.firstOrNull())
            }
        }
    }

    fun removePending(attachment: Attachment) {
        store.attachmentFile(attachment.localName).delete()
        _state.update { it.copy(pending = it.pending - attachment) }
    }

    fun send(text: String) {
        val s = _state.value
        if (s.sending) return
        if (text.isBlank() && s.pending.isEmpty()) return
        if (s.apiKey.isBlank()) {
            _state.update { it.copy(screen = Screen.SETTINGS, notice = "Сначала введите ключ API") }
            return
        }
        val now = System.currentTimeMillis()
        val base = s.current ?: Conversation(
            id = UUID.randomUUID().toString(),
            title = "Разбор от " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru")).format(Date(now)),
            createdAt = now,
        )
        val conversation = base.copy(
            messages = base.messages + ChatMessage(Role.USER, text.trim(), s.pending, now)
        )
        persist(conversation)
        _state.update { it.copy(pending = emptyList()) }
        runReply(conversation)
    }

    /** Sends the conversation again after an error (error notes are never sent to Claude). */
    fun retry() {
        val conversation = _state.value.current ?: return
        if (_state.value.sending) return
        runReply(conversation)
    }

    private fun runReply(start: Conversation) {
        val key = _state.value.apiKey
        _state.update { it.copy(sending = true, streamingText = null, status = "Отправка…") }
        viewModelScope.launch(Dispatchers.IO) {
            var conversation = start
            try {
                val chat = ClaudeChat(key)
                conversation = uploadMissing(chat, conversation)
                persist(conversation)
                _state.update { it.copy(status = "Claude изучает данные…") }
                val result = chat.reply(systemPrompt, conversation.messages) { partial ->
                    _state.update { it.copy(streamingText = partial, status = null) }
                }
                val now = System.currentTimeMillis()
                if (result.text.isNotBlank()) {
                    conversation = conversation.copy(
                        messages = conversation.messages + ChatMessage(Role.ASSISTANT, result.text, timestamp = now)
                    )
                }
                ClaudeChat.describeStop(result.stopReason)?.let { note ->
                    conversation = conversation.copy(
                        messages = conversation.messages + ChatMessage(Role.ASSISTANT, note, timestamp = now, isError = true)
                    )
                }
            } catch (e: Exception) {
                val note = e.message ?: "Неизвестная ошибка"
                conversation = conversation.copy(
                    messages = conversation.messages +
                        ChatMessage(Role.ASSISTANT, note, timestamp = System.currentTimeMillis(), isError = true)
                )
            }
            persist(conversation)
            _state.update { it.copy(sending = false, streamingText = null, status = null) }
        }
    }

    private fun uploadMissing(chat: ClaudeChat, conversation: Conversation): Conversation {
        val messages = conversation.messages.map { msg ->
            if (msg.attachments.all { it.remoteId != null }) return@map msg
            msg.copy(attachments = msg.attachments.map { a ->
                if (a.remoteId != null) return@map a
                _state.update { it.copy(status = "Загрузка файла «${a.displayName}»…") }
                val bytes = store.attachmentFile(a.localName).readBytes()
                a.copy(remoteId = chat.upload(bytes, a.displayName, a.mimeType))
            })
        }
        return conversation.copy(messages = messages)
    }

    private fun persist(conversation: Conversation) {
        store.save(conversation)
        store.currentConversationId = conversation.id
        _state.update { s ->
            s.copy(
                current = conversation,
                conversations = (listOf(conversation) + s.conversations.filterNot { it.id == conversation.id })
                    .sortedByDescending { it.createdAt },
            )
        }
    }
}
