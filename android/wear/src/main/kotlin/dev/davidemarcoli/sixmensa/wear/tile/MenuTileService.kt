package dev.davidemarcoli.sixmensa.wear.tile

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import dev.davidemarcoli.sixmensa.core.AppClock
import dev.davidemarcoli.sixmensa.data.MenuRepository
import dev.davidemarcoli.sixmensa.data.local.SettingsRepository
import dev.davidemarcoli.sixmensa.domain.DayMenu
import dev.davidemarcoli.sixmensa.wear.MainActivity
import dev.davidemarcoli.sixmensa.wear.R
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Lets the data layer refresh the tile without depending on the tiles library. */
class MenuTileUpdater(private val context: Context) {
    fun updateAll() {
        try {
            TileService.getUpdater(context).requestUpdate(MenuTileService::class.java)
        } catch (e: Exception) {
            // Never let a tile problem take down whatever triggered the refresh.
            Log.w(TAG, "requestUpdate failed", e)
        }
    }

    private companion object {
        const val TAG = "MenuTile"
    }
}

/**
 * Tile showing today's menu for the preferred restaurant.
 *
 * Reads settings and the cached week **straight off disk**, exactly like the phone's Glance
 * widget: the tile renderer starts this process cold with no Activity alive, so it must
 * never rely on in-memory state.
 */
class MenuTileService : TileService(), KoinComponent {

    private val menuRepository: MenuRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val clock: AppClock by inject()

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture { buildTile(requestParams) }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        SuspendToFutureAdapter.launchFuture {
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
        }

    private suspend fun buildTile(
        requestParams: RequestBuilders.TileRequest,
    ): TileBuilders.Tile {
        val settings = settingsRepository.current()
        val cached = menuRepository.peekCached(settings.restaurant, settings.contentLanguage)

        // A missing or stale cache refreshes in the background. That call ends up back here
        // through the WidgetUpdater seam, but only once: the second pass finds a fresh
        // cache and does not refresh again.
        if (cached == null || cached.isStale) {
            menuRepository.refresh(settings.restaurant, settings.contentLanguage)
        }

        val today = clock.today()
        val day = cached?.value?.firstOrNull { it.isToday(today) }

        val layout = materialScope(
            context = this,
            deviceConfiguration = requestParams.deviceConfiguration,
        ) {
            primaryLayout(
                titleSlot = { text(settings.restaurant.displayName.layoutString) },
                mainSlot = { menuSlot(day) },
            )
        }

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .setFreshnessIntervalMillis(FRESHNESS_INTERVAL_MILLIS)
            .build()
    }

    /**
     * Only the dish titles. At tile size there is room for roughly three lines, and the
     * description and price are what the app itself is for.
     */
    private fun MaterialScope.menuSlot(day: DayMenu?): LayoutElementBuilders.LayoutElement {
        val titles = day?.items.orEmpty().map { it.title }

        if (titles.isEmpty()) {
            return text(getString(R.string.tile_no_menu).layoutString)
        }

        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(androidx.wear.protolayout.DimensionBuilders.expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(openAppClickable())
                    .build(),
            )

        titles.take(MAX_TILE_ITEMS).forEach { title ->
            column.addContent(text(title.layoutString, maxLines = 2))
        }
        return column.build()
    }

    private fun openAppClickable(): ModifiersBuilders.Clickable =
        ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build(),
                    )
                    .build(),
            )
            .build()

    private companion object {
        /** Bumped only when the tile starts referencing image or font resources. */
        const val RESOURCES_VERSION = "1"

        /** Matches the repository's 6-hour cache TTL — the backend re-scrapes hourly. */
        const val FRESHNESS_INTERVAL_MILLIS = 6 * 60 * 60 * 1000L

        const val MAX_TILE_ITEMS = 3
    }
}
