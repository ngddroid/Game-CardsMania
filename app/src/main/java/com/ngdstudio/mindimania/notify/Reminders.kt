package com.ngdstudio.mindimania.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ngdstudio.mindimania.CardsManiaApp
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.data.Progression
import com.ngdstudio.mindimania.i18n.Strings
import com.ngdstudio.mindimania.data.GameFactory
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * The daily reminder: a local notification around [HOUR]:00 (phone time), scheduled with WorkManager so it survives
 * restarts. It is skipped when the player opened the app in the last [QUIET_HOURS] hours. The text points at
 * something waiting: the daily gift, today's Spider challenge, or just a quick game.
 */
object Reminders {
    const val HOUR = 19
    const val QUIET_HOURS = 8
    private const val WORK = "daily_reminder"

    /** Turns the reminder on or off (Settings). Keeps an existing schedule instead of restarting its clock. */
    fun sync(context: Context, on: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!on) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS, 2, TimeUnit.HOURS)
            .setInitialDelay(delayToNext(HOUR), TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Milliseconds from now until the next [hour]:00. */
    fun delayToNext(hour: Int, now: Calendar = Calendar.getInstance()): Long {
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }
}

/** Builds and posts the reminder text. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as? CardsManiaApp)?.container ?: return Result.success()
        val settings = container.settings.current()
        if (!settings.reminders) return Result.success()
        val now = System.currentTimeMillis()
        if (now - settings.lastOpenAt < Reminders.QUIET_HOURS * 3_600_000L) return Result.success()
        postReminder(applicationContext, container)
        return Result.success()
    }

    companion object {
        /** Picks the reminder text for this player and shows it now (also used by the debug test trigger). */
        suspend fun postReminder(context: Context, container: com.ngdstudio.mindimania.AppContainer) {
            val now = System.currentTimeMillis()
            val p = container.profile.current()
            val today = GameFactory.today()
            val (title, body) = when {
                Progression.giftReady(p, now) ->
                    Strings.get(R.string.notif_gift_title) to Strings.get(R.string.notif_gift_body, Progression.giftAmount(p))
                today !in p.spider.dailySolved ->
                    Strings.get(R.string.notif_daily_title) to Strings.get(R.string.notif_daily_body)
                else -> Strings.get(R.string.notif_play_title) to Strings.get(R.string.notif_play_body)
            }
            Notifications.post(context, Notifications.CHANNEL_REMINDERS, Notifications.ID_REMINDER, title, body)
        }
    }
}
