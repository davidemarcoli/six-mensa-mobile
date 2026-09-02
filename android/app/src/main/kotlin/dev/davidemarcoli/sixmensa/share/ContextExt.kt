package dev.davidemarcoli.sixmensa.share

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Walks the [ContextWrapper] chain to the hosting Activity, or null if there isn't one.
 *
 * Necessary because `ProvideAppLocale` replaces `LocalContext` with the result of
 * `createConfigurationContext`, which is a plain wrapper rather than the Activity. Anything
 * that calls `startActivity` off `LocalContext` must unwrap first, or Android throws
 * "Calling startActivity() from outside of an Activity context requires FLAG_ACTIVITY_NEW_TASK".
 */
fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
