package dev.davidemarcoli.sixmensa.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuTypeNormalizerTest {

    /**
     * Every distinct `type` value present in the live history archive (2025-04 .. 2026-07),
     * enumerated with jq. If the backend introduces a new one this test won't fail — but the
     * ones that exist must never regress.
     */
    @Test
    fun `normalizes every observed type value`() {
        val expected = mapOf(
            "Local" to "Local",
            // The order of the groups is what makes these two correct.
            "Local menu.local_kalbsadrio" to "Local",
            "Climate Vegetarian" to "Climate",
            "Climate Veggie" to "Climate",
            "Climate Menu" to "Climate",
            "Klima Menü" to "Climate",
            "Klima-Menü" to "Climate",
            "Vegetarian" to "Climate",
            "Veggi" to "Climate",
            "Veggie" to "Climate",
            "Vegi" to "Climate",
            "Global" to "Global",
            "Globetrotter" to "Global",
            "Pizza & Pasta" to "Pizza & Pasta",
            // Unknown values pass through untouched.
            "Buffet" to "Buffet",
        )

        expected.forEach { (raw, want) ->
            assertEquals("normalize(\"$raw\")", want, MenuTypeNormalizer.normalize(raw))
        }
    }

    @Test
    fun `local is matched before climate`() {
        // "Local menu.local_kalbsadrio" contains neither climate keyword, but this guards
        // the ordering contract explicitly against a future Map refactor.
        assertEquals("Local", MenuTypeNormalizer.normalize("Local Vegetarian"))
    }

    @Test
    fun `is case insensitive`() {
        assertEquals("Global", MenuTypeNormalizer.normalize("GLOBETROTTER"))
        assertEquals("Pizza & Pasta", MenuTypeNormalizer.normalize("pizza"))
    }

    @Test
    fun `passes through empty and unknown`() {
        assertEquals("", MenuTypeNormalizer.normalize(""))
        assertEquals("Sushi", MenuTypeNormalizer.normalize("Sushi"))
    }
}

class AllergenSanitizerTest {

    /** Verbatim from a live history record (2025-12, ht201, "Olma Sausage"). */
    @Test
    fun `strips serializer junk leaked into the allergens array`() {
        val raw = listOf(
            "Soy", "Gluten", "Dairy products", "Celery", "Mustard", "Sulphites",
            "imagePath", "image/b0ccdd20",
        )
        assertEquals(
            listOf("Soy", "Gluten", "Dairy products", "Celery", "Mustard", "Sulphites"),
            AllergenSanitizer.sanitize(raw),
        )
    }

    @Test
    fun `drops blanks and duplicates`() {
        assertEquals(
            listOf("Eier", "Senf"),
            AllergenSanitizer.sanitize(listOf("Eier", "  ", "Senf", "Eier", "")),
        )
    }

    @Test
    fun `leaves clean lists untouched`() {
        val clean = listOf("Eier", "Senf", "Sulfite")
        assertEquals(clean, AllergenSanitizer.sanitize(clean))
    }

    @Test
    fun `does not strip legitimate allergens that merely contain the word image`() {
        // Only an exact "imagePath" or a leading "image/" is junk.
        assertEquals(listOf("Imagepaste"), AllergenSanitizer.sanitize(listOf("Imagepaste")))
    }
}
