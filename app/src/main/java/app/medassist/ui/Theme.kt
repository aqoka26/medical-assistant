package app.medassist.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Brand colours: calm medical teal with a soft mint background. */
object Brand {
    val Teal = Color(0xFF0E7C7B)
    val TealDark = Color(0xFF075E5D)
    val Mint = Color(0xFF2BB5A0)
    val Sky = Color(0xFF3A86C8)
}

private val LightColors = lightColorScheme(
    primary = Brand.Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFEC),
    onPrimaryContainer = Color(0xFF00201F),
    secondary = Color(0xFF4A6361),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2EF),
    onSecondaryContainer = Color(0xFF05201E),
    tertiary = Brand.Sky,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD5E6F8),
    onTertiaryContainer = Color(0xFF0B2741),
    background = Color(0xFFF4F9F8),
    onBackground = Color(0xFF161D1C),
    surface = Color.White,
    onSurface = Color(0xFF161D1C),
    surfaceVariant = Color(0xFFE6EFED),
    onSurfaceVariant = Color(0xFF3F4948),
    outline = Color(0xFF6F7978),
    outlineVariant = Color(0xFFC8D3D1),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FD6D3),
    onPrimary = Color(0xFF003736),
    primaryContainer = Color(0xFF00504F),
    onPrimaryContainer = Color(0xFF8FF3EF),
    secondary = Color(0xFFB0CCC9),
    onSecondary = Color(0xFF1B3533),
    secondaryContainer = Color(0xFF324B49),
    onSecondaryContainer = Color(0xFFCCE8E5),
    tertiary = Color(0xFFA2CAF0),
    onTertiary = Color(0xFF00315A),
    tertiaryContainer = Color(0xFF1C4A73),
    onTertiaryContainer = Color(0xFFD5E6F8),
    background = Color(0xFF0E1514),
    onBackground = Color(0xFFDDE4E2),
    surface = Color(0xFF161E1D),
    onSurface = Color(0xFFDDE4E2),
    surfaceVariant = Color(0xFF2A3433),
    onSurfaceVariant = Color(0xFFBEC9C7),
    outline = Color(0xFF899391),
    outlineVariant = Color(0xFF3F4948),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun MedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
