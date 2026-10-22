package fr.citoyende2027.feature.about

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.v2.createComposeRule
import fr.citoyende2027.core.data.repository.InstalledPack
import fr.citoyende2027.core.data.repository.PackUpdate
import fr.citoyende2027.core.designsystem.theme.CitoyenTheme
import fr.citoyende2027.core.uitesting.TALL_WINDOW
import fr.citoyende2027.core.uitesting.assertAccessible
import fr.citoyende2027.llm.assistant.AssistantStatus
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AGENTS.md §11 on the "Infos" list and every page, at default and largest font size. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_WINDOW)
class AboutAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val state = AboutUiState(
        packs = listOf(InstalledPack("core", "core-fictif", LocalDate.of(2026, 1, 15), true, LocalDate.of(2026, 1, 1))),
        history = listOf(PackUpdate("core", "core-fictif", "2026-01-15", "2026-01-16")),
        assistant = AssistantStatus.Ready(enabled = true),
        storage = StorageUse(dataBytes = 12_000_000, modelBytes = 2_000_000_000),
    )

    @Test
    fun listAtDefaultSize() = composeRule.assertAccessible(1f) { CitoyenTheme { AboutContent(state, {}) } }

    @Test
    fun listAtLargestFontSize() = composeRule.assertAccessible(2f) { CitoyenTheme { AboutContent(state, {}) } }

    @Test
    fun dataPage() = page { DataPage(state, {}) }

    @Test
    fun settingsPage() = page { SettingsPage(state, {}, {}, {}) }

    @Test
    fun textPages() = page {
        MethodologyPage()
        SourcesPage()
        InclusionPage(state)
        PrivacyPage()
        LegalPage()
    }

    private fun page(content: @androidx.compose.runtime.Composable () -> Unit) =
        composeRule.assertAccessible(2f) { CitoyenTheme { Column { content() } } }
}
