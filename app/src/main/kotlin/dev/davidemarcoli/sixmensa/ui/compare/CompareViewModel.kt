package dev.davidemarcoli.sixmensa.ui.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.davidemarcoli.sixmensa.core.AppClock
import dev.davidemarcoli.sixmensa.core.ContentLanguage
import dev.davidemarcoli.sixmensa.core.Restaurant
import dev.davidemarcoli.sixmensa.data.MenuRepository
import dev.davidemarcoli.sixmensa.data.local.SettingsRepository
import dev.davidemarcoli.sixmensa.domain.ComparedDay
import dev.davidemarcoli.sixmensa.domain.DayMenu
import dev.davidemarcoli.sixmensa.domain.buildComparedDays
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

data class CompareUiState(
    val isLoading: Boolean = true,
    val days: List<ComparedDay> = emptyList(),
    val selectedIndex: Int = 0,
    val today: LocalDate = LocalDate.now(),
) {
    val selected: ComparedDay? get() = days.getOrNull(selectedIndex)
}

class CompareViewModel(
    private val menuRepository: MenuRepository,
    settingsRepository: SettingsRepository,
    private val clock: AppClock,
) : ViewModel() {

    private val selectedDayOfWeek = MutableStateFlow<DayOfWeek?>(null)

    private val language = settingsRepository.settings
        .map { it.contentLanguage }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ContentLanguage.DE)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val weeks = language.flatMapLatest { lang ->
        combine(
            menuRepository.observeWeek(Restaurant.HT201, lang),
            menuRepository.observeWeek(Restaurant.HTP, lang),
        ) { ht201, htp -> mapOf(Restaurant.HT201 to ht201.value, Restaurant.HTP to htp.value) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val uiState: StateFlow<CompareUiState> = combine(
        weeks,
        selectedDayOfWeek,
    ) { weeks, selected ->
        val today = clock.today()
        val days = buildComparedDays(weeks)
        val index = when {
            days.isEmpty() -> 0
            selected != null -> days.indexOfFirst { it.dayOfWeek == selected }.coerceAtLeast(0)
            else -> days.indexOfFirst { it.dayOfWeek == today.dayOfWeek }.takeIf { it >= 0 } ?: 0
        }
        CompareUiState(
            isLoading = weeks.isEmpty(),
            days = days,
            selectedIndex = index,
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompareUiState())

    init {
        viewModelScope.launch { language.collect { refresh() } }
    }

    fun refresh() {
        viewModelScope.launch {
            val lang = language.value
            // Load both in parallel; if one fails the other still renders.
            coroutineScope {
                val a = async { menuRepository.refresh(Restaurant.HT201, lang, force = true) }
                val b = async { menuRepository.refresh(Restaurant.HTP, lang, force = true) }
                a.await()
                b.await()
            }
        }
    }

    fun selectIndex(index: Int) {
        uiState.value.days.getOrNull(index)?.let { selectedDayOfWeek.value = it.dayOfWeek }
    }

    fun step(delta: Int) {
        val state = uiState.value
        if (state.days.isEmpty()) return
        val next = (state.selectedIndex + delta).mod(state.days.size)
        selectIndex(next)
    }

}
