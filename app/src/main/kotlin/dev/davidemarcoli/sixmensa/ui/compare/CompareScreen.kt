package dev.davidemarcoli.sixmensa.ui.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.davidemarcoli.sixmensa.R
import dev.davidemarcoli.sixmensa.core.Restaurant
import dev.davidemarcoli.sixmensa.ui.components.EmptyState
import dev.davidemarcoli.sixmensa.ui.components.MenuItemCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    state: CompareUiState,
    onRefresh: () -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading && state.days.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val day = state.selected
    if (day == null) {
        EmptyState(stringResource(R.string.empty_no_week), modifier.fillMaxSize())
        return
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { onStep(-1) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    stringResource(R.string.compare_previous_day),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(day.label, style = MaterialTheme.typography.titleMedium)
                if (day.date.isNotEmpty()) {
                    Text(
                        day.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { onStep(1) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    stringResource(R.string.compare_next_day),
                )
            }
        }

        HorizontalDivider()

        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Phones are narrow: stack the two restaurants vertically rather than
            // side-by-side, which is what the web app degrades to on mobile anyway.
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Restaurant.entries.forEach { restaurant ->
                    item(key = "header_${restaurant.wire}") {
                        Text(
                            text = restaurant.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    val menu = day.byRestaurant[restaurant]
                    if (menu == null || menu.items.isEmpty()) {
                        item(key = "empty_${restaurant.wire}") {
                            Text(
                                text = stringResource(
                                    R.string.compare_not_served,
                                    restaurant.displayName,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                            )
                        }
                    } else {
                        items(
                            items = menu.items,
                            key = { "${restaurant.wire}_${it.title}_${it.rawType}" },
                        ) { item -> MenuItemCard(item) }
                    }
                }
            }
        }
    }
}
