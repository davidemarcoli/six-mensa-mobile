package dev.davidemarcoli.sixmensa.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.MonthDay
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/**
 * The API exposes no ISO dates at all. Each day carries a localized weekday name
 * (`"Montag"` / `"Monday"`) and a localized human date with **no year** (`"27. Juli"` /
 * `"July 27"`). Worse, history records mix locales *within a single object* — German
 * dates alongside English weekdays are the norm there, not the exception.
 *
 * So every field is parsed against both locales independently, and both lookup tables are
 * derived from `java.time` itself so they cannot drift from whatever formatter the backend uses.
 */
object DayResolver {

    private val monthByName: Map<String, Month> = buildMap {
        for (locale in listOf(Locale.GERMAN, Locale.ENGLISH)) {
            for (month in Month.entries) {
                for (style in listOf(
                    TextStyle.FULL,
                    TextStyle.FULL_STANDALONE,
                    TextStyle.SHORT,
                    TextStyle.SHORT_STANDALONE,
                )) {
                    val name = month.getDisplayName(style, locale)
                        .lowercase(Locale.ROOT)
                        .trim('.', ' ')
                    if (name.isNotEmpty()) put(name, month)
                }
            }
        }
    }

    private val dayOfWeekByName: Map<String, DayOfWeek> = buildMap {
        for (locale in listOf(Locale.GERMAN, Locale.ENGLISH)) {
            for (day in DayOfWeek.entries) {
                // FULL_STANDALONE can differ from FULL in some locales; index both.
                for (style in listOf(TextStyle.FULL, TextStyle.FULL_STANDALONE)) {
                    val name = day.getDisplayName(style, locale).lowercase(Locale.ROOT).trim()
                    if (name.isNotEmpty()) put(name, day)
                }
            }
        }
    }

    private val dayNumber = Regex("""\d{1,2}""")
    private val word = Regex("""\p{L}{3,}""")

    /** `"27. Juli"` / `"July 27"` / `"Jul 27"` -> July 27. Null if nothing parses. */
    fun parseMonthDay(raw: String): MonthDay? {
        val day = dayNumber.find(raw)?.value?.toIntOrNull() ?: return null
        val month = word.findAll(raw)
            .map { it.value.lowercase(Locale.ROOT) }
            .firstNotNullOfOrNull { monthByName[it] }
            ?: return null
        return runCatching { MonthDay.of(month, day) }.getOrNull()
    }

    /** `"Montag"` / `"Monday"` -> MONDAY. Null if nothing matches. */
    fun parseDayOfWeek(raw: String): DayOfWeek? =
        dayOfWeekByName[raw.trim().lowercase(Locale.ROOT)]

    /**
     * Attaches a year to a year-less date by choosing whichever of last/this/next year
     * lands nearest [today]. Naively using `today.year` breaks on the week that straddles
     * New Year (Dec 29 -> Jan 2).
     */
    fun resolveDate(raw: String, today: LocalDate): LocalDate? {
        val monthDay = parseMonthDay(raw) ?: return null
        return listOf(today.year - 1, today.year, today.year + 1)
            // atYear clamps Feb 29 to Feb 28 in non-leap years rather than throwing.
            .mapNotNull { year -> runCatching { monthDay.atYear(year) }.getOrNull() }
            .minByOrNull { abs(ChronoUnit.DAYS.between(it, today)) }
    }
}
