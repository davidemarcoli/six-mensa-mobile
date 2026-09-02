package dev.davidemarcoli.sixmensa.core

import java.util.Locale

/**
 * Collapses the API's free-text `type` field into a small stable set.
 *
 * Ported from `six-mensa/components/stats/utils.ts`. **Order is significant** and this
 * must stay an ordered [List], never a Map: "Local" is tested before "Climate" so that
 * `"Local menu.local_kalbsadrio"` resolves to `Local`, and `"Climate Vegetarian"` hits
 * the Climate group on `climate` rather than falling through to `vegetarian`.
 *
 * Unknown values pass through unchanged (e.g. `Buffet`).
 */
object MenuTypeNormalizer {

    private val typeGroups: List<Pair<String, List<String>>> = listOf(
        "Local" to listOf("local"),
        "Climate" to listOf("climate", "klima", "vegetarian", "veggi", "veggie", "vegi"),
        "Global" to listOf("global", "globetrotter"),
        "Pizza & Pasta" to listOf("pizza"),
    )

    fun normalize(raw: String): String {
        val lower = raw.lowercase(Locale.ROOT)
        return typeGroups
            .firstOrNull { (_, keywords) -> keywords.any { lower.contains(it) } }
            ?.first
            ?: raw
    }
}

/**
 * The API leaks serializer internals into some `allergens` arrays: 20 records in the
 * history archive contain the literal `"imagePath"` plus an `"image/<hex>"` value mixed
 * in with real allergens. The webapp renders those as if they were allergens; we drop them.
 *
 * Allergen names themselves are LLM-translated free text and deliberately not an enum —
 * `Milk`, `Dairy` and `Dairy products` all coexist in the same archive.
 */
object AllergenSanitizer {

    private val imageValue = Regex("^image/", RegexOption.IGNORE_CASE)

    fun sanitize(raw: List<String>): List<String> = raw
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .filterNot { it.equals("imagePath", ignoreCase = true) }
        .filterNot { imageValue.containsMatchIn(it) }
        .distinct()
}
