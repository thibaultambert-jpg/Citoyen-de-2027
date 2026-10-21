package fr.citoyende2027.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import fr.citoyende2027.feature.about.navigation.aboutScreen
import fr.citoyende2027.feature.ask.navigation.askScreen
import fr.citoyende2027.feature.candidate.navigation.candidateScreen
import fr.citoyende2027.feature.candidates.navigation.CandidatesRoute
import fr.citoyende2027.feature.candidates.navigation.candidatesScreen
import fr.citoyende2027.feature.compare.navigation.compareScreen
import fr.citoyende2027.feature.institutions.navigation.institutionsScreen
import fr.citoyende2027.navigation.TopLevelDestination

@Composable
fun CitoyenApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true
                val label = stringResource(destination.label)
                item(
                    selected = selected,
                    onClick = {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    // The label is always visible, so the icon carries no extra description.
                    icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                    label = { Text(label) },
                )
            }
        },
    ) {
        NavHost(navController = navController, startDestination = CandidatesRoute) {
            candidatesScreen()
            candidateScreen()
            compareScreen()
            askScreen()
            institutionsScreen()
            aboutScreen()
        }
    }
}
