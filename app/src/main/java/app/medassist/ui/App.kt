package app.medassist.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.medassist.ChatViewModel
import app.medassist.Screen
import app.medassist.UiState
import app.medassist.core.ChatMessage
import app.medassist.core.Conversation
import app.medassist.core.GeminiChat
import app.medassist.core.Role
import com.mikepenz.markdown.m3.Markdown
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val ALL_FILES_UPLOADED = "ВСЕ ФАЙЛЫ ЗАГРУЖЕНЫ"

@Composable
fun App(vm: ChatViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.notice) {
        state.notice?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.clearNotice()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding()) {
            when (state.screen) {
                Screen.CHAT -> ChatScreen(state, vm)
                Screen.HISTORY -> HistoryScreen(state, vm)
                Screen.SETTINGS -> SettingsScreen(state, vm)
            }
        }
    }

    if (!state.disclaimerAccepted) DisclaimerDialog(onAccept = vm::acceptDisclaimer)
}

@Composable
private fun DisclaimerDialog(onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Важно") },
        text = {
            Text(
                "Это приложение помогает разобраться в медицинских документах с помощью ИИ (Google Gemini). " +
                    "Оно не ставит диагноз и не заменяет врача. ИИ может ошибаться.\n\n" +
                    "При опасных симптомах (боль в груди, затруднённое дыхание, потеря сознания, " +
                    "сильное кровотечение, признаки инсульта) сразу звоните 103 или 112.\n\n" +
                    "Ваши файлы и вопросы отправляются в Google. На бесплатном тарифе Google может " +
                    "использовать их для улучшения своих моделей."
            )
        },
        confirmButton = { Button(onClick = onAccept) { Text("Понятно") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(state: UiState, vm: ChatViewModel) {
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val messages = state.current?.messages.orEmpty()

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addFiles(uris)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) vm.addFiles(listOf(Uri.parse(uri)))
    }

    val itemCount = messages.size + (if (state.streamingText != null) 1 else 0)
    LaunchedEffect(itemCount, state.streamingText?.length) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(state.current?.title ?: "Новый разбор", maxLines = 1) },
            windowInsets = WindowInsets(0),
            actions = {
                IconButton(onClick = vm::newConversation, enabled = !state.sending) {
                    Icon(Icons.Filled.Add, contentDescription = "Новый разбор")
                }
                IconButton(onClick = { vm.show(Screen.HISTORY) }) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "История")
                }
                IconButton(onClick = { vm.show(Screen.SETTINGS) }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                }
            },
        )
        HorizontalDivider()

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty() && state.streamingText == null) {
                EmptyChatHelp()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                ) {
                    items(messages.size) { index ->
                        val msg = messages[index]
                        val isLast = index == messages.lastIndex
                        MessageBubble(
                            msg,
                            onRetry = if (msg.isError && isLast && !state.sending) vm::retry else null,
                        )
                    }
                    state.streamingText?.let { partial ->
                        item { StreamingBubble(partial) }
                    }
                }
            }
        }

        if (state.sending || state.status != null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            state.status?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }

        if (state.pending.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.pending.forEach { a ->
                    InputChip(
                        selected = false,
                        onClick = { vm.removePending(a) },
                        label = { Text(fileIcon(a.mimeType) + " " + a.displayName, maxLines = 1) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Убрать", Modifier.size(16.dp)) },
                    )
                }
            }
        }

        if (messages.isNotEmpty() && !state.sending) {
            Row(Modifier.padding(horizontal = 8.dp)) {
                AssistChip(
                    onClick = { vm.send(ALL_FILES_UPLOADED) },
                    label = { Text("✅ Все файлы загружены") },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            TextButton(
                onClick = { pickFiles.launch(arrayOf("application/pdf", "image/*", "text/plain")) },
                enabled = !state.sending,
            ) { Text("📎", style = MaterialTheme.typography.titleLarge) }
            TextButton(
                onClick = {
                    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                    val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                    cameraUri = uri.toString()
                    takePhoto.launch(uri)
                },
                enabled = !state.sending,
            ) { Text("📷", style = MaterialTheme.typography.titleLarge) }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение или пояснение к файлам") },
                maxLines = 6,
            )
            IconButton(
                onClick = {
                    vm.send(input)
                    input = ""
                },
                enabled = !state.sending && (input.isNotBlank() || state.pending.isNotEmpty()),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
            }
        }
    }
}

