package dev.davidemarcoli.sixmensa.data

import dev.davidemarcoli.sixmensa.core.AppClock
import dev.davidemarcoli.sixmensa.core.ContentLanguage
import dev.davidemarcoli.sixmensa.core.Restaurant
import dev.davidemarcoli.sixmensa.data.local.Cached
import dev.davidemarcoli.sixmensa.data.local.JsonDiskCache
import dev.davidemarcoli.sixmensa.data.remote.MensaApi
import dev.davidemarcoli.sixmensa.data.remote.apiCall
import dev.davidemarcoli.sixmensa.data.remote.dto.DayMenuDto
import dev.davidemarcoli.sixmensa.domain.DayMenu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import java.time.Duration
import java.time.Instant

/**
 * Lets the data layer refresh whatever out-of-app surface the host has — the phone's Glance
 * widget, the watch's tile — without depending on either.
 */
fun interface WidgetUpdater {
    suspend fun updateAll()
}

class MenuRepository(
    private val api: MensaApi,
    private val cache: JsonDiskCache,
    private val clock: AppClock,
    private val scope: CoroutineScope,
    private val widgetUpdater: WidgetUpdater = WidgetUpdater { },
) {
    private val serializer = ListSerializer(DayMenuDto.serializer())
    private val invalidations = MutableSharedFlow<String>(extraBufferCapacity = 16)

    private val inFlightLock = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<Result<List<DayMenu>>>>()
    private val lastFetchedAt = mutableMapOf<String, Instant>()

    /** Cache key includes the language so switching de<->en can never show stale content. */
    private fun key(restaurant: Restaurant, language: ContentLanguage) =
        "week_${restaurant.wire}_${language.wire}"

    /**
     * Emits the cached week immediately (if any), then again after every successful refresh.
     * Never emits an empty state on failure — a stale cache is more useful than nothing, and
     * the caller surfaces the error as a banner alongside it.
     */
    fun observeWeek(restaurant: Restaurant, language: ContentLanguage): Flow<Cached<List<DayMenu>>> {
        val cacheKey = key(restaurant, language)
        return invalidations
            .filter { it == cacheKey }
            .onStart { emit(cacheKey) }
            .mapNotNull { cache.read(cacheKey, serializer, TTL) }
            .map { cached ->
                Cached(
                    value = cached.value.toDomain(clock.today()),
                    fetchedAt = cached.fetchedAt,
                    isStale = cached.isStale,
                )
            }
    }

    /**
     * Concurrent callers asking for the same week share one network call, and a call that
     * lands within [MIN_REFRESH_INTERVAL] of the previous one is served from cache instead.
     *
     * Both are needed: a restaurant switch makes the Menu screen and the app-level settings
     * observer each want a refresh, and they fire close together but not always at the same
     * instant, so coalescing alone still let a duplicate request through.
     *
     * [force] bypasses the interval for pull-to-refresh, where the user has explicitly asked
     * for fresh data.
     */
    suspend fun refresh(
        restaurant: Restaurant,
        language: ContentLanguage,
        force: Boolean = false,
    ): Result<List<DayMenu>> {
        val cacheKey = key(restaurant, language)

        if (!force) {
            val recent = inFlightLock.withLock {
                lastFetchedAt[cacheKey]?.let {
                    Duration.between(it, Instant.now()) < MIN_REFRESH_INTERVAL
                } ?: false
            }
            if (recent) {
                cache.read(cacheKey, serializer, TTL)?.let {
                    return Result.success(it.value.toDomain(clock.today()))
                }
            }
        }

        val deferred = inFlightLock.withLock {
            inFlight[cacheKey]?.takeIf { it.isActive }
                ?: scope.async { fetch(restaurant, language, cacheKey) }
                    .also { inFlight[cacheKey] = it }
        }

        return try {
            deferred.await()
        } finally {
            inFlightLock.withLock {
                if (inFlight[cacheKey] === deferred && !deferred.isActive) inFlight.remove(cacheKey)
            }
        }
    }

    private suspend fun fetch(
        restaurant: Restaurant,
        language: ContentLanguage,
        cacheKey: String,
    ): Result<List<DayMenu>> {
        val result = apiCall { api.week(restaurant.wire, -1, language.wire) }
        result.onSuccess { dtos ->
            cache.write(cacheKey, serializer, dtos)
            inFlightLock.withLock { lastFetchedAt[cacheKey] = Instant.now() }
            invalidations.emit(cacheKey)
            widgetUpdater.updateAll()
        }
        return result.map { it.toDomain(clock.today()) }
    }

    /**
     * A direct disk read for the widget and the notification worker. Those run in a cold
     * process with no ViewModel alive, so they must never depend on in-memory state.
     */
    suspend fun peekCached(
        restaurant: Restaurant,
        language: ContentLanguage,
    ): Cached<List<DayMenu>>? =
        cache.read(key(restaurant, language), serializer, TTL)?.let { cached ->
            Cached(cached.value.toDomain(clock.today()), cached.fetchedAt, cached.isStale)
        }

    companion object {
        /** The backend itself only re-scrapes hourly, so anything fresher is wasted effort. */
        private val TTL: Duration = Duration.ofHours(6)

        /** Long enough to absorb the burst around a settings change, short enough to be invisible. */
        private val MIN_REFRESH_INTERVAL: Duration = Duration.ofSeconds(30)
    }
}
