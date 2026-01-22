@file:Suppress("FunctionNaming")

package com.mobilispect.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobilispect.android.ui.previews.ThemePreviews
import com.mobilispect.mobile.android.ui.theme.MobilispectTheme

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (RowScope.() -> Unit)
) {
    androidx.compose.material3.OutlinedButton(
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSecondary),
        onClick = onClick,
        content = content,
        modifier = modifier
    )
}

@Composable
@ThemePreviews
fun PreviewOutlinedButton() {
    MobilispectTheme {
        OutlinedButton(onClick = {  }) {
            Text("Test")
        }
    }
}
