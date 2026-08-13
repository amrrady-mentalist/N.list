package com.notesapp.offline.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette pulled directly from the web app's CSS custom properties
// (--blob-violet / --blob-rose / --blob-teal / --blob-amber / --accent-a/b
// / --danger) so the native version reads as the same product.
val BlobViolet = Color(0xFF8B7CFF)
val BlobRose = Color(0xFFFF7AA2)
val BlobTeal = Color(0xFF4FE8C4)
val BlobAmber = Color(0xFFFFC35A)
val AccentA = BlobViolet
val AccentB = BlobTeal
val Danger = Color(0xFFFF6B6B)

private val DarkColors = darkColorScheme(
    primary = AccentA,
    secondary = AccentB,
    error = Danger,
    background = Color.Black,
    surface = Color(0xFF121212)
)

private val LightColors = lightColorScheme(
    primary = AccentA,
    secondary = AccentB,
    error = Danger,
    background = Color.White,
    surface = Color(0xFFF5F5F5)
)

@Composable
fun NotesNativeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
