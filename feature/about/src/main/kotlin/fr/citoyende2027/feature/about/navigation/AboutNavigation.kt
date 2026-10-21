package fr.citoyende2027.feature.about.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import fr.citoyende2027.feature.about.AboutScreen
import kotlinx.serialization.Serializable

@Serializable
data object AboutRoute

fun NavGraphBuilder.aboutScreen() {
    composable<AboutRoute> {
        AboutScreen()
    }
}
