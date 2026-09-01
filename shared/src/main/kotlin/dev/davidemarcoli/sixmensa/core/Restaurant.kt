package dev.davidemarcoli.sixmensa.core

/** The two SIX canteens. [wire] is the path segment the API expects. */
enum class Restaurant(val wire: String, val displayName: String) {
    HTP("htp", "HTP"),
    HT201("ht201", "HT 201"),
    ;

    companion object {
        fun fromWire(value: String?): Restaurant? =
            entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) }
    }
}

/**
 * Language of the *menu content*, sent to the API as `?language=`. Independent of the
 * app's UI language, which follows the system locale via `values/` + `values-de/`.
 */
enum class ContentLanguage(val wire: String) {
    DE("de"),
    EN("en"),
    ;

    companion object {
        fun fromWire(value: String?): ContentLanguage? =
            entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) }
    }
}

/** Wire values observed across the whole history archive: only these three. */
enum class DietaryType {
    MEAT,
    VEGETARIAN,
    VEGAN,
    UNKNOWN,
    ;

    companion object {
        fun fromWire(value: String?): DietaryType = when (value?.trim()?.lowercase()) {
            "meat" -> MEAT
            "vegetarian" -> VEGETARIAN
            "vegan" -> VEGAN
            else -> UNKNOWN
        }
    }
}
