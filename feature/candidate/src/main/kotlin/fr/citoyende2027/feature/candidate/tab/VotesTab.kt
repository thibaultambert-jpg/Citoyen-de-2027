package fr.citoyende2027.feature.candidate.tab

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.citoyende2027.core.model.Theme
import fr.citoyende2027.core.model.VoteFilter
import fr.citoyende2027.core.ui.DropdownFilterChip
import fr.citoyende2027.core.ui.VoteItem
import fr.citoyende2027.core.ui.labelRes
import fr.citoyende2027.core.ui.positionLabel
import fr.citoyende2027.feature.candidate.R
import fr.citoyende2027.feature.candidate.VotesState

@Composable
internal fun VotesTab(
    state: VotesState,
    themes: List<Theme>,
    onFilterChange: (VoteFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val themeLabels = themes.associate { it.id to it.label }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { VotesLimitsNotice() }
        if (state.totalCount == 0) {
            item { Text(stringResource(R.string.feature_candidate_votes_none)) }
            return@LazyColumn
        }
        item { VoteFilters(state, themes, onFilterChange) }
        item {
            Text(
                text = stringResource(R.string.feature_candidate_votes_count, state.votes.size, state.totalCount),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        items(state.votes, key = { it.id }) { vote ->
            VoteItem(vote, themeLabels = themeLabels)
            HorizontalDivider()
        }
    }
}

/** Limits of the data, always visible (AGENTS.md §7.3, §10). */
@Composable
private fun VotesLimitsNotice() {
    Card {
        Text(
            text = stringResource(R.string.feature_candidate_votes_limits),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun VoteFilters(state: VotesState, themes: List<Theme>, onFilterChange: (VoteFilter) -> Unit) {
    val filter = state.filter
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DropdownFilterChip(
            title = stringResource(R.string.feature_candidate_filter_body),
            selected = filter.body,
            options = state.options.bodies,
            label = { stringResource(it.labelRes) },
            onSelect = { onFilterChange(filter.copy(body = it)) },
        )
        DropdownFilterChip(
            title = stringResource(R.string.feature_candidate_filter_year),
            selected = filter.year,
            options = state.options.years,
            label = { it.toString() },
            onSelect = { onFilterChange(filter.copy(year = it)) },
        )
        DropdownFilterChip(
            title = stringResource(R.string.feature_candidate_filter_theme),
            selected = themes.firstOrNull { it.id == filter.theme },
            options = themes.filter { it.id in state.options.themes },
            label = { it.label },
            onSelect = { onFilterChange(filter.copy(theme = it?.id)) },
        )
        DropdownFilterChip(
            title = stringResource(R.string.feature_candidate_filter_type),
            selected = filter.subject,
            options = state.options.subjects,
            label = { stringResource(it.labelRes) },
            onSelect = { onFilterChange(filter.copy(subject = it)) },
        )
        DropdownFilterChip(
            title = stringResource(R.string.feature_candidate_filter_position),
            selected = filter.position,
            options = state.options.positions,
            label = { positionLabel(it) },
            onSelect = { onFilterChange(filter.copy(position = it)) },
        )
    }
}
