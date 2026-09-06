package dev.personalterminal.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import dev.personalterminal.R
import dev.personalterminal.data.prefs.Settings
import dev.personalterminal.data.prefs.ThemeMode

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

val LocalPalette = staticCompositionLocalOf { Palettes.draculaDark }
val LocalPrompt = staticCompositionLocalOf { "user@android" }
/** Accessibility pass: larger touch targets, no decorative animation, higher-contrast dim text. */
val LocalAccessible = staticCompositionLocalOf { false }

/** Convenience accessor: `Term.palette.green` */
object Term {
    val palette: TerminalPalette
        @Composable get() = LocalPalette.current
    val prompt: String
        @Composable get() = LocalPrompt.current
}

fun monoTypography(scale: Float, family: FontFamily = JetBrainsMono): Typography {
    fun s(size: Float, weight: FontWeight = FontWeight.Normal, lh: Float = size * 1.45f) = TextStyle(
        fontFamily = family, fontWeight = weight, fontSize = (size * scale).sp, lineHeight = (lh * scale).sp,
    )
    return Typography(
        displayLarge = s(40f, FontWeight.Bold), displayMedium = s(32f, FontWeight.Bold), displaySmall = s(28f, FontWeight.Bold),
        headlineLarge = s(24f, FontWeight.Bold), headlineMedium = s(20f, FontWeight.Bold), headlineSmall = s(18f, FontWeight.Medium),
        titleLarge = s(18f, FontWeight.Medium), titleMedium = s(15f, FontWeight.Medium), titleSmall = s(13f, FontWeight.Medium),
        bodyLarge = s(15f), bodyMedium = s(13.5f), bodySmall = s(12f),
        labelLarge = s(13f, FontWeight.Medium), labelMedium = s(12f, FontWeight.Medium), labelSmall = s(11f),
    )
}

private fun TerminalPalette.toColorScheme(): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = green, onPrimary = bg,
        primaryContainer = bgHighlight, onPrimaryContainer = fg,
        secondary = cyan, onSecondary = bg,
        secondaryContainer = bgHighlight, onSecondaryContainer = fg,
        tertiary = purple, onTertiary = bg,
        background = bg, onBackground = fg,
        surface = bg, onSurface = fg,
        surfaceVariant = bgAlt, onSurfaceVariant = fgDim,
        surfaceContainer = bgAlt, surfaceContainerHigh = bgHighlight, surfaceContainerLow = bgAlt,
        surfaceContainerHighest = bgHighlight, surfaceContainerLowest = bgAlt,
        outline = border, outlineVariant = border,
        error = red, onError = bg, errorContainer = bgHighlight, onErrorContainer = red,
        inverseSurface = fg, inverseOnSurface = bg, scrim = bg,
    )
}

@Composable
fun TerminalTheme(settings: Settings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = Palettes.resolve(settings.themeName, dark, settings.customPaletteJson)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // System bars are transparent via enableEdgeToEdge(); we only paint the decor background
            window.decorView.setBackgroundColor(palette.bg.toArgb())
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val effective = if (settings.accessibilityMode) palette.copy(fgDim = palette.fgDim.mix(palette.fg, 0.35f), border = palette.border.mix(palette.fg, 0.25f)) else palette
    CompositionLocalProvider(LocalPalette provides effective, LocalPrompt provides settings.prompt, LocalAccessible provides settings.accessibilityMode) {
        val scale = if (settings.accessibilityMode) settings.fontScale * 1.1f else settings.fontScale
        MaterialTheme(colorScheme = effective.toColorScheme(), typography = monoTypography(scale, Fonts.family(settings.fontName))) {
            CrtOverlay(enabled = settings.crtEffect, palette = effective, content = content)
        }
    }
}

private fun androidx.compose.ui.graphics.Color.mix(other: androidx.compose.ui.graphics.Color, t: Float) = androidx.compose.ui.graphics.Color(
    red = red + (other.red - red) * t, green = green + (other.green - green) * t, blue = blue + (other.blue - blue) * t, alpha = 1f,
)

/** Preview helper. */
@Composable
fun PreviewTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val palette = Palettes.get(ThemeFamily.DRACULA, dark)
    LocalContext.current
    CompositionLocalProvider(LocalPalette provides palette, LocalPrompt provides "user@android") {
        MaterialTheme(colorScheme = palette.toColorScheme(), typography = monoTypography(1f), content = content)
    }
}
