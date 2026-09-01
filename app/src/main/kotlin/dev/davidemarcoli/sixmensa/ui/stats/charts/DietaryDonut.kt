package dev.davidemarcoli.sixmensa.ui.stats.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.davidemarcoli.sixmensa.R
import dev.davidemarcoli.sixmensa.core.DietaryType
import dev.davidemarcoli.sixmensa.domain.DietaryCount

/**
 * A three-slice donut. Hand-drawn rather than pulled from the chart library: at this size
 * it is a handful of arcs, and drawing it directly means the colours come straight from the
 * Material You scheme instead of being pinned to the webapp's fixed red/green hex values.
 */
@Composable
fun DietaryDonut(
    data: List<DietaryCount>,
    modifier: Modifier = Modifier,
) {
    val total = data.sumOf { it.count }
    val colors = dietaryColors()

    Column(modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(160.dp)) {
                if (total == 0) return@Canvas
                val stroke = size.minDimension * 0.22f
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                var startAngle = -90f
                data.forEach { entry ->
                    if (entry.count == 0) return@forEach
                    val sweep = 360f * entry.count / total
                    drawArc(
                        color = colors.getValue(entry.type),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                    )
                    startAngle += sweep
                }
            }
            Text(
                text = total.toString(),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            data.forEach { entry ->
                StatTile(
                    label = entry.type.label(),
                    count = entry.count,
                    share = entry.share(total),
                    color = colors.getValue(entry.type),
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, count: Int, share: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(10.dp)
                .padding(bottom = 2.dp),
        ) {
            Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        }
        Text(count.toString(), style = MaterialTheme.typography.titleMedium)
        Text(
            text = "%.0f%%".format(share * 100),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun dietaryColors(): Map<DietaryType, Color> = mapOf(
    DietaryType.MEAT to MaterialTheme.colorScheme.primary,
    DietaryType.VEGETARIAN to MaterialTheme.colorScheme.tertiary,
    DietaryType.VEGAN to MaterialTheme.colorScheme.secondary,
    DietaryType.UNKNOWN to MaterialTheme.colorScheme.outlineVariant,
)

@Composable
fun DietaryType.label(): String = when (this) {
    DietaryType.MEAT -> stringResource(R.string.meat)
    DietaryType.VEGETARIAN -> stringResource(R.string.vegetarian)
    DietaryType.VEGAN -> stringResource(R.string.vegan)
    DietaryType.UNKNOWN -> "?"
}
