package dev.davidemarcoli.sixmensa.notification

import dev.davidemarcoli.sixmensa.notification.NotificationScheduler.Companion.nextRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class NotificationSchedulerTest {

    private val zone = ZoneId.of("Europe/Zurich")
    private val at = LocalTime.of(10, 30)

    private fun time(date: LocalDate, hour: Int, minute: Int) =
        ZonedDateTime.of(date, LocalTime.of(hour, minute), zone)

    @Test
    fun `schedules later the same weekday when the time has not passed`() {
        // Monday 08:00 -> Monday 10:30
        val now = time(LocalDate.of(2026, 7, 27), 8, 0)
        assertEquals(time(LocalDate.of(2026, 7, 27), 10, 30), nextRun(now, at))
    }

    @Test
    fun `rolls to the next day once the time has passed`() {
        // Monday 11:00 -> Tuesday 10:30
        val now = time(LocalDate.of(2026, 7, 27), 11, 0)
        assertEquals(time(LocalDate.of(2026, 7, 28), 10, 30), nextRun(now, at))
    }

    @Test
    fun `exactly at the scheduled time schedules the next day, never immediately`() {
        val now = time(LocalDate.of(2026, 7, 27), 10, 30)
        assertEquals(time(LocalDate.of(2026, 7, 28), 10, 30), nextRun(now, at))
    }

    @Test
    fun `skips the weekend`() {
        // Friday after the time -> Monday, not Saturday.
        val friday = time(LocalDate.of(2026, 7, 31), 12, 0)
        val next = nextRun(friday, at)
        assertEquals(DayOfWeek.MONDAY, next.dayOfWeek)
        assertEquals(time(LocalDate.of(2026, 8, 3), 10, 30), next)
    }

    @Test
    fun `saturday and sunday both roll to monday`() {
        listOf(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2)).forEach { weekendDay ->
            val next = nextRun(time(weekendDay, 9, 0), at)
            assertEquals(DayOfWeek.MONDAY, next.dayOfWeek)
            assertEquals(time(LocalDate.of(2026, 8, 3), 10, 30), next)
        }
    }

    @Test
    fun `always returns a strictly future weekday instant`() {
        // Walk a full week at several times of day and assert the invariants hold.
        var day = LocalDate.of(2026, 7, 27)
        repeat(7) {
            listOf(0, 9, 10, 11, 23).forEach { hour ->
                val now = time(day, hour, 30)
                val next = nextRun(now, at)
                assertTrue("next run must be in the future", next.isAfter(now))
                assertTrue(
                    "next run must be a weekday",
                    next.dayOfWeek != DayOfWeek.SATURDAY && next.dayOfWeek != DayOfWeek.SUNDAY,
                )
                assertEquals(at, next.toLocalTime())
            }
            day = day.plusDays(1)
        }
    }
}
