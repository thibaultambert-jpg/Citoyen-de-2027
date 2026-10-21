package fr.citoyende2027.feature.candidates.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import fr.citoyende2027.feature.candidates.CandidatesScreen
import kotlinx.serialization.Serializable

@Serializable
data object CandidatesRoute

fun NavGraphBuilder.candidatesScreen() {
    composable<CandidatesRoute> {
        CandidatesScreen()
    }
}
