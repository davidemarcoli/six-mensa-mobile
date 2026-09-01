package dev.davidemarcoli.sixmensa.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.davidemarcoli.sixmensa.data.MenuRepository
import dev.davidemarcoli.sixmensa.data.local.SettingsRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Keeps the widget fresh even if the app is never opened. Unlike the notification this is a
 * genuine every-N-hours job with no wall-clock meaning, so PeriodicWorkRequest is the right
 * fit here.
 */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val menuRepository: MenuRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val widgetUpdater: MenuWidgetUpdater by inject()

    override suspend fun doWork(): Result {
        val settings = settingsRepository.current()
        menuRepository.refresh(settings.restaurant, settings.contentLanguage)
        widgetUpdater.updateAll()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "widget-refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
