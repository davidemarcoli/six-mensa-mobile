package dev.davidemarcoli.sixmensa.di

import dev.davidemarcoli.sixmensa.data.WidgetUpdater
import dev.davidemarcoli.sixmensa.data.WorkLocationChecker
import dev.davidemarcoli.sixmensa.notification.NotificationScheduler
import dev.davidemarcoli.sixmensa.widget.MenuWidgetUpdater
import dev.davidemarcoli.sixmensa.ui.compare.CompareViewModel
import dev.davidemarcoli.sixmensa.ui.pdf.PdfPageRenderer
import dev.davidemarcoli.sixmensa.ui.pdf.PdfViewModel
import dev.davidemarcoli.sixmensa.ui.settings.SettingsViewModel
import dev.davidemarcoli.sixmensa.ui.stats.StatsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The phone-only half of the graph; everything else comes from [sharedModule]. */
val appModule = module {

    single { WorkLocationChecker(androidContext()) }

    single { MenuWidgetUpdater(androidContext()) }

    // Refreshing the week also refreshes the home-screen widget.
    single<WidgetUpdater> { WidgetUpdater { get<MenuWidgetUpdater>().updateAll() } }

    single { NotificationScheduler(context = androidContext(), clock = get()) }

    viewModel { CompareViewModel(menuRepository = get(), settingsRepository = get(), clock = get()) }
    viewModel { SettingsViewModel(settingsRepository = get(), api = get()) }
    viewModel { StatsViewModel(historyRepository = get(), clock = get()) }

    single { PdfPageRenderer() }
    viewModel { PdfViewModel(pdfRepository = get(), renderer = get()) }
}
