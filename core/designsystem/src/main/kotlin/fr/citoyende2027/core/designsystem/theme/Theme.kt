package fr.citoyende2027.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

internal val LightColors = lightColorScheme(
    primary = Slate40,
    onPrimary = Neutral98,
    primaryContainer = Slate90,
    onPrimaryContainer = Slate10,
    // NeutralVariant50 reached only 4.3:1 against Neutral98 (WCAG AA needs 4.5:1 for text).
    secondary = NeutralVariant40,
    onSecondary = Neutral98,
    secondaryContainer = NeutralVariant90,
    onSecondaryContainer = Neutral10,
    background = Neutral98,
    onBackground = Neutral10,
    surface = Neutral98,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,
    outline = NeutralVariant50,
    surfaceContainerLowest = NeutralLowest,
    surfaceContainerLow = Neutral96,
    surfaceContainer = Neutral94,
    surfaceContainerHigh = Neutral92,
    surfaceContainerHighest = Neutral90,
)

internal val DarkColors = darkColorScheme(
    primary = Slate80,
    onPrimary = Slate20,
    primaryContainer = Slate30,
    onPrimaryContainer = Slate95,
    secondary = NeutralVariant80,
    onSecondary = Slate20,
    secondaryContainer = NeutralVariant30,
    onSecondaryContainer = NeutralVariant90,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant80,
    outline = NeutralVariant50,
    surfaceContainerLowest = Neutral4,
    surfaceContainerLow = Neutral12,
    surfaceContainer = Neutral17,
    surfaceContainerHigh = Neutral22,
    surfaceContainerHighest = Neutral24,
)

/**
 * App theme. Dynamic (wallpaper) colours are deliberately disabled so that every user sees the
 * same neutral palette. Typography uses Material defaults, which scale with the system font size.
 */
@Composable
fun CitoyenTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
