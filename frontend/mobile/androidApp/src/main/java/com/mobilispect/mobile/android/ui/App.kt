package com.mobilispect.mobile.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.mobilispect.mobile.android.navigation.MobilispectNavHost
import com.mobilispect.mobile.android.ui.theme.MobilispectTheme

@Composable
fun App() {
    MobilispectTheme {
        val navController = rememberNavController()
        MobilispectNavHost(navController)
    }
}

@Composable
@Preview
fun AppPreview() {
    MobilispectTheme {
        App()
    }
}
