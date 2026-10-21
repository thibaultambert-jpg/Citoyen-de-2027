package fr.citoyende2027.feature.candidate.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import fr.citoyende2027.feature.candidate.CandidateScreen
import kotlinx.serialization.Serializable

/** Candidate sheet; the id refers to [fr.citoyende2027.core.model.CandidateId]. */
@Serializable
data class CandidateRoute(val candidateId: String)

fun NavController.navigateToCandidate(candidateId: String, navOptions: NavOptions? = null) =
    navigate(CandidateRoute(candidateId), navOptions)

fun NavGraphBuilder.candidateScreen() {
    composable<CandidateRoute> {
        CandidateScreen()
    }
}
