package dev.davidemarcoli.sixmensa.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dev.davidemarcoli.sixmensa.data.WidgetUpdater
import okhttp3.OkHttpClient
import org.junit.Test
import org.koin.test.verify.verify
import java.io.File

/**
 * Koin resolves at runtime, so a missing binding would otherwise surface as a crash the
 * first time you navigate to a screen. This walks the whole graph at build time instead,
 * which is what buys back the compile-time safety Hilt would have given us.
 */
class SharedModuleTest {

    @Test
    fun `every dependency in the graph can be resolved`() {
        sharedModule.verify(
            // Provided by androidContext()/androidApplication() at runtime, and by
            // constructor parameters that Koin does not need to resolve itself.
            extraTypes = listOf(
                Context::class,
                Application::class,
                SavedStateHandle::class,
                OkHttpClient::class,
                File::class,
                // Bound by the host app (widget on phone, tile on watch), optional here.
                WidgetUpdater::class,
            ),
        )
    }
}
