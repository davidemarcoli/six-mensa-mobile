package dev.davidemarcoli.sixmensa.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.davidemarcoli.sixmensa.R
import dev.davidemarcoli.sixmensa.core.DietaryType
import dev.davidemarcoli.sixmensa.core.Restaurant
import dev.davidemarcoli.sixmensa.ui.components.ErrorBanner
import dev.davidemarcoli.sixmensa.ui.stats.charts.CountedColumnChart
import dev.davidemarcoli.sixmensa.ui.stats.charts.DietaryDonut
import dev.davidemarcoli.sixmensa.ui.stats.charts.DishFrequency
import dev.davidemarcoli.sixmensa.ui.stats.charts.PriceTrendChart
import dev.davidemarcoli.sixmensa.ui.stats.charts.label

@Composable
fun StatsScreen(
    state: StatsUiState,
    onSearch: (String) -> Unit,
    onRestaurant: (Restaurant?) -> Unit,
    onMenuType: (String?) -> Unit,
    onDietary: (DietaryType?) -> Unit,
    onAllTime: () -> Unit,
    onResetRange: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading && state.totalCount == 0) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.error?.let { error ->
            item { ErrorBanner(error = error, onRetry = onRetry) }
        }

        item {
            Text(
                text = "${state.filteredCount} / ${state.totalCount}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            FilterBar(
                state = state,
                onSearch = onSearch,
                onRestaurant = onRestaurant,
                onMenuType = onMenuType,
                onDietary = onDietary,
                onAllTime = onAllTime,
                onResetRange = onResetRange,
            )
        }

        item {
            ChartCard(stringResource(R.string.stats_price_trend)) {
                PriceTrendChart(state.priceTrend)
            }
        }

        item {
            ChartCard(stringResource(R.string.stats_dietary)) {
                DietaryDonut(state.dietary)
            }
        }

        item {
            ChartCard(stringResource(R.string.stats_dishes)) {
                DishFrequency(state.dishes)
            }
        }

        item {
            ChartCard(stringResource(R.string.stats_allergens)) {
                CountedColumnChart(
                    data = state.allergens,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterBar(
    state: StatsUiState,
    onSearch: (String) -> Unit,
    onRestaurant: (Restaurant?) -> Unit,
    onMenuType: (String?) -> Unit,
    onDietary: (DietaryType?) -> Unit,
    onAllTime: () -> Unit,
    onResetRange: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.filters.search,
                onValueChange = onSearch,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text(stringResource(R.string.stats_search)) },
            )

            FilterGroup(stringResource(R.string.stats_filter_restaurant)) {
                FilterChip(
                    selected = state.filters.restaurant == null,
                    onClick = { onRestaurant(null) },
                    label = { Text(stringResource(R.string.stats_all)) },
                )
                Restaurant.entries.forEach { restaurant ->
                    FilterChip(
                        selected = state.filters.restaurant == restaurant,
                        onClick = { onRestaurant(restaurant) },
                        label = { Text(restaurant.displayName) },
                    )
                }
            }

            FilterGroup(stringResource(R.string.stats_filter_dietary)) {
                FilterChip(
                    selected = state.filters.dietaryType == null,
                    onClick = { onDietary(null) },
                    label = { Text(stringResource(R.string.stats_all)) },
                )
                listOf(DietaryType.MEAT, DietaryType.VEGETARIAN, DietaryType.VEGAN).forEach { type ->
                    FilterChip(
                        selected = state.filters.dietaryType == type,
                        onClick = { onDietary(type) },
                        label = { Text(type.label()) },
                    )
                }
            }

            if (state.availableTypes.isNotEmpty()) {
                FilterGroup(stringResource(R.string.stats_filter_type)) {
                    FilterChip(
                        selected = state.filters.menuType == null,
                        onClick = { onMenuType(null) },
                        label = { Text(stringResource(R.string.stats_all)) },
                    )
                    state.availableTypes.forEach { type ->
                        FilterChip(
                            selected = state.filters.menuType == type,
                            onClick = { onMenuType(type) },
                            label = { Text(type) },
                        )
                    }
                }
            }

            // Range is the only control that refetches, so it is labelled with its cost.
            FilterGroup(stringResource(R.string.stats_filter_range)) {
                FilterChip(
                    selected = true,
                    onClick = onResetRange,
                    label = { Text("${state.from} – ${state.to}") },
                )
                FilterChip(
                    selected = false,
                    onClick = onAllTime,
                    label = { Text(stringResource(R.string.stats_all_time)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(label: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}
