package dev.davidemarcoli.sixmensa.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.davidemarcoli.sixmensa.MainActivity
import dev.davidemarcoli.sixmensa.R
import dev.davidemarcoli.sixmensa.core.Restaurant
import dev.davidemarcoli.sixmensa.domain.DayMenu
import dev.davidemarcoli.sixmensa.share.MenuTextFormatter

object Notifications {

    const val CHANNEL_DAILY_MENU = "daily_menu"
    private const val NOTIFICATION_ID = 1001

    /** Safe to call unconditionally: notification channels exist from API 26, our minSdk. */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_DAILY_MENU,
            context.getString(R.string.channel_daily_menu),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_daily_menu_description)
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    fun showDailyMenu(context: Context, restaurant: Restaurant, day: DayMenu) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = MenuTextFormatter.summarize(day)

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_MENU)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                context.getString(R.string.notification_title, restaurant.displayName),
            )
            .setContentText(day.items.firstOrNull()?.title.orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // Throws without POST_NOTIFICATIONS on API 33+; the caller checks first, but the
        // permission can also be revoked between the check and here.
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
