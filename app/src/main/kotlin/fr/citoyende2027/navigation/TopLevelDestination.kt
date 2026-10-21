package fr.citoyende2027.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import fr.citoyende2027.R
import fr.citoyende2027.core.designsystem.icon.CitoyenIcons
import fr.citoyende2027.feature.about.navigation.AboutRoute
import fr.citoyende2027.feature.ask.navigation.AskRoute
import fr.citoyende2027.feature.candidates.navigation.CandidatesRoute
import fr.citoyende2027.feature.compare.navigation.CompareRoute
import fr.citoyende2027.feature.institutions.navigation.InstitutionsRoute
import kotlin.reflect.KClass

/** Top-level destinations shown in the adaptive navigation bar/rail (AGENTS.md §11). */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
) {
    CANDIDATES(CandidatesRoute, CandidatesRoute::class, CitoyenIcons.Candidates, R.string.nav_candidates),
    COMPARE(CompareRoute, CompareRoute::class, CitoyenIcons.Compare, R.string.nav_compare),
    ASK(AskRoute, AskRoute::class, CitoyenIcons.Ask, R.string.nav_ask),
    INSTITUTIONS(InstitutionsRoute, InstitutionsRoute::class, CitoyenIcons.Institutions, R.string.nav_institutions),
    ABOUT(AboutRoute, AboutRoute::class, CitoyenIcons.About, R.string.nav_about),
}
