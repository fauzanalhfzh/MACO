package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MacoColorScheme = darkColorScheme(
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
    MaterialTheme(
        colorScheme = MacoColorScheme,
        typography = Typography,
        content = content
    )
}
