package com.eltonkola.appdepo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eltonkola.appdepo.ui.screens.AddAppScreen
import com.eltonkola.appdepo.ui.screens.AppDetailsScreen
import com.eltonkola.appdepo.ui.screens.HomeScreen

object AppDestinations {
    const val HOME_ROUTE = "home"
    const val ADD_APP_ROUTE = "add_app"
    const val APP_DETAILS_ROUTE_PREFIX = "app_details"
    const val APP_ID_ARG = "appId"
    fun appDetailsRoute(appId: Long) = "$APP_DETAILS_ROUTE_PREFIX/$appId"
}

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = AppDestinations.HOME_ROUTE) {
        composable(AppDestinations.HOME_ROUTE) {
            HomeScreen(
                viewModel = hiltViewModel(it),
                onNavigateToAddApp = { navController.navigate(AppDestinations.ADD_APP_ROUTE) },
                onNavigateToDetails = { appId ->
                    navController.navigate(AppDestinations.appDetailsRoute(appId))
                }
            )
        }
        composable(AppDestinations.ADD_APP_ROUTE) {
            AddAppScreen(
                viewModel = hiltViewModel(it),
                onNavigateBack = { navController.popBackStack() },
                onAppAdded = {
                    navController.popBackStack(AppDestinations.HOME_ROUTE, inclusive = false)
                }
            )
        }
        composable(
            route = "${AppDestinations.APP_DETAILS_ROUTE_PREFIX}/{${AppDestinations.APP_ID_ARG}}",
            arguments = listOf(navArgument(AppDestinations.APP_ID_ARG) {
                type = NavType.LongType
            })
        ) { backStackEntry ->
            val appId = backStackEntry.arguments?.getLong(AppDestinations.APP_ID_ARG)
            if (appId != null) {
                AppDetailsScreen(
                    appId = appId,
                    viewModel = hiltViewModel(backStackEntry),
                    onNavigateBack = { navController.popBackStack() }
                )
            } else {
                navController.popBackStack()
            }
        }
    }
}

// Helper to share ViewModel instances scoped to a navigation graph route
@Composable
inline fun <reified T : ViewModel> NavBackStackEntry.sharedViewModel(navController: NavController): T {
    val navGraphRoute = destination.parent?.route ?: return hiltViewModel()
    val parentEntry = remember(this) {
        navController.getBackStackEntry(navGraphRoute)
    }
    return hiltViewModel(parentEntry)
}