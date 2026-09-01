package dev.davidemarcoli.sixmensa.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import okhttp3.OkHttpClient
import org.junit.Test
import org.koin.dsl.module
import org.koin.test.verify.verify
import java.io.File

/**
 * Koin resolves at runtime, so a missing binding would otherwise surface as a crash the
 * first time you navigate to a screen. This walks the whole graph at build time instead,
 * which is what buys back the compile-time safety Hilt would have given us.
 *
 * Verifies the two modules **together**, the way the app actually starts them — checking
 * [appModule] alone would not catch a phone screen depending on a binding :shared dropped.
 */
class AppModuleTest {

    private val wholeGraph = module { includes(sharedModule, appModule) }

    @Test
    fun `every dependency in the graph can be resolved`() {
        wholeGraph.verify(
            // Provided by androidContext()/androidApplication() at runtime, and by
            // constructor parameters that Koin does not need to resolve itself.
            extraTypes = listOf(
                Context::class,
                Application::class,
                SavedStateHandle::class,
                OkHttpClient::class,
                File::class,
            ),
        )
    }
}
