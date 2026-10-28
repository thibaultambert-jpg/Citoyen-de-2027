package fr.citoyende2027.feature.candidates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.citoyende2027.core.designsystem.component.InitialsAvatar
import fr.citoyende2027.core.designsystem.icon.CitoyenIcons
import fr.citoyende2027.core.designsystem.theme.CitoyenTheme
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.core.model.CandidateOrder
import fr.citoyende2027.core.ui.CandidateStatusText
import fr.citoyende2027.core.ui.CoverageBadge
import fr.citoyende2027.core.ui.DataDateBanner

@Composable
internal fun CandidatesScreen(
    onCandidateClick: (CandidateId) -> Unit,
    viewModel: CandidatesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CandidatesContent(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onAlphabeticalOrderSelected = viewModel::onAlphabeticalOrderSelected,
        onRandomOrderSelected = viewModel::onRandomOrderSelected,
        onCandidateClick = onCandidateClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CandidatesContent(
    uiState: CandidatesUiState,
    onQueryChange: (String) -> Unit,
    onAlphabeticalOrderSelected: () -> Unit,
    onRandomOrderSelected: () -> Unit,
    onCandidateClick: (CandidateId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(stringResource(R.string.feature_candidates_title), modifier = Modifier.semantics { heading() })
            },
        )
        when (uiState) {
            CandidatesUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is CandidatesUiState.Ready -> {
                DataDateBanner(uiState.dataInfo)
                if (uiState.totalCount == 0) {
                    Text(
                        text = stringResource(R.string.feature_candidates_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    CandidatesControls(
                        query = uiState.query,
                        order = uiState.order,
                        onQueryChange = onQueryChange,
                        onAlphabeticalOrderSelected = onAlphabeticalOrderSelected,
                        onRandomOrderSelected = onRandomOrderSelected,
                    )
                    CandidatesList(uiState.candidates, onCandidateClick)
                }
            }
        }
    }
}

@Composable
private fun CandidatesControls(
    query: String,
    order: CandidateOrder,
    onQueryChange: (String) -> Unit,
    onAlphabeticalOrderSelected: () -> Unit,
    onRandomOrderSelected: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            label = { Text(stringResource(R.string.feature_candidates_search)) },
            leadingIcon = { Icon(painterResource(CitoyenIcons.Search), contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            painterResource(CitoyenIcons.Clear),
                            contentDescription = stringResource(R.string.feature_candidates_search_clear),
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = order == CandidateOrder.Alphabetical,
                onClick = onAlphabeticalOrderSelected,
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text(stringResource(R.string.feature_candidates_order_alphabetical)) }
            SegmentedButton(
                selected = order is CandidateOrder.Shuffled,
                onClick = onRandomOrderSelected,
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(stringResource(R.string.feature_candidates_order_random)) }
        }
    }
}

@Composable
private fun CandidatesList(candidates: List<Candidate>, onCandidateClick: (CandidateId) -> Unit) {
    if (candidates.isEmpty()) {
        Text(
            text = stringResource(R.string.feature_candidates_no_match),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    LazyColumn {
        items(candidates, key = { it.id.value }) { candidate ->
            CandidateRow(candidate, onClick = { onCandidateClick(candidate.id) })
            HorizontalDivider()
        }
    }
}

/** Identical template for every candidate: avatar, name, status, coverage (AGENTS.md §2.1). */
@Composable
private fun CandidateRow(candidate: Candidate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            // Found by the device benchmarks (benchmarks/, resource id "candidate_row").
            .testTag("candidate_row")
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        InitialsAvatar(candidate.initials)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = candidate.displayName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            CandidateStatusText(candidate, style = MaterialTheme.typography.bodyMedium)
            CoverageBadge(candidate.coverage)
        }
    }
}

@Preview
@Composable
private fun CandidatesScreenEmptyPreview() {
    CitoyenTheme {
        CandidatesContent(
            uiState = CandidatesUiState.Ready(null, CandidateOrder.Alphabetical, "", emptyList(), 0),
            onQueryChange = {},
            onAlphabeticalOrderSelected = {},
            onRandomOrderSelected = {},
            onCandidateClick = {},
        )
    }
}
