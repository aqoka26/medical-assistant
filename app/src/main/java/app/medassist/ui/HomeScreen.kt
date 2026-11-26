package app.medassist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.medassist.ChatViewModel
import app.medassist.Screen
import app.medassist.UiState
import app.medassist.core.Profile
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(state: UiState, vm: ChatViewModel) {
    var showFamily by remember { mutableStateOf(false) }
    var showEmias by remember { mutableStateOf(false) }
    var addReminder by remember { mutableStateOf(false) }
    val a = LocalAccents.current
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Доброе утро"
        in 12..17 -> "Добрый день"
        in 18..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }
    val last = state.profileConversations.firstOrNull()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TabTitle(
            title = if (state.profileId == Profile.ME) (state.login ?: "МедАнализ") else state.profile.name,
            subtitle = greeting,
        ) {
            Avatar(state.profile.name, 44, onClick = { showFamily = true })
        }

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FamilyRow(state, vm, onAdd = { showFamily = true })

            HeroCard(onClick = vm::newConversation)

            if (state.sending) {
                AppCard(onClick = { state.current?.let(vm::open) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TypingDots()
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Готовим разбор", style = MaterialTheme.typography.titleSmall)
                            Text(state.status ?: "Это займёт 1–2 минуты", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else if (last != null) {
                SectionLabel("Последний разбор")
                RecordCard(last, onClick = { vm.open(last) })
            }

            SectionLabel("Сервисы")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ServiceTile(Icons.AutoMirrored.Outlined.ShowChart, a.good, a.goodBg, "Динамика", "${state.profileLabs.map { it.name }.distinct().size} показателей", Modifier.weight(1f)) { vm.show(Screen.LABS) }
                ServiceTile(Icons.Outlined.Alarm, a.warn, a.warnBg, "Напомнить", if (state.reminders.isEmpty()) "о пересдаче" else "${state.reminders.size} активных", Modifier.weight(1f)) { addReminder = true }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ServiceTile(Icons.Outlined.FamilyRestroom, a.violet, a.violetBg, "Семья", "${state.profiles.size} " + people(state.profiles.size), Modifier.weight(1f)) { showFamily = true }
                ServiceTile(Icons.Outlined.IosShare, a.blue, a.blueBg, "Из ЕМИАС", "как загрузить", Modifier.weight(1f)) { showEmias = true }
            }

            val upcoming = state.reminders.sortedBy { it.at }.take(2)
            if (upcoming.isNotEmpty()) {
                SectionLabel("Скоро")
                AppCard {
                    upcoming.forEachIndexed { i, r ->
                        if (i > 0) Gap(10.dp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Outlined.Alarm, a.warn, a.warnBg, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(SimpleDateFormat("d MMMM, HH:mm", Locale("ru")).format(Date(r.at)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            state.usage?.let { u ->
                AppCard(color = MaterialTheme.colorScheme.surfaceContainer, border = false, padding = 14.dp) {
                    val left = (u.dailyLimit - u.usedToday).coerceAtLeast(0)
                    Text("Сегодня доступно ещё $left из ${u.dailyLimit} запросов к ИИ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Gap(8.dp)
                    LinearProgressIndicator(
                        progress = { (u.usedToday.toFloat() / u.dailyLimit.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }

            Text(
                "МедАнализ не ставит диагноз и не заменяет врача. При опасных симптомах звоните 103 или 112.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
            Gap(8.dp)
        }
    }

    if (showFamily) ProfileDialog(state, vm) { showFamily = false }
    if (addReminder) ReminderDialog(vm) { addReminder = false }
    if (showEmias) {
        AlertDialog(
            onDismissRequest = { showEmias = false },
            title = { Text("Как загрузить из ЕМИАС") },
            text = {
                Text(
                    "1. Откройте приложение ЕМИАС или Госуслуги и нужный документ: анализ, выписку, заключение.\n\n" +
                        "2. Нажмите «Поделиться» или «Скачать», затем «Открыть в…».\n\n" +
                        "3. Выберите МедАнализ. Документ сразу появится в новом разборе.\n\n" +
                        "Так же можно переслать файл из почты, WhatsApp или Telegram.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { showEmias = false }) { Text("Понятно") } },
        )
    }
}

private fun people(n: Int) = when {
    n % 10 == 1 && n % 100 != 11 -> "человек"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "человека"
    else -> "человек"
}

@Composable
private fun HeroCard(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(BrandGradient)
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Column {
            Text("Новый разбор", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Gap(6.dp)
            Text(
                "Загрузите анализы, выписки или заключения. Объясним простыми словами, что они значат и что делать дальше.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f),
            )
            Gap(16.dp)
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Brand.TealDark),
                shape = MaterialTheme.shapes.medium,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Загрузить документы", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun ServiceTile(icon: ImageVector, tint: Color, bg: Color, title: String, hint: String, modifier: Modifier, onClick: () -> Unit) {
    AppCard(modifier = modifier, onClick = onClick, padding = 16.dp) {
        IconTile(icon, tint, bg, 40.dp)
        Gap(12.dp)
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun FamilyRow(state: UiState, vm: ChatViewModel, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.profiles.forEach { p ->
            val selected = p.id == state.profileId
            Surface(
                onClick = { vm.selectProfile(p.id) },
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        p.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Surface(
            onClick = onAdd,
            shape = CircleShape,
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Text("Добавить", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Circle with the first letter of a name. */
@Composable
fun Avatar(name: String, sizeDp: Int, inverted: Boolean = false, onClick: (() -> Unit)? = null) {
    val letter = name.trim().firstOrNull()?.uppercase() ?: "?"
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(if (inverted) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primaryContainer)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            style = if (sizeDp >= 40) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
            color = if (inverted) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
