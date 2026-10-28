package fr.citoyende2027.feature.institutions.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import fr.citoyende2027.feature.institutions.InstitutionsScreen
import fr.citoyende2027.feature.institutions.text.LegalTextScreen
import kotlinx.serialization.Serializable

@Serializable
data object InstitutionsRoute

/** A legal text, opened at [articleId] when given (search result). */
@Serializable
data class LegalTextRoute(val textId: String, val articleId: String? = null) {
    companion object {
        /** SavedStateHandle keys (type-safe routes use property names). */
        const val TEXT_ARG = "textId"
        const val ARTICLE_ARG = "articleId"
    }
}

fun NavController.navigateToLegalText(textId: String, articleId: String?) = navigate(LegalTextRoute(textId, articleId))

fun NavGraphBuilder.institutionsScreen(onOpenText: (textId: String, articleId: String?) -> Unit, onBack: () -> Unit) {
    composable<InstitutionsRoute> {
        InstitutionsScreen(onOpenText = onOpenText)
    }
    composable<LegalTextRoute> {
        LegalTextScreen(onBack = onBack)
    }
}
