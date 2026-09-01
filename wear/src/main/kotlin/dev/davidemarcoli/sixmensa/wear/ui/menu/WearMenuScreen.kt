package dev.davidemarcoli.sixmensa.wear.ui.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import dev.davidemarcoli.sixmensa.core.DietaryType
import dev.davidemarcoli.sixmensa.data.remote.ApiError
import dev.davidemarcoli.sixmensa.domain.DayMenu
import dev.davidemarcoli.sixmensa.domain.MenuItem
import dev.davidemarcoli.sixmensa.presentation.menu.MenuUiState
import dev.davidemarcoli.sixmensa.presentation.menu.MenuViewModel
import dev.davidemarcoli.sixmensa.wear.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun WearMenuScreen(onSettings: () -> Unit) {
    val viewModel: MenuViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    WearMenuContent(
        state = state,
        onDaySelected = viewModel::onDaySelected,
        onRefresh = viewModel::refresh,
        onSettings = onSettings,
    )
}

@Composable
private fun WearMenuContent(
    state: MenuUiState,
    onDaySelected: (Int) -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    if (state.isLoading && state.days.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (state.days.isEmpty()) {
        EmptyWeek(error = state.error, onRefresh = onRefresh, onSettings = onSettings)
        return
    }

    // One page per day *present in the data* — 3, 4 and 5 have all been observed, so the
    // page count follows the list rather than assuming a five-day week.
    val pagerState = rememberPagerState(
        initialPage = state.selectedIndex.coerceIn(0, state.days.lastIndex),
    ) { state.days.size }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect(onDaySelected)
    }
    // Restaurant switches change the day list underneath us; follow the clamped index.
    LaunchedEffect(state.selectedIndex, state.days.size) {
        val target = state.selectedIndex.coerceIn(0, state.days.lastIndex)
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }

    HorizontalPagerScaffold(pagerState = pagerState) {
        HorizontalPager(state = pagerState) { page ->
            AnimatedPage(pageIndex = page, pagerState = pagerState) {
                val day = state.days[page]
                DayPage(
                    day = day,
                    header = "${state.restaurant.displayName} · ${dayLabel(day, state)}",
                    onSettings = onSettings,
                )
            }
        }
    }
}

@Composable
private fun dayLabel(day: DayMenu, state: MenuUiState): String =
    if (day.isToday(state.today)) stringResource(R.string.today) else day.rawDay.ifEmpty { "?" }

@Composable
private fun DayPage(
    day: DayMenu,
    header: String,
    onSettings: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ListHeader { Text(header) }
            }

            if (day.items.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.empty_no_menu_today),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
            } else {
                items(day.items) { item -> MenuItemCard(item) }
            }

            item {
                FilledTonalButton(
                    onClick = onSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings))
                }
            }
        }
    }
}

@Composable
private fun MenuItemCard(item: MenuItem) {
    // Nothing is truncated: descriptions run to five or more lines and the card grows to
    // fit, so a long dish is a longer scroll rather than a hidden one.
    // No `subtitle` slot: Wear's TitleCard renders it *below* the content, where the round
    // screen clips it. Dietary type and price share one meta line instead.
    TitleCard(
        title = {
            Text(item.title)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            if (item.description.isNotBlank()) {
                Text(item.description)
            }
            metaLine(item)?.let { line ->
                Text(text = line, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun metaLine(item: MenuItem): String? =
    listOfNotNull(dietaryLabel(item.dietaryType), priceLine(item))
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")

/**
 * Internal / external, formatted exactly like the phone's `price_format` — no currency
 * prefix, which is also what keeps the meta line on one row. Falls back to whichever single
 * price the API gave; plenty of items carry only one.
 */
private fun priceLine(item: MenuItem): String? {
    val price = item.price ?: return null
    val intern = price.intern?.let { "%.2f".format(it) }
    val extern = price.extern?.let { "%.2f".format(it) }
    return when {
        intern != null && extern != null -> "$intern / $extern"
        else -> intern ?: extern
    }
}

@Composable
private fun dietaryLabel(type: DietaryType): String? = when (type) {
    DietaryType.VEGAN -> stringResource(R.string.vegan)
    DietaryType.VEGETARIAN -> stringResource(R.string.vegetarian)
    DietaryType.MEAT -> stringResource(R.string.meat)
    DietaryType.UNKNOWN -> null
}

@Composable
private fun EmptyWeek(error: ApiError?, onRefresh: () -> Unit, onSettings: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text(
                    text = error?.let { stringResource(it.messageRes()) }
                        ?: stringResource(R.string.empty_no_week),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
            item {
                FilledTonalButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.refresh))
                }
            }
            item {
                FilledTonalButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings))
                }
            }
        }
    }
}

private fun ApiError.messageRes(): Int = when (this) {
    is ApiError.Network -> R.string.error_network
    is ApiError.Http, is ApiError.InvalidRestaurant, is ApiError.MenuNotFound -> R.string.error_server
    is ApiError.Malformed -> R.string.error_malformed
}
