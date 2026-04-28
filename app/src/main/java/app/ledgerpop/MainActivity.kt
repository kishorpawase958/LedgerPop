package app.ledgerpop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.ledgerpop.screens.analytics.AnalyticsScreen
import app.ledgerpop.screens.home.HomeScreen
import app.ledgerpop.screens.settings.PermissionsScreen
import app.ledgerpop.screens.settings.SettingsScreen
import app.ledgerpop.screens.settings.SmsAuditScreen
import app.ledgerpop.screens.transactions.TransactionsScreen
import app.ledgerpop.ui.theme.LedgerPopTheme

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Transactions : Screen("transactions")
    object Analytics : Screen("analytics")
    object Settings : Screen("settings")
    object SmsAudit : Screen("sms_audit")
    object Permissions : Screen("permissions")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "Home", Icons.Rounded.Home),
    BottomNavItem(Screen.Transactions, "Transactions", Icons.Rounded.Receipt),
    BottomNavItem(Screen.Analytics, "Analytics", Icons.Rounded.Analytics),
    BottomNavItem(Screen.Settings, "Settings", Icons.Rounded.Settings)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LedgerPopTheme {
                LedgerPopApp()
            }
        }
    }
}

@Composable
fun LedgerPopApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomNavItems.map { it.screen.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.screen.route,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onNavigateToTransactions = {
                            navController.navigate(Screen.Transactions.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
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

                composable(Screen.SmsAudit.route) {
                    SmsAuditScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Permissions.route) {
                    PermissionsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}