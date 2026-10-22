package fr.citoyende2027.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** WCAG 2.1 AA (AGENTS.md §11): 4.5:1 for text, 3:1 for component outlines, in both themes. */
class ContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun ColorScheme.textPairs() = mapOf(
        "onPrimary / primary" to (onPrimary to primary),
        "onPrimaryContainer / primaryContainer" to (onPrimaryContainer to primaryContainer),
        "onSecondary / secondary" to (onSecondary to secondary),
        "onSecondaryContainer / secondaryContainer" to (onSecondaryContainer to secondaryContainer),
        "onBackground / background" to (onBackground to background),
        "onSurface / surface" to (onSurface to surface),
        "onSurfaceVariant / surface" to (onSurfaceVariant to surface),
        "onSurfaceVariant / surfaceVariant" to (onSurfaceVariant to surfaceVariant),
        "primary / surface (text buttons)" to (primary to surface),
        "onSurface / surfaceContainerLowest" to (onSurface to surfaceContainerLowest),
        "onSurface / surfaceContainerLow" to (onSurface to surfaceContainerLow),
        "onSurface / surfaceContainer" to (onSurface to surfaceContainer),
        "onSurface / surfaceContainerHigh" to (onSurface to surfaceContainerHigh),
        "onSurface / surfaceContainerHighest" to (onSurface to surfaceContainerHighest),
        "onSurfaceVariant / surfaceContainerHighest" to (onSurfaceVariant to surfaceContainerHighest),
        "primary / surfaceContainerHighest" to (primary to surfaceContainerHighest),
    )

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `text colours reach 4,5 to 1 and outlines 3 to 1`(dark: Boolean) {
        val scheme = if (dark) DarkColors else LightColors
        val failures = scheme.textPairs().mapNotNull { (name, pair) ->
            ratio(pair.first, pair.second).takeIf { it < 4.5 }?.let { "$name: %.2f".format(it) }
        } + listOfNotNull(
            ratio(scheme.outline, scheme.surface).takeIf { it < 3.0 }?.let { "outline / surface: %.2f".format(it) },
        )
        assertTrue(failures.isEmpty(), "Contrast below WCAG AA (dark=$dark): $failures")
    }
}
