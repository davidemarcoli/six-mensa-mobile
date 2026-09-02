package dev.davidemarcoli.sixmensa.domain

import dev.davidemarcoli.sixmensa.core.Restaurant
import java.time.DayOfWeek

/** One weekday, with whatever each restaurant serves that day (either may be absent). */
data class ComparedDay(
    val dayOfWeek: DayOfWeek?,
    val label: String,
    val date: String,
    val byRestaurant: Map<Restaurant, DayMenu?>,
)

/**
 * Builds the **union** of both restaurants' days, keyed by weekday — so a day only one of
 * them serves still appears, with an empty state for the other.
 *
 * The webapp indexes one restaurant's list by the other's position
 * (`leftMenuData[new Date().getDay() - 1]`), which silently misaligns the two columns
 * whenever they differ in length. HTP regularly serves four days and HT201 five, so that
 * is not a hypothetical.
 */
fun buildComparedDays(weeks: Map<Restaurant, List<DayMenu>>): List<ComparedDay> {
    val all = weeks.values.flatten()
    if (all.isEmpty()) return emptyList()

    val ordered = all.mapNotNull { it.dayOfWeek }.distinct().sorted()
    if (ordered.isEmpty()) return emptyList()

    return ordered.map { dayOfWeek ->
        val perRestaurant = Restaurant.entries.associateWith { restaurant ->
            weeks[restaurant]?.firstOrNull { it.dayOfWeek == dayOfWeek }
        }
        val sample = perRestaurant.values.filterNotNull().firstOrNull()
        ComparedDay(
            dayOfWeek = dayOfWeek,
            label = sample?.rawDay.orEmpty(),
            date = sample?.rawDate.orEmpty(),
            byRestaurant = perRestaurant,
        )
    }
}
