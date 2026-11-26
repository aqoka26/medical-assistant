package app.medassist.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.medassist.ChatViewModel
import app.medassist.Screen
import app.medassist.UiState
import app.medassist.core.Conversation
import app.medassist.core.Role
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(state: UiState, vm: ChatViewModel) {
    BackHandler { vm.show(Screen.CHAT) }
    var toDelete by remember { mutableStateOf<Conversation?>(null) }
    val fmt = remember { SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru")) }

    Column(Modifier.fillMaxSize()) {
        BrandHeader(
            title = "История разборов",
            subtitle = "${state.profile.name} · всего: ${state.profileConversations.size}",
            navigation = {
                IconButton(onClick = { vm.show(Screen.CHAT) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                }
            },
            actions = {
                IconButton(onClick = vm::newConversation) {
                    Icon(Icons.Filled.Add, contentDescription = "Новый разбор", tint = Color.White)
                }
            },
        )
        if (state.profileConversations.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🗂", style = MaterialTheme.typography.displaySmall)
                    Text(
                        "Пока нет разборов.\nНачните новый, прикрепив документы.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.profileConversations, key = { it.id }) { c ->
                    val isCurrent = c.id == state.current?.id
                    val files = c.messages.sumOf { it.attachments.size }
                    val answers = c.messages.count { it.role == Role.ASSISTANT && !it.isError }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { vm.open(c) },
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) { Text("📋", style = MaterialTheme.typography.titleMedium) }
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(
                                    c.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                )
                                Text(
                                    fmt.format(Date(c.createdAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Файлов: $files · ответов: $answers",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(onClick = { toDelete = c }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить разбор?") },
            text = { Text("«${c.title}» и все его файлы будут удалены с телефона. Это нельзя отменить.") },
            confirmButton = { TextButton(onClick = { vm.delete(c); toDelete = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}
