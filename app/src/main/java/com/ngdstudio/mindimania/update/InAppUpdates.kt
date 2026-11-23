package com.ngdstudio.mindimania.update

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.ngdstudio.mindimania.analytics.Analytics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Google Play in-app updates. On start it asks Play whether a newer version exists:
 *  - update priority 4–5 (set when uploading the release with the Play Developer API) → IMMEDIATE: a full-screen
 *    Play update the player must finish;
 *  - otherwise → FLEXIBLE: it downloads in the background while the player keeps playing, then [ready] turns true
 *    and the app shows a "Restart" banner ([restart]).
 * Only works for installs from Google Play (sideloaded and debug builds simply get "no update").
 */
class InAppUpdates(private val activity: ComponentActivity) {
    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    private val launcher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
            Analytics.event("app_update_flow", "result" to r.resultCode)
        }

    private val listener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) _ready.value = true
    }

    /** Call once from onCreate. */
    fun check() {
        manager.registerListener(listener)
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return@addOnSuccessListener
            val type = if (info.updatePriority() >= 4 && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) AppUpdateType.IMMEDIATE
            else if (info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) AppUpdateType.FLEXIBLE
            else return@addOnSuccessListener
            Analytics.event("app_update_offer", "type" to if (type == AppUpdateType.IMMEDIATE) "immediate" else "flexible")
            runCatching { manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(type).build()) }
        }
    }

    /** Call from onResume: finishes an interrupted immediate update, or shows the banner for a finished download. */
    fun resume() {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) _ready.value = true
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                runCatching { manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()) }
            }
        }
    }

    /** Installs the downloaded update (the app restarts). */
    fun restart() = manager.completeUpdate()

    /** Call from onDestroy. */
    fun release() = manager.unregisterListener(listener)
}
