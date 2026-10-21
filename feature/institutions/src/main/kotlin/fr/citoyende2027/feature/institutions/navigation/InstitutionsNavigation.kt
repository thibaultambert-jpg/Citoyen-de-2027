package fr.citoyende2027.feature.institutions.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import fr.citoyende2027.feature.institutions.InstitutionsScreen
import kotlinx.serialization.Serializable

@Serializable
data object InstitutionsRoute

fun NavGraphBuilder.institutionsScreen() {
    composable<InstitutionsRoute> {
        InstitutionsScreen()
    }
}
