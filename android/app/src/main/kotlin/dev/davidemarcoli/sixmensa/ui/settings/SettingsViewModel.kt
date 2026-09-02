package dev.davidemarcoli.sixmensa.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.davidemarcoli.sixmensa.core.ContentLanguage
import dev.davidemarcoli.sixmensa.core.Restaurant
import dev.davidemarcoli.sixmensa.data.local.Settings
import dev.davidemarcoli.sixmensa.data.local.SettingsRepository
import dev.davidemarcoli.sixmensa.data.local.ThemeMode
import dev.davidemarcoli.sixmensa.data.remote.MensaApi
import dev.davidemarcoli.sixmensa.data.remote.apiCall
import dev.davidemarcoli.sixmensa.data.remote.dto.StatusDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val api: MensaApi,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings.defaults())

    private val _status = MutableStateFlow<StatusDto?>(null)
    val status: StateFlow<StatusDto?> = _status.asStateFlow()

    init {
        viewModelScope.launch {
            apiCall { api.status() }.onSuccess { _status.value = it }
        }
    }

    // Every setter writes straight through to DataStore — no Save button, no toast.
    fun setContentLanguage(value: ContentLanguage) = launch { settingsRepository.setContentLanguage(value) }
    fun setRestaurant(value: Restaurant) = launch { settingsRepository.setRestaurant(value) }
    fun setThemeMode(value: ThemeMode) = launch { settingsRepository.setThemeMode(value) }
    fun setUseDynamicColor(value: Boolean) = launch { settingsRepository.setUseDynamicColor(value) }
    fun setSeedColor(value: Int) = launch { settingsRepository.setSeedColor(value) }
    fun setNotificationsEnabled(value: Boolean) = launch { settingsRepository.setNotificationsEnabled(value) }
    fun setNotificationTime(hour: Int, minute: Int) = launch { settingsRepository.setNotificationTime(hour, minute) }
    fun setNotifyOnlyAtWork(value: Boolean) = launch { settingsRepository.setNotifyOnlyAtWork(value) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
