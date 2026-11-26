package app.medassist.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.medassist.ChatViewModel
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.ui.text.style.TextAlign
import app.medassist.UiState
import app.medassist.core.LabResult
import app.medassist.core.Profile
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------- family profiles ----------

@Composable
fun ProfileDialog(state: UiState, vm: ChatViewModel, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<Profile?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Семья") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.profiles.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.selectProfile(p.id); onDismiss() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = p.id == state.profileId, onClick = { vm.selectProfile(p.id); onDismiss() })
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall)
                            val extra = listOfNotNull(
                                p.sex?.let { if (it == "ж") "жен." else "муж." },
                                p.birthYear?.let { "$it г.р." },
                            ).joinToString(", ")
                            if (extra.isNotEmpty()) {
                                Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (p.id != Profile.ME) {
                            TextButton(onClick = { confirmDelete = p }) { Text("Удалить") }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Добавить члена семьи", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(30) },
                    label = { Text("Имя, например «Мама»") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = year, onValueChange = { year = it.filter(Char::isDigit).take(4) },
                    label = { Text("Год рождения") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = sex == "м", onClick = { sex = if (sex == "м") null else "м" }, label = { Text("Мужчина") })
                    FilterChip(selected = sex == "ж", onClick = { sex = if (sex == "ж") null else "ж" }, label = { Text("Женщина") })
                }
                Text(
                    "Пол и возраст помогают ИИ правильно оценить нормы анализов.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.addProfile(name, year.toIntOrNull()?.takeIf { it in 1900..2100 }, sex); onDismiss() },
                enabled = name.isNotBlank(),
            ) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )

    confirmDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Удалить «${p.name}»?") },
            text = { Text("Разборы и показатели этого человека не пропадут: они перейдут в профиль «Я».") },
            confirmButton = { TextButton(onClick = { vm.deleteProfile(p.id); confirmDelete = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Отмена") } },
        )
    }
}

// ---------- reminders ----------

private val reminderOptions = listOf(
    7 to "Через неделю",
    14 to "Через 2 недели",
    30 to "Через месяц",
    90 to "Через 3 месяца",
    180 to "Через полгода",
)

@Composable
fun ReminderDialog(vm: ChatViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("Пересдать анализы") }
    var days by rememberSaveable { mutableStateOf(30) }
    var hour by rememberSaveable { mutableStateOf(10) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val at = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, days)
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Напоминание") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(200) },
                    label = { Text("О чём напомнить") }, modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    reminderOptions.forEach { (d, label) ->
                        FilterChip(selected = days == d, onClick = { days = d }, label = { Text(label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(9, 12, 18).forEach { h ->
                        FilterChip(selected = hour == h, onClick = { hour = h }, label = { Text("%02d:00".format(h)) })
                    }
                }
                Text(
                    "Придёт уведомление " + SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru")).format(Date(at)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    vm.addReminder(text, at)
                    onDismiss()
                },
                enabled = text.isNotBlank(),
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ---------- lab dynamics ----------

private val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val ruDate = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))

private fun showDate(iso: String) = runCatching { ruDate.format(isoDate.parse(iso)!!) }.getOrDefault(iso)

private fun fmt(v: Double) = if (v == Math.rint(v) && kotlin.math.abs(v) < 1e9) v.toLong().toString()
else "%.2f".format(Locale("ru"), v).trimEnd('0').trimEnd(',')

/** Reference range from strings like "130–160", "<5", "до 5", ">1". */
private fun parseRef(ref: String): Pair<Double?, Double?> {
    val num = "(-?\\d+(?:[.,]\\d+)?)"
    val r = ref.replace(" ", "")
    Regex("$num[-–—]$num").find(r)?.let {
        return it.groupValues[1].replace(',', '.').toDoubleOrNull() to it.groupValues[2].replace(',', '.').toDoubleOrNull()
    }
    Regex("^(?:<|≤|<=|до)$num").find(r)?.let { return null to it.groupValues[1].replace(',', '.').toDoubleOrNull() }
    Regex("^(?:>|≥|>=|от)$num").find(r)?.let { return it.groupValues[1].replace(',', '.').toDoubleOrNull() to null }
    return null to null
}

private fun outOfRange(v: Double, ref: Pair<Double?, Double?>) =
    (ref.first != null && v < ref.first!!) || (ref.second != null && v > ref.second!!)

@Composable
fun LabsScreen(state: UiState, vm: ChatViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    val all = state.profileLabs.groupBy { it.name }.toList().sortedBy { it.first }
    val groups = all.filter { query.isBlank() || it.first.contains(query.trim(), ignoreCase = true) }
    val a = LocalAccents.current

    Column(Modifier.fillMaxSize()) {
        TabTitle("Анализы", subtitle = state.profile.name.takeIf { state.profileId != app.medassist.core.Profile.ME })
        if (all.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                IconTile(Icons.AutoMirrored.Outlined.ShowChart, a.good, a.goodBg, 64.dp)
                Gap(16.dp)
                Text("Здесь появится динамика", style = MaterialTheme.typography.titleLarge)
                Gap(6.dp)
                Text(
                    "После разбора приложение само сохранит цифры из анализов. Когда сдадите анализ ещё раз, " +
                        "здесь будет видно, как он изменился.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Gap(80.dp)
            }
        } else {
            val outCount = all.count { (_, v) ->
                val last = v.maxBy { it.date }
                outOfRange(last.value, parseRef(v.lastOrNull { it.ref.isNotBlank() }?.ref.orEmpty()))
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("${all.size}", "показателей", a.blue, a.blueBg, Modifier.weight(1f))
                        StatTile("$outCount", "вне нормы", if (outCount > 0) a.warn else a.good, if (outCount > 0) a.warnBg else a.goodBg, Modifier.weight(1f))
                    }
                }
                if (all.size > 5) {
                    item {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            placeholder = { Text("Найти показатель") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium, colors = fieldColors(),
                        )
                    }
                }
                items(groups, key = { it.first }) { (name, values) -> LabCard(name, values.sortedBy { it.date }, vm::deleteLab) }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, tint: Color, bg: Color, modifier: Modifier) {
    AppCard(modifier = modifier, color = bg, border = false, padding = 16.dp) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = tint)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LabCard(name: String, values: List<LabResult>, onDelete: (LabResult) -> Unit) {
    var expanded by rememberSaveable(name) { mutableStateOf(false) }
    val last = values.last()
    val ref = parseRef(values.lastOrNull { it.ref.isNotBlank() }?.ref.orEmpty())
    val warn = MaterialTheme.colorScheme.error
    AppCard(onClick = { expanded = !expanded }, padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    fmt(last.value) + " " + last.unit,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (outOfRange(last.value, ref)) warn else MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                listOfNotNull(
                    "посл. ${showDate(last.date)}",
                    last.ref.takeIf { it.isNotBlank() }?.let { "норма $it" },
                    if (values.size > 1) "замеров: ${values.size}" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (values.size > 1) LabChart(values, ref)
            if (expanded) {
                values.reversed().forEach { v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(showDate(v.date), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp))
                        Text(
                            fmt(v.value) + " " + v.unit,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (outOfRange(v.value, ref)) warn else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onDelete(v) }) { Text("Убрать") }
                    }
                }
            } else {
                Text(if (values.size > 1) "Все значения ›" else "Подробнее ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun LabChart(values: List<LabResult>, ref: Pair<Double?, Double?>) {
    val line = MaterialTheme.colorScheme.primary
    val band = Brand.Mint.copy(alpha = 0.15f)
    val warn = MaterialTheme.colorScheme.error
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val labelArgb = label.toArgb()
    Canvas(Modifier.fillMaxWidth().height(150.dp).padding(top = 6.dp)) {
        // Fit the chart to the values; show the normal range only where it is near them.
        val vs = values.map { it.value }
        val span = maxOf(vs.max() - vs.min(), kotlin.math.abs(vs.max()) * 0.2, 1e-6)
        val nums = vs + listOfNotNull(
            ref.first?.takeIf { it >= vs.min() - span },
            ref.second?.takeIf { it <= vs.max() + span },
        )
        var lo = nums.min()
        var hi = nums.max()
        if (hi - lo < 1e-9) { lo -= 1; hi += 1 }
        val pad = (hi - lo) * 0.15
        lo -= pad; hi += pad
        val top = 18f
        val bottom = size.height - 22f
        val left = 12f
        val right = size.width - 12f
        fun y(v: Double) = (bottom - (v - lo) / (hi - lo) * (bottom - top)).toFloat()
        fun x(i: Int) = left + (right - left) * i / (values.size - 1).coerceAtLeast(1)

        if (ref.first != null || ref.second != null) {
            val yTop = y(ref.second ?: hi).coerceIn(top - 18f, bottom + 22f)
            val yBottom = y(ref.first ?: lo).coerceIn(top - 18f, bottom + 22f)
            drawRect(band, topLeft = Offset(left, yTop), size = Size(right - left, yBottom - yTop))
        }
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v.value)) else path.lineTo(x(i), y(v.value)) }
        drawPath(path, line, style = Stroke(width = 5f))

        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 26f
            color = labelArgb
            textAlign = android.graphics.Paint.Align.CENTER
        }
        values.forEachIndexed { i, v ->
            val c = Offset(x(i), y(v.value))
            drawCircle(if (outOfRange(v.value, ref)) warn else line, radius = 9f, center = c)
            drawCircle(Color.White, radius = 4f, center = c)
            val px = x(i).coerceIn(40f, size.width - 40f)
            drawContext.canvas.nativeCanvas.drawText(fmt(v.value), px, c.y - 16f, paint)
            drawContext.canvas.nativeCanvas.drawText(
                showDate(v.date).take(5) + (if (values.size <= 4) "." + v.date.take(2 + 2).takeLast(2) else ""),
                px, size.height - 2f, paint,
            )
        }
    }
}

@Composable
fun RemindersCard(state: UiState, vm: ChatViewModel) {
    var adding by remember { mutableStateOf(false) }
    val f = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru"))
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.reminders.isEmpty()) {
                Text(
                    "Пока нет. Напоминание можно поставить из разбора, чтобы не забыть пересдать анализ или сходить к врачу.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.reminders.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.text, style = MaterialTheme.typography.bodyMedium)
                        Text(f.format(Date(r.at)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { vm.deleteReminder(r) }) { Text("Удалить") }
                }
            }
            TextButton(onClick = { adding = true }) { Text("+ Добавить напоминание") }
            Spacer(Modifier.height(0.dp))
        }
    }
    if (adding) ReminderDialog(vm) { adding = false }
}
