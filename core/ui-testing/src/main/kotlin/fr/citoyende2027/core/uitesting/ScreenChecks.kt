package fr.citoyende2027.core.uitesting

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import org.junit.Assert.assertEquals

/** Font sizes checked for every screen: default, and the largest system setting (AGENTS.md §11). */
val CHECKED_FONT_SCALES = listOf(1f, 2f)

/** Composes [content] at [fontScale], then fails with every accessibility problem found. */
fun ComposeContentTestRule.assertAccessible(fontScale: Float = 1f, content: @Composable () -> Unit) {
    setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) { content() }
    }
    assertEquals("Accessibility problems at font scale $fontScale", emptyList<String>(), accessibilityProblems())
}
