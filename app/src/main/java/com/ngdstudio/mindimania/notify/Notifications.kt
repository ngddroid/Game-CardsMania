package com.ngdstudio.mindimania.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ngdstudio.mindimania.MainActivity
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.i18n.Strings

/** Notification channels and posting, shared by the daily reminder (local) and Firebase push messages. */
object Notifications {
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_NEWS = "news"
    const val ID_REMINDER = 1001

    /** Creates both channels (safe to call often; names follow the app language). */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, Strings.get(R.string.notif_channel_reminders), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = Strings.get(R.string.notif_channel_reminders_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_NEWS, Strings.get(R.string.notif_channel_news), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = Strings.get(R.string.notif_channel_news_desc) }
        )
    }

    /** True when the app may show notifications (permission on Android 13+, and not blocked in system settings). */
    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Shows a notification that opens the app when tapped. Does nothing if notifications aren't allowed. */
    fun post(context: Context, channel: String, id: Int, title: String, body: String) {
        if (!allowed(context)) return
        createChannels(context)
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(0xFFFFC83D.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
            // permission was revoked between the check and the post
        }
    }
}
