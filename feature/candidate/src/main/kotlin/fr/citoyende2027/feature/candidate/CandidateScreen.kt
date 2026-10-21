package fr.citoyende2027.feature.candidate

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.citoyende2027.core.designsystem.component.PlaceholderScreen

@Composable
internal fun CandidateScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.feature_candidate_title), modifier = modifier)
}
