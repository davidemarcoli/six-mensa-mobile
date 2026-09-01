package dev.davidemarcoli.sixmensa.wear.ui

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import dev.davidemarcoli.sixmensa.wear.ui.menu.WearMenuScreen
import dev.davidemarcoli.sixmensa.wear.ui.settings.WearSettingsScreen
import dev.davidemarcoli.sixmensa.wear.ui.theme.SixMensaWearTheme

private const val ROUTE_MENU = "menu"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun WearApp() {
    SixMensaWearTheme {
        val navController = rememberSwipeDismissableNavController()

        // AppScaffold owns the clock at the top; each screen's own ScreenScaffold owns the
        // scroll indicator. Nesting them this way is what makes the swipe-to-dismiss edge
        // gesture and the time readout behave consistently across screens.
        AppScaffold {
            SwipeDismissableNavHost(
                navController = navController,
                startDestination = ROUTE_MENU,
            ) {
                composable(ROUTE_MENU) {
                    WearMenuScreen(onSettings = { navController.navigate(ROUTE_SETTINGS) })
                }
                composable(ROUTE_SETTINGS) {
                    WearSettingsScreen()
                }
            }
        }
    }
}
