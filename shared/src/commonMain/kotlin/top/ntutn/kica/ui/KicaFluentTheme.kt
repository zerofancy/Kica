package top.ntutn.kica.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.darkColors
import io.github.composefluent.lightColors
import top.ntutn.kica.model.ThemePreference

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0F6CBD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFA),
    onPrimaryContainer = Color(0xFF0C3B5E),
    secondary = Color(0xFF4F6B85),
    onSecondary = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFF5F5F5),
    surfaceContainer = Color(0xFFF9F9F9),
    surfaceContainerHigh = Color(0xFFF0F0F0),
    background = Color(0xFFF3F3F3),
    onBackground = Color(0xFF1B1B1B),
    onSurface = Color(0xFF1B1B1B),
    onSurfaceVariant = Color(0xFF616161),
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFE0E0E0),
    error = Color(0xFFC42B1C),
    errorContainer = Color(0xFFFDE7E9),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF60A8E8),
    onPrimary = Color(0xFF082338),
    primaryContainer = Color(0xFF143F5F),
    onPrimaryContainer = Color(0xFFDCEBFA),
    secondary = Color(0xFFAFC7DC),
    onSecondary = Color(0xFF183247),
    surface = Color(0xFF2C2C2C),
    surfaceVariant = Color(0xFF292929),
    surfaceContainer = Color(0xFF252525),
    surfaceContainerHigh = Color(0xFF333333),
    background = Color(0xFF202020),
    onBackground = Color(0xFFF5F5F5),
    onSurface = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFFC8C8C8),
    outline = Color(0xFF686868),
    outlineVariant = Color(0xFF454545),
    error = Color(0xFFFF99A4),
    errorContainer = Color(0xFF5A1A20),
)

private val KicaTypography = Typography(
    headlineSmall = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
)

private val KicaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@OptIn(ExperimentalFluentApi::class)
@Composable
fun KicaFluentTheme(
    preference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (preference) {
        ThemePreference.SYSTEM -> systemDark
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val fluentColors = remember(dark) {
        if (dark) darkColors(Color(0xFF0F6CBD)) else lightColors(Color(0xFF0F6CBD))
    }

    FluentTheme(
        colors = fluentColors,
        compactMode = true,
        useAcrylicPopup = false,
    ) {
        PlatformCompatibleTextContextMenu {
            MaterialTheme(
                colorScheme = if (dark) DarkScheme else LightScheme,
                typography = KicaTypography,
                shapes = KicaShapes,
                content = content,
            )
        }
    }
}
