package fr.citoyende2027.feature.candidate.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.citoyende2027.core.model.CandidateProgram
import fr.citoyende2027.core.model.Theme
import fr.citoyende2027.core.model.ThemeFacts
import fr.citoyende2027.core.model.Vote
import fr.citoyende2027.core.model.themesWithFacts
import fr.citoyende2027.core.ui.DropdownFilterChip
import fr.citoyende2027.core.ui.ProgramExcerptCard
import fr.citoyende2027.core.ui.ProgramStatusText
import fr.citoyende2027.core.ui.VoteItem
import fr.citoyende2027.feature.candidate.R

/**
 * Programme excerpts and recorded votes on one theme, side by side (AGENTS.md §10): two columns of
 * facts, never a verdict, a score or a qualifier.
 */
@Composable
internal fun ProgramAndVotesTab(
    program: CandidateProgram,
    votes: List<Vote>,
    themes: List<Theme>,
    modifier: Modifier = Modifier,
) {
    val available = themesWithFacts(themes.comparable(), listOf(program), votes)
    var themeId by rememberSaveable { mutableStateOf<String?>(null) }
    val theme = available.firstOrNull { it.id.value == themeId } ?: available.firstOrNull()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card {
            Text(
                text = stringResource(R.string.feature_candidate_side_by_side_notice),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
            )
        }
        ProgramStatusText(program.status)
        if (theme == null) {
            Text(stringResource(R.string.feature_candidate_side_by_side_no_theme))
            return@Column
        }
        DropdownFilterChip(
            title = stringResource(R.string.feature_candidate_filter_theme),
            selected = theme,
            options = available,
            label = { it.label },
            onSelect = { selected -> themeId = selected?.id?.value },
            allLabel = null,
        )
        SideBySide(ThemeFacts.of(theme, program, votes), program, themes)
    }
}

@Composable
private fun SideBySide(facts: ThemeFacts, program: CandidateProgram, themes: List<Theme>) {
    val themeLabels = themes.associate { it.id to it.label }
    val programColumn: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeading(stringResource(R.string.feature_candidate_side_by_side_program))
            if (facts.excerpts.isEmpty()) Text(stringResource(R.string.feature_candidate_side_by_side_no_excerpt))
            facts.excerpts.forEach { ProgramExcerptCard(it, program.document(it.documentId), themeLabels) }
        }
    }
    val votesColumn: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeading(stringResource(R.string.feature_candidate_side_by_side_votes))
            if (facts.votes.isEmpty()) Text(stringResource(R.string.feature_candidate_side_by_side_no_vote))
            facts.votes.forEach { vote ->
                VoteItem(vote, themeLabels = themeLabels)
                HorizontalDivider()
            }
        }
    }
    // Two columns when there is room, otherwise one under the other (same order: programme, votes).
    BoxWithConstraints {
        if (maxWidth >= 600.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                programColumn(Modifier.weight(1f))
                votesColumn(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                programColumn(Modifier)
                votesColumn(Modifier)
            }
        }
    }
}
