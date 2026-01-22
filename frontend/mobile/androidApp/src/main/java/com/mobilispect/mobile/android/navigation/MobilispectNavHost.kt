@file:Suppress("FunctionNaming")

package com.mobilispect.mobile.android.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.mobilispect.mobile.android.ui.agencies.AgenciesList
import com.mobilispect.mobile.android.ui.agencies.agenciesGraph
import com.mobilispect.mobile.android.ui.frequency_violation.FrequencyViolationList
import com.mobilispect.mobile.android.ui.frequency_violation.frequencyViolationGraph
import com.mobilispect.mobile.android.ui.routes.RoutesList
import com.mobilispect.mobile.android.ui.routes.routesGraph

@Composable
fun MobilispectNavHost(
    navController: NavHostController,
    startDestination: AgenciesList = AgenciesList
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        agenciesGraph(navigateToRoutes = {
            navController.navigate(RoutesList(it))
        })
        routesGraph(navigateToFrequencyViolations = {
            navController.navigate(FrequencyViolationList(it))
        })
        frequencyViolationGraph()
    }
}
