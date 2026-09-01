package dev.davidemarcoli.sixmensa.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.davidemarcoli.sixmensa.core.ContentLanguage
import dev.davidemarcoli.sixmensa.core.Restaurant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    /** Language of the menu *content* (API `?language=`), not the app's UI language. */
    val contentLanguage: ContentLanguage,
    /** Drives the home screen, the widget and the notification. */
    val restaurant: Restaurant,
    val themeMode: ThemeMode,
    val useDynamicColor: Boolean,
    /** Palette seed used when dynamic colour is off or unavailable (API < 31). */
    val seedColor: Int,
    val notificationsEnabled: Boolean,
    val notificationHour: Int,
    val notificationMinute: Int,
    /** Fire the daily notification only when the phone is at one of the SIX buildings. */
    val notifyOnlyAtWork: Boolean,
) {
    val notificationTime: LocalTime get() = LocalTime.of(notificationHour, notificationMinute)

    companion object {
        /** The webapp's default accent, `six-mensa/lib/store.ts`. */
        const val DEFAULT_SEED_COLOR = 0xFFDE3919.toInt()

        fun defaults(systemLocale: Locale = Locale.getDefault()) = Settings(
            // Match the phone's language on first run; the user can override.
            contentLanguage = if (systemLocale.language == "de") ContentLanguage.DE else ContentLanguage.EN,
            restaurant = Restaurant.HTP,
            themeMode = ThemeMode.SYSTEM,
            useDynamicColor = true,
            seedColor = DEFAULT_SEED_COLOR,
            notificationsEnabled = true,
            notificationHour = 10,
            notificationMinute = 30,
            notifyOnlyAtWork = false,
        )
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Settings write through immediately — no Save button, no confirmation toast. That is the
 * Android idiom, and it replaces the webapp's react-hook-form + zod + toast apparatus.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val contentLanguage = stringPreferencesKey("content_language")
        val restaurant = stringPreferencesKey("restaurant")
        val themeMode = stringPreferencesKey("theme_mode")
        val useDynamicColor = booleanPreferencesKey("use_dynamic_color")
        val seedColor = intPreferencesKey("seed_color")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val notificationHour = intPreferencesKey("notification_hour")
        val notificationMinute = intPreferencesKey("notification_minute")
        val notifyOnlyAtWork = booleanPreferencesKey("notify_only_at_work")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    /** For the widget and worker, which need a value without collecting a Flow. */
    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings(): Settings {
        val d = Settings.defaults()
        return Settings(
            contentLanguage = ContentLanguage.fromWire(this[Keys.contentLanguage]) ?: d.contentLanguage,
            restaurant = Restaurant.fromWire(this[Keys.restaurant]) ?: d.restaurant,
            themeMode = this[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: d.themeMode,
            useDynamicColor = this[Keys.useDynamicColor] ?: d.useDynamicColor,
            seedColor = this[Keys.seedColor] ?: d.seedColor,
            notificationsEnabled = this[Keys.notificationsEnabled] ?: d.notificationsEnabled,
            notificationHour = this[Keys.notificationHour] ?: d.notificationHour,
            notificationMinute = this[Keys.notificationMinute] ?: d.notificationMinute,
            notifyOnlyAtWork = this[Keys.notifyOnlyAtWork] ?: d.notifyOnlyAtWork,
        )
    }

    suspend fun setContentLanguage(value: ContentLanguage) = edit { it[Keys.contentLanguage] = value.wire }
    suspend fun setRestaurant(value: Restaurant) = edit { it[Keys.restaurant] = value.wire }
    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.themeMode] = value.name }
    suspend fun setUseDynamicColor(value: Boolean) = edit { it[Keys.useDynamicColor] = value }
    suspend fun setSeedColor(value: Int) = edit { it[Keys.seedColor] = value }
    suspend fun setNotificationsEnabled(value: Boolean) = edit { it[Keys.notificationsEnabled] = value }
    suspend fun setNotifyOnlyAtWork(value: Boolean) = edit { it[Keys.notifyOnlyAtWork] = value }

    suspend fun setNotificationTime(hour: Int, minute: Int) = edit {
        it[Keys.notificationHour] = hour
        it[Keys.notificationMinute] = minute
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
