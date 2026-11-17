package com.puzzel.lab.cardsmania.net

import android.app.Application
import com.puzzel.lab.cardsmania.data.AvatarRef
import com.puzzel.lab.cardsmania.data.Characters
import com.puzzel.lab.cardsmania.data.Profile
import com.puzzel.lab.cardsmania.engine.model.GameConfig
import com.puzzel.lab.cardsmania.engine.model.PlayerSetup
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
        val ip = NetworkInfo.localIpv4(app) ?: return "Connect to Wi-Fi (or turn on your hotspot) to host a room."
        val h = RoomHost(
            scope = scope,
            hostName = profile.name.ifBlank { "Host" },
            hostAvatar = profile.avatar,
            playerAvatar = { AvatarRef.player(it) },
            botIdentity = { i, seed ->
                val list = Characters.all.shuffled(java.util.Random(seed))
                val c = list[i % list.size]
                c.name to c.id
            },
        )
        if (!h.start(ip)) return "Couldn't open a room on this network."
        host = h
        _role.value = NetRole.HOST
        discovery.advertise(profile.name.ifBlank { "Host" }, h.port)
        return null
    }

    fun hostSetup(profile: Profile) = PlayerSetup(profile.name.ifBlank { "Host" }, AvatarRef.player(profile.avatar), isHuman = true)

    fun startHostedGame(profile: Profile): GameConfig? = host?.startGame(System.nanoTime(), hostSetup(profile))

    /** A hosted Mindi match: what the host's Mindi screen needs to start. */
    data class HostedMindi(val setup: com.puzzel.lab.cardsmania.mindi.MindiSetup, val seats: List<com.puzzel.lab.cardsmania.mindi.MindiSeat>, val seed: Long)

    fun startHostedMindi(profile: Profile): HostedMindi? {
        val h = host ?: return null
        val seed = System.nanoTime()
        val me = com.puzzel.lab.cardsmania.mindi.MindiSeat(profile.name.ifBlank { "Host" }, AvatarRef.player(profile.avatar), isHuman = true)
        val (setup, seats) = h.startMindi(seed, me)
        return HostedMindi(setup, seats, seed)
    }

    fun closeRoom(reason: String = "The host closed the room.") {
        discovery.stopAdvertising()
        host?.close(reason)
        host = null
        if (_role.value == NetRole.HOST) _role.value = NetRole.LOCAL
    }

    // ------------------------------------------------------------------ guest

    suspend fun join(ip: String, port: Int, profile: Profile): String? {
        leaveAll()
        val c = RoomClient(scope)
        val err = c.join(ip, port, NetMsg.Hello(profile.name.ifBlank { "Guest" }, profile.avatar, deviceToken))
        if (err != null) return err
        client = c
        _role.value = NetRole.GUEST
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
