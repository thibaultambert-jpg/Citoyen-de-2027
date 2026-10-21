package fr.citoyende2027.feature.candidate.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import fr.citoyende2027.core.model.CandidateId
import fr.citoyende2027.feature.candidate.CandidateScreen
import kotlinx.serialization.Serializable

/** Candidate sheet; [candidateId] is a [CandidateId] value. */
@Serializable
data class CandidateRoute(val candidateId: String) {
    companion object {
        /** SavedStateHandle key of [candidateId] (type-safe routes use property names). */
        const val ID_ARG = "candidateId"
    }
}

fun NavController.navigateToCandidate(candidateId: CandidateId, navOptions: NavOptions? = null) =
    navigate(CandidateRoute(candidateId.value), navOptions)

fun NavGraphBuilder.candidateScreen(onBack: () -> Unit) {
    composable<CandidateRoute> {
        CandidateScreen(onBack = onBack)
    }
}
