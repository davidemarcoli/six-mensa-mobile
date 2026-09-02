package dev.davidemarcoli.sixmensa.ui.stats.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.Fill
import dev.davidemarcoli.sixmensa.domain.Counted
import dev.davidemarcoli.sixmensa.domain.PriceTrend

private const val CHART_HEIGHT_DP = 220

/** Monthly average internal price: one solid overall line plus one line per menu type. */
@Composable
fun PriceTrendChart(
    trend: PriceTrend,
    modifier: Modifier = Modifier,
) {
    if (trend.months.isEmpty()) {
        ChartPlaceholder(modifier)
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(trend) {
        modelProducer.runTransaction {
            lineSeries {
                // Gaps (months with no data) would break the x-alignment between series,
                // so carry the previous value forward instead of dropping the point.
                series(trend.overall.fillGaps())
                trend.byType.values.forEach { series(it.fillGaps()) }
            }
        }
    }

    val monthFormatter = CartesianValueFormatter { _, value, _ ->
        trend.months.getOrNull(value.toInt())?.removePrefix("20").orEmpty()
    }

    // The overall average is the primary line; per-type series recede into muted tones so
    // the chart still reads as part of the Material You scheme rather than Vico's defaults.
    val scheme = MaterialTheme.colorScheme
    val seriesColors = remember(scheme) {
        listOf(
            scheme.primary,
            scheme.tertiary,
            scheme.secondary,
            scheme.error,
            scheme.primaryContainer,
            scheme.tertiaryContainer,
        )
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    seriesColors.map { color ->
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(Fill(color)),
                        )
                    },
                ),
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = monthFormatter),
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT_DP.dp),
    )
}

/** Vertical columns, used for allergen frequency. */
@Composable
fun CountedColumnChart(
    data: List<Counted>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (data.isEmpty()) {
        ChartPlaceholder(modifier)
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(data) {
        modelProducer.runTransaction {
            columnSeries { series(data.map { it.count }) }
        }
    }

    val labelFormatter = CartesianValueFormatter { _, value, _ ->
        data.getOrNull(value.toInt())?.label?.take(10).orEmpty()
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(fill = Fill(color), thickness = 12.dp),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = labelFormatter),
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT_DP.dp),
    )
}

@Composable
private fun ChartPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT_DP.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "—",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Replaces nulls with the last known value (or the next one, for a leading gap). */
private fun List<Double?>.fillGaps(): List<Double> {
    if (isEmpty()) return emptyList()
    val firstKnown = firstOrNull { it != null } ?: 0.0
    var previous = firstKnown
    return map { value ->
        if (value != null) previous = value
        previous
    }
}
