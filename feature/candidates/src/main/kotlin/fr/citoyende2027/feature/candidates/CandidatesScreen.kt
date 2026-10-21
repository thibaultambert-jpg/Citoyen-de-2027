package fr.citoyende2027.feature.candidates

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.citoyende2027.core.designsystem.component.PlaceholderScreen

@Composable
internal fun CandidatesScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.feature_candidates_title), modifier = modifier)
}
