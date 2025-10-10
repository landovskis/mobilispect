package com.mobilispect.mobile.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import com.mobilispect.mobile.android.R
import com.mobilispect.mobile.android.ui.theme.MobilispectTheme

@Composable
fun AppBar(screenTitle: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary)
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.systemBars
            )
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_foreground),
            contentDescription = "logo",
            alignment = Alignment.CenterStart,
            modifier = modifier
        )
        Text(
            color = MaterialTheme.colorScheme.onPrimary,
            text = screenTitle,
            textAlign = TextAlign.Center,
            fontSize = MaterialTheme.typography.titleLarge.fontSize,
            modifier = modifier
        )
    }
}

@Composable
@Preview
@PreviewLightDark
@PreviewScreenSizes
fun TopBarPreview() {
    MobilispectTheme {
        AppBar(screenTitle = "Title")
    }
}
