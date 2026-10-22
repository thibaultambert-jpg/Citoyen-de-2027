package fr.citoyende2027.feature.candidates

import androidx.compose.ui.test.junit4.v2.createComposeRule
import fr.citoyende2027.core.designsystem.theme.CitoyenTheme
import fr.citoyende2027.core.model.CandidateOrder
import fr.citoyende2027.core.testing.FictionalCandidates
import fr.citoyende2027.core.uitesting.TALL_WINDOW
import fr.citoyende2027.core.uitesting.assertAccessible
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AGENTS.md §11: labels, touch targets and headings, at default and largest font size. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_WINDOW)
class CandidatesAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val state = CandidatesUiState.Ready(
        dataInfo = FictionalCandidates.packInfo,
        order = CandidateOrder.Alphabetical,
        query = "",
        candidates = FictionalCandidates.all,
        totalCount = FictionalCandidates.all.size,
    )

    @Test
    fun listAtDefaultSize() = check(1f)

    @Test
    fun listAtLargestFontSize() = check(2f)

    private fun check(fontScale: Float) = composeRule.assertAccessible(fontScale) {
        CitoyenTheme { CandidatesContent(state, {}, {}, {}, {}) }
    }
}
