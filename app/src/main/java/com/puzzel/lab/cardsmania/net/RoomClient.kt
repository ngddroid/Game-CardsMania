package com.puzzel.lab.cardsmania.net

import com.puzzel.lab.cardsmania.R
import com.puzzel.lab.cardsmania.i18n.Strings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface ClientStatus {
    data object Idle : ClientStatus
    data object Connecting : ClientStatus
    data object InLobby : ClientStatus
    data object InGame : ClientStatus
    data class Failed(val reason: String) : ClientStatus
    data class Ended(val reason: String) : ClientStatus
    data object Kicked : ClientStatus
}

/** The guest side of a room (pure Kotlin + java.net). */
class RoomClient(private val scope: CoroutineScope) {
    private var conn: Connection? = null
    @Volatile private var leaving = false

    private val _room = MutableStateFlow<RoomState?>(null)
    val room: StateFlow<RoomState?> = _room
    private val _status = MutableStateFlow<ClientStatus>(ClientStatus.Idle)
    val status: StateFlow<ClientStatus> = _status

    /** Messages for the current game (Start/Sync first, then Steps, Emote, ActionRejected). Replaced at every start. */
    @Volatile var game: Channel<NetMsg> = Channel(Channel.UNLIMITED)
        private set

    /** Fires each time the host starts (or re-syncs) a game. */
    private val _starts = MutableSharedFlow<NetMsg>(replay = 1, extraBufferCapacity = 4)
    val starts: SharedFlow<NetMsg> = _starts

    var guestId: String = ""
        private set

    /** Connects and says hello. Returns null on success, or the reason it failed. */
    suspend fun join(host: String, port: Int, hello: NetMsg.Hello): String? {
        _status.value = ClientStatus.Connecting
        val welcome = CompletableDeferred<String>() // "" = welcomed, otherwise the failure reason
        val c = runCatching { withContext(Dispatchers.IO) { Connection.connect(host, port, scope) } }.getOrElse {
            val unreachable = Strings.get(R.string.mp_err_unreachable)
            _status.value = ClientStatus.Failed(unreachable)
            return unreachable
        }
        conn = c
        c.start(
            onMessage = { msg ->
                when (msg) {
                    is NetMsg.Welcome -> {
                        guestId = msg.guestId
                        if (_status.value == ClientStatus.Connecting) _status.value = ClientStatus.InLobby
                        welcome.complete("")
                    }
                    is NetMsg.Rejected -> {
                        _status.value = ClientStatus.Failed(msg.reason)
                        welcome.complete(msg.reason)
                        c.close()
                    }
                    is NetMsg.Lobby -> {
                        _room.value = msg.room
                        if (!msg.room.inGame && _status.value == ClientStatus.InGame) _status.value = ClientStatus.InLobby
                    }
                    is NetMsg.Start, is NetMsg.Sync, is NetMsg.MindiStart, is NetMsg.MindiSync, is NetMsg.BhabhiStart, is NetMsg.BhabhiSync -> {
                        val ch = Channel<NetMsg>(Channel.UNLIMITED)
                        ch.trySend(msg)
                        game.close()
                        game = ch
                        _status.value = ClientStatus.InGame
                        _starts.tryEmit(msg)
                    }
                    is NetMsg.Steps, is NetMsg.MindiSteps, is NetMsg.BhabhiSteps, is NetMsg.Emote, is NetMsg.ActionRejected -> game.trySend(msg)
                    is NetMsg.Ended -> {
                        _status.value = ClientStatus.Ended(msg.reason)
                        game.trySend(msg)
                    }
                    is NetMsg.Kicked -> {
                        _status.value = ClientStatus.Kicked
                        game.trySend(NetMsg.Ended(Strings.get(R.string.mp_removed)))
                    }
                    else -> Unit
                }
            },
            onClosed = {
                val s = _status.value
                if (!leaving && s !is ClientStatus.Ended && s !is ClientStatus.Kicked && s !is ClientStatus.Failed) {
                    val lost = Strings.get(R.string.mp_lost_connection)
                    _status.value = ClientStatus.Ended(lost)
                    game.trySend(NetMsg.Ended(lost))
                }
                welcome.complete(Strings.get(R.string.mp_connection_closed))
            },
        )
        c.send(hello)
        val answer = withTimeoutOrNull(6000) { welcome.await() } ?: Strings.get(R.string.mp_no_answer)
        val result: String? = answer.ifEmpty { null }
        if (result != null) {
            if (_status.value !is ClientStatus.Failed) _status.value = ClientStatus.Failed(result)
            c.close()
        }
        return result
    }

    fun send(msg: NetMsg) {
        conn?.send(msg)
    }

    fun leave() {
        leaving = true
        val c = conn
        conn = null
        _status.value = ClientStatus.Idle
        game.close()
        if (c != null) {
            c.send(NetMsg.Leave)
            scope.launch(Dispatchers.IO) {
                delay(250)
                c.close()
            }
        }
    }

    companion object {
        /** Asks a host for its lobby without joining (for the room list). */
        suspend fun probe(host: String, port: Int, scope: CoroutineScope): RoomState? {
            val result = CompletableDeferred<RoomState?>()
            val c = runCatching { withContext(Dispatchers.IO) { Connection.connect(host, port, scope, 2000) } }.getOrNull() ?: return null
            c.start(onMessage = { m -> if (m is NetMsg.Lobby) result.complete(m.room) }, onClosed = { result.complete(null) })
            c.send(NetMsg.Probe)
            val r = withTimeoutOrNull(2500) { result.await() }
            c.close()
            return r
        }
    }
}
