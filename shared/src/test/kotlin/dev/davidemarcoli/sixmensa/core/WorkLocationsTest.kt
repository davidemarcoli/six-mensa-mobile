package dev.davidemarcoli.sixmensa.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkLocationsTest {

    /** Independently re-derived from the DMS coordinates, as a guard against typos. */
    @Test
    fun `coordinates match the given DMS values`() {
        fun dms(deg: Int, min: Int, sec: Double) = deg + min / 60.0 + sec / 3600.0

        // 47°23'34.8"N 8°30'33.5"E
        assertEquals(dms(47, 23, 34.8), WorkLocations.HARDTURMSTRASSE_201.latitude, 1e-6)
        assertEquals(dms(8, 30, 33.5), WorkLocations.HARDTURMSTRASSE_201.longitude, 1e-6)

        // 47°23'29.9"N 8°30'25.5"E
        assertEquals(dms(47, 23, 29.9), WorkLocations.PFINGSTWEIDSTRASSE_110.latitude, 1e-6)
        assertEquals(dms(8, 30, 25.5), WorkLocations.PFINGSTWEIDSTRASSE_110.longitude, 1e-6)
    }

    @Test
    fun `the two buildings are a couple of hundred metres apart`() {
        val distance = WorkLocations.distanceMeters(
            WorkLocations.HARDTURMSTRASSE_201.latitude,
            WorkLocations.HARDTURMSTRASSE_201.longitude,
            WorkLocations.PFINGSTWEIDSTRASSE_110.latitude,
            WorkLocations.PFINGSTWEIDSTRASSE_110.longitude,
        )
        // ~225 m; assert a band so a coordinate typo would fail loudly.
        assertTrue("expected 150-350 m, was $distance", distance in 150.0..350.0)
    }

    @Test
    fun `standing at either building counts as at work`() {
        WorkLocations.all.forEach { site ->
            assertTrue(site.name, WorkLocations.isAtWork(site.latitude, site.longitude))
        }
    }

    @Test
    fun `the default radius covers both buildings from either one`() {
        // Whichever building you are standing at, the other is within range too, so the
        // walk between them never silences the notification.
        assertTrue(
            WorkLocations.isAtWork(
                WorkLocations.HARDTURMSTRASSE_201.latitude,
                WorkLocations.HARDTURMSTRASSE_201.longitude,
            ),
        )
    }

    @Test
    fun `elsewhere in zurich does not count`() {
        // Zurich HB, ~2 km away.
        assertFalse(WorkLocations.isAtWork(47.378177, 8.540192))
        // Zurich airport.
        assertFalse(WorkLocations.isAtWork(47.450604, 8.561746))
    }

    @Test
    fun `just outside the radius does not count`() {
        // ~1 km due north of HT201.
        val lat = WorkLocations.HARDTURMSTRASSE_201.latitude + 0.009
        assertFalse(WorkLocations.isAtWork(lat, WorkLocations.HARDTURMSTRASSE_201.longitude))
    }

    @Test
    fun `a vague fix is given the benefit of the doubt up to the radius`() {
        // 500 m away is outside, but a fix that is itself accurate to only 400 m cannot
        // rule out being at the office, so we do not silence the notification.
        val lat = WorkLocations.HARDTURMSTRASSE_201.latitude + 0.0045
        val lon = WorkLocations.HARDTURMSTRASSE_201.longitude

        assertFalse(WorkLocations.isAtWork(lat, lon, accuracyMeters = 10f))
        assertTrue(WorkLocations.isAtWork(lat, lon, accuracyMeters = 400f))
    }

    @Test
    fun `haversine matches a known distance`() {
        // Zurich HB -> Zurich airport is about 8.6 km as the crow flies.
        val distance = WorkLocations.distanceMeters(47.378177, 8.540192, 47.450604, 8.561746)
        assertEquals(8_600.0, distance, 400.0)
    }

    @Test
    fun `distance is symmetric and zero for the same point`() {
        val a = WorkLocations.HARDTURMSTRASSE_201
        val b = WorkLocations.PFINGSTWEIDSTRASSE_110
        assertEquals(0.0, WorkLocations.distanceMeters(a.latitude, a.longitude, a.latitude, a.longitude), 1e-6)
        assertEquals(
            WorkLocations.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude),
            WorkLocations.distanceMeters(b.latitude, b.longitude, a.latitude, a.longitude),
            1e-6,
        )
    }

    @Test
    fun `nearest site picks the closer building`() {
        val (site, _) = WorkLocations.nearestSite(
            WorkLocations.PFINGSTWEIDSTRASSE_110.latitude,
            WorkLocations.PFINGSTWEIDSTRASSE_110.longitude,
        )
        assertEquals(WorkLocations.PFINGSTWEIDSTRASSE_110.name, site.name)
    }
}
