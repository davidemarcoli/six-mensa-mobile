package dev.davidemarcoli.sixmensa.share

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dev.davidemarcoli.sixmensa.MainActivity
import dev.davidemarcoli.sixmensa.R

/**
 * Long-press launcher shortcuts.
 *
 * Registered in code rather than `res/xml/shortcuts.xml` because a static shortcut needs an
 * explicit `targetPackage`, and the debug build carries an `applicationIdSuffix` — a literal
 * in XML would silently break one of the two build types (manifest placeholders don't apply
 * to XML resources).
 */
object Shortcuts {

    const val EXTRA_DESTINATION = "destination"
    const val DESTINATION_MENU = "menu"
    const val DESTINATION_COMPARE = "compare"

    fun register(context: Context) {
        val shortcuts = listOf(
            build(
                context = context,
                id = "today",
                destination = DESTINATION_MENU,
                shortLabel = context.getString(R.string.shortcut_today_short),
                longLabel = context.getString(R.string.shortcut_today_long),
                icon = R.drawable.ic_shortcut_today,
            ),
            build(
                context = context,
                id = "compare",
                destination = DESTINATION_COMPARE,
                shortLabel = context.getString(R.string.shortcut_compare_short),
                longLabel = context.getString(R.string.shortcut_compare_long),
                icon = R.drawable.ic_shortcut_compare,
            ),
        )
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    private fun build(
        context: Context,
        id: String,
        destination: String,
        shortLabel: String,
        longLabel: String,
        icon: Int,
    ) = ShortcutInfoCompat.Builder(context, id)
        .setShortLabel(shortLabel)
        .setLongLabel(longLabel)
        .setIcon(IconCompat.createWithResource(context, icon))
        .setIntent(
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_DESTINATION, destination)
            },
        )
        .build()
}
