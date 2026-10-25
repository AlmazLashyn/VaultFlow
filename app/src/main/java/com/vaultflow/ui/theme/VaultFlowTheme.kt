package com.vaultflow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy = Color(0xFF243B5A)
private val MutedNavy = Color(0xFF536982)
private val Canvas = Color(0xFFF5F6F8)
private val SurfaceLight = Color(0xFFFFFFFF)
private val Ink = Color(0xFF202A35)
private val InkSoft = Color(0xFF66717D)
private val DarkCanvas = Color(0xFF111820)
private val DarkSurface = Color(0xFF1B2530)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    secondary = MutedNavy,
    onSecondary = Color.White,
    background = Canvas,
    onBackground = Ink,
    surface = SurfaceLight,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE9EDF2),
    onSurfaceVariant = InkSoft,
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB5C8E2),
    onPrimary = Color(0xFF1C314C),
    secondary = Color(0xFFC0CCDA),
    onSecondary = Color(0xFF2A3B50),
    background = DarkCanvas,
    onBackground = Color(0xFFE5EAF0),
    surface = DarkSurface,
    onSurface = Color(0xFFE5EAF0),
    surfaceVariant = Color(0xFF2A3643),
    onSurfaceVariant = Color(0xFFBBC6D2),
    error = Color(0xFFFFB4AB),
)

@Composable
fun VaultFlowTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
