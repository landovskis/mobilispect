package com.mobilispect.mobile.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass

/**
 * Main activity for the Mobilispect Android app.
 *
 * Calculates window size class for adaptive layouts following Material Design guidelines.
 */
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display for immersive experience
        enableEdgeToEdge()

        setContent {
            // Calculate window size class for adaptive layouts
            val windowSizeClass = calculateWindowSizeClass(this)

            App(windowSizeClass = windowSizeClass)
        }
    }
}
