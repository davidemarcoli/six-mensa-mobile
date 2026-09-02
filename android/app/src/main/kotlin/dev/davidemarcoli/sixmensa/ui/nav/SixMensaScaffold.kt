package dev.davidemarcoli.sixmensa.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.davidemarcoli.sixmensa.R
import dev.davidemarcoli.sixmensa.core.Restaurant
import dev.davidemarcoli.sixmensa.ui.components.MensaSegmentedSwitch
import kotlinx.serialization.Serializable

/** Outer destinations. [MainRoute] hosts the three bottom-bar screens. */
@Serializable data object MainRoute
@Serializable data object SettingsRoute
@Serializable data class PdfRoute(val restaurant: String)

/** Inner destinations, inside [MainRoute]'s scaffold. */
@Serializable data object MenuRoute
@Serializable data object CompareRoute
@Serializable data object StatsRoute

enum class TopLevelDestination(val icon: ImageVector, val labelRes: Int) {
    MENU(Icons.Outlined.RestaurantMenu, R.string.nav_menu),
    COMPARE(Icons.AutoMirrored.Outlined.CompareArrows, R.string.nav_compare),
    STATS(Icons.Outlined.BarChart, R.string.nav_stats),
}

/**
 * Wraps only the bottom-bar destinations.
 *
 * Settings and the PDF viewer live on the *outer* nav graph, so navigating to them animates
 * this whole scaffold out as one unit. Previously the bars were shown conditionally on the
 * current destination, which made them vanish the instant navigation began — the content
 * then reflowed upward before the fade had finished.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SixMensaScaffold(
    navController: NavHostController,
    restaurant: Restaurant,
    onRestaurantChange: (Restaurant) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPdf: (Restaurant) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    // Compare shows both restaurants by definition, and Stats has its own Restaurant
    // filter — a second control there would just be two ways to say the same thing.
    val showMensaSwitch = destination?.hasRoute(MenuRoute::class) ?: true

    var overflowExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    if (showMensaSwitch) {
                        MensaSegmentedSwitch(selected = restaurant, onSelect = onRestaurantChange)
                    } else {
                        Text(currentTitle(destination))
                    }
                },
                actions = {
                    IconButton(onClick = { overflowExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, stringResource(R.string.more_options))
                    }
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false },
                    ) {
                        Restaurant.entries.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.pdf_plan, entry.displayName)) },
                                onClick = {
                                    overflowExpanded = false
                                    onOpenPdf(entry)
                                },
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, stringResource(R.string.nav_settings))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { dest ->
                    val route = dest.route()
                    val selected = destination?.hasRoute(route::class) == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(route) {
                                    popUpTo(MenuRoute) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        content(Modifier.padding(innerPadding))
    }
}

@Composable
private fun currentTitle(destination: androidx.navigation.NavDestination?): String = when {
    destination?.hasRoute(CompareRoute::class) == true -> stringResource(R.string.nav_compare)
    destination?.hasRoute(StatsRoute::class) == true -> stringResource(R.string.nav_stats)
    else -> stringResource(R.string.app_name)
}

private fun TopLevelDestination.route(): Any = when (this) {
    TopLevelDestination.MENU -> MenuRoute
    TopLevelDestination.COMPARE -> CompareRoute
    TopLevelDestination.STATS -> StatsRoute
}
