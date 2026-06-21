package app.ledgerpop

import android.content.Intent
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
import androidx.compose.runtime.*
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.navArgument
import androidx.navigation.NavType
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.screens.analytics.AnalyticsScreen
import app.ledgerpop.screens.home.HomeScreen
import app.ledgerpop.screens.onboarding.OnboardingScreen
import app.ledgerpop.screens.settings.AccountManagementScreen
import app.ledgerpop.screens.settings.CategoryManagementScreen
import app.ledgerpop.screens.settings.PermissionsScreen
import app.ledgerpop.screens.settings.SettingsScreen
import app.ledgerpop.screens.settings.SmsAuditScreen
import app.ledgerpop.screens.transactions.TransactionsScreen
import app.ledgerpop.ui.theme.LedgerPopTheme
import app.ledgerpop.ui.viewmodel.SettingsViewModel

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Transactions : Screen("transactions?id={id}") {
        fun createRoute(id: Int? = null) = if (id != null) "transactions?id=$id" else "transactions"
    }
    object Analytics : Screen("analytics")
    object Settings : Screen("settings")
    object SmsAudit : Screen("sms_audit")
    object Permissions : Screen("permissions")
    object Categories : Screen("categories")
    object Accounts : Screen("accounts")
    object Onboarding : Screen("onboarding")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "Home", Icons.Rounded.Home),
    BottomNavItem(Screen.Transactions, "Transactions", Icons.Rounded.Receipt),
    BottomNavItem(Screen.Analytics, "Analytics", Icons.Rounded.Analytics),
    BottomNavItem(Screen.Settings, "Settings", Icons.Rounded.Settings)
)

fun isBottomNavItem(route: String?): Boolean {
    if (route == null) return false
    return bottomNavItems.any { item ->
        val baseRoute = item.screen.route.split("?").first()
        route.startsWith(baseRoute)
    }
}

class MainActivity : ComponentActivity() {
    private var pendingTransactionId by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        setContent {
            val db = LedgerPopDatabase.getInstance(this)
            val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(db, this))
            val uiState by settingsViewModel.uiState.collectAsState()

            LedgerPopTheme(appTheme = uiState.appTheme) {
                LedgerPopApp(settingsViewModel, pendingTransactionId) {
                    pendingTransactionId = null
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getIntExtra("transaction_id", -1) ?: -1
        if (id != -1) {
            pendingTransactionId = id
        }
    }
}

@Composable
fun LedgerPopApp(
    settingsViewModel: SettingsViewModel,
    pendingTransactionId: Int? = null,
    onTransactionHandled: () -> Unit = {}
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val hazeState = remember { HazeState() }

    LaunchedEffect(pendingTransactionId) {
        if (pendingTransactionId != null) {
            navController.navigate(Screen.Transactions.createRoute(pendingTransactionId)) {
                // Pop up to the start destination to avoid building up a large stack
                // and to ensure 'Home' is the base.
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                // Avoid multiple copies of the same destination when re-launching from notification
                launchSingleTop = true
                // Restore state if we were already on Transactions but with different arguments
                restoreState = true
            }
            onTransactionHandled()
        }
    }

    val showBottomBar = isBottomNavItem(currentRoute)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Main Content Area that flows behind the floating navigation bar
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = if (uiState.isFirstRun) Screen.Onboarding.route else Screen.Home.route
                ) {
                    composable(Screen.Onboarding.route) {
                        OnboardingScreen {
                            settingsViewModel.setFirstRunComplete()
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                            }
                        }
                    }

                    composable(Screen.Home.route) {
                        HomeScreen(
                            onNavigateToTransactions = {
                                navController.navigate(Screen.Transactions.createRoute()) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }

                    composable(
                        route = Screen.Transactions.route,
                        arguments = listOf(navArgument("id") {
                            type = NavType.IntType
                            defaultValue = -1
                        })
                    ) { backStackEntry ->
                        val transactionId = backStackEntry.arguments?.getInt("id") ?: -1
                        TransactionsScreen(
                            initialTransactionId = if (transactionId != -1) transactionId else null,
                            onClearInitialId = {
                                backStackEntry.arguments?.putInt("id", -1)
                            }
                        )
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
                            },
                            onNavigateToAccounts = {
                                navController.navigate(Screen.Accounts.route)
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

                    composable(Screen.Accounts.route) {
                        AccountManagementScreen(
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
                                                val route = if (item.screen == Screen.Transactions) Screen.Transactions.createRoute() else item.screen.route
                                                navController.navigate(route) {
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
