package app.medassist.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.medassist.ChatViewModel
import app.medassist.Screen

const val ALL_FILES_UPLOADED = "ВСЕ ФАЙЛЫ ЗАГРУЖЕНЫ"

@Composable
fun App(vm: ChatViewModel, onUnlock: () -> Unit, canUseLock: () -> Boolean) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.notice) {
        state.notice?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.clearNotice()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (!state.disclaimerAccepted) {
            WelcomeScreen(onStart = vm::acceptDisclaimer)
        } else if (state.locked) {
            LockScreen(onUnlock)
        } else if (state.screen == Screen.LOGIN) {
            LoginScreen(state, vm)
        } else {
            // The gradient shows under the status bar; content stays inside safe insets.
            Box(Modifier.fillMaxSize().background(BrandGradient)) {
                Box(Modifier.safeDrawingPadding().background(MaterialTheme.colorScheme.background)) {
                    when (state.screen) {
                        Screen.CHAT -> ChatScreen(state, vm)
                        Screen.HISTORY -> HistoryScreen(state, vm)
                        Screen.SETTINGS -> SettingsScreen(state, vm, canUseLock)
                        Screen.LOGIN -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(BrandGradient)
                .statusBarsPadding()
                .padding(vertical = 36.dp, horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppLogo(88.dp)
                Spacer(Modifier.height(16.dp))
                Text("МедАнализ", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(
                    "ИИ-помощник для разбора анализов, выписок и заключений",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            FeatureRow("📄", "Загружайте документы", "PDF и фото анализов, выписок, заключений. Можно из ЕМИАС через «Поделиться».")
            FeatureRow("🧠", "ИИ сопоставит данные", "Найдёт отклонения, динамику и задаст уточняющие вопросы.")
            FeatureRow("📋", "Получите разбор и план", "Что проверить дальше и с чем идти к врачу.")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "⚠️ Важно",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        "Приложение не ставит диагноз и не заменяет врача, ИИ может ошибаться. " +
                            "При опасных симптомах (боль в груди, затруднённое дыхание, потеря сознания, " +
                            "сильное кровотечение, признаки инсульта) сразу звоните 103 или 112.\n\n" +
                            "Файлы и вопросы отправляются в Google для обработки. На бесплатном тарифе " +
                            "Google может использовать их для улучшения своих моделей.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .height(54.dp),
            shape = MaterialTheme.shapes.large,
        ) { Text("Понятно, начать", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(BrandGradient)
            .safeDrawingPadding()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppLogo(96.dp)
            Text("МедАнализ", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text("🔒 Приложение заблокировано", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f))
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onUnlock,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Brand.TealDark,
                ),
                modifier = Modifier.height(52.dp),
                shape = MaterialTheme.shapes.large,
            ) { Text("Разблокировать", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
