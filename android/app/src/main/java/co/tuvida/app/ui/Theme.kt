package co.tuvida.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*

private val Light = lightColorScheme(
    primary = Color(0xFF735719), onPrimary = Color.White,
    primaryContainer = Color(0xFFF3E2AF), onPrimaryContainer = Color(0xFF251A03),
    secondary = Color(0xFF655840), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEE1C9), onSecondaryContainer = Color(0xFF241D10),
    tertiary = Color(0xFF456454), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCAE8D5), onTertiaryContainer = Color(0xFF102C1D),
    background = Color(0xFFFAF8F2), onBackground = Color(0xFF1C1B18),
    surface = Color(0xFFFAF8F2), onSurface = Color(0xFF1C1B18),
    surfaceVariant = Color(0xFFEAE4D8), onSurfaceVariant = Color(0xFF504A3F),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF5F1E9),
    surfaceContainer = Color(0xFFF0ECE3), surfaceContainerHigh = Color(0xFFEAE6DD), surfaceContainerHighest = Color(0xFFE5E0D6),
    outline = Color(0xFF807666), outlineVariant = Color(0xFFD0C6B6),
    error = Color(0xFFAD323B), onError = Color.White, errorContainer = Color(0xFFFFDADB), onErrorContainer = Color(0xFF410008),
    inverseSurface = Color(0xFF302E29), inverseOnSurface = Color(0xFFF6F0E5), inversePrimary = Color(0xFFE9C66F), surfaceTint = Color(0xFF735719)
)
private val Dark = darkColorScheme(
    primary = Color(0xFFE9C66F), onPrimary = Color(0xFF261D06),
    primaryContainer = Color(0xFF443719), onPrimaryContainer = Color(0xFFF5DFA0),
    secondary = Color(0xFFD2BE96), onSecondary = Color(0xFF282215),
    secondaryContainer = Color(0xFF332C20), onSecondaryContainer = Color(0xFFF1E1C4),
    tertiary = Color(0xFFA9CFB5), onTertiary = Color(0xFF173323),
    tertiaryContainer = Color(0xFF2B4735), onTertiaryContainer = Color(0xFFCAE8D5),
    background = Color(0xFF0D0D0E), onBackground = Color(0xFFF2EEE5),
    surface = Color(0xFF0D0D0E), onSurface = Color(0xFFF2EEE5),
    surfaceVariant = Color(0xFF2D2A23), onSurfaceVariant = Color(0xFFCBC3B3),
    surfaceContainerLowest = Color(0xFF09090A), surfaceContainerLow = Color(0xFF161617),
    surfaceContainer = Color(0xFF1C1C1D), surfaceContainerHigh = Color(0xFF252525), surfaceContainerHighest = Color(0xFF30302E),
    outline = Color(0xFF9A907C), outlineVariant = Color(0xFF4C463A),
    error = Color(0xFFFFB3B8), onError = Color(0xFF65001B), errorContainer = Color(0xFF702832), onErrorContainer = Color(0xFFFFDADB),
    inverseSurface = Color(0xFFF2EEE5), inverseOnSurface = Color(0xFF302E29), inversePrimary = Color(0xFF735719), surfaceTint = Color(0xFFE9C66F)
)

@Composable fun TuVidaTheme(mode: String, content: @Composable () -> Unit) {
    val dark = mode == "dark" || (mode == "system" && isSystemInDarkTheme())
    val context = LocalContext.current
    val id = context.resources.getIdentifier("segoe_semibold", "font", context.packageName)
    SideEffect { (context as? android.app.Activity)?.let { activity -> WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark } } }
    val face = if (id != 0) FontFamily(Font(id, FontWeight.SemiBold)) else FontFamily.SansSerif
    val type = Typography()
    fun TextStyle.brand() = copy(fontFamily = face, fontWeight = FontWeight.SemiBold)
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Typography(displayLarge = type.displayLarge.brand(), displayMedium = type.displayMedium.brand(), displaySmall = type.displaySmall.brand(), headlineLarge = type.headlineLarge.brand(), headlineMedium = type.headlineMedium.brand(), headlineSmall = type.headlineSmall.brand(), titleLarge = type.titleLarge.brand(), titleMedium = type.titleMedium.brand(), titleSmall = type.titleSmall.brand(), bodyLarge = type.bodyLarge.brand(), bodyMedium = type.bodyMedium.brand(), bodySmall = type.bodySmall.brand(), labelLarge = type.labelLarge.brand(), labelMedium = type.labelMedium.brand(), labelSmall = type.labelSmall.brand()), content = content)
}
