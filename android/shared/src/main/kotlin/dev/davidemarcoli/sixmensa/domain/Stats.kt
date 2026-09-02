package dev.davidemarcoli.sixmensa.domain

import dev.davidemarcoli.sixmensa.core.DietaryType
import dev.davidemarcoli.sixmensa.core.Restaurant
import java.util.Locale

data class StatsFilters(
    val search: String = "",
    val restaurant: Restaurant? = null,
    val menuType: String? = null,
    val dietaryType: DietaryType? = null,
)

data class MonthlyPricePoint(val yearMonth: String, val average: Double)

data class PriceTrend(
    /** Sorted "YYYY-MM" labels shared by every series. */
    val months: List<String>,
    /** Average internal price across all types, per month. Null where no data. */
    val overall: List<Double?>,
    /** One series per normalized menu type. */
    val byType: Map<String, List<Double?>>,
)

data class DietaryCount(val type: DietaryType, val count: Int) {
    fun share(total: Int): Double = if (total == 0) 0.0 else count.toDouble() / total
}

data class Counted(val label: String, val count: Int)

/**
 * All filtering happens in memory. Only the month range hits the network, because that is
 * what actually changes the payload size (836 KB unfiltered vs ~100 KB for two months).
 */
fun List<FlatMenuItem>.applyFilters(filters: StatsFilters): List<FlatMenuItem> {
    val needle = filters.search.trim().lowercase(Locale.ROOT)
    return filter { flat ->
        val item = flat.item
        if (filters.restaurant != null && !flat.restaurant.equals(filters.restaurant.wire, true)) {
            return@filter false
        }
        if (filters.menuType != null && item.type != filters.menuType) return@filter false
        if (filters.dietaryType != null && item.dietaryType != filters.dietaryType) return@filter false
        if (needle.isNotEmpty()) {
            val haystack = buildString {
                append(item.title.lowercase(Locale.ROOT)).append(' ')
                append(item.description.lowercase(Locale.ROOT)).append(' ')
                append(item.origin.orEmpty().lowercase(Locale.ROOT))
            }
            if (!haystack.contains(needle)) return@filter false
        }
        true
    }
}

/** The normalized menu types actually present, for populating the filter dropdown. */
fun List<FlatMenuItem>.availableMenuTypes(): List<String> =
    mapNotNull { it.item.type.takeIf(String::isNotEmpty) }.distinct().sorted()

fun List<FlatMenuItem>.priceTrend(): PriceTrend {
    val withPrice = filter { it.item.price?.intern != null }
    if (withPrice.isEmpty()) return PriceTrend(emptyList(), emptyList(), emptyMap())

    val months = withPrice.map { it.yearMonth }.distinct().sorted()

    fun averagesFor(items: List<FlatMenuItem>): List<Double?> {
        val byMonth = items.groupBy { it.yearMonth }
        return months.map { month ->
            byMonth[month]
                ?.mapNotNull { it.item.price?.intern }
                ?.takeIf { it.isNotEmpty() }
                ?.average()
        }
    }

    val byType = withPrice
        .groupBy { it.item.type }
        .filterKeys { it.isNotEmpty() }
        .mapValues { (_, items) -> averagesFor(items) }

    return PriceTrend(months = months, overall = averagesFor(withPrice), byType = byType)
}

fun List<FlatMenuItem>.dietaryDistribution(): List<DietaryCount> =
    listOf(DietaryType.MEAT, DietaryType.VEGETARIAN, DietaryType.VEGAN)
        .map { type -> DietaryCount(type, count { it.item.dietaryType == type }) }

fun List<FlatMenuItem>.dishFrequency(limit: Int): List<Counted> =
    groupingBy { it.item.title.trim() }
        .eachCount()
        .filterKeys { it.isNotEmpty() }
        .map { (title, count) -> Counted(title, count) }
        // Stable ordering: count desc, then alphabetically, so the list doesn't jitter.
        .sortedWith(compareByDescending<Counted> { it.count }.thenBy { it.label })
        .take(limit)

fun List<FlatMenuItem>.allergenFrequency(limit: Int = 20): List<Counted> =
    flatMap { it.item.allergens }
        .groupingBy { it }
        .eachCount()
        .map { (allergen, count) -> Counted(allergen, count) }
        .sortedWith(compareByDescending<Counted> { it.count }.thenBy { it.label })
        .take(limit)
