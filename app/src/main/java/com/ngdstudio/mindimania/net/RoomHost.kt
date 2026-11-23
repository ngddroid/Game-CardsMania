package com.ngdstudio.mindimania.net

import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.bhabhi.BStep
import com.ngdstudio.mindimania.bhabhi.BhabhiAction
import com.ngdstudio.mindimania.bhabhi.BhabhiEngine
import com.ngdstudio.mindimania.bhabhi.BhabhiSeat
import com.ngdstudio.mindimania.bhabhi.BhabhiSetup
import com.ngdstudio.mindimania.bhabhi.BhabhiState
import com.ngdstudio.mindimania.engine.Action
import com.ngdstudio.mindimania.engine.Step
import com.ngdstudio.mindimania.engine.model.GameConfig
import com.ngdstudio.mindimania.engine.model.GameState
import com.ngdstudio.mindimania.engine.model.PlayerSetup
import com.ngdstudio.mindimania.i18n.Strings
import com.ngdstudio.mindimania.mindi.MStep
import com.ngdstudio.mindimania.mindi.MindiAction
import com.ngdstudio.mindimania.mindi.MindiEngine
import com.ngdstudio.mindimania.mindi.MindiSeat
import com.ngdstudio.mindimania.mindi.MindiSetup
import com.ngdstudio.mindimania.mindi.MindiState
import com.ngdstudio.mindimania.rummy.RAction
import com.ngdstudio.mindimania.rummy.RStep
import com.ngdstudio.mindimania.rummy.RummyEngine
import com.ngdstudio.mindimania.rummy.RummySeat
import com.ngdstudio.mindimania.rummy.RummySetup
import com.ngdstudio.mindimania.rummy.RummyState
import com.ngdstudio.mindimania.teenpatti.TPAction
import com.ngdstudio.mindimania.teenpatti.TPStep
import com.ngdstudio.mindimania.teenpatti.TeenPattiEngine
import com.ngdstudio.mindimania.teenpatti.TeenPattiSeat
import com.ngdstudio.mindimania.teenpatti.TeenPattiSetup
import com.ngdstudio.mindimania.teenpatti.TeenPattiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.UUID

/**
 * The host side of a room: accepts guests, owns the lobby, assigns seats and relays game traffic.
 * Pure Kotlin + java.net (no Android), so it can be tested on the JVM.
 *
 * [botName] maps a bot index to its (name, avatar) for the game config.
 */
