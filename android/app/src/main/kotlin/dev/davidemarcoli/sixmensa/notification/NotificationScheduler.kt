package dev.davidemarcoli.sixmensa.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.davidemarcoli.sixmensa.core.AppClock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Schedules the daily menu notification.
 *
 * Deliberately a **self-rescheduling one-shot** rather than a PeriodicWorkRequest: periodic
 * work cannot express "weekdays only" and drifts relative to wall-clock time, so a 10:30
 * notification would slowly wander. Each run enqueues the next one.
 */
class NotificationScheduler(
    private val context: Context,
    private val clock: AppClock,
) {

    fun schedule(at: LocalTime) {
        val now = clock.now()
        val next = nextRun(now, at)
        val delay = Duration.between(now, next)

        val request = OneTimeWorkRequestBuilder<DailyMenuWorker>()
            .setInitialDelay(delay)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "daily-menu"

        /** Next Mon–Fri occurrence of [at], strictly in the future, in the canteen's zone. */
        fun nextRun(now: ZonedDateTime, at: LocalTime): ZonedDateTime {
            var candidate = now.with(at)
            if (!candidate.isAfter(now)) candidate = candidate.plusDays(1).with(at)
            while (
                candidate.dayOfWeek == DayOfWeek.SATURDAY ||
                candidate.dayOfWeek == DayOfWeek.SUNDAY
            ) {
                candidate = candidate.plusDays(1).with(at)
            }
            return candidate
        }
    }
}
