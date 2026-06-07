package com.ryu.musicplayer

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 웹 Vinyl 플레이어와 동일한 색상 팔레트 */
object Vinyl {
    val Bg = Color(0xFF1A1512)
    val Surface = Color(0xFF241D18)
    val SurfaceHi = Color(0xFF2E2620)
    val Line = Color(0xFF3A2F26)
    val Text = Color(0xFFF1E7DC)
    val Muted = Color(0xFFA8957F)
    val Faint = Color(0xFF6F6052)
    val Accent = Color(0xFFF0B35B)
    val Accent2 = Color(0xFFD97A3C)
    val Danger = Color(0xFFE0654F)
}

private val VinylScheme = darkColorScheme(
    primary = Vinyl.Accent,
    onPrimary = Vinyl.Bg,
    secondary = Vinyl.Accent2,
    background = Vinyl.Bg,
    onBackground = Vinyl.Text,
    surface = Vinyl.Surface,
    onSurface = Vinyl.Text,
    surfaceVariant = Vinyl.SurfaceHi,
    onSurfaceVariant = Vinyl.Muted,
    outline = Vinyl.Line,
    error = Vinyl.Danger
)

@Composable
fun MusicPlayerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VinylScheme,
        content = content
    )
}
