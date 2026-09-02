package dev.davidemarcoli.sixmensa.domain

import dev.davidemarcoli.sixmensa.core.Restaurant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class CompareTest {

    private fun day(dow: DayOfWeek, title: String) = DayMenu(
        rawDate = "",
        rawDay = dow.name,
        dayOfWeek = dow,
        date = null,
        items = listOf(
            MenuItem(
                title = title,
                description = "",
                rawType = "Local",
                type = "Local",
                dietaryType = dev.davidemarcoli.sixmensa.core.DietaryType.MEAT,
                price = null,
                origin = null,
                allergens = emptyList(),
            ),
        ),
    )

    @Test
    fun `unions days when the two restaurants differ in length`() {
        // The exact real-world shape: HT201 Mon-Fri, HTP Mon-Thu.
        val weeks = mapOf(
            Restaurant.HT201 to listOf(
                day(DayOfWeek.MONDAY, "a"), day(DayOfWeek.TUESDAY, "b"),
                day(DayOfWeek.WEDNESDAY, "c"), day(DayOfWeek.THURSDAY, "d"),
                day(DayOfWeek.FRIDAY, "e"),
            ),
            Restaurant.HTP to listOf(
                day(DayOfWeek.MONDAY, "v"), day(DayOfWeek.TUESDAY, "w"),
                day(DayOfWeek.WEDNESDAY, "x"), day(DayOfWeek.THURSDAY, "y"),
            ),
        )

        val compared = buildComparedDays(weeks)

        assertEquals(5, compared.size)
        assertEquals(
            listOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
            ),
            compared.map { it.dayOfWeek },
        )

        // Friday still appears, with HTP simply absent rather than misaligned.
        val friday = compared.last()
        assertNotNull(friday.byRestaurant[Restaurant.HT201])
        assertNull(friday.byRestaurant[Restaurant.HTP])
    }

    @Test
    fun `never pairs a day with the other restaurants different day`() {
        // This is the webapp's bug: index-based pairing would line HT201's Friday up with
        // nothing, and shift every prior row. Assert each row is internally consistent.
        val weeks = mapOf(
            Restaurant.HT201 to listOf(
                day(DayOfWeek.MONDAY, "a"), day(DayOfWeek.WEDNESDAY, "c"),
                day(DayOfWeek.FRIDAY, "e"),
            ),
            Restaurant.HTP to listOf(
                day(DayOfWeek.TUESDAY, "w"), day(DayOfWeek.WEDNESDAY, "x"),
            ),
        )

        val compared = buildComparedDays(weeks)

        assertEquals(4, compared.size)
        compared.forEach { row ->
            row.byRestaurant.values.filterNotNull().forEach { menu ->
                assertEquals(
                    "row ${row.dayOfWeek} contained a menu for ${menu.dayOfWeek}",
                    row.dayOfWeek,
                    menu.dayOfWeek,
                )
            }
        }

        val wednesday = compared.first { it.dayOfWeek == DayOfWeek.WEDNESDAY }
        assertEquals("c", wednesday.byRestaurant[Restaurant.HT201]?.items?.first()?.title)
        assertEquals("x", wednesday.byRestaurant[Restaurant.HTP]?.items?.first()?.title)
    }

    @Test
    fun `sorts by weekday regardless of input order`() {
        val weeks = mapOf(
            Restaurant.HT201 to listOf(day(DayOfWeek.FRIDAY, "e"), day(DayOfWeek.MONDAY, "a")),
        )
        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            buildComparedDays(weeks).map { it.dayOfWeek },
        )
    }

    @Test
    fun `handles empty input`() {
        assertTrue(buildComparedDays(emptyMap()).isEmpty())
        assertTrue(buildComparedDays(mapOf(Restaurant.HTP to emptyList())).isEmpty())
    }

    @Test
    fun `always includes an entry for every restaurant even when absent`() {
        val weeks = mapOf(Restaurant.HT201 to listOf(day(DayOfWeek.MONDAY, "a")))
        val row = buildComparedDays(weeks).single()

        // The key must be present with a null value, so the UI can render "not served"
        // rather than silently omitting the section.
        assertTrue(row.byRestaurant.containsKey(Restaurant.HTP))
        assertNull(row.byRestaurant[Restaurant.HTP])
    }
}
