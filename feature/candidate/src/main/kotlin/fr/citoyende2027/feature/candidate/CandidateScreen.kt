package fr.citoyende2027.feature.candidate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.citoyende2027.core.designsystem.R as DesignR
import fr.citoyende2027.core.designsystem.component.FacetTile
import fr.citoyende2027.core.designsystem.component.InitialsAvatar
import fr.citoyende2027.core.designsystem.icon.CitoyenIcons
import fr.citoyende2027.core.model.Candidate
import fr.citoyende2027.core.model.CoverageFacet
import fr.citoyende2027.core.model.Sponsorships
import fr.citoyende2027.core.model.VoteFilter
import fr.citoyende2027.core.ui.CandidateStatusText
import fr.citoyende2027.core.ui.DataDateBanner
import fr.citoyende2027.core.ui.R as CoreUiR
import fr.citoyende2027.core.ui.SourceButton
import fr.citoyende2027.core.ui.glyphShape
import fr.citoyende2027.core.ui.labelRes
import fr.citoyende2027.core.ui.toFrenchDate
import fr.citoyende2027.feature.candidate.tab.JourneyTab
import fr.citoyende2027.feature.candidate.tab.ProgramAndVotesTab
import fr.citoyende2027.feature.candidate.tab.ProgramTab
import fr.citoyende2027.feature.candidate.tab.SourcesTab

@Composable
internal fun CandidateScreen(
    onBack: () -> Unit,
    showBack: Boolean = true,
    viewModel: CandidateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CandidateContent(
        uiState = uiState,
        onBack = onBack,
        showBack = showBack,
        onVoteFilterChange = viewModel::onVoteFilterChange,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CandidateContent(
    uiState: CandidateUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    onVoteFilterChange: (VoteFilter) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = (uiState as? CandidateUiState.Ready)?.candidate?.displayName
                        ?: stringResource(R.string.feature_candidate_title),
                    modifier = Modifier.semantics { heading() },
                )
            },
            navigationIcon = {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(CitoyenIcons.Back), stringResource(R.string.feature_candidate_back))
                    }
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
                CandidateSheet(uiState, onVoteFilterChange)
            }
        }
    }
}

@Composable
private fun CandidateSheet(state: CandidateUiState.Ready, onVoteFilterChange: (VoteFilter) -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var journeyView by rememberSaveable { mutableStateOf(JourneyView.MANDATES) }
    val openTab = { tab: CandidateSheetTab -> selected = tab.ordinal }
    val openJourney = { view: JourneyView ->
        journeyView = view
        openTab(CandidateSheetTab.JOURNEY)
    }
    PrimaryScrollableTabRow(selectedTabIndex = selected, edgePadding = 8.dp) {
        CandidateSheetTab.entries.forEachIndexed { index, tab ->
            Tab(
                selected = index == selected,
                onClick = { selected = index },
                text = { Text(stringResource(tab.label)) },
            )
        }
    }
    when (CandidateSheetTab.entries[selected]) {
        CandidateSheetTab.OVERVIEW -> ScrollableTab {
            OverviewTab(
                candidate = state.candidate,
                sponsorships = state.sponsorships,
                onOpenProgram = { openTab(CandidateSheetTab.PROGRAM) },
                onOpenJourney = openJourney,
                onOpenSideBySide = { openTab(CandidateSheetTab.PROGRAM_AND_VOTES) },
            )
        }

        CandidateSheetTab.PROGRAM -> ProgramTab(state.program, state.themes)

        CandidateSheetTab.JOURNEY -> JourneyTab(journeyView, { journeyView = it }, state, onVoteFilterChange)

        CandidateSheetTab.PROGRAM_AND_VOTES -> ProgramAndVotesTab(state.program, state.allVotes, state.themes)

        CandidateSheetTab.SOURCES -> SourcesTab(state)
    }
}

@Composable
private fun ScrollableTab(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@Composable
private fun OverviewTab(
    candidate: Candidate,
    sponsorships: Sponsorships?,
    onOpenProgram: () -> Unit,
    onOpenJourney: (JourneyView) -> Unit,
    onOpenSideBySide: () -> Unit,
) {
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
    // Same four tiles, same order, for every candidate (template parity, AGENTS.md §10).
    val opens = mapOf<CoverageFacet, () -> Unit>(
        CoverageFacet.PROGRAM to onOpenProgram,
        CoverageFacet.MANDATES to { onOpenJourney(JourneyView.MANDATES) },
        CoverageFacet.VOTES to { onOpenJourney(JourneyView.VOTES) },
        CoverageFacet.SPEECHES to { onOpenJourney(JourneyView.SPEECHES) },
    )
    CoverageFacet.entries.chunked(2).forEach { pair ->
        Row(
            modifier = Modifier.height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pair.forEach { facet ->
                val level = candidate.coverage[facet]
                val facetLabel = stringResource(facet.labelRes)
                val levelLabel = stringResource(level.labelRes)
                FacetTile(
                    title = facetLabel,
                    stateLabel = levelLabel,
                    glyph = level.glyphShape,
                    onClick = opens.getValue(facet),
                    description = stringResource(CoreUiR.string.core_ui_coverage_item, facetLabel, levelLabel),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
    SideBySideTile(onOpenSideBySide)
    // Same section for every candidate (template parity, AGENTS.md §10), filled or not.
    SectionTitle(stringResource(R.string.feature_candidate_section_sponsorships))
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SponsorshipsSection(sponsorships)
        }
    }
}

@Composable
private fun SideBySideTile(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(CitoyenIcons.Compare), contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.feature_candidate_tab_program_and_votes),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.feature_candidate_side_by_side_tile),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(painterResource(CitoyenIcons.ChevronRight), contentDescription = null)
        }
    }
}

@Composable
private fun SponsorshipsSection(sponsorships: Sponsorships?) {
    if (sponsorships == null) {
        Text(
            stringResource(DesignR.string.core_designsystem_not_available),
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = pluralStringResource(
                R.plurals.feature_candidate_sponsorships_total,
                sponsorships.total,
                sponsorships.total,
                sponsorships.publishedOn.toFrenchDate(),
            ),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        SourceButton(sponsorships.source)
    }
    if (sponsorships.total > 0) {
        Text(
            text = pluralStringResource(
                R.plurals.feature_candidate_sponsorships_departments,
                sponsorships.departments,
                sponsorships.departments,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        sponsorships.byMandate.forEach { item ->
            Text(
                text = stringResource(R.string.feature_candidate_sponsorships_mandate, item.mandate, item.count),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
}