class RoomHost(
    private val scope: CoroutineScope,
    hostName: String,
    hostAvatar: Int,
    private val playerAvatar: (Int) -> Int,
    private val botIdentity: (index: Int, seed: Long) -> Pair<String, Int>,
) {
    private class Guest(
        val token: String,
        val id: String,
        var name: String,
        var avatar: Int,
        var conn: Connection?,
        var seat: Int = -1,
    )

    private val lock = Any()
    private var server: ServerSocket? = null
    @Volatile private var closed = false
    private val guests = LinkedHashMap<String, Guest>()
    private var config: GameConfig? = null
    private var mindiSetup: MindiSetup? = null
    private var mindiSeats: List<MindiSeat> = emptyList()
    private var bhabhiSetup: BhabhiSetup? = null
    private var bhabhiSeats: List<BhabhiSeat> = emptyList()
    private var tpSetup: TeenPattiSetup? = null
    private var tpSeats: List<TeenPattiSeat> = emptyList()
    private var rummySetup: RummySetup? = null
    private var rummySeats: List<RummySeat> = emptyList()

    var port: Int = -1
        private set

    private val _room = MutableStateFlow(RoomState(hostName = hostName, hostAvatar = hostAvatar))
    val room: StateFlow<RoomState> = _room

    /** (seat, action) from guests during a game. The seat inside the action is already forced to theirs. */
    val actions = Channel<Pair<Int, Action>>(Channel.UNLIMITED)

    /** (seat, Mindi action) from guests during a Mindi match. The seat is already forced to theirs. */
    val mindiActions = Channel<Pair<Int, MindiAction>>(Channel.UNLIMITED)

    /** Supplies the authoritative Mindi state when a guest re-joins mid-match. */
    @Volatile var mindiStateProvider: (() -> MindiState?)? = null

    /** (seat, Bhabhi action) from guests during a Bhabhi game. The seat is already forced to theirs. */
    val bhabhiActions = Channel<Pair<Int, BhabhiAction>>(Channel.UNLIMITED)

    /** Supplies the authoritative Bhabhi state when a guest re-joins mid-game. */
    @Volatile var bhabhiStateProvider: (() -> BhabhiState?)? = null

    /** (seat, Teen Patti action) from guests. The seat is already forced to theirs. */
    val tpActions = Channel<Pair<Int, TPAction>>(Channel.UNLIMITED)

    /** Supplies the authoritative Teen Patti state when a guest re-joins mid-table. */
    @Volatile var tpStateProvider: (() -> TeenPattiState?)? = null

    /** (seat, Rummy action) from guests. The seat is already forced to theirs. */
    val rummyActions = Channel<Pair<Int, RAction>>(Channel.UNLIMITED)

    /** Supplies the authoritative Rummy state when a guest re-joins mid-table. */
    @Volatile var rummyStateProvider: (() -> RummyState?)? = null

    /** (seat, emote text) from guests. */
    val emotes = Channel<Pair<Int, String>>(Channel.UNLIMITED)

    /** Guest seats → connected? (only during a game). */
    private val _seats = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val seatStatus: StateFlow<Map<Int, Boolean>> = _seats

    /** Supplies the authoritative state when a guest re-joins mid-game. */
    @Volatile var stateProvider: (() -> GameState?)? = null

    /** Ordered outgoing broadcast work so game steps never arrive out of order. */
    private val outQueue = Channel<() -> Unit>(Channel.UNLIMITED)

    fun start(ip: String?): Boolean {
        for (i in 0 until Net.PORT_COUNT) {
            val p = Net.PORT_BASE + i
            val s = runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(p))
                }
            }.getOrNull() ?: continue
            server = s
            port = p
            break
        }
        val srv = server ?: return false
        _room.value = _room.value.copy(code = ip?.let { runCatching { RoomCode.encode(it, port) }.getOrNull() } ?: "")
        scope.launch(Dispatchers.IO) {
            while (!closed) {
                val socket = runCatching { srv.accept() }.getOrNull() ?: break
                handle(Connection(socket, scope, Net.GUEST_LINE_LIMIT))
            }
        }
        scope.launch(Dispatchers.Default) {
            for (job in outQueue) runCatching { job() }
        }
        return true
    }

    private fun handle(conn: Connection) {
        var guest: Guest? = null
        conn.start(
            onMessage = { msg ->
                val g = guest
                when {
                    g == null && msg is NetMsg.Probe -> {
                        conn.send(NetMsg.Lobby(_room.value))
                        delay(300)
                        conn.close()
                    }
                    g == null && msg is NetMsg.Hello -> guest = onHello(conn, msg)
                    g != null -> onGuestMessage(g, msg)
                }
            },
            onClosed = { guest?.let { onGuestClosed(it, conn) } },
        )
        scope.launch(Dispatchers.IO) {
            delay(8000)
            if (guest == null) conn.close()
        }
    }

    private fun cleanName(n: String) = n.trim().take(14).ifBlank { Strings.get(R.string.mp_guest) }

    private fun onHello(conn: Connection, h: NetMsg.Hello): Guest? {
        if (h.protocol != Net.PROTOCOL) {
            conn.send(NetMsg.Rejected(Strings.get(R.string.mp_reject_version)))
            return null
        }
        var sync: NetMsg? = null
        val g: Guest
        synchronized(lock) {
            val r = _room.value
            val existing = guests[h.token]
            if (r.inGame) {
                if (existing == null || existing.seat < 0) {
                    conn.send(NetMsg.Rejected(Strings.get(R.string.mp_reject_started)))
                    return null
                }
                existing.conn?.takeIf { it !== conn }?.close()
                existing.conn = conn
                g = existing
                val cfg = config
                val ms = mindiSetup
                val bs = bhabhiSetup
                val ts = tpSetup
                val rs = rummySetup
                if (r.isTeenPatti && ts != null) {
                    tpStateProvider?.invoke()?.let { st -> sync = NetMsg.TeenPattiSync(g.seat, ts, tpSeats, r.speed, TeenPattiRedact.state(st, g.seat)) }
                } else if (r.isRummy && rs != null) {
                    rummyStateProvider?.invoke()?.let { st -> sync = NetMsg.RummySync(g.seat, rs, rummySeats, r.speed, RummyRedact.state(st, g.seat)) }
                } else if (r.isMindi && ms != null) {
                    mindiStateProvider?.invoke()?.let { st -> sync = NetMsg.MindiSync(g.seat, ms, mindiSeats, r.speed, MindiRedact.state(st, g.seat)) }
                } else if (r.isBhabhi && bs != null) {
                    bhabhiStateProvider?.invoke()?.let { st -> sync = NetMsg.BhabhiSync(g.seat, bs, bhabhiSeats, r.speed, BhabhiRedact.state(st, g.seat)) }
                } else {
                    val st = stateProvider?.invoke()
                    if (cfg != null && st != null) sync = NetMsg.Sync(g.seat, cfg, r.speed, Redact.state(st, g.seat))
                }
                _seats.value = _seats.value + (g.seat to true)
            } else if (existing != null) {
                existing.conn?.takeIf { it !== conn }?.close()
                existing.conn = conn
                existing.name = cleanName(h.name)
                existing.avatar = h.avatar
                g = existing
            } else {
                if (r.openSeats <= 0) {
                    conn.send(NetMsg.Rejected(Strings.get(R.string.mp_reject_full)))
                    return null
                }
                g = Guest(h.token, UUID.randomUUID().toString().take(8), cleanName(h.name), h.avatar, conn)
                guests[h.token] = g
            }
            publishLocked()
        }
        conn.send(NetMsg.Welcome(g.id))
        conn.send(NetMsg.Lobby(_room.value))
        sync?.let { conn.send(it) }
        return g
    }

    private fun onGuestMessage(g: Guest, msg: NetMsg) {
        when (msg) {
            is NetMsg.Act -> if (_room.value.inGame && _room.value.isMania && g.seat >= 0) actions.trySend(g.seat to msg.action.withPlayer(g.seat))
            is NetMsg.MindiAct -> if (_room.value.inGame && _room.value.isMindi && g.seat >= 0) mindiActions.trySend(g.seat to msg.action.withSeat(g.seat))
            is NetMsg.BhabhiAct -> if (_room.value.inGame && _room.value.isBhabhi && g.seat >= 0) bhabhiActions.trySend(g.seat to msg.action.withSeat(g.seat))
            is NetMsg.TeenPattiAct -> if (_room.value.inGame && _room.value.isTeenPatti && g.seat >= 0) tpActions.trySend(g.seat to msg.action.withSeat(g.seat))
            is NetMsg.RummyAct -> if (_room.value.inGame && _room.value.isRummy && g.seat >= 0) rummyActions.trySend(g.seat to msg.action.withSeat(g.seat))
            is NetMsg.Emote -> if (_room.value.inGame && g.seat >= 0) emotes.trySend(g.seat to msg.text.take(8))
            is NetMsg.Leave -> {
                synchronized(lock) {
                    if (!_room.value.inGame) guests.remove(g.token)
                    else _seats.value = _seats.value + (g.seat to false)
                    g.conn = null
                    publishLocked()
                }
            }
            else -> Unit
        }
    }

    private fun onGuestClosed(g: Guest, conn: Connection) {
        synchronized(lock) {
            if (g.conn !== conn) return
            g.conn = null
            if (_room.value.inGame) {
                if (g.seat >= 0) _seats.value = _seats.value + (g.seat to false)
            } else {
                guests.remove(g.token)
            }
            publishLocked()
        }
    }

    /** Rebuild the public room state from the guest list and broadcast it. Call with [lock] held. */
    private fun publishLocked() {
        val r = _room.value
        val list = guests.values.map { RoomGuest(it.id, it.name, it.avatar, it.conn != null, it.seat) }
        var next = r.copy(guests = list)
        next = sanitize(next)
        _room.value = next
        val msg = NetMsg.Lobby(next)
        val conns = guests.values.mapNotNull { it.conn }
        outQueue.trySend { conns.forEach { it.send(msg) } }
    }

    private fun sanitize(r0: RoomState): RoomState {
        // Mania, Teen Patti and Rummy tables hold at most 6, so a room with more people stays on Mindi
        val sixMax = r0.game == RoomGame.MANIA || r0.game == RoomGame.TEEN_PATTI || r0.game == RoomGame.RUMMY
        val r = if (sixMax && 1 + r0.guests.size > 6) r0.copy(game = RoomGame.MINDI) else r0
        if (r.isTeenPatti) {
            val seats = maxOf(r.seats, 1 + r.guests.size).coerceIn(TeenPattiEngine.MIN_PLAYERS, TeenPattiEngine.MAX_PLAYERS)
            val bots = r.bots.coerceIn(0, (seats - 1 - r.guests.size).coerceAtLeast(0))
            // always an endless table
            val setup = TeenPattiEngine.normalize(r.teenPatti.copy(players = seats, hands = 0, difficulty = r.botDifficulty))
            return r.copy(seats = seats, bots = bots, teenPatti = setup)
        }
        if (r.isRummy) {
            val seats = maxOf(r.seats, 1 + r.guests.size).coerceIn(RummyEngine.MIN_PLAYERS, RummyEngine.MAX_PLAYERS)
            val bots = r.bots.coerceIn(0, (seats - 1 - r.guests.size).coerceAtLeast(0))
            val setup = RummyEngine.normalize(r.rummy.copy(players = seats, difficulty = r.botDifficulty))
            return r.copy(seats = seats, bots = bots, rummy = setup)
        }
        if (r.isMindi) {
            val need = 1 + r.guests.size
            val wanted = if (r.seats in listOf(4, 6, 8)) r.seats else r.mindi.players
            val seats = listOf(4, 6, 8).firstOrNull { it >= maxOf(need, wanted) } ?: 8
            val bots = r.bots.coerceIn(0, (seats - 1 - r.guests.size).coerceAtLeast(0))
            val setup = MindiEngine.normalize(r.mindi.copy(players = seats, difficulty = r.botDifficulty))
            return r.copy(seats = seats, bots = bots, mindi = setup)
        }
        if (r.isBhabhi) {
            val seats = maxOf(r.seats, 1 + r.guests.size).coerceIn(BhabhiEngine.MIN_PLAYERS, BhabhiEngine.MAX_PLAYERS)
            val bots = r.bots.coerceIn(0, (seats - 1 - r.guests.size).coerceAtLeast(0))
            val setup = BhabhiEngine.normalize(r.bhabhi.copy(players = seats, difficulty = r.botDifficulty))
            return r.copy(seats = seats, bots = bots, bhabhi = setup)
        }
        var seats = r.seats.coerceIn(2, 6)
        seats = maxOf(seats, 1 + r.guests.size).coerceAtMost(6)
        val bots = r.bots.coerceIn(0, (seats - 1 - r.guests.size).coerceAtLeast(0))
        return r.copy(seats = seats, bots = bots, handSize = r.handSize.coerceIn(5, 10), deckCount = r.deckCount.coerceIn(1, 2))
    }

    /** Host changes lobby settings. */
    fun update(transform: (RoomState) -> RoomState) {
        synchronized(lock) {
            if (_room.value.inGame) return
            val t = transform(_room.value)
            _room.value = t.copy(guests = _room.value.guests, inGame = false, code = _room.value.code)
            publishLocked()
        }
    }

    fun kick(guestId: String) {
        synchronized(lock) {
            val g = guests.values.firstOrNull { it.id == guestId } ?: return
            g.conn?.let { c ->
                c.send(NetMsg.Kicked)
                scope.launch(Dispatchers.IO) { delay(300); c.close() }
            }
            g.conn = null
            guests.remove(g.token)
            publishLocked()
        }
    }

    /** Seats the room and returns the game config. Empty open seats are filled with bots. */
    fun startGame(seed: Long, host: PlayerSetup): GameConfig {
        val starts = ArrayList<Pair<Connection, NetMsg.Start>>()
        val cfg: GameConfig
        synchronized(lock) {
            // drop guests that are gone
            guests.values.filter { it.conn == null }.map { it.token }.forEach { guests.remove(it) }
            val r = sanitize(_room.value.copy(guests = guests.values.map { RoomGuest(it.id, it.name, it.avatar) }))
            val players = ArrayList<PlayerSetup>()
            players += host
            guests.values.forEachIndexed { i, g ->
                g.seat = i + 1
                players += PlayerSetup(g.name, playerAvatar(g.avatar), isHuman = true)
            }
            val botCount = (r.seats - players.size).coerceAtLeast(0)
            repeat(botCount) { i ->
                val (name, avatar) = botIdentity(i, seed)
                players += PlayerSetup(name, avatar, isHuman = false, difficulty = r.botDifficulty)
            }
            cfg = GameConfig(r.mode, players, r.handSize, r.deckCount, r.targetScore, r.rules, seed)
            clearGames()
            config = cfg
            _room.value = r.copy(inGame = true)
            _seats.value = guests.values.associate { it.seat to true }
            guests.values.forEach { g -> g.conn?.let { starts += it to NetMsg.Start(g.seat, cfg, r.speed) } }
            publishLocked()
        }
        outQueue.trySend { starts.forEach { (c, m) -> c.send(m) } }
        return cfg
    }

    /**
     * Seats a Mindi match: host = seat 0, guests in join order, bots fill the rest. Teams alternate by seat.
     * Returns the setup and seats (the same list every phone receives).
     */
    fun startMindi(seed: Long, host: MindiSeat): Pair<MindiSetup, List<MindiSeat>> {
        val starts = ArrayList<Pair<Connection, NetMsg.MindiStart>>()
        val setup: MindiSetup
        val seats = ArrayList<MindiSeat>()
        synchronized(lock) {
            guests.values.filter { it.conn == null }.map { it.token }.forEach { guests.remove(it) }
            val r = sanitize(_room.value.copy(game = RoomGame.MINDI, guests = guests.values.map { RoomGuest(it.id, it.name, it.avatar) }))
            setup = r.mindi
            seats += host.copy(isHuman = true)
            guests.values.forEachIndexed { i, g ->
                g.seat = i + 1
                seats += MindiSeat(g.name, playerAvatar(g.avatar), isHuman = true, remote = true)
            }
            var b = 0
            while (seats.size < setup.players) {
                val (name, avatar) = botIdentity(b++, seed)
                seats += MindiSeat(name, avatar, isHuman = false, difficulty = r.botDifficulty)
            }
            clearGames()
            mindiSetup = setup
            mindiSeats = seats.toList()
            _room.value = r.copy(inGame = true)
            _seats.value = guests.values.associate { it.seat to true }
            guests.values.forEach { g -> g.conn?.let { starts += it to NetMsg.MindiStart(g.seat, setup, mindiSeats, r.speed) } }
            publishLocked()
        }
        outQueue.trySend { starts.forEach { (c, m) -> c.send(m) } }
        return setup to seats.toList()
    }

    /**
     * Seats a Bhabhi game: host = seat 0, guests in join order, bots fill the rest.
     * Returns the setup and seats (the same list every phone receives).
     */
    fun startBhabhi(seed: Long, host: BhabhiSeat): Pair<BhabhiSetup, List<BhabhiSeat>> {
        val starts = ArrayList<Pair<Connection, NetMsg.BhabhiStart>>()
        val setup: BhabhiSetup
        val seats = ArrayList<BhabhiSeat>()
        synchronized(lock) {
            guests.values.filter { it.conn == null }.map { it.token }.forEach { guests.remove(it) }
            val r = sanitize(_room.value.copy(game = RoomGame.BHABHI, guests = guests.values.map { RoomGuest(it.id, it.name, it.avatar) }))
            setup = r.bhabhi
            seats += host.copy(isHuman = true)
            guests.values.forEachIndexed { i, g ->
                g.seat = i + 1
                seats += BhabhiSeat(g.name, playerAvatar(g.avatar), isHuman = true, remote = true)
            }
            var b = 0
            while (seats.size < setup.players) {
                val (name, avatar) = botIdentity(b++, seed)
                seats += BhabhiSeat(name, avatar, isHuman = false, difficulty = r.botDifficulty)
            }
            clearGames()
            bhabhiSetup = setup
            bhabhiSeats = seats.toList()
            _room.value = r.copy(inGame = true)
            _seats.value = guests.values.associate { it.seat to true }
            guests.values.forEach { g -> g.conn?.let { starts += it to NetMsg.BhabhiStart(g.seat, setup, bhabhiSeats, r.speed) } }
            publishLocked()
        }
        outQueue.trySend { starts.forEach { (c, m) -> c.send(m) } }
        return setup to seats.toList()
    }

    /** Seats a Teen Patti table: host = seat 0, guests in join order, bots fill the rest. */
    fun startTeenPatti(seed: Long, host: TeenPattiSeat): Pair<TeenPattiSetup, List<TeenPattiSeat>> {
        val starts = ArrayList<Pair<Connection, NetMsg.TeenPattiStart>>()
        val setup: TeenPattiSetup
        val seats = ArrayList<TeenPattiSeat>()
        synchronized(lock) {
            guests.values.filter { it.conn == null }.map { it.token }.forEach { guests.remove(it) }
            val r = sanitize(_room.value.copy(game = RoomGame.TEEN_PATTI, guests = guests.values.map { RoomGuest(it.id, it.name, it.avatar) }))
            setup = r.teenPatti
            seats += host.copy(isHuman = true)
            guests.values.forEachIndexed { i, g ->
                g.seat = i + 1
                seats += TeenPattiSeat(g.name, playerAvatar(g.avatar), isHuman = true, remote = true)
            }
            var b = 0
            while (seats.size < setup.players) {
                val (name, avatar) = botIdentity(b++, seed)
                seats += TeenPattiSeat(name, avatar, isHuman = false, difficulty = r.botDifficulty)
            }
            clearGames()
            tpSetup = setup
            tpSeats = seats.toList()
            _room.value = r.copy(inGame = true)
            _seats.value = guests.values.associate { it.seat to true }
            guests.values.forEach { g -> g.conn?.let { starts += it to NetMsg.TeenPattiStart(g.seat, setup, tpSeats, r.speed) } }
            publishLocked()
        }
        outQueue.trySend { starts.forEach { (c, m) -> c.send(m) } }
        return setup to seats.toList()
    }

    /** Seats a Rummy table: host = seat 0, guests in join order, bots fill the rest. */
    fun startRummy(seed: Long, host: RummySeat): Pair<RummySetup, List<RummySeat>> {
        val starts = ArrayList<Pair<Connection, NetMsg.RummyStart>>()
        val setup: RummySetup
        val seats = ArrayList<RummySeat>()
        synchronized(lock) {
            guests.values.filter { it.conn == null }.map { it.token }.forEach { guests.remove(it) }
            val r = sanitize(_room.value.copy(game = RoomGame.RUMMY, guests = guests.values.map { RoomGuest(it.id, it.name, it.avatar) }))
            setup = r.rummy
            seats += host.copy(isHuman = true)
            guests.values.forEachIndexed { i, g ->
                g.seat = i + 1
                seats += RummySeat(g.name, playerAvatar(g.avatar), isHuman = true, remote = true)
            }
            var b = 0
            while (seats.size < setup.players) {
                val (name, avatar) = botIdentity(b++, seed)
                seats += RummySeat(name, avatar, isHuman = false, difficulty = r.botDifficulty)
            }
            clearGames()
            rummySetup = setup
            rummySeats = seats.toList()
            _room.value = r.copy(inGame = true)
            _seats.value = guests.values.associate { it.seat to true }
            guests.values.forEach { g -> g.conn?.let { starts += it to NetMsg.RummyStart(g.seat, setup, rummySeats, r.speed) } }
            publishLocked()
        }
        outQueue.trySend { starts.forEach { (c, m) -> c.send(m) } }
        return setup to seats.toList()
    }

    /** Forget every game's setup (a new one is starting, or everyone went back to the room). */
    private fun clearGames() {
        config = null
        mindiSetup = null
        bhabhiSetup = null
        tpSetup = null
        rummySetup = null
    }

    /** Endless tables: a bot left and another sat down. Re-joining guests get the new list too. */
    fun updateSeats(tp: List<TeenPattiSeat>? = null, rummy: List<RummySeat>? = null) {
        synchronized(lock) {
            if (tp != null) tpSeats = tp
            if (rummy != null) rummySeats = rummy
        }
        broadcast(NetMsg.SeatsChanged(tp.orEmpty(), rummy.orEmpty()))
    }

    /** Relays Teen Patti steps to every seated guest, redacted for that guest. */
    fun broadcastTeenPatti(steps: List<TPStep>) {
        if (steps.isEmpty()) return
        val targets = synchronized(lock) { guests.values.filter { it.seat >= 0 && it.conn != null }.map { it.seat to it.conn!! } }
        if (targets.isEmpty()) return
        outQueue.trySend {
            for ((seat, conn) in targets) conn.send(NetMsg.TeenPattiSteps(steps.map { TeenPattiRedact.step(it.event, it.state, seat) }))
        }
    }

    /** Relays Rummy steps to every seated guest, redacted for that guest. */
    fun broadcastRummy(steps: List<RStep>) {
        if (steps.isEmpty()) return
        val targets = synchronized(lock) { guests.values.filter { it.seat >= 0 && it.conn != null }.map { it.seat to it.conn!! } }
        if (targets.isEmpty()) return
        outQueue.trySend {
            for ((seat, conn) in targets) conn.send(NetMsg.RummySteps(steps.map { RummyRedact.step(it.event, it.state, seat) }))
        }
    }

    /** Relays Bhabhi steps to every seated guest, redacted for that guest. */
    fun broadcastBhabhi(steps: List<BStep>) {
        if (steps.isEmpty()) return
        val targets = synchronized(lock) { guests.values.filter { it.seat >= 0 && it.conn != null }.map { it.seat to it.conn!! } }
        if (targets.isEmpty()) return
        outQueue.trySend {
            for ((seat, conn) in targets) conn.send(NetMsg.BhabhiSteps(steps.map { BhabhiRedact.step(it.event, it.state, seat) }))
        }
    }

    /** Relays Mindi steps to every seated guest, redacted for that guest. */
    fun broadcastMindi(steps: List<MStep>) {
        if (steps.isEmpty()) return
        val targets = synchronized(lock) { guests.values.filter { it.seat >= 0 && it.conn != null }.map { it.seat to it.conn!! } }
        if (targets.isEmpty()) return
        outQueue.trySend {
            for ((seat, conn) in targets) conn.send(NetMsg.MindiSteps(steps.map { MindiRedact.step(it.event, it.state, seat) }))
        }
    }

    /** Relays the steps of one host action to every seated guest, redacted for that guest. */
    fun broadcastSteps(steps: List<Step>) {
        if (steps.isEmpty()) return
        val targets = synchronized(lock) { guests.values.filter { it.seat >= 0 && it.conn != null }.map { it.seat to it.conn!! } }
        if (targets.isEmpty()) return
        outQueue.trySend {
            for ((seat, conn) in targets) {
                val list = steps.mapNotNull { st -> st.snapshot?.let { NetStep(Redact.event(st.event, it, seat), Redact.state(it, seat)) } }
                conn.send(NetMsg.Steps(list))
            }
        }
    }

    fun sendToSeat(seat: Int, msg: NetMsg) {
        val c = synchronized(lock) { guests.values.firstOrNull { it.seat == seat }?.conn } ?: return
        outQueue.trySend { c.send(msg) }
    }

    fun broadcast(msg: NetMsg, exceptSeat: Int = -1) {
        val conns = synchronized(lock) { guests.values.filter { it.seat != exceptSeat || exceptSeat < 0 }.mapNotNull { it.conn } }
        outQueue.trySend { conns.forEach { it.send(msg) } }
    }

    /** After a match: everyone returns to the lobby. */
    fun backToLobby() {
        synchronized(lock) {
            guests.values.filter { it.conn == null }.map { it.token }.forEach { guests.remove(it) }
            guests.values.forEach { it.seat = -1 }
            clearGames()
            _room.value = _room.value.copy(inGame = false)
            _seats.value = emptyMap()
            publishLocked()
        }
    }

    fun close(reason: String) {
        if (closed) return
        closed = true
        val conns = synchronized(lock) { guests.values.mapNotNull { it.conn } }
        conns.forEach { it.send(NetMsg.Ended(reason)) }
        scope.launch(Dispatchers.IO) {
            delay(400)
            conns.forEach { it.close() }
            runCatching { server?.close() }
            outQueue.close()
        }
    }
}
