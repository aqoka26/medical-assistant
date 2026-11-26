package app.medassist

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.medassist.core.Attachment
import app.medassist.core.AuthClient
import app.medassist.core.Usage
import app.medassist.core.ChatMessage
import app.medassist.core.ChatException
import app.medassist.core.GeminiChat
import app.medassist.core.Conversation
import app.medassist.core.Role
import app.medassist.core.LabResult
import app.medassist.core.Labs
import app.medassist.core.Profile
import app.medassist.core.Reminder
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

enum class Screen {
    LOGIN, HOME, RECORDS, LABS, PROFILE, NEW, REPORT;

    val isTab: Boolean get() = this == HOME || this == RECORDS || this == LABS || this == PROFILE
}

data class UiState(
    val screen: Screen = Screen.HOME,
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
    /** Logged-in account name on the owner's server, if accounts are on. */
    val login: String? = null,
    val authBusy: Boolean = false,
    val usage: Usage? = null,
    val lockEnabled: Boolean = false,
    val locked: Boolean = false,
    /** Text shared into the app from another app, to put into the input box. */
    val draft: String? = null,
    val profiles: List<Profile> = listOf(Profile.DEFAULT),
    val profileId: String = Profile.ME,
    val labs: List<LabResult> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
) {
    val ready: Boolean get() = usesProxy || apiKey.isNotBlank()
    val profile: Profile get() = profiles.firstOrNull { it.id == profileId } ?: Profile.DEFAULT
    val profileConversations: List<Conversation> get() = conversations.filter { it.profileId == profileId }
    val profileLabs: List<LabResult> get() = labs.filter { it.profileId == profileId }
}

