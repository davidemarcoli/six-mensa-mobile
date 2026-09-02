package dev.davidemarcoli.sixmensa.wear.di

import dev.davidemarcoli.sixmensa.data.WidgetUpdater
import dev.davidemarcoli.sixmensa.wear.tile.MenuTileUpdater
import dev.davidemarcoli.sixmensa.wear.ui.settings.WearSettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The watch-only half of the graph; everything else comes from `sharedModule`. */
val wearModule = module {

    single { MenuTileUpdater(androidContext()) }

    // Same seam the phone uses for its Glance widget — here it drives the tile instead,
    // so a refreshed week redraws the tile without the data layer knowing what a tile is.
    single<WidgetUpdater> { WidgetUpdater { get<MenuTileUpdater>().updateAll() } }

    viewModel { WearSettingsViewModel(settingsRepository = get()) }
}
