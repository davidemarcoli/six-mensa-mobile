package dev.davidemarcoli.sixmensa.data

import dev.davidemarcoli.sixmensa.core.AllergenSanitizer
import dev.davidemarcoli.sixmensa.core.DayResolver
import dev.davidemarcoli.sixmensa.core.DietaryType
import dev.davidemarcoli.sixmensa.core.MenuTypeNormalizer
import dev.davidemarcoli.sixmensa.data.remote.dto.DayMenuDto
import dev.davidemarcoli.sixmensa.data.remote.dto.HistoryEntryDto
import dev.davidemarcoli.sixmensa.data.remote.dto.MenuItemDto
import dev.davidemarcoli.sixmensa.data.remote.dto.PriceDto
import dev.davidemarcoli.sixmensa.domain.DayMenu
import dev.davidemarcoli.sixmensa.domain.FlatMenuItem
import dev.davidemarcoli.sixmensa.domain.MenuItem
import dev.davidemarcoli.sixmensa.domain.Price
import java.time.LocalDate

/**
 * The single boundary where wire quirks are neutralised. Nothing downstream of here ever
 * sees a raw `menues` entry, an unnormalised `type`, a polluted allergen list or a blank origin.
 */

fun MenuItemDto.toDomain(): MenuItem = MenuItem(
    title = title.trim(),
    description = description.trim(),
    rawType = type,
    type = MenuTypeNormalizer.normalize(type),
    dietaryType = DietaryType.fromWire(dietaryType),
    price = price?.toDomain()?.takeIf { it.hasAny },
    origin = origin?.trim()?.takeIf { it.isNotEmpty() },
    allergens = AllergenSanitizer.sanitize(allergens),
)

fun PriceDto.toDomain(): Price = Price(intern = intern, extern = extern)

fun DayMenuDto.toDomain(today: LocalDate): DayMenu = DayMenu(
    rawDate = date,
    rawDay = day,
    dayOfWeek = DayResolver.parseDayOfWeek(day),
    date = DayResolver.resolveDate(date, today),
    items = items.map { it.toDomain() },
)

fun List<DayMenuDto>.toDomain(today: LocalDate): List<DayMenu> = map { it.toDomain(today) }

fun List<HistoryEntryDto>.flatten(today: LocalDate): List<FlatMenuItem> =
    flatMap { entry ->
        entry.data.flatMap { day ->
            day.items.map { item ->
                FlatMenuItem(
                    restaurant = entry.restaurant,
                    year = entry.year,
                    month = entry.month,
                    week = entry.week,
                    rawDay = day.day,
                    rawDate = day.date,
                    item = item.toDomain(),
                )
            }
        }
    }
