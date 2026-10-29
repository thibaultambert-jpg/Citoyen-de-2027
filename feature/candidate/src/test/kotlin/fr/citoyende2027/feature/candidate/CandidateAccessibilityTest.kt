package fr.citoyende2027.feature.candidate

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import fr.citoyende2027.core.designsystem.theme.CitoyenTheme
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.testing.FictionalCandidates
import fr.citoyende2027.core.uitesting.TALL_WINDOW
import fr.citoyende2027.core.uitesting.accessibilityProblems
import fr.citoyende2027.core.uitesting.assertAccessible
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AGENTS.md §11 on every tab of the sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_WINDOW)
class CandidateAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun state(candidate: Candidate) = CandidateUiState.Ready(
        candidate = candidate,
        dataInfo = FictionalCandidates.packInfo,
        mandates = FictionalCandidates.mandates.filter { it.candidateId == candidate.id },
        votes = VotesState(
            votes = FictionalCandidates.votes.filter { it.candidateId == candidate.id },
            totalCount = FictionalCandidates.votes.count { it.candidateId == candidate.id },
        ),
        speeches = FictionalCandidates.speeches.filter { it.candidateId == candidate.id },
        program = FictionalCandidates.programsPack.program(candidate.id),
        themes = FictionalCandidates.themes,
        allVotes = FictionalCandidates.votes.filter { it.candidateId == candidate.id },
    )

    @Test
    fun everyTabAtDefaultSize() = checkEveryTab(1f)

    @Test
    fun everyTabAtLargestFontSize() = checkEveryTab(2f)

    private fun checkEveryTab(fontScale: Float) {
        composeRule.assertAccessible(fontScale) {
            CitoyenTheme { CandidateContent(uiState = state(FictionalCandidates.candidateA), onBack = {}) }
        }
        CandidateSheetTab.entries.forEach { tab ->
            composeRule.onNodeWithText(context.getString(tab.label)).performScrollTo().performClick()
            composeRule.waitForIdle()
            assertEquals("Tab ${tab.name}", emptyList<String>(), composeRule.accessibilityProblems())
        }
    }
}
