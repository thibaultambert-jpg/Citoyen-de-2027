package fr.citoyende2027.feature.ask.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import fr.citoyende2027.feature.ask.AskScreen
import kotlinx.serialization.Serializable

@Serializable
data object AskRoute

fun NavGraphBuilder.askScreen(onInfoClick: () -> Unit) {
    composable<AskRoute> {
        AskScreen(onInfoClick = onInfoClick)
    }
}
