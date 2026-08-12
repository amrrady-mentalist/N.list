package com.notesapp.offline.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Placeholder palette pulled from the web app's --accent-a / --accent-b /
// --danger custom properties. The full "liquid glass" look (blurred glass
// panels, blob backgrounds, custom radii) is intentionally deferred to the
// UI-shell phase so this foundation stays easy to read and test.
private val AccentA = Color(0xFF8B7CFF)
private val AccentB = Color(0xFF4FE8C4)
private val Danger = Color(0xFFFF6B6B)

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
