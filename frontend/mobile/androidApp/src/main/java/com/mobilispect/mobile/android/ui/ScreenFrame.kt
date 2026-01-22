@file:Suppress("FunctionNaming")
@file:OptIn(ExperimentalMaterial3Api::class)

package com.mobilispect.mobile.android.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.mobilispect.android.ui.previews.FontScalePreviews
import com.mobilispect.android.ui.previews.ThemePreviews
import com.mobilispect.mobile.android.ui.theme.MobilispectTheme

@Composable
fun ScreenFrame(
    screenTitle: String,
    modifier: Modifier = Modifier,
    function: @Composable () -> Unit
) {
    Scaffold(topBar = { AppBar(screenTitle, Modifier.testTag("title")) }) { padding ->
        Surface(modifier = modifier.padding(padding), color = MaterialTheme.colorScheme.surface) {
            function()
        }
    }
}

@FontScalePreviews
@ThemePreviews
@Composable
fun PreviewScreenFrame() {
    MobilispectTheme {
        ScreenFrame(screenTitle = "Title") { }
    }
}
