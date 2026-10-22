package fr.citoyende2027.feature.institutions

import androidx.compose.ui.test.junit4.createComposeRule
import fr.citoyende2027.core.designsystem.theme.CitoyenTheme
import fr.citoyende2027.core.model.LegalSearchHit
import fr.citoyende2027.core.testing.FictionalLegal
import fr.citoyende2027.core.uitesting.assertAccessible
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** AGENTS.md §11: labels, touch targets and headings, at default and largest font size. */
@RunWith(RobolectricTestRunner::class)
class InstitutionsAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val home =
        InstitutionsUiState(texts = listOf(FictionalLegal.textA, FictionalLegal.textB), dataInfo = FictionalLegal.info)

    private val search = home.copy(
        query = "règle fictive",
        results = listOf(LegalSearchHit(FictionalLegal.articles[1], FictionalLegal.textA, "règle fictive", 1.0)),
    )

    @Test
    fun homeAtDefaultSize() = composeRule.assertAccessible(1f) {
        CitoyenTheme { InstitutionsContent(home, {}, { _, _ -> }) }
    }

    @Test
    fun searchAtLargestFontSize() =
        composeRule.assertAccessible(2f) { CitoyenTheme { InstitutionsContent(search, {}, { _, _ -> }) } }
}
