package dev.davidemarcoli.sixmensa.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.davidemarcoli.sixmensa.R
import dev.davidemarcoli.sixmensa.core.Restaurant
import dev.davidemarcoli.sixmensa.data.remote.ApiError
import java.time.Duration
import java.time.Instant

/** HT201 <-> HTP, shown in the top app bar. */
@Composable
fun MensaSegmentedSwitch(
    selected: Restaurant,
    onSelect: (Restaurant) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = Restaurant.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, restaurant ->
            SegmentedButton(
                selected = restaurant == selected,
                onClick = { onSelect(restaurant) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
                label = {
                    Text(
                        text = restaurant.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false,
                    )
                },
            )
        }
    }
}

@Composable
fun LastUpdatedLabel(
    fetchedAt: Instant,
    modifier: Modifier = Modifier,
    now: Instant = Instant.now(),
) {
    val elapsed = Duration.between(fetchedAt, now)
    val text = when {
        elapsed.toMinutes() < 1 -> stringResource(R.string.updated_just_now)
        elapsed.toHours() < 1 -> stringResource(R.string.updated_minutes_ago, elapsed.toMinutes().toInt())
        elapsed.toDays() < 1 -> stringResource(R.string.updated_hours_ago, elapsed.toHours().toInt())
        else -> stringResource(R.string.updated_days_ago, elapsed.toDays().toInt())
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * Non-blocking: shown *above* stale cached content rather than replacing it. Losing the
 * network should never mean losing the menu you already have.
 */
@Composable
fun ErrorBanner(
    error: ApiError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = error.message(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
fun ApiError.message(): String = when (this) {
    ApiError.Network -> stringResource(R.string.error_offline)
    ApiError.InvalidRestaurant, ApiError.MenuNotFound -> stringResource(R.string.error_server)
    is ApiError.Http -> stringResource(R.string.error_server)
    is ApiError.Malformed -> stringResource(R.string.error_malformed)
}

@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.RestaurantMenu,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
