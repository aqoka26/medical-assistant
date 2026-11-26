package app.medassist.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.medassist.ChatViewModel
import app.medassist.Screen
import app.medassist.UiState
import app.medassist.core.Attachment
import app.medassist.core.ChatMessage
import app.medassist.core.Role
import com.mikepenz.markdown.m3.Markdown
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(state: UiState, vm: ChatViewModel) {
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val messages = state.current?.messages.orEmpty()

    // Text shared into the app from another app lands in the input box.
    LaunchedEffect(state.draft) {
        state.draft?.let {
            input = if (input.isBlank()) it else input + "\n" + it
            vm.clearDraft()
        }
    }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addFiles(uris)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) vm.addFiles(listOf(Uri.parse(uri)))
    }
    val openPicker = { pickFiles.launch(arrayOf("application/pdf", "image/*", "text/plain")) }
    val openCamera = {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        cameraUri = uri.toString()
        takePhoto.launch(uri)
    }

    val itemCount = messages.size + (if (state.sending) 1 else 0)
    LaunchedEffect(itemCount, state.streamingText?.length) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Column(Modifier.fillMaxSize()) {
        BrandHeader(
            title = "МедАнализ",
            subtitle = state.current?.title ?: "Новый разбор",
            actions = {
                IconButton(onClick = vm::newConversation, enabled = !state.sending) {
                    Icon(Icons.Filled.Add, contentDescription = "Новый разбор", tint = Color.White)
                }
                IconButton(onClick = { vm.show(Screen.HISTORY) }) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "История", tint = Color.White)
                }
                IconButton(onClick = { vm.show(Screen.SETTINGS) }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = Color.White)
                }
            },
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty() && !state.sending) {
                EmptyChat(onAttach = openPicker, onCamera = openCamera)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                ) {
                    items(messages.size) { index ->
                        val msg = messages[index]
                        val isLast = index == messages.lastIndex
                        MessageBubble(msg, onRetry = if (msg.isError && isLast && !state.sending) vm::retry else null)
                    }
                    if (state.sending) {
                        item { AnswerInProgress(state.streamingText, state.status) }
                    }
                }
            }
        }

        InputBar(
            state = state,
            input = input,
            onInput = { input = it },
            onAttach = openPicker,
            onCamera = openCamera,
            onRemove = vm::removePending,
            onAllUploaded = { vm.send(ALL_FILES_UPLOADED) },
            onSend = {
                vm.send(input)
                input = ""
            },
        )
    }
}

@Composable
private fun EmptyChat(onAttach: () -> Unit, onCamera: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Новый разбор", style = MaterialTheme.typography.titleLarge)
                StepRow(1, "Прикрепите анализы, выписки, заключения: PDF или фото. Можно несколько файлов за раз.")
                StepRow(2, "Отправляйте документы пакетами. После каждого ИИ подтвердит приём.")
                StepRow(3, "Когда всё загружено, нажмите «Все файлы загружены».")
                StepRow(4, "Ответьте на уточняющие вопросы и получите полный разбор.")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(
                onClick = onAttach,
                modifier = Modifier.weight(1f).height(64.dp),
                shape = MaterialTheme.shapes.medium,
            ) { Text("📎  Файлы", style = MaterialTheme.typography.titleMedium) }
            FilledTonalButton(
                onClick = onCamera,
                modifier = Modifier.weight(1f).height(64.dp),
                shape = MaterialTheme.shapes.medium,
            ) { Text("📷  Фото", style = MaterialTheme.typography.titleMedium) }
        }
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        ) {
            Text(
                "💡 Из ЕМИАС или Госуслуг: откройте документ, нажмите «Поделиться» и выберите МедАнализ.",
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        Text(
            "Это не диагноз и не замена врачу. При опасных симптомах звоните 103 или 112.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale("ru"))

@Composable
private fun MessageBubble(msg: ChatMessage, onRetry: (() -> Unit)?) {
    when {
        msg.isError -> ErrorBubble(msg.text, onRetry)
        msg.role == Role.USER -> UserBubble(msg)
        else -> AssistantBubble(msg.text, timeFormat.format(Date(msg.timestamp)))
    }
}

@Composable
private fun UserBubble(msg: ChatMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                msg.attachments.forEach { a ->
                    Surface(color = Color.White.copy(alpha = 0.16f), shape = RoundedCornerShape(10.dp)) {
                        Text(
                            fileIcon(a.mimeType) + "  " + a.displayName,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (msg.text.isNotBlank()) {
                    SelectionContainer {
                        Text(msg.text, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(
                    timeFormat.format(Date(msg.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun AssistantShell(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        AppLogo(30.dp, Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 1.dp,
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) { content() }
        }
    }
}

@Composable
private fun AssistantBubble(text: String, time: String) {
    val context = LocalContext.current
    AssistantShell {
        SelectionContainer { Markdown(content = text) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { copyToClipboard(context, text) }) { Text("Копировать") }
        }
    }
}

@Composable
private fun AnswerInProgress(text: String?, status: String?) {
    AssistantShell {
        if (!text.isNullOrEmpty()) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TypingDots()
            Text(
                status ?: "МедАнализ пишет ответ…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorBubble(text: String, onRetry: (() -> Unit)?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("⚠️  $text", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            if (onRetry != null) {
                OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) { Text("Повторить") }
            }
        }
    }
}

@Composable
private fun InputBar(
    state: UiState,
    input: String,
    onInput: (String) -> Unit,
    onAttach: () -> Unit,
    onCamera: () -> Unit,
    onRemove: (Attachment) -> Unit,
    onAllUploaded: () -> Unit,
    onSend: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Column {
            if (state.sending) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (state.status != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    state.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }

            val hasMessages = state.current?.messages?.isNotEmpty() == true
            if (state.pending.isNotEmpty() || (hasMessages && !state.sending)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 10.dp, end = 10.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (hasMessages && !state.sending && state.pending.isEmpty()) {
                        AssistChip(
                            onClick = onAllUploaded,
                            label = { Text("✅ Все файлы загружены") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                    }
                    state.pending.forEach { a ->
                        InputChip(
                            selected = false,
                            onClick = { onRemove(a) },
                            label = {
                                Text(
                                    fileIcon(a.mimeType) + " " + a.displayName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 180.dp),
                                )
                            },
                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Убрать", Modifier.size(16.dp)) },
                        )
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconButton(onClick = onAttach, enabled = !state.sending) {
                    Text("📎", style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = onCamera, enabled = !state.sending) {
                    Text("📷", style = MaterialTheme.typography.titleLarge)
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = onInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Сообщение или пояснение") },
                    maxLines = 6,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                )
                Spacer(Modifier.width(6.dp))
                FilledIconButton(
                    onClick = onSend,
                    enabled = !state.sending && (input.isNotBlank() || state.pending.isNotEmpty()),
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Ответ", text))
    Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
}
