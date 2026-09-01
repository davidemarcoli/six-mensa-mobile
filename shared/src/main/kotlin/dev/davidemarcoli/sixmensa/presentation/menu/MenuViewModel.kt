package dev.davidemarcoli.sixmensa.presentation.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.davidemarcoli.sixmensa.core.AppClock
import dev.davidemarcoli.sixmensa.core.ContentLanguage
import dev.davidemarcoli.sixmensa.core.Restaurant
import dev.davidemarcoli.sixmensa.data.MenuRepository
import dev.davidemarcoli.sixmensa.data.RestaurantSelection
import dev.davidemarcoli.sixmensa.data.local.Cached
import dev.davidemarcoli.sixmensa.data.local.SettingsRepository
import dev.davidemarcoli.sixmensa.data.remote.ApiError
import dev.davidemarcoli.sixmensa.data.remote.ApiException
import dev.davidemarcoli.sixmensa.domain.DayMenu
import dev.davidemarcoli.sixmensa.domain.indexOfDayOrClamp
import dev.davidemarcoli.sixmensa.domain.initialPageIndex
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.Instant
import java.time.LocalDate

data class MenuUiState(
    val isLoading: Boolean = true,
    val days: List<DayMenu> = emptyList(),
    val selectedIndex: Int = 0,
    val restaurant: Restaurant = Restaurant.HTP,
    val today: LocalDate = LocalDate.now(),
    val fetchedAt: Instant? = null,
    val isRefreshing: Boolean = false,
    /** Present alongside [days] when we are showing a stale cache after a failed refresh. */
    val error: ApiError? = null,
) {
    val selectedDay: DayMenu? get() = days.getOrNull(selectedIndex)
}

class MenuViewModel(
    private val menuRepository: MenuRepository,
    settingsRepository: SettingsRepository,
    restaurantSelection: RestaurantSelection,
    private val clock: AppClock,
) : ViewModel() {

    /**
     * The selection is a weekday, never an index: the two restaurants return different
     * numbers of days, so an index means something different depending on which is showing.
     */
    private val selectedDayOfWeek = MutableStateFlow<DayOfWeek?>(null)
    private val isRefreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<ApiError?>(null)
    private var hasLoadedOnce = false

    // The pill's transient selection, not the persisted standard restaurant.
    private val source = combine(
        restaurantSelection.selected,
        settingsRepository.settings.map { it.contentLanguage }.distinctUntilChanged(),
    ) { restaurant, language -> restaurant to language }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Restaurant.HTP to ContentLanguage.DE)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val cached: StateFlow<Cached<List<DayMenu>>?> = source
        .flatMapLatest { (restaurant, language) -> menuRepository.observeWeek(restaurant, language) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<MenuUiState> = combine(
        cached,
        source,
        selectedDayOfWeek,
        isRefreshing,
        error,
    ) { cached, (restaurant, _), selected, refreshing, err ->
        val days = cached?.value.orEmpty()
        val today = clock.today()
        val index = when {
            days.isEmpty() -> 0
            selected == null -> days.initialPageIndex(today)
            else -> days.indexOfDayOrClamp(selected)
        }
        MenuUiState(
            isLoading = cached == null && !hasLoadedOnce,
            days = days,
            selectedIndex = index,
            restaurant = restaurant,
            today = today,
            fetchedAt = cached?.fetchedAt,
            isRefreshing = refreshing,
            error = err,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MenuUiState())

    init {
        // Refresh whenever the restaurant or the content language changes.
        viewModelScope.launch {
            source.collect { (restaurant, language) -> load(restaurant, language) }
        }
    }

    fun refresh() {
        val (restaurant, language) = source.value
        viewModelScope.launch { load(restaurant, language, force = true) }
    }

    fun onDaySelected(index: Int) {
        uiState.value.days.getOrNull(index)?.dayOfWeek?.let { selectedDayOfWeek.value = it }
    }

    private suspend fun load(restaurant: Restaurant, language: ContentLanguage, force: Boolean = false) {
        isRefreshing.value = true
        val result = menuRepository.refresh(restaurant, language, force)
        hasLoadedOnce = true
        isRefreshing.value = false
        error.value = result.exceptionOrNull()?.let { throwable ->
            (throwable as? ApiException)?.error ?: ApiError.Network
        }
    }
}
