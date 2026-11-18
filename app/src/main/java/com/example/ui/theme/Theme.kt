package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DroidVmDarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = CyanOnPrimary,
    primaryContainer = CyanContainer,
    onPrimaryContainer = CyanOnContainer,
    secondary = EmeraldSecondary,
    onSecondary = EmeraldOnSecondary,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = EmeraldOnContainer,
    tertiary = AmberTertiary,
    onTertiary = AmberOnTertiary,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberOnContainer,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = ObsidianOutline,
    error = ErrorRed,
    errorContainer = ErrorContainerDark,
    onError = Color.White,
    onErrorContainer = Color(0xFFFECACA)
)

private val DroidVmLightColorScheme = lightColorScheme(
    primary = CyanPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0F4FF),
    onPrimaryContainer = Color(0xFF002028),
    secondary = EmeraldSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF002116),
    tertiary = AmberTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF281800),
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightOutline,
    error = ErrorRed
)

@Composable
fun DroidVmTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DroidVmDarkColorScheme
        else -> DroidVmLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DroidVmTypography,
        content = content
    )
}
