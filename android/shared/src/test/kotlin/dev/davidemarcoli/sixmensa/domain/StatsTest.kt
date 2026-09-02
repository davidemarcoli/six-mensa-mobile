package dev.davidemarcoli.sixmensa.domain

import dev.davidemarcoli.sixmensa.core.DietaryType
import dev.davidemarcoli.sixmensa.core.Restaurant
import dev.davidemarcoli.sixmensa.data.flatten
import dev.davidemarcoli.sixmensa.data.remote.dto.HistoryEntryDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val today = LocalDate.of(2026, 7, 27)

    private val fixture: List<FlatMenuItem> by lazy {
        val text = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("fixtures/history_ht201_2025_12.json"),
        ).bufferedReader().readText()
        json.decodeFromString(ListSerializer(HistoryEntryDto.serializer()), text).flatten(today)
    }

    private fun item(
        title: String = "Dish",
        description: String = "",
        type: String = "Local",
        dietary: DietaryType = DietaryType.MEAT,
        intern: Double? = 10.0,
        origin: String? = null,
        allergens: List<String> = emptyList(),
        restaurant: String = "htp",
        month: Int = 1,
    ) = FlatMenuItem(
        restaurant = restaurant,
        year = 2026,
        month = month,
        week = 1,
        rawDay = "Montag",
        rawDate = "1. Januar",
        item = MenuItem(
            title = title,
            description = description,
            rawType = type,
            type = type,
            dietaryType = dietary,
            price = intern?.let { Price(it, it + 5) },
            origin = origin,
            allergens = allergens,
        ),
    )

    @Test
    fun `search matches title description and origin`() {
        val items = listOf(
            item(title = "Currywurst"),
            item(title = "Salat", description = "mit Currysauce"),
            item(title = "Pasta", origin = "Fleisch: Schwein; CH"),
            item(title = "Suppe"),
        )

        assertEquals(2, items.applyFilters(StatsFilters(search = "curry")).size)
        assertEquals(1, items.applyFilters(StatsFilters(search = "schwein")).size)
        assertEquals(4, items.applyFilters(StatsFilters(search = "")).size)
        assertEquals(0, items.applyFilters(StatsFilters(search = "sushi")).size)
    }

    @Test
    fun `filters combine`() {
        val items = listOf(
            item(title = "A", type = "Local", dietary = DietaryType.MEAT, restaurant = "htp"),
            item(title = "B", type = "Climate", dietary = DietaryType.VEGAN, restaurant = "htp"),
            item(title = "C", type = "Local", dietary = DietaryType.MEAT, restaurant = "ht201"),
        )

        assertEquals(2, items.applyFilters(StatsFilters(restaurant = Restaurant.HTP)).size)
        assertEquals(2, items.applyFilters(StatsFilters(menuType = "Local")).size)
        assertEquals(
            1,
            items.applyFilters(
                StatsFilters(restaurant = Restaurant.HTP, dietaryType = DietaryType.MEAT),
            ).size,
        )
    }

    @Test
    fun `price trend averages per month and keeps series aligned`() {
        val items = listOf(
            item(type = "Local", intern = 10.0, month = 1),
            item(type = "Local", intern = 12.0, month = 1),
            item(type = "Global", intern = 20.0, month = 2),
        )

        val trend = items.priceTrend()

        assertEquals(listOf("2026-01", "2026-02"), trend.months)
        assertEquals(11.0, trend.overall[0]!!, 0.001)
        assertEquals(20.0, trend.overall[1]!!, 0.001)

        // Every per-type series must have one slot per month, so the x-axis lines up.
        trend.byType.values.forEach { assertEquals(trend.months.size, it.size) }
        // Local has no February data -> an explicit null, not a shifted value.
        assertEquals(null, trend.byType.getValue("Local")[1])
    }

    @Test
    fun `items without a price are excluded from the trend`() {
        val trend = listOf(item(intern = null), item(intern = null)).priceTrend()
        assertTrue(trend.months.isEmpty())
    }

    @Test
    fun `dietary distribution always returns all three buckets`() {
        val distribution = listOf(item(dietary = DietaryType.VEGAN)).dietaryDistribution()

        assertEquals(3, distribution.size)
        assertEquals(1, distribution.first { it.type == DietaryType.VEGAN }.count)
        assertEquals(0, distribution.first { it.type == DietaryType.MEAT }.count)
        assertEquals(1.0, distribution.first { it.type == DietaryType.VEGAN }.share(1), 0.001)
    }

    @Test
    fun `dish frequency is ordered by count then alphabetically`() {
        val items = listOf(
            item(title = "Pasta"), item(title = "Pasta"), item(title = "Pasta"),
            item(title = "Zopf"), item(title = "Zopf"),
            item(title = "Apfel"), item(title = "Apfel"),
        )

        val top = items.dishFrequency(limit = 3)

        assertEquals(listOf("Pasta", "Apfel", "Zopf"), top.map { it.label })
        assertEquals(3, top.first().count)
    }

    @Test
    fun `allergen frequency never contains the serializer junk`() {
        // End-to-end over the real fixture that carries the known pollution.
        val allergens = fixture.allergenFrequency(limit = 100)

        assertTrue("fixture should produce allergens", allergens.isNotEmpty())
        assertFalse(allergens.any { it.label == "imagePath" })
        assertFalse(allergens.any { it.label.startsWith("image/") })
    }

    @Test
    fun `available menu types are normalized and sorted`() {
        val types = fixture.availableMenuTypes()

        assertTrue(types.isNotEmpty())
        // Raw values must have been collapsed by the normalizer before reaching Stats.
        assertFalse(types.contains("Klima Menü"))
        assertFalse(types.contains("Veggi"))
        assertFalse(types.contains("Local menu.local_kalbsadrio"))
        assertEquals(types.sorted(), types)
    }

    @Test
    fun `real fixture aggregates without error`() {
        assertTrue(fixture.isNotEmpty())
        assertTrue(fixture.priceTrend().months.isNotEmpty())
        assertEquals(fixture.size, fixture.dietaryDistribution().sumOf { it.count })
    }
}
