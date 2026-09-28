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
    val background = Color(0xFF0B101B)
    val surface = Color(0xFF131B2B)
    val raised = Color(0xFF1B2639)
    val border = Color(0xFF29354A)
    val violet = Color(0xFFAD9BFF)
    val mint = Color(0xFF58DBC5)
    val blue = Color(0xFF83B6FF)
    val text = Color(0xFFF4F5FF)
    val muted = Color(0xFF92A1BA)
    val danger = Color(0xFFFF8C99)
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
