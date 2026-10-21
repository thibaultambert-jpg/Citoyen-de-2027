package fr.citoyende2027.feature.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.citoyende2027.core.designsystem.component.InitialsAvatar
import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.core.model.ThemeId
import fr.citoyende2027.core.ui.DataDateBanner
import fr.citoyende2027.core.ui.DropdownFilterChip
import fr.citoyende2027.core.ui.ProgramExcerptCard
import fr.citoyende2027.core.ui.ProgramStatusText
import fr.citoyende2027.core.ui.VoteItem

@Composable
internal fun CompareScreen(modifier: Modifier = Modifier, viewModel: CompareViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CompareContent(uiState, viewModel::toggleCandidate, viewModel::selectTheme, modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CompareContent(
    state: CompareUiState,
    onToggleCandidate: (CandidateId) -> Unit,
    onSelectTheme: (ThemeId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        DataDateBanner(state.dataInfo)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.feature_compare_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            if (state.candidates.isEmpty()) {
                Text(stringResource(R.string.feature_compare_no_data))
                return@Column
            }
            Card {
                Text(
                    text = stringResource(R.string.feature_compare_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Text(
                text = stringResource(
                    R.string.feature_compare_candidates,
                    CompareUiState.MIN_CANDIDATES,
                    CompareUiState.MAX_CANDIDATES,
                ),
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.candidates.forEach { candidate ->
                    val selected = candidate.id in state.selected
                    FilterChip(
                        selected = selected,
                        onClick = { onToggleCandidate(candidate.id) },
                        enabled = selected || state.canSelectMore,
                        label = { Text(candidate.displayName) },
                    )
                }
            }
            DropdownFilterChip(
                title = stringResource(R.string.feature_compare_theme),
                selected = state.theme,
                options = state.themes,
                label = { it.label },
                onSelect = { onSelectTheme(it?.id) },
                allLabel = stringResource(R.string.feature_compare_choose_theme),
            )
            if (state.columns.isEmpty()) {
                Text(stringResource(R.string.feature_compare_hint), style = MaterialTheme.typography.bodyMedium)
            }
            state.columns.forEach { CandidateColumnCard(it, state) }
        }
    }
}

/** Identical template for every candidate: name, programme status, excerpts, votes. */
@Composable
private fun CandidateColumnCard(column: CandidateColumn, state: CompareUiState) {
    val themeLabels = state.themes.associate { it.id to it.label }
    val facts = column.facts
    OutlinedCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                InitialsAvatar(column.candidate.initials)
                Text(
                    text = column.candidate.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }
            ProgramStatusText(facts.programStatus)
            SubHeading(stringResource(R.string.feature_compare_program))
            if (facts.excerpts.isEmpty()) Text(stringResource(R.string.feature_compare_no_excerpt))
            facts.excerpts.forEach { chunk ->
                ProgramExcerptCard(chunk, column.documents.firstOrNull { it.id == chunk.documentId }, themeLabels)
            }
            SubHeading(stringResource(R.string.feature_compare_votes))
            if (facts.votes.isEmpty()) Text(stringResource(R.string.feature_compare_no_vote))
            facts.votes.forEach { vote ->
                VoteItem(vote, themeLabels = themeLabels)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SubHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}
