package fr.citoyende2027.feature.candidates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
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
import fr.citoyende2027.core.ui.CoverageGlyphRow
import fr.citoyende2027.core.ui.DataDateBanner
import fr.citoyende2027.core.ui.InfoActionButton
import java.text.Normalizer

/**
 * List of candidates. [selectedId] marks the candidate shown in the detail pane on large screens;
 * it never changes the order or the look of the other rows.
 */
@Composable
fun CandidatesPane(
    onCandidateClick: (CandidateId) -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedId: CandidateId? = null,
    viewModel: CandidatesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CandidatesContent(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onAlphabeticalOrderSelected = viewModel::onAlphabeticalOrderSelected,
        onRandomOrderSelected = viewModel::onRandomOrderSelected,
        onCandidateClick = onCandidateClick,
        onInfoClick = onInfoClick,
        selectedId = selectedId,
        modifier = modifier,
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
    onInfoClick: () -> Unit = {},
    selectedId: CandidateId? = null,
) {
    // The large title collapses as the list scrolls.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
        LargeTopAppBar(
            title = {
                Text(stringResource(R.string.feature_candidates_title), modifier = Modifier.semantics { heading() })
            },
            actions = { InfoActionButton(onInfoClick) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            scrollBehavior = scrollBehavior,
        )
        when (uiState) {
            CandidatesUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is CandidatesUiState.Ready -> {
                if (uiState.totalCount == 0) {
                    DataDateBanner(uiState.dataInfo)
                    Text(
                        text = stringResource(R.string.feature_candidates_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    CandidatesList(
                        uiState = uiState,
                        selectedId = selectedId,
                        onQueryChange = onQueryChange,
                        onAlphabeticalOrderSelected = onAlphabeticalOrderSelected,
                        onRandomOrderSelected = onRandomOrderSelected,
                        onCandidateClick = onCandidateClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidatesList(
    uiState: CandidatesUiState.Ready,
    selectedId: CandidateId?,
    onQueryChange: (String) -> Unit,
    onAlphabeticalOrderSelected: () -> Unit,
    onRandomOrderSelected: () -> Unit,
    onCandidateClick: (CandidateId) -> Unit,
) {
    // Letter headers only make sense in alphabetical order; a random order is shown as one list.
    val groups = remember(uiState.candidates, uiState.order) {
        if (uiState.order == CandidateOrder.Alphabetical) uiState.candidates.groupBy { it.initial() } else null
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "data-date") { DataDateBanner(uiState.dataInfo) }
        item(key = "controls") {
            CandidatesControls(
                query = uiState.query,
                order = uiState.order,
                onQueryChange = onQueryChange,
                onAlphabeticalOrderSelected = onAlphabeticalOrderSelected,
                onRandomOrderSelected = onRandomOrderSelected,
            )
        }
        when {
            uiState.candidates.isEmpty() -> item(key = "no-match") {
                Text(
                    text = stringResource(R.string.feature_candidates_no_match),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }

            groups != null -> groups.forEach { (letter, members) ->
                item(key = "letter-$letter") { LetterHeader(letter) }
                items(members, key = { it.id.value }) { candidate ->
                    CandidateRow(candidate, candidate.id == selectedId) { onCandidateClick(candidate.id) }
                }
            }

            else -> items(uiState.candidates, key = { it.id.value }) { candidate ->
                CandidateRow(candidate, candidate.id == selectedId) { onCandidateClick(candidate.id) }
            }
        }
    }
}

/** First letter of the sort name, without accent, as shown in the group header. */
private fun Candidate.initial(): String = Normalizer.normalize(sortName.trim(), Normalizer.Form.NFD)
    .firstOrNull { it.isLetter() }
    ?.uppercaseChar()
    ?.toString()
    ?: "#"

@Composable
private fun LetterHeader(letter: String) {
    Text(
        text = letter,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
            .semantics { heading() },
    )
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
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.feature_candidates_search)) },
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
            shape = RoundedCornerShape(50),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            ),
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

/** Identical template for every candidate: avatar, name, status, coverage (AGENTS.md §2.1). */
@Composable
private fun CandidateRow(candidate: Candidate, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val muted = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            // Found by the device benchmarks (benchmarks/, resource id "candidate_row").
            .testTag("candidate_row")
            .semantics { role = Role.Button },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InitialsAvatar(candidate.initials)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = candidate.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                CandidateStatusText(candidate, style = MaterialTheme.typography.bodyMedium.copy(color = muted))
                CoverageGlyphRow(candidate.coverage, color = muted)
            }
            Icon(painterResource(CitoyenIcons.ChevronRight), contentDescription = null, tint = muted)
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
