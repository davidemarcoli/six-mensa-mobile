package dev.davidemarcoli.sixmensa.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.rememberDynamicColorScheme
import dev.davidemarcoli.sixmensa.data.local.Settings
import dev.davidemarcoli.sixmensa.data.local.ThemeMode

/**
 * The webapp's default accent colour (`six-mensa/lib/store.ts`). Used as the palette
 * seed whenever Material You dynamic colour is unavailable or switched off.
 */
val BrandSeed = Color(Settings.DEFAULT_SEED_COLOR)

val supportsDynamicColor: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Colour precedence: Material You (API 31+) -> custom seed -> [BrandSeed].
 *
 * On API 26-30 the seeded path is the only path, so it is exercised regardless of whether
 * anyone ever touches the accent-colour setting.
 */
@Composable
fun SixMensaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColor: Boolean = true,
    seedColor: Color = BrandSeed,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (useDynamicColor && supportsDynamicColor) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        // material-kolor runs Google's real HCT algorithm at runtime, which is what lets
        // the accent stay a live colour picker instead of a baked-in scheme.
        rememberDynamicColorScheme(seedColor = seedColor, isDark = darkTheme)
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
