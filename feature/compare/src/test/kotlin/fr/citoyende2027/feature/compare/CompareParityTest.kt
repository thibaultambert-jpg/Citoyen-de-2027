package fr.citoyende2027.feature.compare

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import fr.citoyende2027.core.designsystem.theme.CitoyenTheme
import fr.citoyende2027.core.model.ThemeFacts
import fr.citoyende2027.core.testing.FictionalCandidates
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Template parity (AGENTS.md §10): every compared candidate gets the same sections, in order. */
@RunWith(RobolectricTestRunner::class)
class CompareParityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everyColumnHasTheSameSections() {
        val theme = FictionalCandidates.theme1
        val columns = FictionalCandidates.all.map { candidate ->
            val program = FictionalCandidates.programsPack.program(candidate.id)
            val votes = FictionalCandidates.votes.filter { it.candidateId == candidate.id }
            CandidateColumn(candidate, ThemeFacts.of(theme, program, votes), program.documents)
        }
        val state = CompareUiState(
            candidates = FictionalCandidates.all,
            selected = FictionalCandidates.all.map { it.id }.toSet(),
            themes = listOf(FictionalCandidates.theme1, FictionalCandidates.theme2),
            theme = theme,
            columns = columns,
            dataInfo = FictionalCandidates.packInfo,
        )
        composeRule.setContent { CitoyenTheme { CompareContent(state, {}, {}) } }

        val headings = composeRule.onAllNodes(isHeading()).fetchSemanticsNodes().map { node ->
            node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString()
        }
        val sections = listOf(
            context.getString(R.string.feature_compare_program),
            context.getString(R.string.feature_compare_votes),
        )
        val expected = listOf(context.getString(R.string.feature_compare_title)) +
            FictionalCandidates.all.flatMap { listOf(it.displayName) + sections }
        assertEquals(expected, headings)
    }
}
