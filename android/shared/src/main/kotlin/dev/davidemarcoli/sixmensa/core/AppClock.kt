package dev.davidemarcoli.sixmensa.core

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * All date logic is anchored to Zurich, never the device zone: the canteen is in Zurich,
 * so "today's menu" and the 10:30 notification must stay correct when the phone travels.
 */
interface AppClock {
    val zone: ZoneId
    fun today(): LocalDate
    fun now(): ZonedDateTime
}

object SystemAppClock : AppClock {
    override val zone: ZoneId = ZoneId.of("Europe/Zurich")
    override fun today(): LocalDate = LocalDate.now(zone)
    override fun now(): ZonedDateTime = ZonedDateTime.now(zone)
}

/** Test double. */
class FixedAppClock(private val fixed: ZonedDateTime) : AppClock {
    override val zone: ZoneId = fixed.zone
    override fun today(): LocalDate = fixed.toLocalDate()
    override fun now(): ZonedDateTime = fixed
}
