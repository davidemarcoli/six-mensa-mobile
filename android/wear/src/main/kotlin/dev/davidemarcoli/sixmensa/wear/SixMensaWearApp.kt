package dev.davidemarcoli.sixmensa.wear

import android.app.Application
import dev.davidemarcoli.sixmensa.data.MenuRepository
import dev.davidemarcoli.sixmensa.data.local.SettingsRepository
import dev.davidemarcoli.sixmensa.di.sharedModule
import dev.davidemarcoli.sixmensa.wear.di.wearModule
import dev.davidemarcoli.sixmensa.wear.tile.MenuTileUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class SixMensaWearApp : Application() {

    private val settingsRepository: SettingsRepository by inject()
    private val menuRepository: MenuRepository by inject()
    private val tileUpdater: MenuTileUpdater by inject()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@SixMensaWearApp)
            modules(sharedModule, wearModule)
        }

        // The watch's counterpart to the phone's widget observer: a restaurant or language
        // change has to reach the tile even though nothing refreshed the week yet.
        appScope.launch {
            settingsRepository.settings
                .map { it.restaurant to it.contentLanguage }
                .distinctUntilChanged()
                .drop(1)
                .collect { (restaurant, language) ->
                    tileUpdater.updateAll()
                    menuRepository.refresh(restaurant, language)
                }
        }
    }
}
