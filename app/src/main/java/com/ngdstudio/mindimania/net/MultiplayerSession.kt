package com.ngdstudio.mindimania.net

import android.app.Application
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.data.AvatarRef
import com.ngdstudio.mindimania.data.Characters
import com.ngdstudio.mindimania.data.Profile
import com.ngdstudio.mindimania.engine.model.GameConfig
import com.ngdstudio.mindimania.engine.model.PlayerSetup
import com.ngdstudio.mindimania.i18n.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.UUID

enum class NetRole { LOCAL, HOST, GUEST }

/**
 * Process-wide owner of the current multiplayer room (hosting or joined).
 * Screens and the game ViewModel talk to the room through this object.
 */
class MultiplayerSession(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val discovery = RoomDiscovery(app, scope)

    var host: RoomHost? = null
        private set
    var client: RoomClient? = null
        private set

    private val _role = MutableStateFlow(NetRole.LOCAL)
    val role: StateFlow<NetRole> = _role

    /** Stable per-install id so a guest can get their seat back after a dropped connection. */
    val deviceToken: String by lazy {
        val f = File(app.filesDir, "device_token")
        runCatching { f.readText().trim() }.getOrNull()?.takeIf { it.length >= 16 } ?: UUID.randomUUID().toString().also { runCatching { f.writeText(it) } }
    }

    // ------------------------------------------------------------------ host

    /** Opens a room on this phone. Returns null on success or an error message. */
    fun hostRoom(profile: Profile): String? {
        leaveAll()
        val ip = NetworkInfo.localIpv4(app) ?: return Strings.get(R.string.mp_err_no_wifi)
        val h = RoomHost(
            scope = scope,
            hostName = profile.name.ifBlank { Strings.get(R.string.mp_host) },
            hostAvatar = profile.avatar,
            playerAvatar = { AvatarRef.player(it) },
            botIdentity = { i, seed ->
                val list = Characters.all.shuffled(java.util.Random(seed))
                val c = list[i % list.size]
                c.name to c.id
            },
        )
        if (!h.start(ip)) return Strings.get(R.string.mp_err_open_failed)
        com.ngdstudio.mindimania.analytics.Analytics.event("mp_host")
        host = h
        _role.value = NetRole.HOST
        discovery.advertise(profile.name.ifBlank { Strings.get(R.string.mp_host) }, h.port)
        return null
    }

    fun hostSetup(profile: Profile) = PlayerSetup(profile.name.ifBlank { Strings.get(R.string.mp_host) }, AvatarRef.player(profile.avatar), isHuman = true)

    fun startHostedGame(profile: Profile): GameConfig? = host?.startGame(System.nanoTime(), hostSetup(profile))

    /** A hosted Mindi match: what the host's Mindi screen needs to start. */
    data class HostedMindi(val setup: com.ngdstudio.mindimania.mindi.MindiSetup, val seats: List<com.ngdstudio.mindimania.mindi.MindiSeat>, val seed: Long)

    fun startHostedMindi(profile: Profile): HostedMindi? {
        val h = host ?: return null
        val seed = System.nanoTime()
        val me = com.ngdstudio.mindimania.mindi.MindiSeat(profile.name.ifBlank { Strings.get(R.string.mp_host) }, AvatarRef.player(profile.avatar), isHuman = true)
        val (setup, seats) = h.startMindi(seed, me)
        return HostedMindi(setup, seats, seed)
    }

    /** A hosted Bhabhi game: what the host's Bhabhi screen needs to start. */
    data class HostedBhabhi(val setup: com.ngdstudio.mindimania.bhabhi.BhabhiSetup, val seats: List<com.ngdstudio.mindimania.bhabhi.BhabhiSeat>, val seed: Long)

    fun startHostedBhabhi(profile: Profile): HostedBhabhi? {
        val h = host ?: return null
        val seed = System.nanoTime()
        val me = com.ngdstudio.mindimania.bhabhi.BhabhiSeat(profile.name.ifBlank { Strings.get(R.string.mp_host) }, AvatarRef.player(profile.avatar), isHuman = true)
        val (setup, seats) = h.startBhabhi(seed, me)
        return HostedBhabhi(setup, seats, seed)
    }

    /** A hosted Teen Patti table: what the host's table needs to start. */
    data class HostedTeenPatti(val setup: com.ngdstudio.mindimania.teenpatti.TeenPattiSetup, val seats: List<com.ngdstudio.mindimania.teenpatti.TeenPattiSeat>, val seed: Long)

    fun startHostedTeenPatti(profile: Profile): HostedTeenPatti? {
        val h = host ?: return null
        val seed = System.nanoTime()
        val me = com.ngdstudio.mindimania.teenpatti.TeenPattiSeat(profile.name.ifBlank { Strings.get(R.string.mp_host) }, AvatarRef.player(profile.avatar), isHuman = true)
        val (setup, seats) = h.startTeenPatti(seed, me)
        return HostedTeenPatti(setup, seats, seed)
    }

    /** A hosted Rummy table: what the host's table needs to start. */
    data class HostedRummy(val setup: com.ngdstudio.mindimania.rummy.RummySetup, val seats: List<com.ngdstudio.mindimania.rummy.RummySeat>, val seed: Long)

    fun startHostedRummy(profile: Profile): HostedRummy? {
        val h = host ?: return null
        val seed = System.nanoTime()
        val me = com.ngdstudio.mindimania.rummy.RummySeat(profile.name.ifBlank { Strings.get(R.string.mp_host) }, AvatarRef.player(profile.avatar), isHuman = true)
        val (setup, seats) = h.startRummy(seed, me)
        return HostedRummy(setup, seats, seed)
    }

    fun closeRoom(reason: String = Strings.get(R.string.mp_host_closed_room)) {
        discovery.stopAdvertising()
        host?.close(reason)
        host = null
        if (_role.value == NetRole.HOST) _role.value = NetRole.LOCAL
    }

    // ------------------------------------------------------------------ guest

    suspend fun join(ip: String, port: Int, profile: Profile): String? {
        leaveAll()
        val c = RoomClient(scope)
        val err = c.join(ip, port, NetMsg.Hello(profile.name.ifBlank { Strings.get(R.string.mp_guest) }, profile.avatar, deviceToken))
        if (err != null) return err
        client = c
        _role.value = NetRole.GUEST
        com.ngdstudio.mindimania.analytics.Analytics.event("mp_join")
        return null
    }

    fun leaveRoom() {
        client?.leave()
        client = null
        if (_role.value == NetRole.GUEST) _role.value = NetRole.LOCAL
    }

    fun leaveAll() {
        closeRoom()
        leaveRoom()
    }
}
