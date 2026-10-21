package fr.citoyende2027.feature.compare

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.citoyende2027.core.designsystem.component.PlaceholderScreen

@Composable
internal fun CompareScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.feature_compare_title), modifier = modifier)
}
