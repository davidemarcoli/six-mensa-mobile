package dev.davidemarcoli.sixmensa.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

/**
 * On Wear the palette comes from the active watch face, not from the phone app's seed
 * colour — matching the face is what makes an app feel native there. Watches without a
 * dynamic-colour-capable face fall back to the standard Wear scheme.
 */
@Composable
fun SixMensaWearTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dynamic: ColorScheme? = remember(context) {
        runCatching { dynamicColorScheme(context) }.getOrNull()
    }

    if (dynamic != null) {
        MaterialTheme(colorScheme = dynamic, content = content)
    } else {
        MaterialTheme(content = content)
    }
}
