package com.mobilispect.mobile.android.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Top-level navigation destinations for the app shell.
 *
 * These destinations appear in the bottom navigation bar (compact screens)
 * or navigation rail (medium/expanded screens).
 */
enum class TopLevelDestination(
    val icon: ImageVector,
    val label: String,
    val route: String
) {
    AGENCIES(
        icon = Icons.Default.Home,
        label = "Agencies",
        route = "agencies_list"
    ),
    ROUTES(
        icon = Icons.Default.List,
        label = "Routes",
        route = "routes"
    ),
    VIOLATIONS(
        icon = Icons.Default.Warning,
        label = "Violations",
        route = "violations"
    )
}
