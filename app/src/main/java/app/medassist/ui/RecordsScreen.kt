package app.medassist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.medassist.ALL_FILES_MARKER
import app.medassist.ChatViewModel
import app.medassist.UiState
import app.medassist.core.Conversation
import app.medassist.core.Report
import app.medassist.core.Role
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The first real answer in a conversation, read as a report. */
fun reportOf(c: Conversation): Report? =
    c.messages.firstOrNull { it.role == Role.ASSISTANT && !it.isError && it.text.length > 300 }
        ?.let { Report.parse(it.text) }
        ?: c.messages.firstOrNull { it.role == Role.ASSISTANT && !it.isError }?.let { Report.parse(it.text) }

private val dateFmt = SimpleDateFormat("d MMMM yyyy", Locale("ru"))

@Composable
fun RecordsScreen(state: UiState, vm: ChatViewModel) {
    var toDelete by remember { mutableStateOf<Conversation?>(null) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TabTitle("Разборы", subtitle = state.profile.name.takeIf { state.profileId != app.medassist.core.Profile.ME })
            if (state.profileConversations.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    IconTile(Icons.Outlined.FolderOpen, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, 64.dp)
                    Gap(16.dp)
                    Text("Пока пусто", style = MaterialTheme.typography.titleLarge)
                    Gap(6.dp)
                    Text(
                        "Здесь будут все разборы ваших документов. Начните с первого.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Gap(80.dp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.profileConversations, key = { it.id }) { c ->
                        RecordCard(c, onClick = { vm.open(c) }, onDelete = { toDelete = c })
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = vm::newConversation,
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("Новый разбор") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    toDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить разбор?") },
            text = { Text("«${c.title}» и его файлы будут удалены с телефона.") },
            confirmButton = { TextButton(onClick = { vm.delete(c); toDelete = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}

@Composable
fun RecordCard(c: Conversation, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    val report = remember(c.messages.size) { reportOf(c) }
    val files = c.messages.sumOf { it.attachments.size }
    AppCard(onClick = onClick, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (report != null) StatusChip(report.status, short = true)
            else Text("Не завершён", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(dateFmt.format(Date(c.createdAt)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
        Gap(10.dp)
        Text(report?.title ?: c.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val summary = report?.summary ?: report?.intro?.takeIf { it.isNotBlank() }
            ?: c.messages.firstOrNull { it.role == Role.USER }?.text?.replace(ALL_FILES_MARKER, "")?.trim()
        if (!summary.isNullOrBlank()) {
            Gap(4.dp)
            Text(
                summary.replace("**", "").replace("#", ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (files > 0) {
            Gap(10.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text("$files " + filesWord(files), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

fun filesWord(n: Int) = when {
    n % 10 == 1 && n % 100 != 11 -> "документ"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "документа"
    else -> "документов"
}
