package com.mobilispect.mobile.android.ui

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.mobilispect.mobile.android.navigation.MobilispectNavHost
import com.mobilispect.mobile.android.ui.theme.MobilispectTheme

/**
 * Root composable for the Mobilispect Android app.
 *
 * Implements adaptive layouts using window size classes following Material Design guidelines.
 *
 * @param windowSizeClass The current window size class for responsive layout decisions
 */
@Composable
fun App(windowSizeClass: WindowSizeClass) {
    MobilispectTheme {
        val navController = rememberNavController()

        AdaptiveAppShell(
            windowSizeClass = windowSizeClass,
            navController = navController
        ) {
            MobilispectNavHost(navController)
        }
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
@Preview
fun AppPreview() {
    val configuration = LocalConfiguration.current
    val windowSizeClass = WindowSizeClass.calculateFromSize(
        DpSize(configuration.screenWidthDp.dp, configuration.screenHeightDp.dp)
    )

    MobilispectTheme {
        App(windowSizeClass = windowSizeClass)
    }
}
