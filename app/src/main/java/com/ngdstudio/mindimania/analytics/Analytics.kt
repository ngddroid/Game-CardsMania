package com.ngdstudio.mindimania.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Firebase Analytics + Crashlytics in one place. Every call is safe without Firebase (no google-services.json, or
 * Firebase failed to start): it simply does nothing. Nothing personal is sent: no names, no contacts, no location.
 * The player can turn both off in Settings ([setSharing]); Firebase remembers that choice across launches.
 */
object Analytics {
    @Volatile private var fa: FirebaseAnalytics? = null
    @Volatile private var ready = false

    fun init(context: Context) {
        ready = FirebaseApp.getApps(context).isNotEmpty()
        if (ready) fa = FirebaseAnalytics.getInstance(context)
    }

    /** Usage statistics and crash reports on/off (Settings → Privacy). */
    fun setSharing(on: Boolean) {
        if (!ready) return
        fa?.setAnalyticsCollectionEnabled(on)
        runCatching { FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = on }
    }

    /** Logs an event. Names: lowercase_with_underscores, up to 40 characters; values: String, Int, Long, Double or Boolean. */
    fun event(name: String, vararg params: Pair<String, Any?>) {
        val a = fa ?: return
        val b = Bundle()
        for ((k, v) in params) when (v) {
            is String -> b.putString(k, v)
            is Int -> b.putLong(k, v.toLong())
            is Long -> b.putLong(k, v)
            is Double -> b.putDouble(k, v)
            is Float -> b.putDouble(k, v.toDouble())
            is Boolean -> b.putString(k, if (v) "yes" else "no")
            null -> Unit
            else -> b.putString(k, v.toString())
        }
        a.logEvent(name, b)
    }

    /** A screen was shown ([route] = navigation route without arguments, e.g. "spider"). */
    fun screen(route: String) {
        val r = route.substringBefore('?').substringBefore('/')
        event(FirebaseAnalytics.Event.SCREEN_VIEW, FirebaseAnalytics.Param.SCREEN_NAME to r, FirebaseAnalytics.Param.SCREEN_CLASS to r)
        crashKey("screen", r)
    }

    /** A property used to split reports (e.g. the app language). */
    fun userProperty(name: String, value: String) {
        fa?.setUserProperty(name, value)
        crashKey(name, value)
    }

    /** Extra context attached to the next crash report. */
    fun crashKey(key: String, value: String) {
        if (!ready) return
        runCatching { FirebaseCrashlytics.getInstance().setCustomKey(key, value) }
    }

    /** Reports a caught problem as a non-fatal issue in Crashlytics. */
    fun recordError(t: Throwable) {
        if (!ready) return
        runCatching { FirebaseCrashlytics.getInstance().recordException(t) }
    }
}
