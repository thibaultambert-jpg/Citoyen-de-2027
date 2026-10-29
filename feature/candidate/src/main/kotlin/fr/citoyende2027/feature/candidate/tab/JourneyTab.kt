package fr.citoyende2027.feature.candidate.tab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.citoyende2027.core.model.VoteFilter
import fr.citoyende2027.feature.candidate.CandidateUiState
import fr.citoyende2027.feature.candidate.JourneyView

/** Mandates, votes and speeches of a candidate, one view at a time under a segmented control. */
@Composable
internal fun JourneyTab(
    view: JourneyView,
    onViewChange: (JourneyView) -> Unit,
    state: CandidateUiState.Ready,
    onVoteFilterChange: (VoteFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            JourneyView.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = entry == view,
                    onClick = { onViewChange(entry) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = JourneyView.entries.size),
                ) { Text(stringResource(entry.label)) }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            when (view) {
                JourneyView.MANDATES -> MandatesTab(state.mandates)
                JourneyView.VOTES -> VotesTab(state.votes, state.themes, onVoteFilterChange)
                JourneyView.SPEECHES -> SpeechesTab(state.speeches)
            }
        }
    }
}
