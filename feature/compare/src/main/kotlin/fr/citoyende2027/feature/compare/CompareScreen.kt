package fr.citoyende2027.feature.compare

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
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

/** Width from which the candidate columns sit side by side instead of scrolling sideways. */
private const val SIDE_BY_SIDE_MIN_WIDTH_DP = 600

/** Width of a candidate column when the columns scroll sideways. */
private val ColumnWidth = 300.dp

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun CompareContent(
    state: CompareUiState,
    onToggleCandidate: (CandidateId) -> Unit,
    onSelectTheme: (ThemeId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
        LargeTopAppBar(
            title = {
                Text(stringResource(R.string.feature_compare_title), modifier = Modifier.semantics { heading() })
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            scrollBehavior = scrollBehavior,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DataDateBanner(state.dataInfo)
            if (state.candidates.isEmpty()) {
                Text(stringResource(R.string.feature_compare_no_data), modifier = Modifier.padding(horizontal = 16.dp))
                return@Column
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
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
            }
            CandidateColumns(state)
        }
    }
}

/**
 * One column per candidate, same template and same order (AGENTS.md §2.1). Side by side on wide
 * screens; on a phone the columns scroll sideways.
 */
@Composable
private fun CandidateColumns(state: CompareUiState) {
    if (state.columns.isEmpty()) return
    if (LocalConfiguration.current.screenWidthDp >= SIDE_BY_SIDE_MIN_WIDTH_DP) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.columns.forEach { CandidateColumnCard(it, state, Modifier.weight(1f)) }
        }
    } else {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.columns.forEach { CandidateColumnCard(it, state, Modifier.width(ColumnWidth)) }
        }
    }
}

/** Identical template for every candidate: name, programme status, excerpts, votes. */
@Composable
private fun CandidateColumnCard(column: CandidateColumn, state: CompareUiState, modifier: Modifier = Modifier) {
    val themeLabels = state.themes.associate { it.id to it.label }
    val facts = column.facts
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                InitialsAvatar(column.candidate.initials)
                Text(
                    text = column.candidate.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }
            SubHeading(stringResource(R.string.feature_compare_program))
            ProgramStatusText(facts.programStatus)
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
