package dev.davidemarcoli.sixmensa.data

import dev.davidemarcoli.sixmensa.core.DietaryType
import dev.davidemarcoli.sixmensa.data.remote.dto.DayMenuDto
import dev.davidemarcoli.sixmensa.data.remote.dto.HistoryEntryDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** Parses the real API responses captured under `src/test/resources/fixtures/`. */
class MenuParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    private val today = LocalDate.of(2026, 7, 27)

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "missing fixture $name"
        }.bufferedReader().readText()

    private fun week(name: String): List<DayMenuDto> =
        json.decodeFromString(ListSerializer(DayMenuDto.serializer()), fixture(name))

    @Test
    fun `reads the misspelled menues field`() {
        val days = week("week_htp_de.json")
        assertTrue("expected items to be populated via @SerialName(\"menues\")", days.all { it.items.isNotEmpty() })
    }

    @Test
    fun `restaurants return different numbers of days`() {
        // Never assume five. HTP served four days and HT201 five when captured.
        assertEquals(4, week("week_htp_de.json").size)
        assertEquals(5, week("week_ht201_de.json").size)
    }

    @Test
    fun `maps german week to domain`() {
        val days = week("week_htp_de.json").toDomain(today)

        assertEquals("Montag", days.first().rawDay)
        assertEquals("27. Juli", days.first().rawDate)
        assertEquals(DayOfWeek.MONDAY, days.first().dayOfWeek)
        assertEquals(LocalDate.of(2026, 7, 27), days.first().date)
        assertTrue(days.first().isToday(today))
    }

    @Test
    fun `maps english week to domain with the same resolved dates`() {
        val de = week("week_htp_de.json").toDomain(today)
        val en = week("week_htp_en.json").toDomain(today)

        assertEquals("Monday", en.first().rawDay)
        assertEquals("July 27", en.first().rawDate)
        // Language changes the display strings but never the resolved calendar meaning.
        assertEquals(de.map { it.dayOfWeek }, en.map { it.dayOfWeek })
        assertEquals(de.map { it.date }, en.map { it.date })
    }

    @Test
    fun `normalizes types and dietary values`() {
        val items = week("week_htp_de.json").toDomain(today).flatMap { it.items }

        assertTrue(items.isNotEmpty())
        assertTrue(
            "normalized types should be from the known set",
            items.all { it.type.isNotEmpty() },
        )
        assertTrue(items.none { it.dietaryType == DietaryType.UNKNOWN })
    }

    @Test
    fun `blank origins become null`() {
        val items = week("week_htp_de.json").toDomain(today).flatMap { it.items }
        assertTrue("no origin should be an empty string", items.none { it.origin == "" })
    }

    @Test
    fun `sanitizes polluted allergens across the whole history fixture`() {
        val entries = json.decodeFromString(
            ListSerializer(HistoryEntryDto.serializer()),
            fixture("history_ht201_2025_12.json"),
        )

        // The raw fixture really does contain the junk...
        val rawAllergens = entries.flatMap { it.data }.flatMap { it.items }.flatMap { it.allergens }
        assertTrue("fixture should contain the known pollution", rawAllergens.contains("imagePath"))

        // ...and nothing downstream of the mapper ever sees it.
        val flat = entries.flatten(today)
        val mapped = flat.flatMap { it.item.allergens }
        assertFalse(mapped.contains("imagePath"))
        assertTrue(mapped.none { it.startsWith("image/") })
        assertTrue("real allergens survive", mapped.contains("Gluten"))
    }

    @Test
    fun `history flattening keeps week coordinates`() {
        val entries = json.decodeFromString(
            ListSerializer(HistoryEntryDto.serializer()),
            fixture("history_ht201_2025_12.json"),
        )
        val flat = entries.flatten(today)

        assertTrue(flat.isNotEmpty())
        assertTrue(flat.all { it.restaurant == "ht201" })
        assertTrue(flat.all { it.year == 2025 && it.month == 12 })
        assertEquals("2025-12", flat.first().yearMonth)
    }

    @Test
    fun `tolerates a missing price and missing allergens`() {
        val dto = json.decodeFromString(
            ListSerializer(DayMenuDto.serializer()),
            """[{"date":"27. Juli","day":"Montag","menues":[{"title":"X","type":"Local","dietaryType":"meat"}]}]""",
        )
        val item = dto.toDomain(today).first().items.first()

        assertNull(item.price)
        assertNull(item.origin)
        assertTrue(item.allergens.isEmpty())
        assertEquals("Local", item.type)
    }

    @Test
    fun `tolerates unknown new fields`() {
        val dto = json.decodeFromString(
            ListSerializer(DayMenuDto.serializer()),
            """[{"date":"27. Juli","day":"Montag","somethingNew":42,"menues":[]}]""",
        )
        assertNotNull(dto.first())
        assertEquals("Montag", dto.first().day)
    }
}
