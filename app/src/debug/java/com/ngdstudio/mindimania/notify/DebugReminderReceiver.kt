package com.ngdstudio.mindimania.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ngdstudio.mindimania.CardsManiaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * DEBUG BUILDS ONLY (src/debug): shows the daily reminder right now, skipping the 19:00 schedule and the quiet hours.
 *   adb shell am broadcast -n com.ngdstudio.mindimania/.notify.DebugReminderReceiver
 */
class DebugReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as? CardsManiaApp)?.container ?: return
        val done = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ReminderWorker.postReminder(context.applicationContext, container)
            } finally {
                done.finish()
            }
        }
    }
}
