package dev.davidemarcoli.sixmensa.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import dev.davidemarcoli.sixmensa.R
import dev.davidemarcoli.sixmensa.core.DietaryType
import dev.davidemarcoli.sixmensa.domain.MenuItem
import dev.davidemarcoli.sixmensa.domain.Price

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MenuItemCard(
    item: MenuItem,
    modifier: Modifier = Modifier,
) {
    var allergensExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.type,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    DietaryBadge(item.dietaryType)
                }
                // Hidden entirely when absent rather than rendering an empty slot.
                item.price?.let { PriceLabel(it) }
            }

            Text(text = item.title, style = MaterialTheme.typography.titleMedium)

            if (item.description.isNotEmpty()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 815 of 2689 items have an empty origin; never render bare parentheses.
            item.origin?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (item.allergens.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .clickable { allergensExpanded = !allergensExpanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.allergens_count, item.allergens.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = if (allergensExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (allergensExpanded) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.allergens.forEach { allergen ->
                            AssistChip(
                                onClick = {},
                                label = { Text(allergen, style = MaterialTheme.typography.labelSmall) },
                                colors = AssistChipDefaults.assistChipColors(
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceLabel(price: Price, modifier: Modifier = Modifier) {
    val intern = price.intern?.let { "%.2f".format(it) }
    val extern = price.extern?.let { "%.2f".format(it) }
    val text = when {
        intern != null && extern != null -> stringResource(R.string.price_format, intern, extern)
        intern != null -> intern
        extern != null -> extern
        else -> return
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun DietaryBadge(type: DietaryType, modifier: Modifier = Modifier) {
    val label = when (type) {
        DietaryType.VEGAN -> stringResource(R.string.vegan)
        DietaryType.VEGETARIAN -> stringResource(R.string.vegetarian)
        else -> return
    }
    Icon(
        imageVector = Icons.Outlined.Eco,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.tertiary,
        modifier = modifier
            .size(18.dp)
            // The icon alone is meaningless to TalkBack; give it the word instead.
            .clearAndSetSemantics { contentDescription = label },
    )
}
