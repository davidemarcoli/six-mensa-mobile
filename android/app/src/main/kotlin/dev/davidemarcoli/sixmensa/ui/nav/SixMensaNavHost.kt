package dev.davidemarcoli.sixmensa.ui.nav

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.davidemarcoli.sixmensa.core.Restaurant
import dev.davidemarcoli.sixmensa.share.Shortcuts
import dev.davidemarcoli.sixmensa.ui.compare.CompareScreen
import dev.davidemarcoli.sixmensa.ui.compare.CompareViewModel
import dev.davidemarcoli.sixmensa.ui.menu.MenuScreen
import dev.davidemarcoli.sixmensa.presentation.menu.MenuViewModel
import dev.davidemarcoli.sixmensa.ui.pdf.PdfScreen
import dev.davidemarcoli.sixmensa.ui.pdf.PdfViewModel
import dev.davidemarcoli.sixmensa.ui.settings.SettingsScreen
import dev.davidemarcoli.sixmensa.ui.settings.SettingsViewModel
import dev.davidemarcoli.sixmensa.ui.stats.StatsScreen
import dev.davidemarcoli.sixmensa.ui.stats.StatsViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * Two-level graph. The outer host holds [MainRoute] (the bottom-bar shell) alongside the
 * full-screen destinations, so pushing Settings or the PDF viewer animates the entire
 * shell — bars included — instead of tearing the bars off first.
 */
@Composable
fun SixMensaNavHost(
    restaurant: Restaurant,
    onRestaurantChange: (Restaurant) -> Unit,
    modifier: Modifier = Modifier,
    /** Set by the launcher shortcuts; see [dev.davidemarcoli.sixmensa.share.Shortcuts]. */
    startDestination: String? = null,
) {
    val outerNavController = rememberNavController()
    val innerStart: Any = when (startDestination) {
        Shortcuts.DESTINATION_COMPARE -> CompareRoute
        else -> MenuRoute
    }

    NavHost(
        navController = outerNavController,
        startDestination = MainRoute,
        modifier = modifier,
    ) {
        composable<MainRoute> {
            val innerNavController = rememberNavController()

            SixMensaScaffold(
                navController = innerNavController,
                restaurant = restaurant,
                onRestaurantChange = onRestaurantChange,
                onOpenSettings = { outerNavController.navigate(SettingsRoute) },
                onOpenPdf = { outerNavController.navigate(PdfRoute(it.wire)) },
            ) { contentModifier ->
                NavHost(
                    navController = innerNavController,
                    startDestination = innerStart,
                    modifier = contentModifier,
                ) {
                    composable<MenuRoute> {
                        val viewModel: MenuViewModel = koinViewModel()
                        val state by viewModel.uiState.collectAsStateWithLifecycle()
                        MenuScreen(
                            state = state,
                            onRefresh = viewModel::refresh,
                            onDaySelected = viewModel::onDaySelected,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    composable<CompareRoute> {
                        val viewModel: CompareViewModel = koinViewModel()
                        val state by viewModel.uiState.collectAsStateWithLifecycle()
                        CompareScreen(
                            state = state,
                            onRefresh = viewModel::refresh,
                            onStep = viewModel::step,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    composable<StatsRoute> {
                        val viewModel: StatsViewModel = koinViewModel()
                        val state by viewModel.uiState.collectAsStateWithLifecycle()
                        StatsScreen(
                            state = state,
                            onSearch = viewModel::setSearch,
                            onRestaurant = viewModel::setRestaurant,
                            onMenuType = viewModel::setMenuType,
                            onDietary = viewModel::setDietaryType,
                            onAllTime = viewModel::setAllTime,
                            onResetRange = viewModel::resetRange,
                            onRetry = viewModel::load,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        composable<SettingsRoute> {
            val viewModel: SettingsViewModel = koinViewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val status by viewModel.status.collectAsStateWithLifecycle()
            val context = LocalContext.current

            SettingsScreen(
                settings = settings,
                status = status,
                notificationsBlocked = !hasNotificationPermission(context),
                onBack = { outerNavController.popBackStack() },
                onContentLanguage = viewModel::setContentLanguage,
                onRestaurant = viewModel::setRestaurant,
                onThemeMode = viewModel::setThemeMode,
                onDynamicColor = viewModel::setUseDynamicColor,
                onSeedColor = viewModel::setSeedColor,
                onNotificationsEnabled = viewModel::setNotificationsEnabled,
                onNotificationTime = viewModel::setNotificationTime,
                onNotifyOnlyAtWork = viewModel::setNotifyOnlyAtWork,
            )
        }

        composable<PdfRoute> { backStackEntry ->
            val route: PdfRoute = backStackEntry.toRoute()
            val pdfRestaurant = Restaurant.fromWire(route.restaurant) ?: Restaurant.HTP
            val viewModel: PdfViewModel = koinViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            PdfScreen(
                restaurant = pdfRestaurant,
                state = state,
                onLoad = { width -> viewModel.load(pdfRestaurant, width) },
                onRetry = { width -> viewModel.retry(pdfRestaurant, width) },
                onBack = { outerNavController.popBackStack() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Below API 33 notifications need no runtime permission. */
private fun hasNotificationPermission(context: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
