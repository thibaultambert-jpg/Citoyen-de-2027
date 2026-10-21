package fr.citoyende2027.feature.candidate.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.citoyende2027.core.model.SourceRef
import fr.citoyende2027.core.ui.SourceButton
import fr.citoyende2027.core.ui.toFrenchDate
import fr.citoyende2027.feature.candidate.CandidateUiState
import fr.citoyende2027.feature.candidate.R

/** Every source behind the facts of this sheet, plus how they are used (AGENTS.md §2.3). */
@Composable
internal fun SourcesTab(state: CandidateUiState.Ready, modifier: Modifier = Modifier) {
    val sources = (
        listOf(state.candidate.statusSource) + state.mandates.map { it.source } + state.votes.allSources +
            state.speeches.map { it.source }
        ).distinct().sortedBy { it.name }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.feature_candidate_method_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        item {
            Text(stringResource(R.string.feature_candidate_method_text), style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Text(
                text = stringResource(R.string.feature_candidate_sources_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        items(sources, key = { it.url + it.retrievedOn }) { SourceRow(it) }
    }
}

@Composable
private fun SourceRow(source: SourceRef) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(source.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(
                    R.string.feature_candidate_source_line,
                    source.retrievedOn.toFrenchDate(),
                    source.license,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        SourceButton(source)
    }
}
