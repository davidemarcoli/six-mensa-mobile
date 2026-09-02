package dev.davidemarcoli.sixmensa.data

import dev.davidemarcoli.sixmensa.core.AppClock
import dev.davidemarcoli.sixmensa.core.Restaurant
import dev.davidemarcoli.sixmensa.data.local.JsonDiskCache
import dev.davidemarcoli.sixmensa.data.remote.MensaApi
import dev.davidemarcoli.sixmensa.data.remote.apiCall
import dev.davidemarcoli.sixmensa.data.remote.dto.HistoryEntryDto
import dev.davidemarcoli.sixmensa.domain.FlatMenuItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.time.Duration
import java.time.YearMonth

class HistoryRepository(
    private val api: MensaApi,
    private val cache: JsonDiskCache,
    private val clock: AppClock,
) {
    private val serializer = ListSerializer(HistoryEntryDto.serializer())

    /**
     * Always range-bounded. The unparameterised `/history` is 836 KB and the server sends
     * **no gzip**, while a two-month window is ~102 KB — an 8x saving on mobile data.
     */
    suspend fun load(
        restaurant: Restaurant?,
        from: YearMonth,
        to: YearMonth,
    ): Result<List<FlatMenuItem>> {
        val fromParam = from.toParam()
        val toParam = to.toParam()
        val cacheKey = "history_${restaurant?.wire ?: "all"}_${fromParam}_$toParam"

        cache.read(cacheKey, serializer, TTL)?.takeIf { !it.isStale }?.let { cached ->
            return Result.success(flattenOffMainThread(cached.value))
        }

        val result = apiCall {
            if (restaurant == null) {
                api.history(fromParam, toParam)
            } else {
                api.history(restaurant.wire, fromParam, toParam)
            }
        }

        result.onSuccess { cache.write(cacheKey, serializer, it) }

        return result.fold(
            onSuccess = { Result.success(flattenOffMainThread(it)) },
            onFailure = { error ->
                // Fall back to a stale cache rather than failing outright.
                cache.read(cacheKey, serializer, TTL)
                    ?.let { Result.success(flattenOffMainThread(it.value)) }
                    ?: Result.failure(error)
            },
        )
    }

    private suspend fun flattenOffMainThread(entries: List<HistoryEntryDto>): List<FlatMenuItem> =
        withContext(Dispatchers.Default) { entries.flatten(clock.today()) }

    /** Default Stats window: the last three months. */
    fun defaultRange(): Pair<YearMonth, YearMonth> {
        val to = YearMonth.from(clock.today())
        return to.minusMonths(2) to to
    }

    /** Earliest data the archive actually holds, for the "All time" option. */
    fun earliestAvailable(): YearMonth = EARLIEST

    companion object {
        private val TTL: Duration = Duration.ofHours(12)
        private val EARLIEST: YearMonth = YearMonth.of(2025, 4)

        /** The API accepts strictly `YYYY-MM`; anything else silently returns an empty list. */
        fun YearMonth.toParam(): String = "%04d-%02d".format(year, monthValue)
    }
}
