package dev.davidemarcoli.sixmensa.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class DaySelectionTest {

    private fun week(vararg days: DayOfWeek): List<DayMenu> = days.map {
        DayMenu(rawDate = "", rawDay = it.name, dayOfWeek = it, date = null, items = emptyList())
    }

    private val monToFri = week(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )

    /** HTP regularly serves only four days. */
    private val monToThu = week(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    )

    @Test
    fun `opens on today`() {
        val wednesday = LocalDate.of(2026, 7, 29)
        assertEquals(2, monToFri.initialPageIndex(wednesday))
    }

    @Test
    fun `falls back to monday at the weekend`() {
        val saturday = LocalDate.of(2026, 8, 1)
        assertEquals(0, monToFri.initialPageIndex(saturday))
    }

    @Test
    fun `falls back to monday when today is not served`() {
        // Friday, but HTP stops at Thursday.
        val friday = LocalDate.of(2026, 7, 31)
        assertEquals(0, monToThu.initialPageIndex(friday))
    }

    @Test
    fun `prefers an exact date match over a weekday match`() {
        val today = LocalDate.of(2026, 7, 28)
        val days = listOf(
            DayMenu("27. Juli", "Montag", DayOfWeek.MONDAY, LocalDate.of(2026, 7, 27), emptyList()),
            DayMenu("28. Juli", "Dienstag", DayOfWeek.TUESDAY, LocalDate.of(2026, 7, 28), emptyList()),
        )
        assertEquals(1, days.initialPageIndex(today))
    }

    @Test
    fun `handles an empty week`() {
        assertEquals(0, emptyList<DayMenu>().initialPageIndex(LocalDate.of(2026, 7, 27)))
    }

    @Test
    fun `keeps the selected weekday when switching restaurant`() {
        assertEquals(2, monToThu.indexOfDayOrClamp(DayOfWeek.WEDNESDAY))
    }

    @Test
    fun `clamps to the last day rather than snapping back to monday`() {
        // The important one: Friday selected on HT201, then switch to HTP (Mon-Thu).
        // Landing on Thursday is far less jarring than resetting to Monday.
        assertEquals(3, monToThu.indexOfDayOrClamp(DayOfWeek.FRIDAY))
    }

    @Test
    fun `moves forward to the next served day when one is missing mid-week`() {
        val noWednesday = week(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)
        assertEquals(2, noWednesday.indexOfDayOrClamp(DayOfWeek.WEDNESDAY))
    }

    @Test
    fun `handles null and empty gracefully`() {
        assertEquals(0, monToFri.indexOfDayOrClamp(null))
        assertEquals(0, emptyList<DayMenu>().indexOfDayOrClamp(DayOfWeek.MONDAY))
    }
}
