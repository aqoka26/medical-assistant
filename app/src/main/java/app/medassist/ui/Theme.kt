package app.medassist.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.medassist.R

object Brand {
    val Teal = Color(0xFF0B7A75)
    val TealDark = Color(0xFF075955)
    val Mint = Color(0xFF2BB5A0)
    val Sky = Color(0xFF2F6FDE)
}

/** Colours for analysis status and small accents, light and dark. */
@Immutable
data class Accents(
    val good: Color, val goodBg: Color,
    val warn: Color, val warnBg: Color,
    val bad: Color, val badBg: Color,
    val blue: Color, val blueBg: Color,
    val violet: Color, val violetBg: Color,
)

private val LightAccents = Accents(
    good = Color(0xFF16875A), goodBg = Color(0xFFE5F5EE),
    warn = Color(0xFFB86E00), warnBg = Color(0xFFFFF3DF),
    bad = Color(0xFFCC3B3B), badBg = Color(0xFFFDEBEB),
    blue = Color(0xFF2F6FDE), blueBg = Color(0xFFE8EFFC),
    violet = Color(0xFF7452D6), violetBg = Color(0xFFF0EBFD),
)

private val DarkAccents = Accents(
    good = Color(0xFF5BD39B), goodBg = Color(0xFF173528),
    warn = Color(0xFFF2B54F), warnBg = Color(0xFF3A2C12),
    bad = Color(0xFFF07B7B), badBg = Color(0xFF3D1E1E),
    blue = Color(0xFF8DB0F5), blueBg = Color(0xFF1C2A44),
    violet = Color(0xFFB9A4F5), violetBg = Color(0xFF2A2342),
)

val LocalAccents = staticCompositionLocalOf { LightAccents }

private val Light = lightColorScheme(
    primary = Color(0xFF0B7A75),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1F2F0),
    onPrimaryContainer = Color(0xFF05433F),
    secondary = Color(0xFF2F6FDE),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EFFC),
    onSecondaryContainer = Color(0xFF12336E),
    tertiary = Color(0xFF7452D6),
    tertiaryContainer = Color(0xFFF0EBFD),
    onTertiaryContainer = Color(0xFF2E1B6B),
    background = Color(0xFFF3F5F7),
    onBackground = Color(0xFF15212B),
    surface = Color.White,
    onSurface = Color(0xFF15212B),
    surfaceVariant = Color(0xFFF0F3F5),
    onSurfaceVariant = Color(0xFF5E6E7B),
    surfaceContainer = Color(0xFFF7F9FA),
    surfaceContainerHigh = Color(0xFFEEF2F4),
    outline = Color(0xFFC9D2D9),
    outlineVariant = Color(0xFFE4E9ED),
    error = Color(0xFFCC3B3B),
    errorContainer = Color(0xFFFDEBEB),
    onErrorContainer = Color(0xFF6E1414),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5CC8C0),
    onPrimary = Color(0xFF00302D),
    primaryContainer = Color(0xFF0F3E3B),
    onPrimaryContainer = Color(0xFFBDEDE8),
    secondary = Color(0xFF8DB0F5),
    secondaryContainer = Color(0xFF1C2A44),
    onSecondaryContainer = Color(0xFFD6E2FB),
    tertiary = Color(0xFFB9A4F5),
    tertiaryContainer = Color(0xFF2A2342),
    onTertiaryContainer = Color(0xFFE6DDFD),
    background = Color(0xFF0F1519),
    onBackground = Color(0xFFE3E9ED),
    surface = Color(0xFF172026),
    onSurface = Color(0xFFE3E9ED),
    surfaceVariant = Color(0xFF1E2930),
    onSurfaceVariant = Color(0xFF9AABB7),
    surfaceContainer = Color(0xFF1A242A),
    surfaceContainerHigh = Color(0xFF223038),
    outline = Color(0xFF3A4952),
    outlineVariant = Color(0xFF26333B),
    error = Color(0xFFF07B7B),
    errorContainer = Color(0xFF3D1E1E),
    onErrorContainer = Color(0xFFFFD9D9),
)

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

private fun style(size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = Manrope, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = 0.sp)

// Roomy line height and medium sizes keep long medical text easy to read.
private val AppTypography = Typography(
    displayLarge = style(34, 40, FontWeight.ExtraBold),
    displayMedium = style(30, 36, FontWeight.ExtraBold),
    displaySmall = style(26, 32, FontWeight.Bold),
    headlineLarge = style(26, 32, FontWeight.Bold),
    headlineMedium = style(24, 30, FontWeight.Bold),
    headlineSmall = style(21, 28, FontWeight.Bold),
    titleLarge = style(19, 26, FontWeight.Bold),
    titleMedium = style(16, 22, FontWeight.SemiBold),
    titleSmall = style(15, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 25, FontWeight.Normal),
    bodyMedium = style(15, 22, FontWeight.Normal),
    bodySmall = style(13, 19, FontWeight.Normal),
    labelLarge = style(15, 20, FontWeight.SemiBold),
    labelMedium = style(13, 18, FontWeight.SemiBold),
    labelSmall = style(12, 16, FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MedTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalAccents provides if (dark) DarkAccents else LightAccents) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
