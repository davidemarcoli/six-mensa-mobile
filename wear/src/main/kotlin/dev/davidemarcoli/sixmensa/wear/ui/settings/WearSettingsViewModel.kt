package dev.davidemarcoli.sixmensa.wear.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.davidemarcoli.sixmensa.core.ContentLanguage
import dev.davidemarcoli.sixmensa.core.Restaurant
import dev.davidemarcoli.sixmensa.data.local.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WearSettingsUiState(
    val restaurant: Restaurant = Restaurant.HTP,
    val contentLanguage: ContentLanguage = ContentLanguage.DE,
)

/**
 * Only the two settings that change what the watch shows. Theme, notification time and the
 * seed colour are all phone-side concerns — the watch takes its palette from the watch face
 * and its notifications are bridged from the phone.
 */
class WearSettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<WearSettingsUiState> = settingsRepository.settings
        .map { WearSettingsUiState(it.restaurant, it.contentLanguage) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WearSettingsUiState())

    fun setRestaurant(restaurant: Restaurant) {
        viewModelScope.launch { settingsRepository.setRestaurant(restaurant) }
    }

    fun setContentLanguage(language: ContentLanguage) {
        viewModelScope.launch { settingsRepository.setContentLanguage(language) }
    }
}
