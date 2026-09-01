package dev.davidemarcoli.sixmensa.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.davidemarcoli.sixmensa.MainActivity
import dev.davidemarcoli.sixmensa.R
import dev.davidemarcoli.sixmensa.core.AppClock
import dev.davidemarcoli.sixmensa.data.MenuRepository
import dev.davidemarcoli.sixmensa.data.local.SettingsRepository
import dev.davidemarcoli.sixmensa.domain.DayMenu
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Lets the data layer refresh the widget without depending on Glance. */
class MenuWidgetUpdater(private val context: Context) {
    suspend fun updateAll() {
        try {
            MenuWidget().updateAll(context)
        } catch (e: Exception) {
            // Never let a widget problem take down whatever triggered the refresh, but do
            // not swallow it silently either — that hid a real failure once already.
            Log.w("MenuWidget", "updateAll failed", e)
        }
    }
}

/**
 * Home-screen widget showing today's menu for the preferred restaurant.
 *
 * Reads settings and the cached week **straight off disk**. The widget host can start this
 * process cold with no Activity or ViewModel alive, so it must never rely on in-memory state.
 */
class MenuWidget : GlanceAppWidget(), KoinComponent {

    private val menuRepository: MenuRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val clock: AppClock by inject()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Only used as the first frame, to avoid a flash of the wrong restaurant.
        val initialSettings = settingsRepository.current()

        provideContent {
            // Read inside the composition, not before it. `provideContent` suspends and
            // keeps the composition alive, so an update recomposes rather than re-running
            // provideGlance — anything captured outside would stay frozen at its initial
            // value and leave the widget one change behind.
            val settings by settingsRepository.settings.collectAsState(initial = initialSettings)

            val cached by remember(settings.restaurant, settings.contentLanguage) {
                menuRepository.observeWeek(settings.restaurant, settings.contentLanguage)
            }.collectAsState(initial = null)

            val today = clock.today()
            val day = cached?.value?.firstOrNull { it.isToday(today) }

            GlanceTheme {
                WidgetContent(
                    restaurantName = settings.restaurant.displayName,
                    day = day,
                    emptyText = context.getString(R.string.widget_no_menu),
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        restaurantName: String,
        day: DayMenu?,
        emptyText: String,
    ) {
        val openApp = actionStartActivity<MainActivity>()

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp),
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(openApp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = restaurantName,
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.width(8.dp))
                day?.rawDate?.takeIf { it.isNotEmpty() }?.let {
                    Text(
                        text = it,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                    )
                }
            }

            Spacer(GlanceModifier.height(6.dp))

            if (day == null || day.items.isEmpty()) {
                Text(
                    text = emptyText,
                    modifier = GlanceModifier.clickable(openApp),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                )
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(day.items) { item ->
                        Column(modifier = GlanceModifier.clickable(openApp)) {
                            Text(
                                text = item.title,
                                maxLines = 1,
                                style = TextStyle(color = GlanceTheme.colors.onSurface),
                            )
                            Spacer(GlanceModifier.height(2.dp))
                        }
                    }
                }
            }
        }
    }
}

class MenuWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MenuWidget()
}

