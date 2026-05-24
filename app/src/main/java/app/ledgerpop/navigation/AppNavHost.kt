package app.ledgerpop.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import app.ledgerpop.screens.settings.AccountManagementScreen
import app.ledgerpop.screens.settings.CategoryManagementScreen
import app.ledgerpop.screens.settings.PermissionsScreen
import app.ledgerpop.screens.settings.SettingsScreen
import app.ledgerpop.screens.settings.SmsAuditScreen

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = NavRoutes.HOME) {
        composable(NavRoutes.HOME) {
            // Home screen handled in MainActivity usually
        }

        composable(NavRoutes.SETTINGS) {
            SettingsScreen(
                onNavigateToPermissions = { navController.navigate("permissions") },
                onNavigateToSmsAudit = { navController.navigate("sms_audit") },
                onNavigateToCategories = { navController.navigate(NavRoutes.CATEGORIES) },
                onNavigateToAccounts = { navController.navigate(NavRoutes.ACCOUNTS) }
            )
        }

        composable("permissions") {
            PermissionsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.CATEGORIES) {
            CategoryManagementScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.ACCOUNTS) {
            AccountManagementScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("sms_audit") {
            SmsAuditScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
