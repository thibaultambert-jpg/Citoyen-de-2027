package fr.citoyende2027.feature.compare.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import fr.citoyende2027.feature.compare.CompareScreen
import kotlinx.serialization.Serializable

@Serializable
data object CompareRoute

fun NavGraphBuilder.compareScreen(onInfoClick: () -> Unit) {
    composable<CompareRoute> {
        CompareScreen(onInfoClick = onInfoClick)
    }
}
