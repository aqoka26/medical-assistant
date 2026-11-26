package app.medassist.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.medassist.ChatViewModel
import app.medassist.Screen

private data class Tab(val screen: Screen, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Screen.HOME, "Главная", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Screen.RECORDS, "Разборы", Icons.Outlined.Folder, Icons.Filled.Folder),
    Tab(Screen.LABS, "Анализы", Icons.AutoMirrored.Outlined.ShowChart, Icons.AutoMirrored.Filled.ShowChart),
    Tab(Screen.PROFILE, "Профиль", Icons.Outlined.Person, Icons.Filled.Person),
)

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
        when {
            !state.disclaimerAccepted -> WelcomeScreen(onStart = vm::acceptDisclaimer)
            state.locked -> LockScreen(onUnlock)
            state.screen == Screen.LOGIN -> LoginScreen(state, vm)
            state.screen == Screen.NEW -> NewAnalysisScreen(state, vm)
            state.screen == Screen.REPORT -> ReportScreen(state, vm)
            else -> Column(Modifier.fillMaxSize()) {
                BackHandler(enabled = state.screen != Screen.HOME) { vm.show(Screen.HOME) }
                Box(Modifier.weight(1f)) {
                    when (state.screen) {
                        Screen.RECORDS -> RecordsScreen(state, vm)
                        Screen.LABS -> LabsScreen(state, vm)
                        Screen.PROFILE -> ProfileScreen(state, vm, canUseLock)
                        else -> HomeScreen(state, vm)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    tabs.forEach { t ->
                        val selected = state.screen == t.screen
                        NavigationBarItem(
                            selected = selected,
                            onClick = { vm.show(t.screen) },
                            icon = { Icon(if (selected) t.selectedIcon else t.icon, contentDescription = null) },
                            label = { Text(t.label, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit) {
    val a = LocalAccents.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Gap(40.dp)
            AppLogo(64.dp)
            Gap(28.dp)
            Text("МедАнализ", style = MaterialTheme.typography.displaySmall)
            Gap(8.dp)
            Text(
                "Понятный разбор ваших анализов и медицинских документов",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Gap(32.dp)
            WelcomePoint(Icons.Outlined.UploadFile, a.blue, a.blueBg, "Загрузите документы", "PDF или фото анализов, выписок и заключений. Можно прямо из ЕМИАС или Госуслуг.")
            WelcomePoint(Icons.Outlined.AutoAwesome, a.violet, a.violetBg, "Получите понятный разбор", "Что в норме, на что обратить внимание и к какому врачу идти. Простыми словами.")
            WelcomePoint(Icons.AutoMirrored.Outlined.ShowChart, a.good, a.goodBg, "Следите за динамикой", "Показатели сохраняются, и видно, как они меняются от анализа к анализу.")
            WelcomePoint(Icons.Outlined.VerifiedUser, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, "Данные на вашем телефоне", "История хранится только у вас. Можно закрыть приложение отпечатком пальца.")
            Gap(12.dp)
            Text(
                "МедАнализ не ставит диагноз и не заменяет врача. ИИ может ошибаться. При опасных симптомах сразу звоните 103 или 112.\n\n" +
                    "Нажимая «Начать», вы соглашаетесь, что загруженные документы обрабатывает сторонний ИИ-сервис на зарубежных серверах. " +
                    "История разборов хранится только на вашем телефоне.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Gap(16.dp)
        }
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .height(56.dp),
            shape = MaterialTheme.shapes.medium,
        ) { Text("Начать", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun WelcomePoint(icon: ImageVector, tint: Color, bg: Color, title: String, text: String) {
    Row(Modifier.padding(bottom = 22.dp)) {
        IconTile(icon, tint, bg, 46.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Gap(2.dp)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AppLogo(72.dp)
            Gap(4.dp)
            Text("МедАнализ", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Приложение защищено. Подтвердите, что это вы.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Gap(8.dp)
            Button(
                onClick = onUnlock,
                modifier = Modifier.height(52.dp),
                shape = MaterialTheme.shapes.medium,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Разблокировать", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
