package fr.citoyende2027.feature.compare

import androidx.compose.ui.test.junit4.v2.createComposeRule
import fr.citoyende2027.core.designsystem.theme.CitoyenTheme
import fr.citoyende2027.core.model.ThemeFacts
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
class CompareAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val state = CompareUiState(
        candidates = FictionalCandidates.all,
        selected = FictionalCandidates.all.map { it.id }.toSet(),
        themes = listOf(FictionalCandidates.theme1, FictionalCandidates.theme2),
        theme = FictionalCandidates.theme1,
        columns = FictionalCandidates.all.map { candidate ->
            val program = FictionalCandidates.programsPack.program(candidate.id)
            val votes = FictionalCandidates.votes.filter { it.candidateId == candidate.id }
            CandidateColumn(candidate, ThemeFacts.of(FictionalCandidates.theme1, program, votes), program.documents)
        },
        dataInfo = FictionalCandidates.packInfo,
    )

    @Test
    fun comparisonAtDefaultSize() = composeRule.assertAccessible(1f) { CitoyenTheme { CompareContent(state, {}, {}) } }

    @Test
    fun comparisonAtLargestFontSize() = composeRule.assertAccessible(2f) {
        CitoyenTheme { CompareContent(state, {}, {}) }
    }
}
