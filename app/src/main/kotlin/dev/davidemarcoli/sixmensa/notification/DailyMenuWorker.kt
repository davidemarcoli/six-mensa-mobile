package dev.davidemarcoli.sixmensa.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.davidemarcoli.sixmensa.core.AppClock
import dev.davidemarcoli.sixmensa.data.MenuRepository
import dev.davidemarcoli.sixmensa.data.WorkLocationChecker
import dev.davidemarcoli.sixmensa.data.local.SettingsRepository
import dev.davidemarcoli.sixmensa.widget.MenuWidgetUpdater
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Posts today's menu, then schedules the next weekday run.
 *
 * Dependencies come from Koin's global context rather than a custom WorkerFactory: a worker
 * is constructed by the framework in a process that may have no Activity or ViewModel alive,
 * and `KoinComponent` resolves that without the WorkManager-initializer surgery that a
 * Hilt-style injected worker would need.
 */
class DailyMenuWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val menuRepository: MenuRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val clock: AppClock by inject()
    private val widgetUpdater: MenuWidgetUpdater by inject()
    private val workLocationChecker: WorkLocationChecker by inject()

    override suspend fun doWork(): Result {
        val settings = settingsRepository.current()
        try {
            if (!settings.notificationsEnabled) return Result.success()

            val restaurant = settings.restaurant
            val language = settings.contentLanguage

            // Null means "couldn't tell" — fail open and notify, so a GPS hiccup at the
            // office never silently costs you the menu.
            if (settings.notifyOnlyAtWork && workLocationChecker.isAtWork() == false) {
                return Result.success()
            }

            menuRepository.refresh(restaurant, language)
            widgetUpdater.updateAll()

            val today = clock.today()
            val day = menuRepository.peekCached(restaurant, language)
                ?.value
                ?.firstOrNull { it.isToday(today) }

            // Nothing served today (weekend, or HTP on a Friday) — stay quiet rather than
            // posting an empty notification every week.
            if (day != null && day.items.isNotEmpty()) {
                Notifications.showDailyMenu(applicationContext, restaurant, day)
            }

            return Result.success()
        } catch (e: Exception) {
            return Result.success()
        } finally {
            // Always rechain, even when the fetch failed — otherwise one bad morning would
            // silently end the notification series forever.
            if (settings.notificationsEnabled) {
                NotificationScheduler(applicationContext, clock)
                    .schedule(settings.notificationTime)
            }
        }
    }
}
