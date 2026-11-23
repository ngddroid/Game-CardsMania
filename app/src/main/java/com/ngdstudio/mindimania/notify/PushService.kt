package com.ngdstudio.mindimania.notify

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging. Messages are sent from the Firebase console (Messaging → New campaign), to the topic
 * "all" or to one language: "lang_en", "lang_hi", "lang_gu". While the app is in the background Android shows a
 * notification message by itself (channel "news"); this service shows it while the app is open, and handles
 * data-only messages with "title" / "body" keys.
 */
class PushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val n = message.notification
        val title = n?.title ?: message.data["title"] ?: return
        val body = n?.body ?: message.data["body"] ?: ""
        Notifications.post(this, Notifications.CHANNEL_NEWS, (System.currentTimeMillis() % 100_000).toInt() + 2000, title, body)
    }

    override fun onNewToken(token: String) {
        // targeting is done with topics, so there is no server to send the token to
    }
}

object Push {
    const val TOPIC_ALL = "all"
    private val LANGS = listOf("en", "hi", "gu")

    /**
     * News & events on: allow the token, join "all" and the topic of the current language (and leave the others).
     * Off: leave every topic and delete the token, so nothing arrives at all.
     */
    fun sync(context: Context, on: Boolean, lang: String) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        runCatching {
            val fm = FirebaseMessaging.getInstance()
            if (on) {
                fm.isAutoInitEnabled = true
                fm.subscribeToTopic(TOPIC_ALL)
                for (l in LANGS) if (l == lang) fm.subscribeToTopic("lang_$l") else fm.unsubscribeFromTopic("lang_$l")
            } else {
                fm.unsubscribeFromTopic(TOPIC_ALL)
                for (l in LANGS) fm.unsubscribeFromTopic("lang_$l")
                fm.isAutoInitEnabled = false
                fm.deleteToken()
            }
        }
    }
}
