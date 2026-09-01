package dev.davidemarcoli.sixmensa.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.davidemarcoli.sixmensa.core.AppClock
import dev.davidemarcoli.sixmensa.core.DietaryType
import dev.davidemarcoli.sixmensa.core.Restaurant
import dev.davidemarcoli.sixmensa.data.HistoryRepository
import dev.davidemarcoli.sixmensa.data.remote.ApiError
import dev.davidemarcoli.sixmensa.data.remote.ApiException
import dev.davidemarcoli.sixmensa.domain.Counted
import dev.davidemarcoli.sixmensa.domain.DietaryCount
import dev.davidemarcoli.sixmensa.domain.FlatMenuItem
import dev.davidemarcoli.sixmensa.domain.PriceTrend
import dev.davidemarcoli.sixmensa.domain.StatsFilters
import dev.davidemarcoli.sixmensa.domain.allergenFrequency
import dev.davidemarcoli.sixmensa.domain.applyFilters
import dev.davidemarcoli.sixmensa.domain.availableMenuTypes
import dev.davidemarcoli.sixmensa.domain.dietaryDistribution
import dev.davidemarcoli.sixmensa.domain.dishFrequency
import dev.davidemarcoli.sixmensa.domain.priceTrend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

data class StatsUiState(
    val isLoading: Boolean = true,
    val error: ApiError? = null,
    val totalCount: Int = 0,
    val filteredCount: Int = 0,
    val filters: StatsFilters = StatsFilters(),
    val availableTypes: List<String> = emptyList(),
    val from: YearMonth = YearMonth.now(),
    val to: YearMonth = YearMonth.now(),
    val priceTrend: PriceTrend = PriceTrend(emptyList(), emptyList(), emptyMap()),
    val dietary: List<DietaryCount> = emptyList(),
    val dishes: List<Counted> = emptyList(),
    val allergens: List<Counted> = emptyList(),
)

class StatsViewModel(
    private val historyRepository: HistoryRepository,
    private val clock: AppClock,
) : ViewModel() {

    private val raw = MutableStateFlow<List<FlatMenuItem>>(emptyList())
    private val filters = MutableStateFlow(StatsFilters())
    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<ApiError?>(null)

    private val defaultRange = historyRepository.defaultRange()
    private val range = MutableStateFlow(defaultRange)

    @OptIn(FlowPreview::class)
    val uiState: StateFlow<StatsUiState> = combine(
        raw,
        // Debounced so typing in the search field doesn't re-aggregate on every keystroke.
        filters.debounce { if (it.search.isEmpty()) 0L else 300L },
        loading,
        error,
        range,
    ) { items, filters, loading, error, range ->
        val filtered = items.applyFilters(filters)
        StatsUiState(
            isLoading = loading,
            error = error,
            totalCount = items.size,
            filteredCount = filtered.size,
            filters = filters,
            availableTypes = items.availableMenuTypes(),
            from = range.first,
            to = range.second,
            priceTrend = filtered.priceTrend(),
            dietary = filtered.dietaryDistribution(),
            dishes = filtered.dishFrequency(limit = 50),
            allergens = filtered.allergenFrequency(),
        )
    }
        // Aggregation over thousands of items must not run on the main thread.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading.value = true
            val (from, to) = range.value
            val result = historyRepository.load(restaurant = null, from = from, to = to)
            result.onSuccess { raw.value = it; error.value = null }
            result.onFailure { error.value = (it as? ApiException)?.error ?: ApiError.Network }
            loading.value = false
        }
    }

    fun setSearch(value: String) = update { it.copy(search = value) }
    fun setRestaurant(value: Restaurant?) = update { it.copy(restaurant = value) }
    fun setMenuType(value: String?) = update { it.copy(menuType = value) }
    fun setDietaryType(value: DietaryType?) = update { it.copy(dietaryType = value) }

    /** Changing the range is the only filter that refetches — it changes the payload size. */
    fun setRange(from: YearMonth, to: YearMonth) {
        range.value = from to to
        load()
    }

    fun setAllTime() = setRange(historyRepository.earliestAvailable(), YearMonth.from(clock.today()))

    fun resetRange() = setRange(defaultRange.first, defaultRange.second)

    private fun update(block: (StatsFilters) -> StatsFilters) {
        filters.value = block(filters.value)
    }
}
