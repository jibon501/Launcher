package com.petal.launcher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.petal.launcher.data.LauncherSettings
import com.petal.launcher.data.Palette
import com.petal.launcher.data.ThemeMode

private data class Seed(val light: Color, val lightContainer: Color, val dark: Color, val darkContainer: Color)

private fun seedOf(p: Palette): Seed = when (p) {
    Palette.GREEN -> Seed(Color(0xFF2E7D32), Color(0xFFC8E6C9), Color(0xFF81C995), Color(0xFF1B5E20))
    Palette.PURPLE -> Seed(Color(0xFF6750A4), Color(0xFFEADDFF), Color(0xFFD0BCFF), Color(0xFF4F378B))
    Palette.ORANGE -> Seed(Color(0xFFE65100), Color(0xFFFFE0B2), Color(0xFFFFB74D), Color(0xFF8F3300))
    Palette.ROSE -> Seed(Color(0xFFC2185B), Color(0xFFF8BBD0), Color(0xFFF48FB1), Color(0xFF880E4F))
    else -> Seed(Color(0xFF1565C0), Color(0xFFD3E3FD), Color(0xFFA8C7FA), Color(0xFF0842A0))
}

private fun presetScheme(p: Palette, dark: Boolean): ColorScheme {
    val s = seedOf(p)
    return if (dark) darkColorScheme(
        primary = s.dark, onPrimary = Color(0xFF0B1220), primaryContainer = s.darkContainer,
        onPrimaryContainer = Color.White, secondary = s.dark, tertiary = s.dark
    ) else lightColorScheme(
        primary = s.light, primaryContainer = s.lightContainer,
        onPrimaryContainer = Color(0xFF111111), secondary = s.light, tertiary = s.light
    )
}

@Composable
fun PetalTheme(settings: LauncherSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    val scheme = if (settings.palette == Palette.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        presetScheme(if (settings.palette == Palette.DYNAMIC) Palette.BLUE else settings.palette, dark)
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
