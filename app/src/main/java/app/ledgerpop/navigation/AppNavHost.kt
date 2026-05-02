package app.ledgerpop.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import app.ledgerpop.screens.settings.CategoryManagementScreen
import app.ledgerpop.screens.settings.PermissionsScreen
import app.ledgerpop.screens.settings.SettingsScreen
import app.ledgerpop.screens.settings.SmsAuditScreen

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = "home") {
        composable("home") {
            // Home screen
        }

        composable("settings") {
            SettingsScreen(
                onNavigateToPermissions = { navController.navigate("permissions") },
                onNavigateToSmsAudit = { navController.navigate("sms_audit") },
                onNavigateToCategories = { navController.navigate("categories") }
            )
        }

        composable("permissions") {
            PermissionsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("categories") {
            CategoryManagementScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // SMS Audit route (reuses existing screen)
        composable("sms_audit") {
            SmsAuditScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}