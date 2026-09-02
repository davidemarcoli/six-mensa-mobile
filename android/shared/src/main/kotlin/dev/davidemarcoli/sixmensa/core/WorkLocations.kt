package dev.davidemarcoli.sixmensa.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The two SIX buildings the canteens belong to. Kept free of Android types so the geometry
 * is unit testable.
 */
object WorkLocations {

    data class Site(val name: String, val latitude: Double, val longitude: Double)

    /** 47°23'34.8"N 8°30'33.5"E */
    val HARDTURMSTRASSE_201 = Site("Hardturmstrasse 201", 47.393000, 8.509306)

    /** 47°23'29.9"N 8°30'25.5"E */
    val PFINGSTWEIDSTRASSE_110 = Site("Pfingstweidstrasse 110", 47.391639, 8.507083)

    val all = listOf(HARDTURMSTRASSE_201, PFINGSTWEIDSTRASSE_110)

    /**
     * The two buildings are about 225 m apart, so this comfortably covers both plus the
     * walk between them, while still excluding home or the commute.
     */
    const val DEFAULT_RADIUS_METERS = 300.0

    private const val EARTH_RADIUS_METERS = 6_371_008.8

    /** Haversine great-circle distance. */
    fun distanceMeters(
        latitude1: Double,
        longitude1: Double,
        latitude2: Double,
        longitude2: Double,
    ): Double {
        val dLat = Math.toRadians(latitude2 - latitude1)
        val dLon = Math.toRadians(longitude2 - longitude1)
        val lat1 = Math.toRadians(latitude1)
        val lat2 = Math.toRadians(latitude2)

        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a).coerceIn(0.0, 1.0))
    }

    fun nearestSite(latitude: Double, longitude: Double): Pair<Site, Double> =
        all.map { it to distanceMeters(latitude, longitude, it.latitude, it.longitude) }
            .minBy { it.second }

    fun isAtWork(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double = DEFAULT_RADIUS_METERS,
        /** A fix accurate to worse than the radius can't answer the question. */
        accuracyMeters: Float? = null,
    ): Boolean {
        val (_, distance) = nearestSite(latitude, longitude)
        val slack = (accuracyMeters ?: 0f).toDouble().coerceAtMost(radiusMeters)
        return distance <= radiusMeters + slack
    }
}
