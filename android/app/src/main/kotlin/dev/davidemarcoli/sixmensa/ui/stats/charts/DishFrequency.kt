package dev.davidemarcoli.sixmensa.ui.stats.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.davidemarcoli.sixmensa.domain.Counted

/**
 * Chart/Table toggle, ported from the webapp.
 *
 * The "chart" is a list of proportional bars rather than a real bar chart: dish names are
 * long, and on a phone rotated axis labels are unreadable. Same information, legible.
 */
@Composable
fun DishFrequency(
    dishes: List<Counted>,
    modifier: Modifier = Modifier,
    chartLimit: Int = 20,
    tableLimit: Int = 50,
) {
    var showChart by remember { mutableStateOf(true) }

    Column(modifier.fillMaxWidth()) {
        SingleChoiceSegmentedButtonRow(Modifier.padding(bottom = 12.dp)) {
            SegmentedButton(
                selected = showChart,
                onClick = { showChart = true },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Chart") }
            SegmentedButton(
                selected = !showChart,
                onClick = { showChart = false },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Table") }
        }

        if (dishes.isEmpty()) {
            Text(
                text = "—",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        val max = dishes.first().count.coerceAtLeast(1)

        if (showChart) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                dishes.take(chartLimit).forEach { dish ->
                    DishBar(dish = dish, fraction = dish.count.toFloat() / max)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                dishes.take(tableLimit).forEachIndexed { index, dish ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(32.dp),
                        )
                        Text(
                            text = dish.label,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = dish.count.toString(),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DishBar(dish: Counted, fraction: Float) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = dish.label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = dish.count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.shapes.extraSmall,
                ),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.shapes.extraSmall,
                    ),
            )
        }
    }
}
