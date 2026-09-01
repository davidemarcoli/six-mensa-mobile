package dev.davidemarcoli.sixmensa.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay

class DayResolverTest {

    @Test
    fun `parses german dates`() {
        assertEquals(MonthDay.of(7, 27), DayResolver.parseMonthDay("27. Juli"))
        assertEquals(MonthDay.of(4, 1), DayResolver.parseMonthDay("1. April"))
        assertEquals(MonthDay.of(12, 10), DayResolver.parseMonthDay("10. Dezember"))
        assertEquals(MonthDay.of(3, 5), DayResolver.parseMonthDay("5. März"))
    }

    @Test
    fun `parses english dates`() {
        assertEquals(MonthDay.of(7, 27), DayResolver.parseMonthDay("July 27"))
        assertEquals(MonthDay.of(10, 1), DayResolver.parseMonthDay("October 1"))
    }

    @Test
    fun `parses both locales regardless of which language was requested`() {
        // History records genuinely mix a German date with an English weekday in one object,
        // so neither field may assume the other's locale.
        assertEquals(MonthDay.of(4, 14), DayResolver.parseMonthDay("14. April"))
        assertEquals(DayOfWeek.MONDAY, DayResolver.parseDayOfWeek("Monday"))
    }

    @Test
    fun `parses weekdays in both locales`() {
        assertEquals(DayOfWeek.MONDAY, DayResolver.parseDayOfWeek("Montag"))
        assertEquals(DayOfWeek.MONDAY, DayResolver.parseDayOfWeek("Monday"))
        assertEquals(DayOfWeek.WEDNESDAY, DayResolver.parseDayOfWeek("Mittwoch"))
        assertEquals(DayOfWeek.FRIDAY, DayResolver.parseDayOfWeek("Freitag"))
        assertEquals(DayOfWeek.THURSDAY, DayResolver.parseDayOfWeek("thursday"))
        assertEquals(DayOfWeek.THURSDAY, DayResolver.parseDayOfWeek("  Donnerstag  "))
    }

    @Test
    fun `returns null for unparseable input`() {
        assertNull(DayResolver.parseMonthDay(""))
        assertNull(DayResolver.parseMonthDay("Kein Menu"))
        assertNull(DayResolver.parseMonthDay("32"))
        assertNull(DayResolver.parseDayOfWeek("Sometime"))
        assertNull(DayResolver.parseDayOfWeek(""))
    }

    @Test
    fun `rejects impossible day-month combinations`() {
        assertNull(DayResolver.parseMonthDay("31. Februar"))
    }

    @Test
    fun `attaches the current year for a nearby date`() {
        val today = LocalDate.of(2026, 7, 27)
        assertEquals(LocalDate.of(2026, 7, 27), DayResolver.resolveDate("27. Juli", today))
        assertEquals(LocalDate.of(2026, 7, 31), DayResolver.resolveDate("31. Juli", today))
    }

    @Test
    fun `picks the nearest year across the new year boundary`() {
        // The week of Mon 29 Dec 2025 - Fri 2 Jan 2026. On the Tuesday, "2. Januar" must
        // resolve into 2026 and "29. Dezember" must stay in 2025. Naively using today.year
        // gets one of these wrong whichever way you pick.
        val tuesday = LocalDate.of(2025, 12, 30)
        assertEquals(LocalDate.of(2025, 12, 29), DayResolver.resolveDate("29. Dezember", tuesday))
        assertEquals(LocalDate.of(2026, 1, 2), DayResolver.resolveDate("2. Januar", tuesday))

        val friday = LocalDate.of(2026, 1, 2)
        assertEquals(LocalDate.of(2025, 12, 29), DayResolver.resolveDate("29. Dezember", friday))
        assertEquals(LocalDate.of(2026, 1, 2), DayResolver.resolveDate("2. Januar", friday))
    }

    @Test
    fun `handles leap day without throwing`() {
        val today = LocalDate.of(2024, 3, 1)
        assertEquals(LocalDate.of(2024, 2, 29), DayResolver.resolveDate("29. Februar", today))
        // In a non-leap year the nearest candidate clamps rather than blowing up.
        assertEquals(LocalDate.of(2025, 2, 28), DayResolver.resolveDate("29. Februar", LocalDate.of(2025, 3, 1)))
    }
}
