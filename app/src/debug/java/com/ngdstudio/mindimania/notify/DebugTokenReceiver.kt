package com.ngdstudio.mindimania.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

/**
 * DEBUG BUILDS ONLY (src/debug): prints this phone's FCM registration token to logcat (tag "FCMToken"), for sending
 * a test push from Firebase console → Messaging → "Send test message".
 *   adb shell am broadcast -n com.ngdstudio.mindimania/.notify.DebugTokenReceiver
 *   adb logcat -d -s FCMToken
 */
class DebugTokenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            Log.i(TAG, "Firebase is not set up (no google-services.json)")
            return
        }
        val done = goAsync()
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { Log.i(TAG, "token=$it") }
            .addOnFailureListener { Log.i(TAG, "error=${it.message}") }
            .addOnCompleteListener { done.finish() }
    }

    private companion object { const val TAG = "FCMToken" }
}
