package fr.citoyende2027.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.citoyende2027.R
import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.feature.candidate.navigation.CandidateRoute
import fr.citoyende2027.feature.candidate.navigation.candidateScreen
import fr.citoyende2027.feature.candidates.CandidatesPane
import kotlinx.serialization.Serializable

/** Width from which the list and the candidate sheet are shown side by side (Material "expanded"). */
private const val TWO_PANE_MIN_WIDTH_DP = 840

/**
 * Home of the app. On a phone it is the list alone, and a tap opens the sheet as a new screen. On a
 * tablet or an unfolded phone the sheet opens in a second pane next to the list.
 */
@Composable
fun CandidatesHome(onCandidateClick: (CandidateId) -> Unit, onInfoClick: () -> Unit) {
    if (LocalConfiguration.current.screenWidthDp < TWO_PANE_MIN_WIDTH_DP) {
        CandidatesPane(onCandidateClick = onCandidateClick, onInfoClick = onInfoClick)
        return
    }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    Row(modifier = Modifier.fillMaxSize()) {
        CandidatesPane(
            onCandidateClick = { selected = it.value },
            onInfoClick = onInfoClick,
            selectedId = selected?.let(::CandidateId),
            modifier = Modifier.weight(0.4f),
        )
        VerticalDivider(modifier = Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
        CandidateDetailPane(selected, modifier = Modifier.weight(0.6f))
    }
}

@Serializable
private data object NoCandidateSelected

/** Hosts the candidate sheet in its own small graph, so the sheet keeps its usual view model. */
@Composable
private fun CandidateDetailPane(candidateId: String?, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = NoCandidateSelected, modifier = modifier) {
        composable<NoCandidateSelected> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.candidates_select_one),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        candidateScreen(onBack = {}, showBack = false)
    }
    LaunchedEffect(candidateId) {
        if (candidateId != null) {
            navController.navigate(CandidateRoute(candidateId)) {
                popUpTo<NoCandidateSelected>()
                launchSingleTop = true
            }
        }
    }
}
