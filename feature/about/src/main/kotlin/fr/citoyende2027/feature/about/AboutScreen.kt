package fr.citoyende2027.feature.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.citoyende2027.core.designsystem.component.PlaceholderScreen

@Composable
internal fun AboutScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.feature_about_title), modifier = modifier)
}
