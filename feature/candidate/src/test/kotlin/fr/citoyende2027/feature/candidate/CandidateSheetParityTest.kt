package fr.citoyende2027.feature.candidate

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import fr.citoyende2027.core.designsystem.R as DesignR
import fr.citoyende2027.core.designsystem.theme.CitoyenTheme
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.testing.FictionalCandidates
import fr.citoyende2027.core.ui.R as CoreUiR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Template parity (AGENTS.md §10): every candidate sheet shows the same tabs, the same overview
 * sections and one coverage item per facet, whatever data is available for that candidate.
 */
@RunWith(RobolectricTestRunner::class)
class CandidateSheetParityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun candidateA_usesTheCommonTemplate() = assertCommonTemplate(FictionalCandidates.candidateA)

    @Test
    fun candidateB_usesTheCommonTemplate() = assertCommonTemplate(FictionalCandidates.candidateB)

    @Test
    fun candidateC_usesTheCommonTemplate() = assertCommonTemplate(FictionalCandidates.candidateC)

    private fun assertCommonTemplate(candidate: Candidate) {
        composeRule.setContent {
            CitoyenTheme {
                CandidateContent(uiState = readyState(candidate), onBack = {})
            }
        }

        CandidateSheetTab.entries.forEach { tab ->
            assertTrue(
                "Missing tab ${tab.name}",
                composeRule.onAllNodesWithText(context.getString(tab.label)).fetchSemanticsNodes().isNotEmpty(),
            )
        }
        assertEquals(
            listOf(
                candidate.displayName,
                context.getString(R.string.feature_candidate_section_status),
                context.getString(R.string.feature_candidate_section_coverage),
            ),
            composeRule.onAllNodes(isHeading()).fetchSemanticsNodes().map { node ->
                node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString()
            },
        )
        composeRule.onNodeWithText(context.getString(DesignR.string.core_designsystem_source)).assertExists()
        composeRule.onAllNodes(coverageItem).assertCountEquals(4)
    }

    /** Every tab opens for every candidate, whatever data exists (no tab disappears or fails). */
    @Test
    fun everyTabRendersForEveryCandidate() {
        var candidate by mutableStateOf(FictionalCandidates.candidateA)
        composeRule.setContent { CitoyenTheme { CandidateContent(uiState = readyState(candidate), onBack = {}) } }
        FictionalCandidates.all.forEach { current ->
            candidate = current
            CandidateSheetTab.entries.forEach { tab ->
                composeRule.onNodeWithText(context.getString(tab.label)).performScrollTo().performClick()
                composeRule.waitForIdle()
            }
            composeRule.onNodeWithText(
                context.getString(CandidateSheetTab.OVERVIEW.label),
            ).performScrollTo().performClick()
        }
        candidate = FictionalCandidates.candidateA
        composeRule.onNodeWithText(context.getString(CandidateSheetTab.PROGRAM.label)).performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(CoreUiR.string.core_ui_program_status_complete)).assertExists()
    }

    private fun readyState(candidate: Candidate) = CandidateUiState.Ready(
        candidate = candidate,
        dataInfo = FictionalCandidates.packInfo,
        mandates = FictionalCandidates.mandates.filter { it.candidateId == candidate.id },
        votes = VotesState(
            votes = FictionalCandidates.votes.filter { it.candidateId == candidate.id },
            totalCount = FictionalCandidates.votes.count { it.candidateId == candidate.id },
        ),
        program = FictionalCandidates.programsPack.program(candidate.id),
        themes = FictionalCandidates.themes,
        allVotes = FictionalCandidates.votes.filter { it.candidateId == candidate.id },
    )

    private val coverageItem = SemanticsMatcher("coverage item") { node ->
        node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.any { it.contains(" : ") }
    }
}
