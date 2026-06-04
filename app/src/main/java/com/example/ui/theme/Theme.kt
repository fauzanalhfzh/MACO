package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkMacoColorScheme = darkColorScheme(
    primary = PremiumOrange,
    secondary = NeonOrangeAccent,
    tertiary = PastelOrange,
    background = DeepDarkBackground,
    surface = CharcoalSurface,
    onPrimary = SoftWhite,
    onSecondary = DeepDarkBackground,
    onBackground = SoftWhite,
    onSurface = SoftWhite,
    primaryContainer = CharcoalCard,
    onPrimaryContainer = SoftWhite,
    surfaceVariant = CharcoalCard,
    onSurfaceVariant = SoftWhite,
    outline = DarkBorder
)

private val LightMacoColorScheme = lightColorScheme(
    primary = PremiumOrange,
    secondary = NeonOrangeAccent,
    tertiary = PastelOrange,
    background = DeepDarkBackground,
    surface = CharcoalSurface,
    onPrimary = SoftWhite,
    onSecondary = DeepDarkBackground,
    onBackground = SoftWhite,
    onSurface = SoftWhite,
    primaryContainer = CharcoalCard,
    onPrimaryContainer = SoftWhite,
    surfaceVariant = CharcoalCard,
    onSurfaceVariant = SoftWhite,
    outline = DarkBorder
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = if (isLightThemeGlobal) LightMacoColorScheme else DarkMacoColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
