package app.ledgerpop

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.ledgerpop.screens.analytics.AnalyticsScreen
import app.ledgerpop.screens.home.HomeScreen
import app.ledgerpop.screens.settings.SettingsScreen
import app.ledgerpop.screens.settings.SmsAuditScreen
import app.ledgerpop.screens.settings.PermissionsScreen
import app.ledgerpop.screens.transactions.TransactionsScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route

    ) {
        composable(Screen.Home.route) {
            HomeScreen()
        }

        composable(Screen.Transactions.route) {
            TransactionsScreen()
        }

        composable(Screen.Analytics.route) {
            AnalyticsScreen()
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToAudit = {
                    navController.navigate(Screen.SmsAudit.route)
                },
                onNavigateToPermissions = {
                    navController.navigate(Screen.Permissions.route)
                }
            )
        }

        composable(Screen.Permissions.route) {
            PermissionsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SmsAudit.route) {
            SmsAuditScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}