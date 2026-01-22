@file:Suppress(
    "FunctionNaming",
)

package com.mobilispect.android.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mobilispect.android.ui.previews.ThemePreviews
import com.mobilispect.mobile.android.ui.theme.MobilispectTheme

@Composable
fun LoadingCard(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier
            .size(100.dp)
            .testTag("loading"),
    )
}

@ThemePreviews
@Composable
fun PreviewLoadingCard() {
    MobilispectTheme {
        LoadingCard()
    }
}
