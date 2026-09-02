package dev.davidemarcoli.sixmensa.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dev.davidemarcoli.sixmensa.core.AppClock
import dev.davidemarcoli.sixmensa.core.SystemAppClock
import dev.davidemarcoli.sixmensa.data.HistoryRepository
import dev.davidemarcoli.sixmensa.data.MenuRepository
import dev.davidemarcoli.sixmensa.data.PdfRepository
import dev.davidemarcoli.sixmensa.data.RestaurantSelection
import dev.davidemarcoli.sixmensa.data.WidgetUpdater
import dev.davidemarcoli.sixmensa.data.local.JsonDiskCache
import dev.davidemarcoli.sixmensa.data.local.SettingsRepository
import dev.davidemarcoli.sixmensa.data.remote.MensaApi
import dev.davidemarcoli.sixmensa.presentation.menu.MenuViewModel
import dev.davidemarcoli.sixmensa.shared.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Everything below the UI, shared by the phone app and the watch app. Anything that needs a
 * surface only one of them has — the Glance widget, the notification worker, the Wear tile —
 * is bound in that module's own Koin module instead.
 */
val sharedModule = module {

    single {
        Json {
            // The API adds fields over time (imagePath appeared this way).
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
            isLenient = true
        }
    }

    single {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .cache(Cache(File(androidContext().cacheDir, "http"), 5L * 1024 * 1024))
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        },
                    )
                }
            }
            .build()
    }

    single<MensaApi> {
        Retrofit.Builder()
            // Trailing slash matters: Retrofit resolves relative paths against it.
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(get())
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MensaApi::class.java)
    }

    single<AppClock> { SystemAppClock }

    single { JsonDiskCache(root = File(androidContext().filesDir, "cache"), json = get()) }

    single { SettingsRepository(androidContext()) }

    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single { RestaurantSelection(settingsRepository = get(), scope = get()) }

    single {
        MenuRepository(
            api = get(),
            cache = get(),
            clock = get(),
            scope = get(),
            // Optional: the phone binds the widget, the watch binds the tile, and a
            // consumer with neither simply gets the no-op default.
            widgetUpdater = getOrNull() ?: WidgetUpdater { },
        )
    }

    single { HistoryRepository(api = get(), cache = get(), clock = get()) }

    single {
        PdfRepository(
            api = get(),
            cache = get(),
            httpClient = get(),
            pdfDir = File(androidContext().cacheDir, "pdf"),
        )
    }

    viewModel {
        MenuViewModel(
            menuRepository = get(),
            settingsRepository = get(),
            restaurantSelection = get(),
            clock = get(),
        )
    }
}
