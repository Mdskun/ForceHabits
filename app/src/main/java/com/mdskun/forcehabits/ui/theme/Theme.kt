package com.mdskun.forcehabits.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── App color palette ─────────────────────────────────────────────
// No dynamic color — we own every pixel
private val AppOrange  = Color(0xFFFF6B35)
private val AppGreen   = Color(0xFF4CAF50)
private val AppBlue    = Color(0xFF2196F3)
private val BgDark     = Color(0xFF0D0D0D)
private val SurfDark   = Color(0xFF1A1A1A)
private val SurfDark2  = Color(0xFF242424)
private val OnSurface  = Color(0xFFEAEAEA)
private val OnSurfLow  = Color(0xFF888888)

private val StrictDarkColorScheme = darkColorScheme(
    primary          = AppOrange,
    onPrimary        = Color.White,
    primaryContainer = AppOrange.copy(alpha = 0.18f),
    onPrimaryContainer = AppOrange,

    secondary        = AppGreen,
    onSecondary      = Color.White,

    tertiary         = AppBlue,
    onTertiary       = Color.White,

    background       = BgDark,
    onBackground     = OnSurface,

    surface          = SurfDark,
    onSurface        = OnSurface,
    surfaceVariant   = SurfDark2,
    onSurfaceVariant = OnSurfLow,

    outline          = Color(0xFF3A3A3A),
    outlineVariant   = Color(0xFF2A2A2A),

    error            = Color(0xFFFF5252),
    onError          = Color.White,
)

@Composable
fun ForceHabitsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StrictDarkColorScheme,
        typography  = Typography,
        content     = content
    )
}
