package dev.davidemarcoli.sixmensa.share

import dev.davidemarcoli.sixmensa.core.DietaryType
import dev.davidemarcoli.sixmensa.core.Restaurant
import dev.davidemarcoli.sixmensa.domain.DayMenu
import dev.davidemarcoli.sixmensa.domain.MenuItem
import dev.davidemarcoli.sixmensa.domain.Price
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class MenuTextFormatterTest {

    private fun item(
        title: String,
        description: String = "",
        type: String = "Local",
        price: Price? = Price(9.9, 14.9),
    ) = MenuItem(
        title = title,
        description = description,
        rawType = type,
        type = type,
        dietaryType = DietaryType.MEAT,
        price = price,
        origin = null,
        allergens = emptyList(),
    )

    private fun day(vararg items: MenuItem) = DayMenu(
        rawDate = "27. Juli",
        rawDay = "Montag",
        dayOfWeek = DayOfWeek.MONDAY,
        date = null,
        items = items.toList(),
    )

    @Test
    fun `formats a day with header prices and descriptions`() {
        val text = MenuTextFormatter.format(
            Restaurant.HTP,
            day(item("Currywurst", "mit fruchtiger Currysauce")),
        )

        assertTrue(text.startsWith("SIX Mensa · HTP — Montag, 27. Juli"))
        assertTrue(text.contains("Local · 9.90 / 14.90"))
        assertTrue(text.contains("Currywurst — mit fruchtiger Currysauce"))
    }

    @Test
    fun `omits the price section entirely when there is no price`() {
        val text = MenuTextFormatter.format(Restaurant.HTP, day(item("Buffet", price = null)))

        assertTrue(text.contains("Local"))
        assertFalse("no stray separator when price is absent", text.contains("·  "))
        assertFalse(text.contains("null"))
    }

    @Test
    fun `omits the dash when there is no description`() {
        val text = MenuTextFormatter.format(Restaurant.HTP, day(item("Zopf", description = "")))
        assertTrue(text.contains("Zopf"))
        assertFalse(text.contains("Zopf —"))
    }

    @Test
    fun `summarize caps the number of dishes`() {
        val menu = day(item("A"), item("B"), item("C"), item("D"))
        val summary = MenuTextFormatter.summarize(menu, limit = 3)

        assertEquals(3, summary.lines().size)
        assertFalse(summary.contains("D"))
    }

    @Test
    fun `handles an empty day without producing a dangling header`() {
        val text = MenuTextFormatter.format(Restaurant.HT201, day())
        assertEquals("SIX Mensa · HT 201 — Montag, 27. Juli", text)
        assertEquals("", MenuTextFormatter.summarize(day()))
    }
}