const val ALL_FILES_MARKER = "ВСЕ ФАЙЛЫ ЗАГРУЖЕНЫ"

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val importer = FileImporter(app, store)
    private val systemPrompt: String by lazy {
        val read = { name: String -> app.assets.open(name).bufferedReader().use { it.readText() } }
        read("system_prompt.md") + "\n\n" + read("app_rules.md")
    }

    private val usesProxy = BuildConfig.PROXY_URL.isNotBlank()

    private val auth by lazy { AuthClient(BuildConfig.PROXY_URL) }

    private fun client(key: String = _state.value.apiKey): GeminiChat =
        if (usesProxy) GeminiChat(
            apiKey = null,
            baseUrl = BuildConfig.PROXY_URL,
            appToken = BuildConfig.APP_TOKEN,
            authToken = store.authToken,
        )
        else GeminiChat(key)

    /** The owner's server has accounts unless it told us otherwise. */
    private fun needsLogin() = usesProxy && store.authToken == null && !store.accountsOff

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val all = store.loadAll()
        val profiles = store.profiles
        val profileId = store.currentProfileId.takeIf { id -> profiles.any { it.id == id } } ?: Profile.ME
        val current = all.firstOrNull { it.id == store.currentConversationId && it.profileId == profileId }
        _state.value = UiState(
            profiles = profiles,
            profileId = profileId,
            labs = store.labs,
            reminders = store.reminders.filter { it.at > System.currentTimeMillis() },
            conversations = all,
            current = current,
            apiKey = store.apiKey,
            model = store.model,
            disclaimerAccepted = store.disclaimerAccepted,
            usesProxy = usesProxy,
            login = store.authLogin,
            lockEnabled = store.lockEnabled,
            locked = store.lockEnabled,
            screen = when {
                needsLogin() -> Screen.LOGIN
                !usesProxy && store.apiKey.isBlank() && store.disclaimerAccepted -> Screen.PROFILE
                else -> Screen.HOME
            },
        )
    }

    /** Design screenshots in CI: fills the screen with sample data, saves nothing. */
    fun demo(name: String) {
        if (!BuildConfig.DEMO) return
        if (name == "welcome") { _state.update { it.copy(disclaimerAccepted = false) }; return }
        val screen = runCatching { Screen.valueOf(name.uppercase()) }.getOrDefault(Screen.HOME)
        _state.value = DemoData.state(_state.value, screen)
    }

    fun show(screen: Screen) {
        _state.update { it.copy(screen = screen) }
        if (screen == Screen.PROFILE || screen == Screen.HOME) refreshUsage()
    }

    // ---------- accounts ----------

    fun login(login: String, password: String) = authenticate { auth.login(login, password) }

    fun register(login: String, password: String, invite: String) =
        authenticate { auth.register(login, password, invite) }

    private fun authenticate(call: () -> app.medassist.core.Account) {
        _state.update { it.copy(authBusy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val account = call()
                store.authToken = account.token
                store.authLogin = account.login
                store.accountsOff = false
                _state.update {
                    it.copy(
                        authBusy = false, login = account.login, screen = Screen.HOME,
                        usage = Usage(account.login, account.usedToday, account.dailyLimit),
                        notice = "Вы вошли как ${account.login}",
                    )
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Не удалось войти"
                if ("не включены" in msg) {
                    // The server has no accounts yet: work without login.
                    store.accountsOff = true
                    _state.update { it.copy(authBusy = false, screen = Screen.HOME) }
                } else {
                    _state.update { it.copy(authBusy = false, notice = msg) }
                }
            }
        }
    }

    fun logout() {
        val token = store.authToken
        store.authToken = null
        store.authLogin = null
        _state.update { it.copy(login = null, usage = null, screen = Screen.LOGIN) }
        if (token != null) viewModelScope.launch(Dispatchers.IO) { auth.logout(token) }
    }

    private fun refreshUsage() {
        val token = store.authToken ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { auth.me(token) }.onSuccess { u -> _state.update { it.copy(usage = u) } }
        }
    }

    private fun onAuthLost() {
        store.authToken = null
        store.authLogin = null
        store.accountsOff = false
        _state.update { it.copy(login = null, usage = null, screen = Screen.LOGIN) }
    }

    // ---------- app lock ----------

    fun setLockEnabled(enabled: Boolean) {
        store.lockEnabled = enabled
        _state.update { it.copy(lockEnabled = enabled) }
    }

    fun lock() {
        if (store.lockEnabled) _state.update { it.copy(locked = true) }
    }

    fun unlock() = _state.update { it.copy(locked = false) }

    // ---------- sharing into the app ----------

    fun receiveShared(uris: List<Uri>, text: String?) {
        if (uris.isNotEmpty()) addFiles(uris)
        if (!text.isNullOrBlank()) _state.update { it.copy(draft = text) }
        _state.update { if (it.screen == Screen.LOGIN) it else it.copy(screen = Screen.NEW, current = null) }
    }

    // ---------- family profiles ----------

    fun selectProfile(id: String) {
        if (_state.value.sending) return
        store.currentProfileId = id
        store.currentConversationId = null
        _state.update { it.copy(profileId = id, current = null, pending = emptyList(), screen = if (it.screen == Screen.REPORT) Screen.HOME else it.screen) }
    }

    fun addProfile(name: String, birthYear: Int?, sex: String?) {
        val p = Profile(UUID.randomUUID().toString(), name.trim(), birthYear, sex)
        store.profiles = _state.value.profiles + p
        _state.update { it.copy(profiles = store.profiles) }
        selectProfile(p.id)
    }

    fun updateProfile(p: Profile) {
        store.profiles = _state.value.profiles.map { if (it.id == p.id) p else it }
        _state.update { it.copy(profiles = store.profiles) }
    }

    /** Removes a family member; their analyses and lab values move to «Я», nothing is lost. */
    fun deleteProfile(id: String) {
        if (id == Profile.ME || _state.value.sending) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.value.conversations.filter { it.profileId == id }.forEach { store.save(it.copy(profileId = Profile.ME)) }
            store.labs = _state.value.labs.map { if (it.profileId == id) it.copy(profileId = Profile.ME) else it }
            store.profiles = _state.value.profiles.filterNot { it.id == id }
            _state.update { it.copy(profiles = store.profiles, conversations = store.loadAll(), labs = store.labs) }
            if (_state.value.profileId == id) selectProfile(Profile.ME)
        }
    }

    // ---------- lab dynamics ----------

    fun deleteLab(lab: LabResult) {
        store.labs = _state.value.labs - lab
        _state.update { it.copy(labs = store.labs) }
    }

    // ---------- reminders ----------

    fun addReminder(text: String, at: Long) {
        val r = Reminder((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), text.trim(), at)
        store.reminders = store.reminders + r
        Reminders.schedule(getApplication<Application>(), r)
        _state.update { it.copy(reminders = (it.reminders + r).sortedBy { x -> x.at }, notice = "Напоминание сохранено") }
    }

    fun deleteReminder(r: Reminder) {
        Reminders.cancel(getApplication<Application>(), r)
        store.reminders = store.reminders.filterNot { it.id == r.id }
        _state.update { it.copy(reminders = it.reminders.filterNot { x -> x.id == r.id }) }
    }

    fun clearDraft() = _state.update { it.copy(draft = null) }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun acceptDisclaimer() {
        store.disclaimerAccepted = true
        _state.update {
            it.copy(
                disclaimerAccepted = true,
                screen = when {
                    needsLogin() -> Screen.LOGIN
                    !it.ready -> Screen.PROFILE
                    else -> it.screen
                },
            )
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
                    it.copy(apiKey = k, model = m, checkingKey = false, notice = if (usesProxy) "Сохранено" else "Ключ работает и сохранён")
                }
            } else {
                _state.update { it.copy(checkingKey = false, notice = error.message ?: "Ключ не подошёл") }
            }
        }
    }

    fun newConversation() {
        if (_state.value.sending) return
        store.currentConversationId = null
        _state.update { it.copy(current = null, pending = emptyList(), screen = Screen.NEW, status = null) }
    }

    fun open(conversation: Conversation) {
        if (_state.value.sending) return
        store.currentConversationId = conversation.id
        _state.update { it.copy(current = conversation, pending = emptyList(), screen = Screen.REPORT) }
    }

    fun delete(conversation: Conversation) {
        if (_state.value.sending && _state.value.current?.id == conversation.id) return
        // Copies uploaded to Google are deleted by Google automatically after 48 hours.
        store.delete(conversation)
        _state.update { s ->
            s.copy(
                conversations = s.conversations.filterNot { it.id == conversation.id },
                current = if (s.current?.id == conversation.id) null else s.current,
                screen = if (s.current?.id == conversation.id && s.screen == Screen.REPORT) Screen.RECORDS else s.screen,
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
            _state.update { it.copy(screen = Screen.PROFILE, notice = "Сначала введите ключ API") }
            return
        }
        val now = System.currentTimeMillis()
        val base = s.current ?: Conversation(
            id = UUID.randomUUID().toString(),
            title = "Разбор от " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru")).format(Date(now)),
            createdAt = now,
            profileId = s.profileId,
        )
        val conversation = base.copy(
            messages = base.messages + ChatMessage(Role.USER, text.trim(), s.pending, now)
        )
        persist(conversation)
        _state.update { it.copy(pending = emptyList()) }
        runReply(conversation)
    }

    /** Starts a new analysis with all attached documents and the answers from the form. */
    fun startAnalysis(complaints: String, medicines: String, question: String) {
        val s = _state.value
        if (s.sending || s.pending.isEmpty()) return
        val text = buildString {
            appendLine("Новый разбор. Все документы приложены к этому сообщению.")
            if (complaints.isNotBlank()) appendLine("Жалобы и самочувствие: ${complaints.trim()}")
            if (medicines.isNotBlank()) appendLine("Лекарства и добавки: ${medicines.trim()}")
            if (question.isNotBlank()) appendLine("Что хочу узнать: ${question.trim()}")
            append(ALL_FILES_MARKER)
        }
        _state.update { it.copy(current = null) }
        send(text)
        _state.update { it.copy(screen = Screen.REPORT) }
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
        val profile = _state.value.profiles.firstOrNull { it.id == start.profileId } ?: Profile.DEFAULT
        val prompt = buildString {
            append(systemPrompt)
            append("\n\n").append(Labs.INSTRUCTION)
            if (profile.id != Profile.ME || profile.birthYear != null || profile.sex != null) {
                append("\n\n## ПАЦИЕНТ\n\nДокументы относятся к: ").append(profile.describe()).append('.')
            }
        }
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
                    chat.reply(m, prompt, conversation.messages, onText)
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
                val found = Labs.extract(result.text, conversation.profileId)
                if (found.isNotEmpty()) {
                    store.labs = Labs.merge(store.labs, found)
                    _state.update { it.copy(labs = store.labs, notice = "Показатели добавлены в «Динамику»: ${found.size}") }
                }
                if (conversation.messages.none { it.role == Role.ASSISTANT && !it.isError }) {
                    app.medassist.core.Report.parse(result.text).title?.let { t ->
                        conversation = conversation.copy(title = t.take(60))
                    }
                }
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
                if (e is ChatException && e.kind == ChatException.Kind.AUTH) onAuthLost()
                val note = e.message ?: "Неизвестная ошибка"
                conversation = conversation.copy(
                    messages = conversation.messages +
                        ChatMessage(Role.ASSISTANT, note, timestamp = System.currentTimeMillis(), isError = true)
                )
            }
            persist(conversation)
            _state.update { it.copy(sending = false, streamingText = null, status = null) }
            refreshUsage()
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
