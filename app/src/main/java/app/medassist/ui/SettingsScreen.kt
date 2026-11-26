package app.medassist.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.medassist.ChatViewModel
import app.medassist.Screen
import app.medassist.UiState
import app.medassist.core.GeminiChat

@Composable
fun SettingsScreen(state: UiState, vm: ChatViewModel, canUseLock: () -> Boolean) {
    BackHandler { vm.show(Screen.CHAT) }
    val context = LocalContext.current
    var key by rememberSaveable { mutableStateOf(state.apiKey) }
    var model by rememberSaveable { mutableStateOf(state.model) }
    var visible by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        BrandHeader(
            title = "Настройки",
            subtitle = state.login?.let { "Аккаунт: $it" },
            navigation = {
                IconButton(onClick = { vm.show(Screen.CHAT) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.login != null) {
                SettingsCard("👤 Аккаунт") {
                    Text(state.login, style = MaterialTheme.typography.titleMedium)
                    state.usage?.let { u ->
                        val limit = u.dailyLimit.coerceAtLeast(1)
                        Text(
                            "Сегодня использовано ${u.usedToday} из ${u.dailyLimit} сообщений",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        LinearProgressIndicator(
                            progress = { (u.usedToday.toFloat() / limit).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Лимит обновляется каждый день в 03:00 по Москве.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = vm::logout) { Text("Выйти из аккаунта") }
                }
            }

            RemindersCard(state, vm)

            SettingsCard("🔒 Защита") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Вход по отпечатку или PIN", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Приложение попросит подтвердить, что это вы, при запуске и после минуты в фоне.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.lockEnabled,
                        onCheckedChange = { on ->
                            if (on && !canUseLock()) {
                                Toast.makeText(
                                    context,
                                    "Сначала включите блокировку экрана (PIN, отпечаток) в настройках телефона",
                                    Toast.LENGTH_LONG,
                                ).show()
                            } else {
                                vm.setLockEnabled(on)
                            }
                        },
                    )
                }
            }

            if (!state.usesProxy) {
                SettingsCard("🔑 Ключ Google Gemini") {
                    Text(
                        "1. Откройте aistudio.google.com и войдите в Google-аккаунт.\n" +
                            "2. «Get API key» → «Create API key», скопируйте ключ (AIza…).\n" +
                            "3. Вставьте сюда и нажмите «Проверить и сохранить». Карта не нужна.",
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
            }

            SettingsCard("🧠 Модель ИИ") {
                listOf(
                    GeminiChat.DEFAULT_MODEL to "Умнее. Бесплатно около 20 сообщений в день на всё приложение.",
                    GeminiChat.LITE_MODEL to "Проще и быстрее. Бесплатно около 500 сообщений в день.",
                ).forEach { (id, hint) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { model = id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = model == id, onClick = { model = id })
                        Column {
                            Text(if (id == GeminiChat.DEFAULT_MODEL) "Gemini Flash" else "Gemini Flash-Lite")
                            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(
                    "Если лимит умной модели закончится, приложение само переключится на облегчённую.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.usesProxy && model != state.model) {
                    Button(onClick = { vm.saveSettings("", model) }, enabled = !state.checkingKey) {
                        Text(if (state.checkingKey) "Проверяю…" else "Сохранить")
                    }
                }
            }

            SettingsCard("ℹ️ О приложении") {
                Text(
                    "МедАнализ не ставит диагноз и не заменяет врача. При опасных симптомах звоните 103 или 112.\n\n" +
                        "Разборы и файлы хранятся на этом телефоне. Для анализа они отправляются в Google; " +
                        "на бесплатном тарифе Google может использовать их для улучшения своих моделей. " +
                        "Копии файлов на сервере Google удаляются автоматически через 48 часов.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}
