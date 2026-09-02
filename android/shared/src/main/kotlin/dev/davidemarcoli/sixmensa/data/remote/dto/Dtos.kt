package dev.davidemarcoli.sixmensa.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DayMenuDto(
    /** Localized, no year: "27. Juli" | "July 27". */
    val date: String = "",
    /** Localized weekday: "Montag" | "Monday". */
    val day: String = "",
    // DO NOT "fix" this spelling: `menues` is the actual field name on the wire.
    @SerialName("menues") val items: List<MenuItemDto> = emptyList(),
)

@Serializable
data class MenuItemDto(
    val title: String = "",
    val description: String = "",
    /** Free text; 15 distinct values observed. Normalised after parsing, never an enum here. */
    val type: String = "",
    /** "meat" | "vegetarian" | "vegan". */
    val dietaryType: String = "",
    val price: PriceDto? = null,
    val origin: String? = null,
    /** LLM-translated free strings, and occasionally polluted with serializer junk. */
    val allergens: List<String> = emptyList(),
    /** Currently always 404s (`imageGeneration: false`). Parsed but unused. */
    val imagePath: String? = null,
)

@Serializable
data class PriceDto(
    val intern: Double? = null,
    val extern: Double? = null,
)

@Serializable
data class HistoryEntryDto(
    val restaurant: String = "",
    val year: Int = 0,
    val month: Int = 0,
    val week: Int = 0,
    val data: List<DayMenuDto> = emptyList(),
)

@Serializable
data class StatusDto(
    val version: String = "",
    val features: FeaturesDto = FeaturesDto(),
    val restaurants: List<String> = emptyList(),
)

@Serializable
data class FeaturesDto(
    val imageGeneration: Boolean = false,
    val menuTranslation: Boolean = false,
    val autoUpdate: Boolean = false,
    val pdfScraping: Boolean = false,
    val updateInterval: String = "",
)
