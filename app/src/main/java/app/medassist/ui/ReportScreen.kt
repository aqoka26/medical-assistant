package app.medassist.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.medassist.ALL_FILES_MARKER
import app.medassist.ChatViewModel
import app.medassist.PdfReport
import app.medassist.Screen
import app.medassist.UiState
import app.medassist.core.ChatMessage
import app.medassist.core.Labs
import app.medassist.core.Report
import app.medassist.core.Role
import com.mikepenz.markdown.m3.Markdown
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportScreen(state: UiState, vm: ChatViewModel) {
    BackHandler { vm.show(Screen.HOME) }
    val context = LocalContext.current
    val conversation = state.current
    if (conversation == null) {
        LaunchedEffect(Unit) { vm.show(Screen.HOME) }
        return
    }
    var input by rememberSaveable { mutableStateOf("") }
    var remind by remember { mutableStateOf(false) }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val heard = r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!heard.isNullOrBlank()) input = if (input.isBlank()) heard else "$input $heard"
    }

    val messages = conversation.messages
    val answers = messages.filter { it.role == Role.ASSISTANT && !it.isError }
    val reportMsg = answers.firstOrNull { Report.parse(it.text).isStructured } ?: answers.firstOrNull()
    val report = remember(reportMsg?.text) { reportMsg?.let { Report.parse(it.text) } }
    val firstUser = messages.firstOrNull { it.role == Role.USER }
    val rest = messages.filter { it !== reportMsg && it !== firstUser }
    val liveReport = if (state.sending && reportMsg == null) Report.parse(state.streamingText.orEmpty()) else null

    val listState = rememberLazyListState()
    // Scroll down only when a new question or answer appears, not when the report is opened.
    var seen by remember(conversation.id) { mutableStateOf(rest.size) }
    LaunchedEffect(rest.size, state.sending) {
        val n = listState.layoutInfo.totalItemsCount
        if ((rest.size > seen || (state.sending && reportMsg != null)) && n > 1) listState.animateScrollToItem(n - 2)
        seen = rest.size
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(
            title = report?.title ?: liveReport?.title ?: conversation.title,
            subtitle = SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(conversation.createdAt)) +
                (if (state.profiles.size > 1) " · ${state.profile.name}" else ""),
            onBack = { vm.show(Screen.HOME) },
        ) {
            if (reportMsg != null) {
                IconButton(onClick = { remind = true }) { Icon(Icons.Outlined.Alarm, contentDescription = "Напомнить") }
                IconButton(onClick = {
                    runCatching {
                        PdfReport.share(context, Labs.visible(reportMsg.text), report?.title ?: conversation.title, state.profile.name.takeIf { state.profiles.size > 1 })
                    }.onFailure { Toast.makeText(context, "Не удалось создать PDF", Toast.LENGTH_LONG).show() }
                }) { Icon(Icons.Outlined.PictureAsPdf, contentDescription = "PDF") }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (state.sending) LinearProgressIndicator(Modifier.fillMaxWidth())

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            firstUser?.let { u -> item { SourceStrip(u) } }

            val shown = report ?: liveReport
            if (shown != null && (shown.isStructured || shown.summary != null)) {
                item { SummaryCard(shown) }
                shown.sections.forEachIndexed { i, s -> item(key = "s$i") { SectionCard(s) } }
                if (shown.intro.isNotBlank() && shown.sections.isEmpty()) item { MarkdownCard(shown.intro) }
            } else if (shown != null && shown.intro.isNotBlank()) {
                item { MarkdownCard(shown.intro) }
            }
            if (state.sending && reportMsg == null) item { PreparingCard(state.status, started = !state.streamingText.isNullOrBlank()) }

            if (rest.any { !it.isError } || (state.sending && reportMsg != null)) {
                item { SectionLabel("Вопросы по разбору") }
            }
            rest.forEachIndexed { i, m ->
                item(key = "r$i") {
                    when {
                        m.isError -> ErrorCard(m.text, onRetry = if (i == rest.lastIndex && !state.sending) vm::retry else null)
                        m.role == Role.USER -> QuestionBubble(m.text.replace(ALL_FILES_MARKER, "").trim(), m)
                        else -> AnswerCard(Labs.visible(m.text))
                    }
                }
            }
            if (state.sending && reportMsg != null) {
                item {
                    AppCard {
                        val t = Labs.visible(state.streamingText.orEmpty())
                        if (t.isNotBlank()) { Markdown(content = t); Gap(8.dp) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TypingDots()
                            Spacer(Modifier.width(10.dp))
                            Text(state.status ?: "Пишу ответ…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Text(
                    "Разбор подготовлен ИИ и не заменяет врача. При опасных симптомах звоните 103 или 112.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp),
                )
            }
        }

        if (reportMsg != null || !state.sending) {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                Row(
                    Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Задать вопрос по разбору") },
                        maxLines = 5,
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = fieldColors(),
                        trailingIcon = {
                            IconButton(onClick = { startVoice(context) { voice.launch(it) } }, enabled = !state.sending) {
                                Icon(Icons.Outlined.Mic, contentDescription = "Голосом", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { vm.send(input); input = "" },
                        enabled = !state.sending && input.isNotBlank(),
                        modifier = Modifier.size(52.dp),
                    ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить") }
                }
            }
        }
    }
    if (remind) ReminderDialog(vm) { remind = false }
}

@Composable
private fun SourceStrip(m: ChatMessage) {
    if (m.attachments.isEmpty()) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        m.attachments.forEach { a ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(fileIcon(a.mimeType), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(a.displayName, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(r: Report) {
    val look = statusLook(r.status)
    AppCard(color = look.bg, border = false, padding = 20.dp) {
        StatusChip(r.status)
        r.summary?.let {
            Gap(12.dp)
            SelectionContainer { Text(it.replace("**", ""), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)) }
        }
    }
}

private data class SectionLook(val icon: ImageVector, val tint: Color, val bg: Color)

@Composable
private fun lookFor(title: String): SectionLook {
    val a = LocalAccents.current
    val t = title.lowercase()
    return when {
        "главн" in t -> SectionLook(Icons.Outlined.Lightbulb, a.blue, a.blueBg)
        "поряд" in t || "норм" in t -> SectionLook(Icons.Outlined.CheckCircle, a.good, a.goodBg)
        "вниман" in t -> SectionLook(Icons.Outlined.WarningAmber, a.warn, a.warnBg)
        "причин" in t || "значит" in t -> SectionLook(Icons.Outlined.Psychology, a.violet, a.violetBg)
        "делать" in t || "план" in t -> SectionLook(Icons.Outlined.Checklist, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
        "врач" in t -> SectionLook(Icons.Outlined.MedicalServices, a.blue, a.blueBg)
        "уточн" in t || "вопрос" in t -> SectionLook(Icons.Outlined.QuestionAnswer, a.violet, a.violetBg)
        else -> SectionLook(Icons.Outlined.Description, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun SectionCard(s: Report.Section) {
    var open by rememberSaveable(s.title) { mutableStateOf(true) }
    val look = lookFor(s.title)
    AppCard(modifier = Modifier.animateContentSize()) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            IconTile(look.icon, look.tint, look.bg, 38.dp)
            Spacer(Modifier.width(12.dp))
            Text(s.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(
                if (open) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) {
            Gap(10.dp)
            SelectionContainer { Markdown(content = s.body) }
        }
    }
}

@Composable
private fun MarkdownCard(text: String) {
    AppCard { SelectionContainer { Markdown(content = text) } }
}

@Composable
private fun PreparingCard(status: String?, started: Boolean) {
    AppCard(padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypingDots()
            Spacer(Modifier.width(12.dp))
            Text(if (started) "Пишем разбор…" else "Готовим разбор", style = MaterialTheme.typography.titleMedium)
        }
        Gap(8.dp)
        Text(
            status ?: if (started) "Разделы появляются по мере готовности." else "Читаем документы. Обычно это занимает 1–2 минуты, можно свернуть приложение.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuestionBubble(text: String, m: ChatMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                m.attachments.forEach { a ->
                    Text("📎 " + a.displayName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun AnswerCard(text: String) {
    val context = LocalContext.current
    AppCard {
        SelectionContainer { Markdown(content = text) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { copy(context, text) }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Копировать")
            }
        }
    }
}

@Composable
private fun ErrorCard(text: String, onRetry: (() -> Unit)?) {
    val a = LocalAccents.current
    AppCard(color = a.badBg, border = false) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = a.bad)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        if (onRetry != null) {
            Gap(10.dp)
            OutlinedButton(onClick = onRetry, shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Повторить")
            }
        }
    }
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Ответ", text))
    Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
}
