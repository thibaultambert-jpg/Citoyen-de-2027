package fr.citoyende2027.feature.candidate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.citoyende2027.core.designsystem.R as DesignR
import fr.citoyende2027.core.designsystem.component.InitialsAvatar
import fr.citoyende2027.core.designsystem.icon.CitoyenIcons
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.ui.CandidateStatusText
import fr.citoyende2027.core.ui.CoverageBadge
import fr.citoyende2027.core.ui.DataDateBanner
import fr.citoyende2027.core.ui.SourceButton

@Composable
internal fun CandidateScreen(onBack: () -> Unit, viewModel: CandidateViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CandidateContent(uiState = uiState, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CandidateContent(uiState: CandidateUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = (uiState as? CandidateUiState.Ready)?.candidate?.displayName
                        ?: stringResource(R.string.feature_candidate_title),
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(CitoyenIcons.Back), stringResource(R.string.feature_candidate_back))
                }
            },
        )
        when (uiState) {
            CandidateUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            CandidateUiState.NotFound -> Text(
                text = stringResource(R.string.feature_candidate_not_found),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp),
            )

            is CandidateUiState.Ready -> {
                DataDateBanner(uiState.dataInfo)
                CandidateSheet(uiState.candidate)
            }
        }
    }
}

@Composable
private fun CandidateSheet(candidate: Candidate) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    PrimaryScrollableTabRow(selectedTabIndex = selected) {
        CandidateSheetTab.entries.forEachIndexed { index, tab ->
            Tab(
                selected = index == selected,
                onClick = { selected = index },
                text = { Text(stringResource(tab.label)) },
            )
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (CandidateSheetTab.entries[selected]) {
            CandidateSheetTab.OVERVIEW -> OverviewTab(candidate)

            // Delivered in later milestones (J3–J4).
            else -> Text(stringResource(DesignR.string.core_designsystem_coming_soon))
        }
    }
}

@Composable
private fun OverviewTab(candidate: Candidate) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        InitialsAvatar(candidate.initials, size = 64.dp)
        Text(candidate.displayName, style = MaterialTheme.typography.headlineSmall)
    }
    SectionTitle(stringResource(R.string.feature_candidate_section_status))
    Row(verticalAlignment = Alignment.CenterVertically) {
        CandidateStatusText(candidate, modifier = Modifier.weight(1f))
        SourceButton(candidate.statusSource)
    }
    SectionTitle(stringResource(R.string.feature_candidate_section_coverage))
    Text(
        text = stringResource(R.string.feature_candidate_coverage_explanation),
        style = MaterialTheme.typography.bodyMedium,
    )
    CoverageBadge(candidate.coverage)
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
}
