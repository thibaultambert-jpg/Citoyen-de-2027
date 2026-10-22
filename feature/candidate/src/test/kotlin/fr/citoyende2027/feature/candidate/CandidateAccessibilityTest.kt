package fr.citoyende2027.feature.candidate

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import fr.citoyende2027.core.designsystem.theme.CitoyenTheme
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.model.ReservePeriod
import fr.citoyende2027.core.testing.FictionalCandidates
import fr.citoyende2027.core.ui.R as CoreUiR
import fr.citoyende2027.core.uitesting.TALL_WINDOW
import fr.citoyende2027.core.uitesting.accessibilityProblems
import fr.citoyende2027.core.uitesting.assertAccessible
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AGENTS.md §11 on every tab of the sheet; §10 reserve period notice in place of campaign content. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_WINDOW)
class CandidateAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun state(candidate: Candidate, reserve: ReservePeriod = ReservePeriod.NONE) = CandidateUiState.Ready(
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
        reserve = reserve,
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

    @Test
    fun reservePeriodReplacesCampaignTabs() {
        val reserve = ReservePeriod(true, LocalDate.of(2026, 1, 20))
        composeRule.setContent {
            CitoyenTheme { CandidateContent(uiState = state(FictionalCandidates.candidateA, reserve), onBack = {}) }
        }
        val notice = context.getString(CoreUiR.string.core_ui_reserve)
        listOf(CandidateSheetTab.PROGRAM, CandidateSheetTab.SPEECHES, CandidateSheetTab.PROGRAM_AND_VOTES).forEach {
            composeRule.onNodeWithText(context.getString(it.label)).performScrollTo().performClick()
            composeRule.onNodeWithText(notice).assertExists()
        }
        composeRule.onNodeWithText(context.getString(CandidateSheetTab.MANDATES.label)).performScrollTo().performClick()
        composeRule.onNodeWithText(notice).assertDoesNotExist()
    }
}
