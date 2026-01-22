@file:Suppress("MagicNumber")

package com.mobilispect.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobilispect.android.ui.previews.ThemePreviews
import com.mobilispect.mobile.android.ui.theme.MobilispectTheme

@Composable
@Suppress("LongParameterList")
fun <T> RankedItem(
    index: Int,
    rankedValues: Collection<Number>,
    rankedUnit: String,
    item: T,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier, T) -> Unit
) {
    Row(
        modifier = modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Rank(index = index, modifier = Modifier.fillMaxWidth(0.05f))
        content(Modifier.fillMaxWidth(0.45f), item)
        RankedValues(
            values = rankedValues,
            unit = rankedUnit,
            modifier = Modifier.fillMaxWidth(1f)
        )
    }
}

@Composable
private fun Rank(index: Int, modifier: Modifier = Modifier) {
    Text(text = "${index + 1}", style = MaterialTheme.typography.displaySmall, modifier = modifier)
}

@Composable
private fun RankedValues(
    values: Collection<Number>,
    unit: String,
    modifier: Modifier = Modifier
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        val text = if (values.size == 2) {
            val first = values.first()
            val second = values.last()
            if (first != second) {
                "$first - $second"
            } else {
                first.toString()
            }
        } else if (values.isNotEmpty()) {
            values.first().toString()
        } else {
            ""
        }
        Text(
            text = text, modifier = Modifier
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.onSecondary),
                    shape = MaterialTheme.shapes.medium
                )
                .padding(5.dp), style = MaterialTheme.typography.displaySmall
        )
        Text(
            text = unit.uppercase(), style = MaterialTheme.typography.labelLarge
        )
    }
}

@ThemePreviews
@Composable
fun PreviewRankedItem() {
    MobilispectTheme {
        RankedItem(
            index = 1,
            rankedValues = listOf(5, 10),
            rankedUnit = "Minutes",
            item = ""
        ) { modifier, _: Any ->
            Text(text = "Main Street", modifier = modifier)
        }
    }
}
