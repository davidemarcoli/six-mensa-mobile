package dev.davidemarcoli.sixmensa.data

import dev.davidemarcoli.sixmensa.core.Restaurant
import dev.davidemarcoli.sixmensa.data.local.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Which restaurant the Menu screen is showing right now.
 *
 * Deliberately **not** persisted. The top-bar pill is for glancing at what the other
 * canteen has; the standard restaurant in Settings is the one the widget, the notification
 * and the next app start use. Picking up the phone should always start from the standard.
 */
class RestaurantSelection(
    settingsRepository: SettingsRepository,
    scope: CoroutineScope,
) {
    private val explored = MutableStateFlow<Restaurant?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selected: StateFlow<Restaurant> = settingsRepository.settings
        .map { it.restaurant }
        .distinctUntilChanged()
        .flatMapLatest { standard ->
            // Changing the standard is an explicit decision, so it supersedes exploring.
            explored.value = null
            explored.map { it ?: standard }
        }
        .stateIn(scope, SharingStarted.Eagerly, Restaurant.HTP)

    fun explore(restaurant: Restaurant) {
        explored.value = restaurant
    }
}
