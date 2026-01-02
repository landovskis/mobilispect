package com.mobilispect.mobile.android.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.mobilispect.mobile.android.navigation.TopLevelDestination

/**
 * Adaptive App Shell that responds to window size classes following Android guidelines.
 *
 * Window Size Classes:
 * - Compact (< 600dp): Bottom navigation bar (phones in portrait)
 * - Medium (600-840dp): Navigation rail (tablets in portrait, foldables)
 * - Expanded (> 840dp): Navigation rail or permanent nav drawer (tablets in landscape)
 *
 * Reference: https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes
 */
@Composable
fun AdaptiveAppShell(
    windowSizeClass: WindowSizeClass,
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    val useBottomNav = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact

    if (useBottomNav) {
        // Compact: Bottom Navigation
        CompactAppShell(
            navController = navController,
            content = content
        )
    } else {
        // Medium/Expanded: Navigation Rail
        ExpandedAppShell(
            navController = navController,
            content = content
        )
    }
}

/**
 * App shell for compact screens using bottom navigation.
 */
@Composable
private fun CompactAppShell(
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    Scaffold(
        bottomBar = {
            AppBottomNavigation(navController)
        }
    ) { paddingValues ->
        content()
    }
}

/**
 * App shell for medium/expanded screens using navigation rail.
 */
@Composable
private fun ExpandedAppShell(
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        AppNavigationRail(navController)
        content()
    }
}

/**
 * Bottom navigation bar for compact screens.
 */
@Composable
private fun AppBottomNavigation(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true

            NavigationBarItem(
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
                selected = selected,
                onClick = {
                    navController.navigate(destination.route) {
                        // Pop up to the start destination to avoid building up a back stack
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = true
                    }
                }
            )
        }
    }
}

/**
 * Navigation rail for medium and expanded screens.
 */
@Composable
private fun AppNavigationRail(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationRail {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true

            NavigationRailItem(
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
                selected = selected,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}
