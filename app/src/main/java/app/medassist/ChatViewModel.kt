package app.medassist

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.medassist.core.Attachment
import app.medassist.core.ChatMessage
import app.medassist.core.ChatException
import app.medassist.core.GeminiChat
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
    val model: String = GeminiChat.DEFAULT_MODEL,
    val disclaimerAccepted: Boolean = false,
    val checkingKey: Boolean = false,
    /** True when the app talks to the owner's proxy and nobody needs a key. */
    val usesProxy: Boolean = false,
) {
    val ready: Boolean get() = usesProxy || apiKey.isNotBlank()
}

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val importer = FileImporter(app, store)
    private val systemPrompt: String by lazy {
        app.assets.open("system_prompt.md").bufferedReader().use { it.readText() }
    }

    private val usesProxy = BuildConfig.PROXY_URL.isNotBlank()

    private fun client(key: String = _state.value.apiKey): GeminiChat =
        if (usesProxy) GeminiChat(apiKey = null, baseUrl = BuildConfig.PROXY_URL, appToken = BuildConfig.APP_TOKEN)
        else GeminiChat(key)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val all = store.loadAll()
        val current = all.firstOrNull { it.id == store.currentConversationId }
        _state.value = UiState(
            conversations = all,
            current = current,
            apiKey = store.apiKey,
            model = store.model,
            disclaimerAccepted = store.disclaimerAccepted,
            usesProxy = usesProxy,
            screen = if (!usesProxy && store.apiKey.isBlank() && store.disclaimerAccepted) Screen.SETTINGS else Screen.CHAT,
        )
    }

    fun show(screen: Screen) = _state.update { it.copy(screen = screen) }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun acceptDisclaimer() {
        store.disclaimerAccepted = true
        _state.update {
            it.copy(disclaimerAccepted = true, screen = if (!it.ready) Screen.SETTINGS else it.screen)
        }
    }

    fun saveSettings(key: String, model: String) {
        val k = key.trim()
        val m = model.trim().ifBlank { GeminiChat.DEFAULT_MODEL }
        _state.update { it.copy(checkingKey = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val error = runCatching { client(k).checkKey() }.exceptionOrNull()
            if (error == null) {
                store.apiKey = k
                store.model = m
                _state.update {
                    it.copy(apiKey = k, model = m, checkingKey = false, screen = Screen.CHAT, notice = if (usesProxy) "Сохранено" else "Ключ работает и сохранён")
                }
            } else {
                _state.update { it.copy(checkingKey = false, notice = error.message ?: "Ключ не подошёл") }
            }
        }
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
        // Copies uploaded to Google are deleted by Google automatically after 48 hours.
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
        if (!s.ready) {
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

    /** Sends the conversation again after an error (error notes are never sent to the model). */
    fun retry() {
        val conversation = _state.value.current ?: return
        if (_state.value.sending) return
        runReply(conversation)
    }

    private fun runReply(start: Conversation) {
        val key = _state.value.apiKey
        val model = _state.value.model
        _state.update { it.copy(sending = true, streamingText = null, status = "Отправка…") }
        viewModelScope.launch(Dispatchers.IO) {
            var conversation = start
            try {
                val chat = client(key)
                conversation = refreshUploads(chat, conversation, force = false)
                persist(conversation)
                val onText: (String) -> Unit = { partial -> _state.update { it.copy(streamingText = partial, status = null) } }
                val ask: (String) -> app.medassist.core.ReplyResult = { m ->
                    _state.update { it.copy(status = "Gemini изучает данные…") }
                    chat.reply(m, systemPrompt, conversation.messages, onText)
                }
                val result = try {
                    ask(model)
                } catch (e: ChatException) {
                    when {
                        e.kind == ChatException.Kind.FILE_GONE -> {
                            conversation = refreshUploads(chat, conversation, force = true)
                            persist(conversation)
                            ask(model)
                        }
                        e.kind == ChatException.Kind.DAILY_LIMIT && model != GeminiChat.LITE_MODEL -> {
                            _state.update {
                                it.copy(notice = "Дневной лимит основной модели исчерпан, отвечает облегчённая модель")
                            }
                            ask(GeminiChat.LITE_MODEL)
                        }
                        else -> throw e
                    }
                }
                val now = System.currentTimeMillis()
                if (result.text.isNotBlank()) {
                    conversation = conversation.copy(
                        messages = conversation.messages + ChatMessage(Role.ASSISTANT, result.text, timestamp = now)
                    )
                }
                val note = GeminiChat.describeFinish(result.finishReason)
                    ?: if (result.text.isBlank()) "Пустой ответ. Нажмите «Повторить»." else null
                note?.let {
                    conversation = conversation.copy(
                        messages = conversation.messages + ChatMessage(Role.ASSISTANT, it, timestamp = now, isError = true)
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

    /** Uploads files that were never uploaded or whose 48-hour server copy has expired. */
    private fun refreshUploads(chat: GeminiChat, conversation: Conversation, force: Boolean): Conversation {
        val now = System.currentTimeMillis()
        fun stale(a: Attachment) =
            force || a.remoteId == null || now - a.uploadedAt > GeminiChat.FILE_LIFETIME_MS
        val messages = conversation.messages.map { msg ->
            if (msg.attachments.none(::stale)) return@map msg
            msg.copy(attachments = msg.attachments.map { a ->
                if (!stale(a)) return@map a
                _state.update { it.copy(status = "Загрузка файла «${a.displayName}»…") }
                val file = store.attachmentFile(a.localName)
                if (!file.exists()) throw ChatException("Файл «${a.displayName}» пропал с телефона. Начните новый разбор.")
                a.copy(remoteId = chat.upload(file.readBytes(), a.displayName, a.mimeType), uploadedAt = now)
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
