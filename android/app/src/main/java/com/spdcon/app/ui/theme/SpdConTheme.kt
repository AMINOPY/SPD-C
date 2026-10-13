package com.spdcon.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val NeonBlue = Color(0xFF00B4FF)
val NeonCyan = Color(0xFF4DC3FF)
val NeonGlow = Color(0xFF00E5FF)
val DeepNavy = Color(0xFF050B18)
val SurfaceGlass = Color(0xA60C1C37)
val SurfaceGlassLight = Color(0x8C122A4A)
val SuccessGreen = Color(0xFF00FF9C)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFA0B3D1)
val BorderGlow = Color(0x5900B4FF)
val DangerRed = Color(0xFFFF4D6A)

private val DarkColorScheme = darkColorScheme(
    primary = NeonBlue,
    onPrimary = Color.Black,
    secondary = NeonCyan,
    background = DeepNavy,
    surface = SurfaceGlass,
    onSurface = TextPrimary,
    onBackground = TextPrimary,
    outline = BorderGlow,
    error = DangerRed
)

private val SpdTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, color = TextPrimary),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = TextPrimary),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 18.sp, color = TextPrimary),
    bodyLarge = TextStyle(fontSize = 16.sp, color = TextPrimary),
    bodyMedium = TextStyle(fontSize = 14.sp, color = TextSecondary),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TextPrimary),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, color = TextSecondary)
)

@Composable
fun SpdConTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = SpdTypography,
        content = content
    )
}
