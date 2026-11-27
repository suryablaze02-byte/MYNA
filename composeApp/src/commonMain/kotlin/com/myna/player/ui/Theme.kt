package com.myna.player.ui

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Lime = Color(0xFFC6F432)
val OnLime = Color(0xFF111111)

/** Bento colours. [block] is the "black tile" (inverted in dark mode), [soft] and [lav] are the pastel tiles. */
@Immutable
data class Pal(
    val tray: Color, val tile: Color, val ink: Color, val sub: Color, val line: Color,
    val block: Color, val onBlock: Color, val soft: Color, val lav: Color, val dark: Boolean,
)

val LightPal = Pal(
    tray = Color(0xFFEDEDF0), tile = Color.White, ink = Color(0xFF111111), sub = Color(0xFF6E6E73),
    line = Color(0xFFE2E2E6), block = Color(0xFF111111), onBlock = Color.White,
    soft = Color(0xFFE9F8B8), lav = Color(0xFFDCD7FF), dark = false,
)
val DarkPal = Pal(
    tray = Color(0xFF0B0B0D), tile = Color(0xFF1C1C1F), ink = Color(0xFFF4F4F5), sub = Color(0xFF9B9BA3),
    line = Color(0xFF2C2C30), block = Color(0xFFF4F4F5), onBlock = Color(0xFF111111),
    soft = Color(0xFF2E3A10), lav = Color(0xFF2A2647), dark = true,
)

val LocalPal = staticCompositionLocalOf { LightPal }

@Composable
/** [mode]: 0 follows the system, 1 light, 2 dark. */
fun MynaTheme(mode: Int = 0, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val p = if (dark) DarkPal else LightPal
    val scheme = (if (p.dark) darkColorScheme(primary = Lime, onPrimary = OnLime, background = p.tray, surface = p.tile, onSurface = p.ink, onBackground = p.ink)
    else lightColorScheme(primary = Lime, onPrimary = OnLime, background = p.tray, surface = p.tile, onSurface = p.ink, onBackground = p.ink))
        .copy(surfaceContainer = p.tile, surfaceContainerHigh = p.tile, surfaceTint = p.tile)
    MaterialTheme(colorScheme = scheme, shapes = androidx.compose.material3.Shapes(extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(16.dp))) {
        CompositionLocalProvider(LocalPal provides p, LocalContentColor provides p.ink) { content() }
    }
}
