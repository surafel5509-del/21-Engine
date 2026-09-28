package com.sengine.studio.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object StudioColors {
    val background = Color(0xFF181A1E)
    val surface = Color(0xFF22252A)
    val raised = Color(0xFF2E3239)
    val border = Color(0xFF414650)
    val violet = Color(0xFF7FA7FF)
    val mint = Color(0xFF6DD7BC)
    val blue = Color(0xFF8DB8FF)
    val text = Color(0xFFECEEF3)
    val muted = Color(0xFFAFB6C3)
    val danger = Color(0xFFFF909A)
}

private val colors = darkColorScheme(
    primary = StudioColors.violet,
    onPrimary = Color(0xFF181329),
    secondary = StudioColors.mint,
    background = StudioColors.background,
    onBackground = StudioColors.text,
    surface = StudioColors.surface,
    onSurface = StudioColors.text,
    surfaceVariant = StudioColors.raised,
    onSurfaceVariant = StudioColors.muted,
    outline = StudioColors.border,
    error = StudioColors.danger,
)

private val studioTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.9f).sp),
    headlineSmall = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5f).sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8f.sp),
)

@Composable
fun StudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = studioTypography, content = content)
}
