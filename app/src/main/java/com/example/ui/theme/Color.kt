package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

// Observable global state variable for light/dark theme tracking
var isLightThemeGlobal: Boolean by mutableStateOf(false)

// Elegant minimalist dark theme with premium orange accents / Light modes
val DeepDarkBackground: Color
    get() = if (isLightThemeGlobal) Color(0xFFF7F4F0) else Color(0xFF0A0A0A)

val CharcoalSurface: Color
    get() = if (isLightThemeGlobal) Color(0xFFFFFFFF) else Color(0xFF141414)

val PremiumOrange: Color
    get() = if (isLightThemeGlobal) Color(0xFFD35A11) else Color(0xFFF27D26)

val NeonOrangeAccent: Color
    get() = if (isLightThemeGlobal) Color(0xFFE06516) else Color(0xFFD96B1F)

val PastelOrange: Color
    get() = if (isLightThemeGlobal) Color(0xFFFFB380) else Color(0xFFFFB380)

val CharcoalCard: Color
    get() = if (isLightThemeGlobal) Color(0xFFF0EDE9) else Color(0xFF1A1A1A)

val DarkBorder: Color
    get() = if (isLightThemeGlobal) Color(0xFFE2DDD5) else Color(0xFF222222)

val MutedText: Color
    get() = if (isLightThemeGlobal) Color(0xFF7A7067) else Color(0xFF888888)

val SoftWhite: Color
    get() = if (isLightThemeGlobal) Color(0xFF2C2520) else Color(0xFFE0E0E0)

// Success & Deficit helpers
val EmeraldGreen = Color(0xFF1B5E20)
val CrimsonRed = Color(0xFFD32F2F)

val SoftGreen: Color
    get() = if (isLightThemeGlobal) Color(0xFF2E7D32) else Color(0xFF4ADE80)

val SoftRed: Color
    get() = if (isLightThemeGlobal) Color(0xFFC62828) else Color(0xFFF16E6E)

val SoftYellow: Color
    get() = if (isLightThemeGlobal) Color(0xFFF57F17) else Color(0xFFFFD54F)

val SoftBlue: Color
    get() = if (isLightThemeGlobal) Color(0xFF1976D2) else Color(0xFF4FC3F7)
