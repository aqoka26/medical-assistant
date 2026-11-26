package app.medassist.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.medassist.BuildConfig
import app.medassist.ChatViewModel
import app.medassist.UiState
import app.medassist.core.GeminiChat

@Composable
fun ProfileScreen(state: UiState, vm: ChatViewModel, canUseLock: () -> Boolean) {
    val context = LocalContext.current
    val a = LocalAccents.current
    var family by remember { mutableStateOf(false) }
    var key by rememberSaveable { mutableStateOf(state.apiKey) }
    var model by rememberSaveable { mutableStateOf(state.model) }
    var visible by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TabTitle("Профиль")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(state.login ?: "Я", 56)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.login ?: "Без аккаунта", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (state.login != null) "Аккаунт МедАнализ" else "Данные хранятся на телефоне",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                state.usage?.let { u ->
                    Gap(16.dp)
                    Row {
                        Text("Запросов сегодня", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${u.usedToday} из ${u.dailyLimit}", style = MaterialTheme.typography.titleSmall)
                    }
                    Gap(8.dp)
                    LinearProgressIndicator(
                        progress = { (u.usedToday.toFloat() / u.dailyLimit.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Gap(6.dp)
                    Text("Обновляется каждый день в 03:00 по Москве.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionLabel("Семья")
            AppCard(padding = 8.dp) {
                Column(Modifier.padding(horizontal = 10.dp)) {
                    state.profiles.forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.selectProfile(p.id) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(p.name, 36)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall)
                                val n = state.conversations.count { it.profileId == p.id }
                                Text("Разборов: $n", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RadioButton(selected = p.id == state.profileId, onClick = { vm.selectProfile(p.id) })
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NavRow(Icons.Outlined.Groups, a.violet, a.violetBg, "Добавить или изменить", "Мама, папа, ребёнок…") { family = true }
                }
            }

            SectionLabel("Напоминания")
            RemindersCard(state, vm)

            SectionLabel("Безопасность")
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Outlined.Fingerprint, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Вход по отпечатку или PIN", style = MaterialTheme.typography.titleSmall)
                        Text("При запуске и после минуты в фоне", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = state.lockEnabled,
                        onCheckedChange = { on ->
                            if (on && !canUseLock()) {
                                Toast.makeText(context, "Сначала включите блокировку экрана (PIN, отпечаток) в настройках телефона", Toast.LENGTH_LONG).show()
                            } else vm.setLockEnabled(on)
                        },
                    )
                }
            }

            if (!state.usesProxy) {
                SectionLabel("Ключ Google Gemini")
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Outlined.Key, a.warn, a.warnBg, 40.dp)
                        Spacer(Modifier.width(14.dp))
                        Text("Возьмите ключ на aistudio.google.com → Get API key.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                    Gap(12.dp)
                    OutlinedTextField(
                        value = key, onValueChange = { key = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("AIza…") }, singleLine = true, shape = MaterialTheme.shapes.medium, colors = fieldColors(),
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    )
                    Gap(10.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.saveSettings(key, model) }, enabled = key.isNotBlank() && !state.checkingKey) {
                            Text(if (state.checkingKey) "Проверяю…" else "Сохранить")
                        }
                        OutlinedButton(onClick = { visible = !visible }) { Text(if (visible) "Скрыть" else "Показать") }
                    }
                }
            }

            SectionLabel("Качество разбора")
            AppCard(padding = 12.dp) {
                listOf(
                    GeminiChat.DEFAULT_MODEL to ("Подробный" to "Умнее и внимательнее. Лимит Google меньше."),
                    GeminiChat.LITE_MODEL to ("Быстрый" to "Быстрее и проще. Подходит для простых вопросов."),
                ).forEach { (id, t) ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            model = id
                            if (state.usesProxy) vm.saveSettings("", id)
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = model == id, onClick = {
                            model = id
                            if (state.usesProxy) vm.saveSettings("", id)
                        })
                        Column {
                            Text(t.first, style = MaterialTheme.typography.titleSmall)
                            Text(t.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            SectionLabel("О приложении")
            AppCard {
                Row(verticalAlignment = Alignment.Top) {
                    IconTile(Icons.Outlined.Info, a.blue, a.blueBg, 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("МедАнализ ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
                        Gap(4.dp)
                        Text(
                            "Не ставит диагноз и не заменяет врача. При опасных симптомах звоните 103 или 112.\n\n" +
                                "Разборы и файлы хранятся на этом телефоне. Для анализа документы отправляются в Google; " +
                                "на бесплатном тарифе Google может использовать их для улучшения своих моделей. " +
                                "Копии файлов у Google удаляются через 48 часов.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.login != null) {
                OutlinedButton(
                    onClick = vm::logout,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Выйти из аккаунта")
                }
            }
            Gap(20.dp)
        }
    }
    if (family) ProfileDialog(state, vm) { family = false }
}
