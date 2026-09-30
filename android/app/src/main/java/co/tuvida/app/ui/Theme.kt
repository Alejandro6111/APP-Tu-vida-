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

private val Light = lightColorScheme(primary = Color(0xFF245B50), onPrimary = Color.White, primaryContainer = Color(0xFFCCEADF), onPrimaryContainer = Color(0xFF103A30), secondary = Color(0xFF715B36), secondaryContainer = Color(0xFFF1E2C5), onSecondaryContainer = Color(0xFF403114), background = Color(0xFFF7F5F0), surface = Color(0xFFF7F5F0), surfaceVariant = Color(0xFFE7EBE6), onSurface = Color(0xFF182D31), onSurfaceVariant = Color(0xFF4A5A5C), outline = Color(0xFF788585), error = Color(0xFFAF3538), errorContainer = Color(0xFFFFDADB))
private val Dark = darkColorScheme(primary = Color(0xFFA2D5C4), onPrimary = Color(0xFF06392C), primaryContainer = Color(0xFF245B50), onPrimaryContainer = Color(0xFFCCEADF), secondary = Color(0xFFDCC59A), secondaryContainer = Color(0xFF514127), onSecondaryContainer = Color(0xFFF1E2C5), background = Color(0xFF10252B), surface = Color(0xFF10252B), surfaceVariant = Color(0xFF293E43), onSurface = Color(0xFFE3ECEA), onSurfaceVariant = Color(0xFFB5C7C5), outline = Color(0xFF819795), error = Color(0xFFFFB4B6), errorContainer = Color(0xFF681A22))

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
