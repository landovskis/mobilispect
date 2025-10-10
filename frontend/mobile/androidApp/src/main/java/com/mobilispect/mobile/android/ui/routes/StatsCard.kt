package com.mobilispect.mobile.android.ui.routes

import android.R.attr.text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.mobilispect.android.ui.previews.LanguagePreviews
import com.mobilispect.android.ui.previews.ThemePreviews
import com.mobilispect.mobile.android.R
import com.mobilispect.mobile.android.ui.Card
import com.mobilispect.mobile.android.ui.theme.MobilispectTheme

@Composable
fun StatsCard(uiState: StatsCardUIState, onClick: (() -> Unit)? = null) {
    val title = stringResource(uiState.title)
    var modifier: Modifier = Modifier
    if (onClick != null) {
        modifier = modifier.clickable(onClick = onClick)
    }
    Card(modifier = modifier)
    {
        Column() {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Text(
                text = uiState.value.toString(),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.Companion.fillMaxWidth()
            )

            if (uiState.unit != null) {
                Text(
                    text = uiState.unit,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.Companion.fillMaxWidth()
                )
            }
        }
    }
}

@ThemePreviews
@LanguagePreviews
@Composable
fun PreviewStatsCard() {
    MobilispectTheme {
        StatsCard(
            StatsCardUIState(
                R.string.frequency_violations,
                120,
                unit = "m",
            )
        )
    }
}
