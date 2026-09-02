package dev.davidemarcoli.sixmensa

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.davidemarcoli.sixmensa.data.RestaurantSelection
import dev.davidemarcoli.sixmensa.data.local.Settings
import dev.davidemarcoli.sixmensa.data.local.SettingsRepository
import dev.davidemarcoli.sixmensa.share.Shortcuts
import dev.davidemarcoli.sixmensa.ui.ProvideAppLocale
import dev.davidemarcoli.sixmensa.ui.nav.SixMensaNavHost
import dev.davidemarcoli.sixmensa.ui.theme.SixMensaTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val settingsRepository: SettingsRepository by inject()
    private val restaurantSelection: RestaurantSelection by inject()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* state re-read on resume */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate to swap the splash theme out for the app theme.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        maybeRequestNotificationPermission()

        val startDestination = intent?.getStringExtra(Shortcuts.EXTRA_DESTINATION)

        setContent {
            val settings by settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = Settings.defaults())
            val selectedRestaurant by restaurantSelection.selected.collectAsStateWithLifecycle()

            ProvideAppLocale(settings.contentLanguage) {
                SixMensaTheme(
                    themeMode = settings.themeMode,
                    useDynamicColor = settings.useDynamicColor,
                    seedColor = Color(settings.seedColor),
                ) {
                    SixMensaNavHost(
                        restaurant = selectedRestaurant,
                        onRestaurantChange = restaurantSelection::explore,
                        startDestination = startDestination,
                    )
                }
            }
        }
    }

    /**
     * The daily notification ships enabled, so ask on first launch. Denial is respected —
     * Settings then shows the switch off with a shortcut into the system screen.
     */
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
