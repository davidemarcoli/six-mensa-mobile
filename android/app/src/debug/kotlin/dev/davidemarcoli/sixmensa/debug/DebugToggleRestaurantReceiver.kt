package dev.davidemarcoli.sixmensa.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.davidemarcoli.sixmensa.core.Restaurant
import dev.davidemarcoli.sixmensa.data.local.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Debug-only: flips the standard restaurant, so the settings-driven widget/notification
 * update path can be exercised without driving the UI.
 *
 *   adb shell am broadcast -a dev.davidemarcoli.sixmensa.DEBUG_TOGGLE_RESTAURANT \
 *     -n dev.davidemarcoli.sixmensa.debug/dev.davidemarcoli.sixmensa.debug.DebugToggleRestaurantReceiver
 */
class DebugToggleRestaurantReceiver : BroadcastReceiver(), KoinComponent {

    private val settingsRepository: SettingsRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val current = settingsRepository.current().restaurant
                val next = if (current == Restaurant.HTP) Restaurant.HT201 else Restaurant.HTP
                Log.d("MenuWidget", "DEBUG toggle standard restaurant: $current -> $next")
                settingsRepository.setRestaurant(next)
            } finally {
                pending.finish()
            }
        }
    }
}
