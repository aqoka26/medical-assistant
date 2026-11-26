package app.medassist.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.medassist.ChatViewModel
import app.medassist.Screen
import app.medassist.UiState
import java.io.File

@Composable
fun NewAnalysisScreen(state: UiState, vm: ChatViewModel) {
    BackHandler { vm.show(Screen.HOME) }
    val context = LocalContext.current
    var complaints by rememberSaveable { mutableStateOf("") }
    var medicines by rememberSaveable { mutableStateOf("") }
    var question by rememberSaveable { mutableStateOf("") }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var medCard by rememberSaveable { mutableStateOf(false) }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.addFiles(it) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) vm.addFiles(listOf(Uri.parse(uri)))
    }
    val openCamera = {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        cameraUri = uri.toString()
        takePhoto.launch(uri)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("Новый разбор", onBack = { vm.show(Screen.HOME) })
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (state.status != null) LinearProgressIndicator(Modifier.fillMaxWidth())

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.profiles.size > 1) {
                Text("Для кого", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.profiles.forEach { p ->
                        FilterChip(
                            selected = p.id == state.profileId,
                            onClick = { vm.selectProfile(p.id) },
                            label = { Text(p.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }

            AppCard {
                StepHeader(1, "Документы", "PDF, фото или скриншоты анализов, выписок, заключений. Можно несколько сразу.")
                Gap(14.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { pickFiles.launch(arrayOf("application/pdf", "image/*", "text/plain")) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Файлы")
                    }
                    OutlinedButton(
                        onClick = openCamera,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Камера")
                    }
                }
                state.status?.let {
                    Gap(10.dp)
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (state.pending.isNotEmpty()) {
                    Gap(12.dp)
                    state.pending.forEach { a ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Row(Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconTile(fileIcon(a.mimeType), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, 36.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    a.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { vm.removePending(a) }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Убрать", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            AppCard(onClick = { medCard = true }, color = MaterialTheme.colorScheme.primaryContainer, border = false, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (state.profile.hasMedCard) "Медкарта будет учтена: ${state.profile.medCardLines().size} пункта. Нажмите, чтобы изменить."
                        else "Заполните медкарту (аллергии, болезни, лекарства), и разбор будет точнее.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            AppCard {
                StepHeader(2, "Расскажите о себе", "Необязательно, но так разбор будет точнее.")
                Gap(14.dp)
                VoiceField(complaints, { complaints = it }, "Что беспокоит", "Например: слабость, выпадают волосы")
                Gap(10.dp)
                VoiceField(medicines, { medicines = it }, "Лекарства и добавки", "Что принимаете сейчас и в какой дозе")
                Gap(10.dp)
                VoiceField(question, { question = it }, "Что хотите узнать", "Например: нужно ли идти к эндокринологу")
            }
            Gap(4.dp)
        }

        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
            Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                Button(
                    onClick = { vm.startAnalysis(complaints, medicines, question) },
                    enabled = state.pending.isNotEmpty() && !state.sending && state.status == null,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        if (state.pending.isEmpty()) "Добавьте хотя бы один документ"
                        else "Получить разбор · ${state.pending.size} ${filesWord(state.pending.size)}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
    if (medCard) MedCardDialog(state, vm) { medCard = false }
}

@Composable
private fun StepHeader(n: Int, title: String, hint: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                "$n",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Text field with a microphone button for dictation. */
@Composable
fun VoiceField(value: String, onChange: (String) -> Unit, label: String, placeholder: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val heard = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!heard.isNullOrBlank()) onChange(if (value.isBlank()) heard else "$value $heard")
    }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = modifier.fillMaxWidth(),
        maxLines = 5,
        shape = MaterialTheme.shapes.medium,
        colors = fieldColors(),
        trailingIcon = {
            IconButton(onClick = { startVoice(context) { voice.launch(it) } }) {
                Icon(Icons.Outlined.Mic, contentDescription = "Сказать голосом", tint = MaterialTheme.colorScheme.primary)
            }
        },
    )
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
)

fun startVoice(context: Context, launch: (Intent) -> Unit) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите…")
    try {
        launch(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "На телефоне не настроен голосовой ввод", Toast.LENGTH_LONG).show()
    }
}
