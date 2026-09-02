package dev.davidemarcoli.sixmensa.share

import dev.davidemarcoli.sixmensa.core.Restaurant
import dev.davidemarcoli.sixmensa.domain.DayMenu

/**
 * Plain-text rendering of one day's menu, for the share sheet and the notification body.
 * Kept free of Android types so it can be unit tested.
 */
object MenuTextFormatter {

    fun format(restaurant: Restaurant, day: DayMenu): String = buildString {
        append("SIX Mensa · ").append(restaurant.displayName)
        if (day.rawDay.isNotEmpty()) {
            append(" — ").append(day.rawDay)
            if (day.rawDate.isNotEmpty()) append(", ").append(day.rawDate)
        }

        day.items.forEach { item ->
            append("\n\n")
            append(item.type)
            item.price?.intern?.let { append(" · ").append("%.2f".format(it)) }
            item.price?.extern?.let { append(" / ").append("%.2f".format(it)) }
            append('\n').append(item.title)
            if (item.description.isNotEmpty()) append(" — ").append(item.description)
        }
    }

    /** One line per dish, for the notification's expanded text. */
    fun summarize(day: DayMenu, limit: Int = Int.MAX_VALUE): String =
        day.items.take(limit).joinToString("\n") { item ->
            buildString {
                append(item.title)
                item.price?.intern?.let { append("  ").append("%.2f".format(it)) }
            }
        }
}
