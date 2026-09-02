package dev.davidemarcoli.sixmensa.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import dev.davidemarcoli.sixmensa.core.ContentLanguage
import java.util.Locale

/**
 * Applies the user's language setting to the app's own strings, so a single control drives
 * both the UI chrome (`values/` vs `values-de/`) and the `?language=` menu content.
 *
 * Done in-composition rather than via `AppCompatDelegate.setApplicationLocales` on purpose:
 * that API would add a second, OS-level source of truth (Android's per-app language screen)
 * which could drift out of sync with the DataStore setting that also drives the API calls.
 * The default still derives from the system locale on first run, in Settings.defaults().
 *
 * The wrapper below is load-bearing. `createConfigurationContext` returns a *detached*
 * context with no Activity in its `ContextWrapper` chain, and a surprising amount of
 * AndroidX resolves the Activity by walking up from `LocalContext` — `startActivity`,
 * and `rememberLauncherForActivityResult` via `LocalActivityResultRegistryOwner`. Providing
 * that detached context directly breaks all of them. So we keep the original context as the
 * base and swap only the resources.
 */
@Composable
fun ProvideAppLocale(
    language: ContentLanguage,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val localizedContext = remember(context, language) {
        val locale = Locale.forLanguageTag(language.wire)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        val localized = context.createConfigurationContext(configuration)

        object : ContextWrapper(context) {
            override fun getResources(): Resources = localized.resources
            override fun getAssets(): AssetManager = localized.assets
        }
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        content = content,
    )
}
