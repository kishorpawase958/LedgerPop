package app.ledgerpop.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavItem(val label: String, val route: String, val icon: ImageVector)

val bottomNavItems = listOf(
    BottomNavItem("Home",         NavRoutes.HOME,         Icons.Rounded.Home),
    BottomNavItem("Transactions", NavRoutes.TRANSACTIONS, Icons.Rounded.List),
    BottomNavItem("Analytics",    NavRoutes.ANALYTICS,    Icons.Rounded.Analytics),
    BottomNavItem("Settings",     NavRoutes.SETTINGS,     Icons.Rounded.Settings),
)