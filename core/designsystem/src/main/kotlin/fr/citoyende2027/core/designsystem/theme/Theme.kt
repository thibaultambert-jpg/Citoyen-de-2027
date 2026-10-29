package fr.citoyende2027.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val DarkColors = darkColorScheme(
    primary = Ink80,
    onPrimary = Ink20,
    primaryContainer = Ink30,
    onPrimaryContainer = Ink90,
    secondary = Slate75,
    onSecondary = Slate8,
    // Selected chips and the navigation indicator use the same container as primary.
    secondaryContainer = Ink30,
    onSecondaryContainer = Ink90,
    background = Slate8,
    onBackground = Slate90,
    surface = Slate10,
    onSurface = Slate90,
    surfaceVariant = Slate22,
    onSurfaceVariant = Slate75,
    // Component outlines need 3:1 against the surface; dividers use outlineVariant.
    outline = SlateOutline,
    outlineVariant = SlateOutlineVariant,
    surfaceContainerLowest = Slate4,
    surfaceContainerLow = Slate12,
    surfaceContainer = Slate17,
    surfaceContainerHigh = Slate22,
    surfaceContainerHighest = Slate27,
)

/** Colours that have no Material role. */
@Immutable
class CitoyenColors(
    /** Background of anything written by the local LLM. */
    val generatedContainer: Color,
    val onGeneratedContainer: Color,
)

internal val DarkCitoyenColors = CitoyenColors(generatedContainer = Sand20, onGeneratedContainer = Sand90)

private val LocalCitoyenColors = staticCompositionLocalOf { DarkCitoyenColors }

val MaterialTheme.citoyenColors: CitoyenColors
    @Composable
    @ReadOnlyComposable
    get() = LocalCitoyenColors.current

/**
 * App theme. The app is dark only, so every user sees the same neutral palette. Dynamic
 * (wallpaper) colours are deliberately disabled. Typography scales with the system font size.
 */
@Composable
fun CitoyenTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCitoyenColors provides DarkCitoyenColors) {
        MaterialTheme(
            colorScheme = DarkColors,
            typography = CitoyenTypography,
            shapes = CitoyenShapes,
            content = content,
        )
    }
}
