package dev.davidemarcoli.sixmensa.domain

import dev.davidemarcoli.sixmensa.core.DietaryType
import dev.davidemarcoli.sixmensa.core.Restaurant
import java.time.DayOfWeek
import java.time.LocalDate

data class Price(val intern: Double?, val extern: Double?) {
    val hasAny: Boolean get() = intern != null || extern != null
}

data class MenuItem(
    val title: String,
    val description: String,
    /** Raw API value, kept for debugging and for the Stats "raw type" case. */
    val rawType: String,
    /** [rawType] collapsed via MenuTypeNormalizer. */
    val type: String,
    val dietaryType: DietaryType,
    val price: Price?,
    /** Blank origins (815 of 2689 items) are normalised to null so the UI can just skip them. */
    val origin: String?,
    /** Sanitized: serializer junk removed. */
    val allergens: List<String>,
)

data class DayMenu(
    /** Exactly as the API sent it, e.g. "27. Juli" — used for display. */
    val rawDate: String,
    /** Exactly as the API sent it, e.g. "Montag" — used for display. */
    val rawDay: String,
    /** Resolved, or null when unparseable. */
    val dayOfWeek: DayOfWeek?,
    /** Resolved by attaching the nearest plausible year, or null when unparseable. */
    val date: LocalDate?,
    val items: List<MenuItem>,
) {
    fun isToday(today: LocalDate): Boolean =
        date?.let { it == today } ?: (dayOfWeek != null && dayOfWeek == today.dayOfWeek)
}

data class WeekMenu(
    val restaurant: Restaurant,
    val days: List<DayMenu>,
)

/** One history menu item flattened with its week coordinates, for the Stats screen. */
data class FlatMenuItem(
    val restaurant: String,
    val year: Int,
    val month: Int,
    val week: Int,
    val rawDay: String,
    val rawDate: String,
    val item: MenuItem,
) {
    /** "YYYY-MM", matching the API's from/to parameter format. */
    val yearMonth: String get() = "%04d-%02d".format(year, month)
}

/**
 * Which day the Menu screen should open on.
 *
 * Deliberately tolerant: the two restaurants return different numbers of days (3, 4 or 5
 * have all been observed), so never assume five and never index by `dayOfWeek.value - 1`.
 * Falls back to Monday on weekends, or when today's weekday simply isn't served.
 */
fun List<DayMenu>.initialPageIndex(today: LocalDate): Int {
    if (isEmpty()) return 0
    indexOfFirst { it.date == today }.let { if (it >= 0) return it }
    indexOfFirst { it.dayOfWeek == today.dayOfWeek }.let { if (it >= 0) return it }
    return 0
}

/**
 * Keeps the selected weekday stable when the user switches restaurant. If the selected day
 * isn't served by the new restaurant (Friday on HT201 -> HTP, which stops at Thursday),
 * clamp to the last available day rather than snapping back to Monday.
 */
fun List<DayMenu>.indexOfDayOrClamp(day: DayOfWeek?): Int {
    if (isEmpty()) return 0
    if (day == null) return 0
    val exact = indexOfFirst { it.dayOfWeek == day }
    if (exact >= 0) return exact
    val firstAfter = indexOfFirst { it.dayOfWeek != null && it.dayOfWeek > day }
    return if (firstAfter >= 0) firstAfter else lastIndex
}
