package com.mobilispect.mobile.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobilispect.android.ui.previews.FontScalePreviews
import com.mobilispect.android.ui.previews.ThemePreviews
import com.mobilispect.mobile.android.ui.theme.MobilispectTheme

@Composable
fun Card(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable () -> Unit = {}
) {
    androidx.compose.material3.OutlinedCard(
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(0.5f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    )
    {
        Column(modifier = modifier.padding(8.dp)) {
            if (title != null) {
                Text(text = title.uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }

            content()
        }
    }
}

@FontScalePreviews
@ThemePreviews
@Composable
fun PreviewCard() {
    MobilispectTheme {
        Card(title = "Test", subtitle = "Testing")
    }
}
