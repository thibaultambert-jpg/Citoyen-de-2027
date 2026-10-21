package fr.citoyende2027.feature.candidate

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import fr.citoyende2027.core.designsystem.R as DesignR
import fr.citoyende2027.core.designsystem.theme.CitoyenTheme
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.testing.FictionalCandidates
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
                CandidateContent(
                    uiState = CandidateUiState.Ready(candidate, FictionalCandidates.packInfo),
                    onBack = {},
                )
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

    private val coverageItem = SemanticsMatcher("coverage item") { node ->
        node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.any { it.contains(" : ") }
    }
}
