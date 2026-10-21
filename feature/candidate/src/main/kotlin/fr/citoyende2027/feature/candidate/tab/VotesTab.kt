package fr.citoyende2027.feature.candidate.tab

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import fr.citoyende2027.core.model.Vote
import fr.citoyende2027.core.model.VoteFilter
import fr.citoyende2027.core.model.VotePosition
import fr.citoyende2027.core.model.positionCategory
import fr.citoyende2027.core.ui.SourceButton
import fr.citoyende2027.core.ui.labelRes
import fr.citoyende2027.core.ui.toFrenchDate
import fr.citoyende2027.feature.candidate.R
import fr.citoyende2027.feature.candidate.VotesState

@Composable
internal fun VotesTab(state: VotesState, onFilterChange: (VoteFilter) -> Unit, modifier: Modifier = Modifier) {
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
        item { VoteFilters(state, onFilterChange) }
        item {
            Text(
                text = stringResource(R.string.feature_candidate_votes_count, state.votes.size, state.totalCount),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        items(state.votes, key = { it.id }) { vote ->
            VoteItem(vote)
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
private fun VoteFilters(state: VotesState, onFilterChange: (VoteFilter) -> Unit) {
    val filter = state.filter
    val all = stringResource(R.string.feature_candidate_filter_all)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterMenu(
            title = stringResource(R.string.feature_candidate_filter_body),
            selected = filter.body,
            options = state.options.bodies,
            label = { stringResource(it.labelRes) },
            allLabel = all,
            onSelect = { onFilterChange(filter.copy(body = it)) },
        )
        FilterMenu(
            title = stringResource(R.string.feature_candidate_filter_year),
            selected = filter.year,
            options = state.options.years,
            label = { it.toString() },
            allLabel = all,
            onSelect = { onFilterChange(filter.copy(year = it)) },
        )
        FilterMenu(
            title = stringResource(R.string.feature_candidate_filter_type),
            selected = filter.subject,
            options = state.options.subjects,
            label = { stringResource(it.labelRes) },
            allLabel = all,
            onSelect = { onFilterChange(filter.copy(subject = it)) },
        )
        FilterMenu(
            title = stringResource(R.string.feature_candidate_filter_position),
            selected = filter.position,
            options = state.options.positions,
            label = { positionLabel(it, null) },
            allLabel = all,
            onSelect = { onFilterChange(filter.copy(position = it)) },
        )
    }
}

@Composable
private fun <T : Any> FilterMenu(
    title: String,
    selected: T?,
    options: List<T>,
    label: @Composable (T) -> String,
    allLabel: String,
    onSelect: (T?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = selected?.let { label(it) } ?: allLabel
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { expanded = true },
            label = { Text("$title : $current") },
            modifier = Modifier.semantics { stateDescription = current },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(allLabel) }, onClick = {
                expanded = false
                onSelect(null)
            })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(label(option)) }, onClick = {
                    expanded = false
                    onSelect(option)
                })
            }
        }
    }
}

/** Same neutral style for every position: the position is carried by its text (AGENTS.md §10). */
@Composable
private fun VoteItem(vote: Vote) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "${vote.ballotDate.toFrenchDate()} · ${stringResource(vote.body.labelRes)}",
            style = MaterialTheme.typography.labelLarge,
        )
        Text(vote.ballotTitle, style = MaterialTheme.typography.bodyLarge)
        vote.ballotSubject?.let {
            Text(
                text = stringResource(R.string.feature_candidate_vote_subject, stringResource(it.labelRes)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        vote.ballotType?.let {
            Text(stringResource(R.string.feature_candidate_vote_type, it), style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = stringResource(R.string.feature_candidate_vote_position, positionLabel(vote.positionCategory, vote)),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { uriHandler.openUri(vote.ballotUrl) }) {
                Text(stringResource(R.string.feature_candidate_vote_official_link))
            }
            SourceButton(vote.source)
        }
    }
}

@Composable
private fun positionLabel(position: VotePosition, vote: Vote?): String =
    position.labelRes?.let { stringResource(it) } ?: vote?.position
        ?: stringResource(R.string.feature_candidate_position_other)