@Composable
private fun EmptyChatHelp() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Как пользоваться", style = MaterialTheme.typography.titleLarge)
        Text("1. Нажмите 📎 или 📷 и прикрепите анализы, выписки, заключения (PDF или фото). Можно несколько файлов за раз.")
        Text("2. Отправьте пакет. ИИ зарегистрирует документы и подтвердит приём. Повторите для следующих пакетов.")
        Text("3. Когда всё загружено, нажмите «✅ Все файлы загружены».")
        Text("4. Ответьте на уточняющие вопросы, после этого придёт полный анализ.")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Text(
                "Это не диагноз и не замена врачу. При опасных симптомах звоните 103 или 112.",
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

private fun fileIcon(mime: String) = when {
    mime.startsWith("image/") -> "🖼"
    mime == "application/pdf" -> "📄"
    else -> "📝"
}

@Composable
private fun MessageBubble(msg: ChatMessage, onRetry: (() -> Unit)?) {
    val context = LocalContext.current
    val isUser = msg.role == Role.USER
    val color = when {
        msg.isError -> MaterialTheme.colorScheme.errorContainer
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = if (isUser) 320.dp else 2000.dp)
                .background(color, RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            msg.attachments.forEach { a ->
                Text(fileIcon(a.mimeType) + " " + a.displayName, style = MaterialTheme.typography.bodySmall)
            }
            if (msg.attachments.isNotEmpty() && msg.text.isNotBlank()) Spacer(Modifier.height(6.dp))
            when {
                msg.isError -> {
                    Text("⚠️ " + msg.text, color = MaterialTheme.colorScheme.onErrorContainer)
                    if (onRetry != null) {
                        OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 6.dp)) { Text("Повторить") }
                    }
                }
                isUser -> if (msg.text.isNotBlank()) SelectionContainer { Text(msg.text) }
                else -> {
                    SelectionContainer { Markdown(content = msg.text) }
                    TextButton(onClick = { copyToClipboard(context, msg.text) }) { Text("Копировать") }
                }
            }
        }
    }
}

@Composable
private fun StreamingBubble(text: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Text(text.ifEmpty { "…" })
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Ответ", text))
    Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(state: UiState, vm: ChatViewModel) {
    BackHandler { vm.show(Screen.CHAT) }
    var toDelete by remember { mutableStateOf<Conversation?>(null) }
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru")) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("История разборов") },
            windowInsets = WindowInsets(0),
            navigationIcon = {
                IconButton(onClick = { vm.show(Screen.CHAT) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            },
        )
        if (state.conversations.isEmpty()) {
            Text("Пока нет сохранённых разборов.", Modifier.padding(20.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.conversations, key = { it.id }) { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.open(c) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c.title, fontWeight = if (c.id == state.current?.id) FontWeight.Bold else FontWeight.Normal)
                        Text(
                            "${fmt.format(Date(c.createdAt))} · сообщений: ${c.messages.size}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(onClick = { toDelete = c }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                    }
                }
                HorizontalDivider()
            }
        }
    }

    toDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить разбор?") },
            text = { Text("«${c.title}» и все его файлы будут удалены с телефона. Это нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(c); toDelete = null }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(state: UiState, vm: ChatViewModel) {
    BackHandler { vm.show(Screen.CHAT) }
    var key by rememberSaveable { mutableStateOf(state.apiKey) }
    var model by rememberSaveable { mutableStateOf(state.model) }
    var visible by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Настройки") },
            windowInsets = WindowInsets(0),
            navigationIcon = {
                IconButton(onClick = { vm.show(Screen.CHAT) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.usesProxy) {
            Text("Бесплатный ключ Google Gemini", style = MaterialTheme.typography.titleMedium)
            Text(
                "1. Откройте aistudio.google.com и войдите в Google-аккаунт.\n" +
                    "2. Нажмите «Get API key», затем «Create API key».\n" +
                    "3. Скопируйте ключ (начинается с AIza…).\n" +
                    "4. Вставьте его сюда и нажмите «Проверить и сохранить».\n" +
                    "Карта не нужна.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("AIza…") },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.saveSettings(key, model) },
                    enabled = key.isNotBlank() && !state.checkingKey,
                ) { Text(if (state.checkingKey) "Проверяю…" else "Проверить и сохранить") }
                OutlinedButton(onClick = { visible = !visible }) { Text(if (visible) "Скрыть" else "Показать") }
            }

            }

            Text("Модель", style = MaterialTheme.typography.titleMedium)
            listOf(
                GeminiChat.DEFAULT_MODEL to "умнее, около 20 запросов в день бесплатно",
                GeminiChat.LITE_MODEL to "проще, около 500 запросов в день бесплатно",
            ).forEach { (id, hint) ->
                Row(
                    Modifier.fillMaxWidth().clickable { model = id },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = model == id, onClick = { model = id })
                    Column {
                        Text(id)
                        Text(hint, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Или впишите другую модель") },
                singleLine = true,
            )
            if (state.usesProxy) {
                Button(onClick = { vm.saveSettings("", model) }, enabled = !state.checkingKey) {
                    Text(if (state.checkingKey) "Проверяю…" else "Сохранить")
                }
            }
            Text(
                "Если дневной лимит основной модели закончится, приложение само переключится на облегчённую. " +
                    "Лимиты обновляются в полночь по тихоокеанскому времени (около 11:00 по Москве). " +
                    "Изменение модели сохраняется кнопкой «Сохранить».",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.width(1.dp))
            Text(
                (if (state.usesProxy) "" else "Ключ хранится только на этом телефоне. ") +
                    "Файлы и сообщения отправляются в Google; " +
                    "на бесплатном тарифе Google может использовать их для улучшения своих моделей. " +
                    "Копии файлов на сервере Google удаляются автоматически через 48 часов.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
