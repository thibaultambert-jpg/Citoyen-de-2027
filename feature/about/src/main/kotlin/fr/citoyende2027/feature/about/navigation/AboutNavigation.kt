package fr.citoyende2027.feature.about.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import fr.citoyende2027.feature.about.AboutPageScreen
import fr.citoyende2027.feature.about.AboutScreen
import fr.citoyende2027.feature.about.InfoPage
import kotlinx.serialization.Serializable

@Serializable
data object AboutRoute

/** One page of "Infos" ([InfoPage] name). */
@Serializable
data class AboutPageRoute(val page: String)

fun NavController.navigateToInfoPage(page: InfoPage) = navigate(AboutPageRoute(page.name))

fun NavGraphBuilder.aboutScreen(onOpenPage: (InfoPage) -> Unit, onBack: () -> Unit) {
    composable<AboutRoute> {
        AboutScreen(onOpenPage = onOpenPage, onBack = onBack)
    }
    composable<AboutPageRoute> { entry ->
        AboutPageScreen(page = InfoPage.valueOf(entry.toRoute<AboutPageRoute>().page), onBack = onBack)
    }
}
