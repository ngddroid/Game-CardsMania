package com.ngdstudio.mindimania.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ngdstudio.mindimania.CardsManiaApp
import com.ngdstudio.mindimania.data.AvatarFrame
import com.ngdstudio.mindimania.data.TableTheme
import com.ngdstudio.mindimania.ui.render.CardBack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * DEBUG BUILDS ONLY (src/debug): unlocks every Collection item (tables, card backs, avatar frames) on this phone,
 * whatever the player's level or coins. Spider backgrounds are always free. Nothing else in the profile changes.
 *   adb shell am broadcast -n com.ngdstudio.mindimania/.debug.DebugUnlockAllReceiver
 */
class DebugUnlockAllReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as? CardsManiaApp)?.container ?: return
        val keys = TableTheme.entries.map { "theme:${it.name}" } +
            CardBack.entries.map { "back:${it.name}" } +
            AvatarFrame.entries.map { "frame:${it.name}" }
        val done = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val p = container.profile.update { it.copy(unlocked = it.unlocked + keys) }
                Log.i(TAG, "unlocked ${keys.size} items (${p.unlocked.size} owned now)")
            } finally {
                done.finish()
            }
        }
    }

    private companion object { const val TAG = "DebugUnlock" }
}
