package dev.davidemarcoli.sixmensa.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.davidemarcoli.sixmensa.notification.DailyMenuWorker

/**
 * Debug-only: runs [DailyMenuWorker] immediately so the notification can be exercised
 * without waiting for the scheduled time.
 *
 *   adb shell am broadcast -a dev.davidemarcoli.sixmensa.DEBUG_NOTIFY \
 *     -n dev.davidemarcoli.sixmensa.debug/dev.davidemarcoli.sixmensa.debug.DebugNotifyReceiver
 *
 * Lives in the `debug` source set, so it cannot ship in a release build.
 */
class DebugNotifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WorkManager.getInstance(context)
            .enqueue(OneTimeWorkRequestBuilder<DailyMenuWorker>().build())
    }
}
