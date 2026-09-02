package dev.davidemarcoli.sixmensa

import android.app.Application
import dev.davidemarcoli.sixmensa.data.MenuRepository
import dev.davidemarcoli.sixmensa.data.local.SettingsRepository
import dev.davidemarcoli.sixmensa.di.appModule
import dev.davidemarcoli.sixmensa.di.sharedModule
import dev.davidemarcoli.sixmensa.notification.NotificationScheduler
import dev.davidemarcoli.sixmensa.notification.Notifications
import dev.davidemarcoli.sixmensa.share.Shortcuts
import dev.davidemarcoli.sixmensa.widget.MenuWidgetUpdater
import dev.davidemarcoli.sixmensa.widget.WidgetRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalTime
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class SixMensaApp : Application() {

    private val settingsRepository: SettingsRepository by inject()
    private val notificationScheduler: NotificationScheduler by inject()
    private val menuRepository: MenuRepository by inject()
    private val widgetUpdater: MenuWidgetUpdater by inject()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@SixMensaApp)
            modules(sharedModule, appModule)
        }

        Notifications.createChannel(this)
        Shortcuts.register(this)
        WidgetRefreshWorker.schedule(this)

        observeSettings()
    }

    /**
     * Keeps the out-of-app surfaces in step with the settings that drive them. Without this
     * the widget only caught up on the next successful refresh or the 6-hour worker, and a
     * changed notification time did not apply until the next app start.
     */
    private fun observeSettings() {
        appScope.launch {
            val settings = settingsRepository.current()
            if (settings.notificationsEnabled) {
                notificationScheduler.schedule(settings.notificationTime)
            } else {
                notificationScheduler.cancel()
            }
        }

        appScope.launch {
            settingsRepository.settings
                .map { it.restaurant to it.contentLanguage }
                .distinctUntilChanged()
                .drop(1) // the initial value is already handled on start
                .collect { (restaurant, language) ->
                    // Redraw straight away off the existing cache so the change is visible
                    // even offline, then refresh, which redraws again with fresh data.
                    widgetUpdater.updateAll()
                    menuRepository.refresh(restaurant, language)
                }
        }

        appScope.launch {
            settingsRepository.settings
                .map { Triple(it.notificationsEnabled, it.notificationHour, it.notificationMinute) }
                .distinctUntilChanged()
                .drop(1)
                .collect { (enabled, hour, minute) ->
                    if (enabled) {
                        notificationScheduler.schedule(LocalTime.of(hour, minute))
                    } else {
                        notificationScheduler.cancel()
                    }
                }
        }
    }
}
