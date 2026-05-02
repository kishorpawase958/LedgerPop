package app.ledgerpop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeDefaults
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.screens.analytics.AnalyticsScreen
import app.ledgerpop.screens.home.HomeScreen
import app.ledgerpop.screens.settings.CategoryManagementScreen
import app.ledgerpop.screens.settings.PermissionsScreen
import app.ledgerpop.screens.settings.SettingsScreen
import app.ledgerpop.screens.settings.SmsAuditScreen
import app.ledgerpop.screens.transactions.TransactionsScreen
import app.ledgerpop.ui.theme.LedgerPopTheme
import app.ledgerpop.ui.viewmodel.SettingsViewModel

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Transactions : Screen("transactions")
    object Analytics : Screen("analytics")
    object Settings : Screen("settings")
    object SmsAudit : Screen("sms_audit")
    object Permissions : Screen("permissions")
    object Categories : Screen("categories")
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
            val db = LedgerPopDatabase.getInstance(this)
            val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(db, this))
            val uiState by settingsViewModel.uiState.collectAsState()

            LedgerPopTheme(appTheme = uiState.appTheme) {
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
    val hazeState = remember { HazeState() }

    val showBottomBar = currentRoute in bottomNavItems.map { it.screen.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Main Content Area that flows behind the floating navigation bar
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
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
                            onNavigateToSmsAudit = {
                                navController.navigate(Screen.SmsAudit.route)
                            },
                            onNavigateToPermissions = {
                                navController.navigate(Screen.Permissions.route)
                            },
                            onNavigateToCategories = {
                                navController.navigate(Screen.Categories.route)
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

                    composable(Screen.Categories.route) {
                        CategoryManagementScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }

            // Floating Navigation Bar Overlay
            AnimatedVisibility(
                visible = showBottomBar,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .hazeEffect(
                                state = hazeState,
                                style = HazeDefaults.style(
                                    backgroundColor = MaterialTheme.colorScheme.surface,
                                    blurRadius = 20.dp,
                                    tint = HazeDefaults.tint(MaterialTheme.colorScheme.surface.copy(alpha = 0.25f))
                                )
                            ),
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Transparent,
                        tonalElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            bottomNavItems.forEach { item ->
                                val selected = currentRoute == item.screen.route
                                val contentColor by animateColorAsState(
                                    targetValue = if (selected) MaterialTheme.colorScheme.primary 
                                                 else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    label = "nav_item_color"
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(64.dp)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            if (!selected) {
                                                navController.navigate(item.screen.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        // The Glass Plate (Nested Haze Effect)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .hazeEffect(
                                                    state = hazeState,
                                                    style = HazeDefaults.style(
                                                        backgroundColor = MaterialTheme.colorScheme.surface,
                                                        blurRadius = 15.dp,
                                                        tint = HazeDefaults.tint(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                                    )
                                                )
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                                                .border(
                                                    0.5.dp,
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                                    RoundedCornerShape(16.dp)
                                                )
                                        )
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.label,
                                            tint = contentColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text(
                                            text = item.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = contentColor,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
