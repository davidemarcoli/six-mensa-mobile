package dev.davidemarcoli.sixmensa.ui.menu

import dev.davidemarcoli.sixmensa.presentation.menu.MenuUiState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.davidemarcoli.sixmensa.R
import dev.davidemarcoli.sixmensa.domain.DayMenu
import dev.davidemarcoli.sixmensa.share.MenuTextFormatter
import dev.davidemarcoli.sixmensa.share.ShareActions
import dev.davidemarcoli.sixmensa.ui.components.EmptyState
import dev.davidemarcoli.sixmensa.ui.components.ErrorBanner
import dev.davidemarcoli.sixmensa.ui.components.LastUpdatedLabel
import dev.davidemarcoli.sixmensa.ui.components.MenuItemCard
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    state: MenuUiState,
    onRefresh: () -> Unit,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading && state.days.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val context = LocalContext.current
    val shareLabel = stringResource(R.string.share_menu)

    Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        // Stale content plus an explanation beats an empty error screen.
        state.error?.let { error ->
            ErrorBanner(
                error = error,
                onRetry = onRefresh,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (state.days.isEmpty()) {
            EmptyState(stringResource(R.string.empty_no_week), Modifier.fillMaxSize())
            return@Column
        }

        // One tab per day *present in the data* — 3, 4 and 5 have all been observed.
        val pagerState = rememberPagerState(
            initialPage = state.selectedIndex.coerceIn(0, state.days.lastIndex),
            pageCount = { state.days.size },
        )

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect(onDaySelected)
        }
        // Restaurant switches change the day list underneath us; follow the clamped index.
        LaunchedEffect(state.selectedIndex, state.days.size) {
            val target = state.selectedIndex.coerceIn(0, state.days.lastIndex)
            if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
        }

        PrimaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage.coerceIn(0, state.days.lastIndex),
            edgePadding = 16.dp,
        ) {
            state.days.forEachIndexed { index, day ->
                val isToday = day.isToday(state.today)
                Tab(
                    selected = index == pagerState.currentPage,
                    onClick = { onDaySelected(index) },
                    text = {
                        Text(
                            text = day.rawDay.ifEmpty { "?" },
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                DayPage(
                    day = state.days[page],
                    today = state.today,
                    fetchedAt = state.fetchedAt,
                )
            }
        }
    }

        state.selectedDay?.takeIf { it.items.isNotEmpty() }?.let { day ->
            FloatingActionButton(
                onClick = {
                    ShareActions.shareText(
                        context = context,
                        text = MenuTextFormatter.format(state.restaurant, day),
                        title = shareLabel,
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Default.Share, contentDescription = shareLabel)
            }
        }
    }
}

@Composable
private fun DayPage(
    day: DayMenu,
    today: LocalDate,
    fetchedAt: java.time.Instant?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = buildString {
                        append(day.rawDay)
                        if (day.rawDate.isNotEmpty()) append(" · ").append(day.rawDate)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = if (day.isToday(today)) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                fetchedAt?.let { LastUpdatedLabel(it) }
            }
        }

        if (day.items.isEmpty()) {
            item { EmptyState(stringResource(R.string.empty_no_menu_today)) }
        } else {
            items(day.items, key = { it.title + it.rawType }) { item ->
                MenuItemCard(item)
            }
        }
    }
}
