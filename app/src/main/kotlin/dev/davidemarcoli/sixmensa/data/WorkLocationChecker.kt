package dev.davidemarcoli.sixmensa.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import dev.davidemarcoli.sixmensa.core.WorkLocations
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Answers "is the phone at one of the SIX buildings right now?".
 *
 * Returns **null** when it cannot tell — no permission, location disabled, no fix in time.
 * Callers treat null as "notify anyway", so a GPS hiccup never silently costs you the menu.
 */
class WorkLocationChecker(private val context: Context) {

    suspend fun isAtWork(): Boolean? {
        if (!hasPermission()) {
            Log.d(TAG, "no location permission")
            return null
        }

        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val location = recentCachedFix(manager) ?: freshFix(manager)

        if (location == null) {
            Log.d(TAG, "no fix available")
            return null
        }

        val atWork = WorkLocations.isAtWork(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
        )
        val (site, distance) = WorkLocations.nearestSite(location.latitude, location.longitude)
        Log.d(TAG, "nearest ${site.name}: ${distance.toInt()} m -> atWork=$atWork")
        return atWork
    }

    private fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return false

        // From API 29 a background worker also needs the "all the time" grant, otherwise
        // every provider simply returns nothing while the app is not in the foreground.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /** A very recent fix is free, so try that before waking the radios. */
    @SuppressLint("MissingPermission")
    private fun recentCachedFix(manager: LocationManager): Location? =
        manager.allProviders
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < MAX_CACHED_AGE_MILLIS }
            .maxByOrNull { it.time }

    @SuppressLint("MissingPermission")
    private suspend fun freshFix(manager: LocationManager): Location? {
        val provider = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        ).firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return null

        return withTimeoutOrNull(FIX_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = CancellationSignal()
                    continuation.invokeOnCancellation { signal.cancel() }
                    // mainExecutor rather than a new single-thread pool: this runs once a
                    // day and an unshut-down executor would leak a thread each time.
                    manager.getCurrentLocation(
                        provider,
                        signal,
                        ContextCompat.getMainExecutor(context),
                    ) { location -> if (continuation.isActive) continuation.resume(location) }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            manager.removeUpdates(this)
                            if (continuation.isActive) continuation.resume(location)
                        }

                        @Deprecated("Required on API < 29")
                        override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) = Unit
                        override fun onProviderDisabled(provider: String) = Unit
                        override fun onProviderEnabled(provider: String) = Unit
                    }
                    continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                }
            }
        }
    }

    private companion object {
        const val TAG = "WorkLocation"
        const val MAX_CACHED_AGE_MILLIS = 10 * 60 * 1000L
        const val FIX_TIMEOUT_MILLIS = 60_000L
    }
}
