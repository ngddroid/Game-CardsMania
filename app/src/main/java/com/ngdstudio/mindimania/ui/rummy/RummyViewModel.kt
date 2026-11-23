package com.ngdstudio.mindimania.ui.rummy

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ngdstudio.mindimania.CardsManiaApp
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.audio.Sfx
import com.ngdstudio.mindimania.audio.Track
import com.ngdstudio.mindimania.data.Achievement
import com.ngdstudio.mindimania.data.Achievements
import com.ngdstudio.mindimania.data.AppJson
import com.ngdstudio.mindimania.data.AvatarRef
import com.ngdstudio.mindimania.data.Characters
import com.ngdstudio.mindimania.data.DifficultyInfo
import com.ngdstudio.mindimania.data.Profile
import com.ngdstudio.mindimania.data.SaveFiles
import com.ngdstudio.mindimania.data.Settings
import com.ngdstudio.mindimania.engine.model.Difficulty
import com.ngdstudio.mindimania.i18n.Strings
import com.ngdstudio.mindimania.rummy.Finish
import com.ngdstudio.mindimania.rummy.GroupKind
import com.ngdstudio.mindimania.rummy.RAction
import com.ngdstudio.mindimania.rummy.RCard
import com.ngdstudio.mindimania.rummy.REvent
import com.ngdstudio.mindimania.rummy.RPhase
import com.ngdstudio.mindimania.rummy.RStep
import com.ngdstudio.mindimania.rummy.RummyBot
import com.ngdstudio.mindimania.rummy.RummyEngine
import com.ngdstudio.mindimania.rummy.RummyMelds
import com.ngdstudio.mindimania.rummy.RummySeat
import com.ngdstudio.mindimania.rummy.RummySetup
import com.ngdstudio.mindimania.rummy.RummyState
import com.ngdstudio.mindimania.net.ClientStatus
import com.ngdstudio.mindimania.net.Net
import com.ngdstudio.mindimania.net.NetMsg
import com.ngdstudio.mindimania.net.NetRole
import com.ngdstudio.mindimania.net.RoomClient
import com.ngdstudio.mindimania.net.RoomHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.random.Random

@Serializable
data class RummySave(val state: RummyState, val seats: List<RummySeat>, val groups: List<List<Int>> = emptyList(), val grouping: Boolean = true, val myTurns: Int = 0)

/** What the Rummy screen should start: a new table with [setup] (null = last setup), or the saved one. */
data class RummyLaunch(
    val setup: RummySetup? = null,
    val resume: Boolean = false,
    /** Wi-Fi: HOST passes the room's [seats] and [seed]; GUEST waits for the host's RummyStart. */
    val net: NetRole = NetRole.LOCAL,
    val seats: List<RummySeat>? = null,
    val seed: Long? = null,
)

/** A card flying across the table (an animation only). */
data class RFly(val from: FlyPt, val to: FlyPt, val card: RCard?, val faceUp: Boolean, val id: Long)

sealed interface FlyPt {
    data object Closed : FlyPt
    data object Open : FlyPt
    data object Finish : FlyPt
    data object Hand : FlyPt
    data class Seat(val seat: Int) : FlyPt
}

/** Where dragged cards land in my hand: inside group [group] before its card [index], or as a new group at [group]. */
data class HandDrop(val group: Int, val index: Int, val newGroup: Boolean)

data class RummyUi(
    val ready: Boolean = false,
    val me: Int = 0,
    val seats: List<RummySeat> = emptyList(),
    val state: RummyState? = null,
    val idle: Boolean = false,
    /** My cards as groups (card ids), left to right. */
    val groups: List<List<Int>> = emptyList(),
    /** Grouping ON: my cards are kept in their best arrangement automatically. */
    val grouping: Boolean = true,
    val selected: Set<Int> = emptySet(),
    /** The card I just picked (highlighted). */
    val newCard: Int? = null,
    val banner: String? = null,
    val bannerAt: Long = 0L,
    val emotes: Map<Int, Pair<String, Long>> = emptyMap(),
    val turnDeadline: Long = 0L,
    val turnTotal: Long = 0L,
    /** Someone declared: I arrange until this time (0 = not arranging). */
    val arrangeDeadline: Long = 0L,
    /** A hand is over: the next one deals at this time. */
    val nextHandAt: Long = 0L,
    /** Asking before an invalid declaration (the card I'd finish with). */
    val confirmWrong: Int? = null,
    val confirmDrop: Boolean = false,
    val settings: Settings = Settings(),
    val profile: Profile = Profile(),
    val net: NetRole = NetRole.LOCAL,
    /** Remote human seats → connected (Wi-Fi only). */
    val netSeats: Map<Int, Boolean> = emptyMap(),
    /** Set when a Wi-Fi table ends unexpectedly (host left, connection lost). */
    val ended: String? = null,
)

class RummyViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as CardsManiaApp).container
    private val saveFile = File(app.filesDir, SAVE_FILE)

    private val _ui = MutableStateFlow(RummyUi())
    val ui: StateFlow<RummyUi> = _ui
    private val _flies = MutableSharedFlow<RFly>(extraBufferCapacity = 64)
    val flies: SharedFlow<RFly> = _flies
    private var flyId = 0L

    private var state: RummyState? = null
    private var seats: List<RummySeat> = emptyList()
    private var me = 0
    private var net = NetRole.LOCAL
    private var roomHost: RoomHost? = null
    private var roomClient: RoomClient? = null
    private var awaitingServer = false
    private var netSpeed: Int? = null
    private val isNet: Boolean get() = net != NetRole.LOCAL
    /** My arrange countdown after someone declares (its own job: other players' moves must not cancel it). */
    private var arrangeJob: Job? = null
    /** Host: remote players who must arrange, and their countdowns. */
    private val remoteArrange = HashMap<Int, Job>()
    /** My own turns this hand (for the quick-finish achievement). */
    private var myTurns = 0
    /** I won this hand by declaring (not because everyone else dropped). */
    private var iDeclared = false
    private val queue = ArrayDeque<RStep>()
    private var pumpJob: Job? = null
    private var botJob: Job? = null
    private var timerJob: Job? = null
    private var groupJob: Job? = null
    private var version = 0L
    private var started = false
    private val rnd = Random(System.nanoTime())

    private val speed: Float
        get() = when (netSpeed ?: _ui.value.settings.speed) {
            0 -> 1.3f
            2 -> 0.65f
            else -> 1f
        }

    fun start(launch: RummyLaunch) {
        if (started) return
        started = true
        viewModelScope.launch {
            val s = c.settings.current()
            val p = c.profile.current()
            _ui.update { it.copy(settings = s, profile = p) }
            launch { c.settings.settings.collect { ns -> _ui.update { it.copy(settings = ns) } } }
            c.music.play(Track.DAY)
            when (launch.net) {
                NetRole.HOST -> {
                    startHost(launch)
                    return@launch
                }
                NetRole.GUEST -> {
                    startGuest()
                    return@launch
                }
                else -> Unit
            }
            if (launch.resume) {
                val saved = SaveFiles.read(saveFile)?.let { text ->
                    withContext(Dispatchers.Default) { runCatching { AppJson.decodeFromString(RummySave.serializer(), text) }.getOrNull() }
                }
                if (saved != null) {
                    state = saved.state
                    seats = saved.seats
                    myTurns = saved.myTurns
                    _ui.update { it.copy(ready = true, seats = seats, state = saved.state, grouping = saved.grouping, groups = saved.groups) }
                    syncGroups(saved.state, regroup = saved.groups.isEmpty())
                    queue.add(RStep(REvent.Restored(saved.state.handNo), saved.state))
                    pumpJob = viewModelScope.launch { pump() }
                    return@launch
                }
            }
            val setup = RummyEngine.normalize(launch.setup ?: p.rummy.setup)
            val seed = System.nanoTime()
            seats = buildSeats(setup, p, seed)
            val steps = RummyEngine.newGame(setup, seed)
            state = steps.last().state
            _ui.update { it.copy(ready = true, seats = seats) }
            enqueue(steps)
        }
    }

    private fun buildSeats(setup: RummySetup, p: Profile, seed: Long): List<RummySeat> {
        val chars = Characters.all.shuffled(java.util.Random(seed))
        val you = RummySeat(p.name.ifBlank { Strings.get(R.string.tp_you) }, AvatarRef.player(p.avatar), isHuman = true)
        return listOf(you) + (1 until setup.players).map { i ->
            val ch = chars[(i - 1) % chars.size]
            RummySeat(ch.name, ch.id, isHuman = false, difficulty = setup.difficulty)
        }
    }

    // ------------------------------------------------------------------ my groups

    /** Keep my groups in step with my hand: drop cards I no longer hold, add new ones (or regroup when Grouping is on). */
    private fun syncGroups(s: RummyState, regroup: Boolean = false, newCard: Int? = null) {
        val hand = s.hands.getOrNull(me).orEmpty()
        val ids = hand.map { it.id }.toSet()
        val u = _ui.value
        if (u.grouping || regroup) {
            // the best arrangement is a short search: off the main thread
            val v = ++groupVersion
            groupJob?.cancel()
            val wild = s.wild
            groupJob = viewModelScope.launch {
                val g = withContext(Dispatchers.Default) {
                    if (u.grouping) RummyMelds.best(hand, wild).groups else RummyMelds.bySuit(hand, wild)
                }
                if (v != groupVersion) return@launch
                _ui.update { it.copy(groups = g.map { gr -> gr.map { c -> c.id } }) }
            }
            // meanwhile: the old groups minus what's gone, with the new card beside its suit
            _ui.update { it.copy(groups = withNewCards(u.groups, hand, s.wild)) }
            return
        }
        _ui.update { it.copy(groups = withNewCards(u.groups, hand, s.wild), selected = it.selected.filter { id -> id in ids }.toSet()) }
    }


    private var groupVersion = 0L

    private fun myGroupsFor(hand: List<RCard>, exclude: Int? = null): List<List<Int>> {
        val ids = hand.map { it.id }.filter { it != exclude }.toSet()
        val g = _ui.value.groups.map { gr -> gr.filter { it in ids } }.filter { it.isNotEmpty() }
        val missing = ids.filter { id -> g.none { id in it } }
        return g + (if (missing.isNotEmpty()) listOf(missing) else emptyList())
    }

    // ------------------------------------------------------------------ step pump

    // ------------------------------------------------------------------ Wi-Fi

    private fun startHost(launch: RummyLaunch) {
        val host = c.multiplayer.host
        val setup = launch.setup
        val launchSeats = launch.seats
        if (host == null || setup == null || launchSeats == null) {
            _ui.update { it.copy(ready = true, ended = Strings.get(R.string.mindi_room_closed)) }
            return
        }
        net = NetRole.HOST
        roomHost = host
        me = 0
        netSpeed = host.room.value.speed
        seats = launchSeats
        val steps = RummyEngine.newGame(setup, launch.seed ?: System.nanoTime())
        state = steps.last().state
        host.rummyStateProvider = { state }
        _ui.update { it.copy(ready = true, me = me, seats = seats, net = NetRole.HOST, netSeats = host.seatStatus.value) }
        viewModelScope.launch { for ((seat, a) in host.rummyActions) onRemoteAction(seat, a) }
        viewModelScope.launch {
            for ((seat, text) in host.emotes) {
                showEmote(seat, text)
                host.broadcast(NetMsg.Emote(seat, text), exceptSeat = seat)
            }
        }
        viewModelScope.launch {
            host.seatStatus.collect { st ->
                val before = _ui.value.netSeats
                _ui.update { it.copy(netSeats = st) }
                // someone dropped while it's their move: a bot takes over right away
                val s = state ?: return@collect
                val dropped = st.filter { (seat, on) -> !on && before[seat] == true }.keys
                if (dropped.isEmpty() || !_ui.value.idle) return@collect
                val ph = s.phase
                if (s.turn in dropped || (ph is RPhase.Showdown && ph.pending.any { it in dropped })) afterIdle()
            }
        }
        enqueue(steps)
    }

    private fun startGuest() {
        val client = c.multiplayer.client
        if (client == null) {
            _ui.update { it.copy(ready = true, ended = Strings.get(R.string.mindi_not_in_room)) }
            return
        }
        net = NetRole.GUEST
        roomClient = client
        _ui.update { it.copy(net = NetRole.GUEST) }
        viewModelScope.launch {
            client.room.collect { room ->
                if (room == null) return@collect
                val map = HashMap<Int, Boolean>()
                map[0] = true
                room.guests.filter { it.seat >= 0 && it.seat != me }.forEach { map[it.seat] = it.connected }
                _ui.update { it.copy(netSeats = map) }
            }
        }
        viewModelScope.launch {
            for (msg in client.game) {
                when (msg) {
                    is NetMsg.RummyStart -> {
                        me = msg.yourSeat
                        seats = msg.seats
                        netSpeed = msg.speed
                        _ui.update { it.copy(ready = true, me = me, seats = seats) }
                    }
                    is NetMsg.RummySync -> {
                        me = msg.yourSeat
                        seats = msg.seats
                        netSpeed = msg.speed
                        queue.clear()
                        state = msg.state
                        _ui.update { it.copy(ready = true, me = me, seats = seats, state = msg.state) }
                        syncGroups(msg.state, regroup = true)
                        enqueue(listOf(RStep(REvent.Restored(msg.state.handNo), msg.state)))
                    }
                    is NetMsg.RummySteps -> {
                        awaitingServer = false
                        val list = msg.steps.map { RStep(it.event, it.state) }
                        if (list.isNotEmpty()) state = list.last().state
                        enqueue(list)
                    }
                    is NetMsg.SeatsChanged -> if (msg.rummySeats.isNotEmpty()) {
                        seats = msg.rummySeats
                        _ui.update { it.copy(seats = seats) }
                    }
                    is NetMsg.Emote -> if (msg.playerId != me) showEmote(msg.playerId, msg.text)
                    is NetMsg.ActionRejected -> {
                        awaitingServer = false
                        dragged = false
                        pendingDrop = null
                        c.sound.play(Sfx.ERROR, 0.6f)
                        if (queue.isEmpty()) afterIdle()
                    }
                    is NetMsg.Ended -> _ui.update { it.copy(ended = msg.reason) }
                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            client.status.collect { st ->
                if (st is ClientStatus.Ended && _ui.value.ended == null) _ui.update { it.copy(ended = st.reason) }
            }
        }
    }

    private fun onRemoteAction(seat: Int, a: RAction) {
        if (!submit(a)) roomHost?.sendToSeat(seat, NetMsg.ActionRejected("That move isn't allowed right now."))
        else if (a is RAction.Arrange) remoteArrange.remove(seat)?.cancel()
    }

    private fun showEmote(seat: Int, text: String) {
        _ui.update { it.copy(emotes = it.emotes + (seat to (text to System.currentTimeMillis()))) }
    }

    /** True if [seat] is a connected remote player (host side). */
    private fun remoteConnected(seat: Int) = net == NetRole.HOST && seat != me && seats.getOrNull(seat)?.isHuman == true && _ui.value.netSeats[seat] == true

    /** Wi-Fi turns always have a clock so one idle phone can't stall the table: the table's timer, else 30 s. */
    private fun netTurnMs(): Long = (state?.setup?.turnTimer ?: 0).takeIf { it > 0 }?.let { it * 1000L } ?: Net.REMOTE_TURN_MS

    /** A remote player ran out of time: draw blind and throw back what was drawn (like a timed-out turn here). */
    private fun startRemoteTimer(seat: Int) {
        val v = version
        timerJob = viewModelScope.launch {
            delay(netTurnMs() + 1500)
            if (version != v) return@launch
            val s = state ?: return@launch
            if (s.turn != seat) return@launch
            if (s.phase == RPhase.Pick) submit(RAction.PickClosed(seat))
            delay(600)
            val s2 = state ?: return@launch
            if (s2.phase == RPhase.Discard && s2.turn == seat) submit(RAction.Discard(seat, s2.hands[seat].last().id))
        }
    }

    /** A remote player must show their cards after a declaration; if they don't in time, the host groups them as well as it can. */
    private fun startRemoteArrange(seat: Int) {
        if (remoteArrange[seat]?.isActive == true) return
        remoteArrange[seat] = viewModelScope.launch {
            delay(ARRANGE_MS + 2000)
            val s = state ?: return@launch
            val ph = s.phase as? RPhase.Showdown ?: return@launch
            if (seat !in ph.pending) return@launch
            val best = withContext(Dispatchers.Default) { RummyMelds.best(s.hands[seat], s.wild) }
            submit(RAction.Arrange(seat, best.groups.map { g -> g.map { it.id } }))
        }
    }

    /** Someone else must move: guests wait for the host; the host waits for a remote player or runs a bot. */
    private fun othersTurn(seat: Int) {
        when {
            net == NetRole.GUEST -> Unit
            remoteConnected(seat) -> startRemoteTimer(seat)
            else -> scheduleBot(seat, null)
        }
    }

    private fun enqueue(steps: List<RStep>) {
        if (net == NetRole.HOST) roomHost?.broadcastRummy(steps)
        queue.addAll(steps)
        _ui.update { it.copy(idle = false, turnDeadline = 0L) }
        if (pumpJob?.isActive != true) pumpJob = viewModelScope.launch { pump() }
    }

    private fun duration(e: REvent): Long = when (e) {
        is REvent.Dealt -> 1500
        is REvent.PickedClosed, is REvent.PickedOpen -> 450
        is REvent.Discarded -> 450
        is REvent.Dropped -> 800
        is REvent.WrongShow -> 1400
        is REvent.Declared -> 1100
        is REvent.Arranged -> 250
        is REvent.Reshuffled -> 700
        is REvent.HandWon -> 500
        is REvent.Refilled -> 1200
        is REvent.Restored -> 200
    }

    private suspend fun pump() {
        while (queue.isNotEmpty()) {
            val step = queue.removeFirst()
            _ui.update { it.copy(state = step.state) }
            onStep(step)
            delay((duration(step.event) * speed).toLong())
        }
        afterIdle()
    }

    private fun fly(from: FlyPt, to: FlyPt, card: RCard?, faceUp: Boolean) {
        _flies.tryEmit(RFly(from, to, card, faceUp, flyId++))
    }

    /** A card I dragged is already where it's going: skip my next flying-card animation. */
    private var dragged = false
    /** Where the card I'm picking by dragging goes in my hand. */
    private var pendingDrop: HandDrop? = null

    private fun myFly(from: FlyPt, to: FlyPt, card: RCard?, faceUp: Boolean) {
        if (dragged) dragged = false else fly(from, to, card, faceUp)
    }

    private fun onStep(step: RStep) {
        val s = step.state
        when (val e = step.event) {
            is REvent.Dealt -> {
                myTurns = 0
                iDeclared = false
                dragged = false
                pendingDrop = null
                _ui.update { it.copy(selected = emptySet(), newCard = null, groups = emptyList(), nextHandAt = 0L, arrangeDeadline = 0L) }
                viewModelScope.launch {
                    c.sound.play(Sfx.SHUFFLE)
                    for (i in 0 until 13) {
                        for (seat in 0 until s.players) fly(FlyPt.Closed, if (seat == me) FlyPt.Hand else FlyPt.Seat(seat), null, false)
                        c.sound.play(Sfx.CARD_SLIDE, 0.3f)
                        delay((70 * speed).toLong())
                    }
                    syncGroups(s, regroup = true)
                }
                toast(Strings.get(R.string.rm_pop_hand, e.handNo))
            }
            is REvent.PickedClosed -> {
                if (e.seat == me) myFly(FlyPt.Closed, FlyPt.Hand, e.card, true) else fly(FlyPt.Closed, FlyPt.Seat(e.seat), e.card, false)
                c.sound.play(Sfx.CARD_SLIDE, 0.6f)
                if (e.seat == me) {
                    _ui.update { it.copy(newCard = e.card.id) }
                    syncGroups(s)
                    placePicked(e.card.id)
                }
            }
            is REvent.PickedOpen -> {
                if (e.seat == me) myFly(FlyPt.Open, FlyPt.Hand, e.card, true) else fly(FlyPt.Open, FlyPt.Seat(e.seat), e.card, true)
                c.sound.play(Sfx.CARD_SLIDE, 0.6f)
                if (e.seat == me) {
                    _ui.update { it.copy(newCard = e.card.id) }
                    syncGroups(s)
                    placePicked(e.card.id)
                }
            }
            is REvent.Discarded -> {
                if (e.seat == me) myFly(FlyPt.Hand, FlyPt.Open, e.card, true) else fly(FlyPt.Seat(e.seat), FlyPt.Open, e.card, true)
                c.sound.snap(0.8f)
                if (e.seat == me) {
                    myTurns++
                    _ui.update { it.copy(newCard = null, selected = emptySet()) }
                    syncGroups(s)
                }
            }
            is REvent.Dropped -> {
                c.sound.play(Sfx.WHOOSH, 0.6f)
                if (e.seat != me) bump(e.seat, listOf("🙈", "😒", "🏳️").random(rnd), 0.6)
            }
            is REvent.WrongShow -> {
                fly(if (e.seat == me) FlyPt.Hand else FlyPt.Seat(e.seat), FlyPt.Open, e.card, true)
                c.sound.play(Sfx.ERROR, 0.8f)
                c.haptics.medium()
                if (e.seat == me) {
                    _ui.update { it.copy(selected = emptySet(), newCard = null) }
                    syncGroups(s)
                } else bump(e.seat, "🤦", 1.0)
            }
            is REvent.Declared -> {
                fly(if (e.seat == me) FlyPt.Hand else FlyPt.Seat(e.seat), FlyPt.Finish, e.card, false)
                c.sound.play(Sfx.PRISM, 0.8f)
                c.haptics.medium()
                if (e.seat == me) {
                    _ui.update { it.copy(selected = emptySet(), newCard = null) }
                    syncGroups(s)
                    iDeclared = true
                    countDeclare()
                } else bump(e.seat, listOf("😎", "🥳", "✌️").random(rnd), 0.9)
            }
            is REvent.Arranged -> if (e.seat == me) _ui.update { it.copy(arrangeDeadline = 0L) }
            is REvent.Reshuffled -> {
                c.sound.play(Sfx.SHUFFLE, 0.6f)
                toast(Strings.get(R.string.rm_pop_reshuffle))
            }
            is REvent.HandWon -> {
                if (e.winner == me) {
                    c.sound.play(Sfx.WIN, 0.8f)
                    c.haptics.medium()
                } else c.sound.play(Sfx.LOSE, 0.6f)
                settleHand(s, e)
            }
            is REvent.Refilled -> refilled(e)
            is REvent.Restored -> Unit
        }
    }

    private fun afterIdle() {
        val s = state ?: return
        version++
        timerJob?.cancel()
        persist()
        when (val ph = s.phase) {
            is RPhase.HandOver -> {
                val at = System.currentTimeMillis() + NEXT_HAND_MS
                _ui.update { it.copy(idle = true, nextHandAt = at, arrangeDeadline = 0L) }
                arrangeJob?.cancel()
                remoteArrange.values.forEach { it.cancel() }
                remoteArrange.clear()
                // on Wi-Fi only the host deals the next hand
                if (net == NetRole.GUEST) return
                val v = version
                viewModelScope.launch {
                    delay(NEXT_HAND_MS)
                    if (version == v) nextHand()
                }
            }
            is RPhase.Showdown -> {
                _ui.update { it.copy(idle = true) }
                if (me in ph.pending) {
                    if (_ui.value.arrangeDeadline == 0L) startArrangeTimer()
                }
                if (net == NetRole.GUEST) return
                // friends on Wi-Fi arrange on their own phones (with a deadline)
                ph.pending.filter { remoteConnected(it) }.forEach { startRemoteArrange(it) }
                // bots (and dropped friends) show their cards one after another
                val bot = ph.pending.firstOrNull { it != me && !remoteConnected(it) }
                if (bot != null) scheduleBot(bot, 700L)
            }
            else -> {
                if (s.turn == me) {
                    _ui.update { it.copy(idle = true) }
                    c.sound.play(Sfx.TURN, 0.5f)
                    startTurnTimer()
                } else {
                    _ui.update { it.copy(idle = true, turnDeadline = 0L) }
                    othersTurn(s.turn)
                }
            }
        }
    }

    fun nextHand() {
        val s = state ?: return
        if (!s.handOver || net == NetRole.GUEST) return
        version++
        _ui.update { it.copy(nextHandAt = 0L) }
        val steps = RummyEngine.nextHand(s)
        state = steps.last().state
        enqueue(steps)
    }

    private fun scheduleBot(seat: Int, fixedMs: Long?) {
        val s = state ?: return
        val v = version
        val diff = seats.getOrNull(seat)?.difficulty ?: Difficulty.NORMAL
        botJob = viewModelScope.launch {
            val think = (fixedMs ?: ((if (s.phase == RPhase.Pick) 900L else 700L) + rnd.nextLong(0, 700))) * speed
            val t0 = System.currentTimeMillis()
            val decision = withContext(Dispatchers.Default) { RummyBot.decide(s, seat, diff, Random(rnd.nextLong())) }
            delay((think.toLong() - (System.currentTimeMillis() - t0)).coerceAtLeast(0))
            if (version != v || decision == null) return@launch
            if (!submit(decision)) {
                // should not happen: fall back to something always legal
                val fallback = when (s.phase) {
                    RPhase.Pick -> RAction.PickClosed(seat)
                    RPhase.Discard -> RAction.Discard(seat, s.hands[seat].last().id)
                    else -> null
                }
                if (fallback != null) submit(fallback)
            }
        }
    }

    private fun startTurnTimer() {
        val secs = if (isNet) (netTurnMs() / 1000).toInt() else state?.setup?.turnTimer ?: 0
        if (secs <= 0) {
            _ui.update { it.copy(turnDeadline = 0L, turnTotal = 0L) }
            return
        }
        val total = secs * 1000L
        val v = version
        _ui.update { it.copy(turnDeadline = System.currentTimeMillis() + total, turnTotal = total) }
        timerJob = viewModelScope.launch {
            delay(total)
            if (version != v) return@launch
            val s = state ?: return@launch
            toast(Strings.get(R.string.tp_toast_time_up))
            // a guest's time is kept by the host, which plays the timed-out move
            if (net == NetRole.GUEST) return@launch
            // time's up: draw blind and throw back what was drawn
            if (s.phase == RPhase.Pick) submit(RAction.PickClosed(me))
            val s2 = state ?: return@launch
            if (s2.phase == RPhase.Discard && s2.turn == me) {
                delay(500)
                submit(RAction.Discard(me, s2.hands[me].last().id))
            }
        }
    }

    private fun startArrangeTimer() {
        val total = ARRANGE_MS
        _ui.update { it.copy(arrangeDeadline = System.currentTimeMillis() + total) }
        arrangeJob?.cancel()
        arrangeJob = viewModelScope.launch {
            delay(total)
            val s = state ?: return@launch
            val ph = s.phase as? RPhase.Showdown ?: return@launch
            if (me in ph.pending) arrange()
        }
    }

    private fun submit(a: RAction): Boolean {
        val s = state ?: return false
        if (RummyEngine.validate(s, a) != null) return false
        if (net == NetRole.GUEST) {
            if (awaitingServer) return false
            awaitingServer = true
            timerJob?.cancel()
            if (a is RAction.Arrange) arrangeJob?.cancel()
            _ui.update { it.copy(idle = false, turnDeadline = 0L) }
            roomClient?.send(NetMsg.RummyAct(a))
            return true
        }
        version++
        timerJob?.cancel()
        val steps = RummyEngine.apply(s, a)
        state = steps.last().state
        enqueue(steps)
        return true
    }

    // ------------------------------------------------------------------ input

    private fun myTurn(): Boolean {
        val s = state ?: return false
        return _ui.value.idle && !s.handOver && s.phase !is RPhase.Showdown && s.turn == me
    }

    fun canPick(): Boolean = myTurn() && state?.phase == RPhase.Pick
    fun canThrow(): Boolean = myTurn() && state?.phase == RPhase.Discard

    fun tapCard(id: Int) {
        val s = state ?: return
        if (s.hands.getOrNull(me)?.none { it.id == id } != false) return
        c.sound.play(Sfx.SELECT, 0.5f)
        c.haptics.tick()
        _ui.update { u -> u.copy(selected = if (id in u.selected) u.selected - id else u.selected + id) }
    }

    fun clearSelection() = _ui.update { it.copy(selected = emptySet()) }

    fun pickClosed() {
        if (canPick()) submit(RAction.PickClosed(me))
    }

    fun pickOpen() {
        if (canPick()) submit(RAction.PickOpen(me))
    }

    fun discard() {
        val id = _ui.value.selected.singleOrNull() ?: return
        if (canThrow()) submit(RAction.Discard(me, id))
    }

    /** Finish with the selected card: asks first when the other 13 don't make a valid hand. */
    fun declare() {
        val id = _ui.value.selected.singleOrNull() ?: return
        if (!canThrow()) return
        val s = state ?: return
        val rest = s.hands[me].filter { it.id != id }
        val groups = myGroupsFor(rest)
        val ok = RummyMelds.evaluate(RummyEngine.groupsOf(rest, groups), s.wild).valid
        if (!ok) {
            _ui.update { it.copy(confirmWrong = id) }
            return
        }
        submit(RAction.Declare(me, id, groups))
    }

    fun confirmWrong(go: Boolean) {
        val id = _ui.value.confirmWrong
        _ui.update { it.copy(confirmWrong = null) }
        if (!go || id == null || !canThrow()) return
        val s = state ?: return
        submit(RAction.Declare(me, id, myGroupsFor(s.hands[me], exclude = id)))
    }

    fun askDrop() {
        if (canPick()) _ui.update { it.copy(confirmDrop = true) }
    }

    fun confirmDrop(go: Boolean) {
        _ui.update { it.copy(confirmDrop = false) }
        if (go && canPick()) submit(RAction.Drop(me))
    }

    /** Someone declared: show my cards as they're grouped now. */
    fun arrange() {
        val s = state ?: return
        val ph = s.phase as? RPhase.Showdown ?: return
        if (me !in ph.pending) return
        submit(RAction.Arrange(me, myGroupsFor(s.hands[me])))
    }

    /** The selected cards become a new group (Grouping turns off: my layout is mine now). */
    fun group() {
        val sel = _ui.value.selected
        if (sel.size < 2) return
        _ui.update { u ->
            val rest = u.groups.map { g -> g.filter { it !in sel } }.filter { it.isNotEmpty() }
            val ordered = u.groups.flatten().filter { it in sel }
            u.copy(groups = rest + listOf(ordered), selected = emptySet(), grouping = false)
        }
        groupVersion++
        persist()
    }

    /** Move the selected cards to the end of group [index]. */
    fun moveTo(index: Int) {
        val sel = _ui.value.selected
        if (sel.isEmpty()) return
        _ui.update { u ->
            val target = u.groups.getOrNull(index) ?: return@update u
            val targetKept = target.filter { it !in sel }
            val moved = u.groups.flatten().filter { it in sel }
            val out = u.groups.mapIndexed { i, g -> if (i == index) targetKept + moved else g.filter { it !in sel } }.filter { it.isNotEmpty() }
            u.copy(groups = out, selected = emptySet(), grouping = false)
        }
        groupVersion++
        c.sound.play(Sfx.SELECT, 0.6f)
        persist()
    }

    // ------------------------------------------------------------------ drag and drop

    /** Move [ids] (in the order they sit in my hand) to [drop]. Grouping turns off: my layout is mine now. */
    fun placeCards(ids: List<Int>, drop: HandDrop) {
        val s = state ?: return
        val hand = s.hands.getOrNull(me)?.map { it.id }?.toSet() ?: return
        val moving = ids.filter { it in hand }.toSet()
        if (moving.isEmpty()) return
        groupJob?.cancel()
        groupVersion++
        _ui.update { u ->
            val base = u.groups.map { g -> g.filter { it in hand } }.filter { it.isNotEmpty() }
            val ordered = base.flatten().filter { it in moving }
            val rest = base.map { g -> g.filter { it !in moving } }.filter { it.isNotEmpty() }.toMutableList()
            if (drop.newGroup || drop.group !in rest.indices) {
                rest.add(drop.group.coerceIn(0, rest.size), ordered)
            } else {
                val g = rest[drop.group]
                val k = drop.index.coerceIn(0, g.size)
                rest[drop.group] = g.take(k) + ordered + g.drop(k)
            }
            u.copy(groups = rest, selected = u.selected - moving, grouping = false)
        }
        c.sound.play(Sfx.SELECT, 0.6f)
        c.haptics.light()
        persist()
    }

    /** A small tick while a dragged card moves over a new spot. */
    fun dragTick() = c.haptics.tick()

    /** Dropped one card on the open pile: throw it. */
    fun dragDiscard(id: Int): Boolean {
        if (!canThrow() || state?.hands?.getOrNull(me)?.none { it.id == id } != false) return false
        dragged = true
        _ui.update { it.copy(selected = setOf(id)) }
        if (!submit(RAction.Discard(me, id))) { dragged = false; return false }
        return true
    }

    /** Dropped one card on the finish slot: declare with it. */
    fun dragDeclare(id: Int): Boolean {
        if (!canThrow() || state?.hands?.getOrNull(me)?.none { it.id == id } != false) return false
        _ui.update { it.copy(selected = setOf(id)) }
        declare()
        return true
    }

    /** Dragged a card from a pile into my hand: pick it, and put it where it was dropped. */
    fun dragPick(open: Boolean, drop: HandDrop): Boolean {
        if (!canPick() || (open && state?.openTop == null)) return false
        dragged = true
        pendingDrop = drop
        // keep my layout as it is so the drop spot still means the same place
        _ui.update { it.copy(grouping = false) }
        groupJob?.cancel()
        groupVersion++
        if (!submit(if (open) RAction.PickOpen(me) else RAction.PickClosed(me))) { dragged = false; pendingDrop = null; return false }
        return true
    }

    private fun placePicked(id: Int) {
        val drop = pendingDrop ?: return
        pendingDrop = null
        placeCards(listOf(id), drop)
        _ui.update { it.copy(newCard = id) }
    }

    fun sort() {
        val s = state ?: return
        groupVersion++
        _ui.update { u -> u.copy(groups = RummyMelds.bySuit(s.hands[me], s.wild).map { g -> g.map { it.id } }, grouping = false, selected = emptySet()) }
        persist()
    }

    fun setGrouping(on: Boolean) {
        val s = state ?: return
        _ui.update { it.copy(grouping = on) }
        if (on) syncGroups(s, regroup = true)
        persist()
    }

    fun emote(text: String) {
        _ui.update { it.copy(emotes = it.emotes + (me to (text to System.currentTimeMillis()))) }
        c.sound.play(Sfx.SELECT, 0.6f)
        when (net) {
            NetRole.HOST -> roomHost?.broadcast(NetMsg.Emote(me, text))
            NetRole.GUEST -> roomClient?.send(NetMsg.Emote(me, text))
            else -> Unit
        }
        if (isNet) return
        val s = state ?: return
        val others = (1 until s.players).toList()
        if (others.isNotEmpty() && rnd.nextDouble() < 0.6) viewModelScope.launch {
            delay(700)
            bump(others.random(rnd), listOf("😏", "😂", "🙈", "😎", "🤞").random(rnd), 1.0)
        }
    }

    private fun bump(seat: Int, emoji: String, chance: Double) {
        if (seat == me || seats.getOrNull(seat)?.isHuman == true || rnd.nextDouble() > chance) return
        _ui.update { it.copy(emotes = it.emotes + (seat to (emoji to System.currentTimeMillis()))) }
    }

    private fun toast(msg: String) = _ui.update { it.copy(banner = msg, bannerAt = System.currentTimeMillis()) }

    // ------------------------------------------------------------------ rewards

    private fun countDeclare() {
        val turns = myTurns + 1
        viewModelScope.launch {
            c.profile.update { p ->
                val st = p.rummy.stats
                p.copy(rummy = p.rummy.copy(stats = st.copy(declares = st.declares + 1, fastestDeclare = if (st.fastestDeclare == 0) turns else minOf(st.fastestDeclare, turns))))
            }
        }
    }

    private fun refilled(e: REvent.Refilled) {
        for (seat in e.seats) {
            if (seat == me) {
                toast(Strings.get(R.string.tp_toast_refill, e.chips))
                continue
            }
            val old = seats.getOrNull(seat) ?: continue
            // a friend on Wi-Fi keeps their seat (with a fresh stack); only a bot is replaced, and only by the host
            if (old.isHuman || net == NetRole.GUEST) continue
            val taken = seats.map { it.avatar }.toSet()
            val ch = Characters.all.filter { it.id !in taken }.randomOrNull(rnd) ?: continue
            seats = seats.toMutableList().also { it[seat] = RummySeat(ch.name, ch.id, isHuman = false, difficulty = old.difficulty) }
            toast(Strings.get(R.string.tp_toast_new_player, old.name, ch.name))
        }
        _ui.update { it.copy(seats = seats) }
        if (net == NetRole.HOST) roomHost?.updateSeats(rummy = seats)
    }

    /** Coins and XP after every hand (the table never ends), stats and achievements. */
    private fun settleHand(s: RummyState, e: REvent.HandWon) {
        val won = e.winner == me
        val diff = seats.filter { !it.isHuman }.maxOfOrNull { it.difficulty } ?: Difficulty.NORMAL
        val gain = e.delta.getOrElse(me) { 0 }
        val coins = if (won) ((10 + gain / 40).coerceAtMost(50) * DifficultyInfo.multiplier(diff)).toInt() else 0
        val xp = if (won) 20 else 5
        val declared = won && iDeclared
        val usedNoJoker = won && s.hands[me].none { RummyMelds.isJoker(it, s.wild) }
        val turns = myTurns
        val myFinish = s.finish.getOrNull(me)
        com.ngdstudio.mindimania.analytics.Analytics.event("game_end", "game" to "rummy", "won" to won, "players" to s.players, "net" to "local")
        viewModelScope.launch {
            var earned: List<Achievement> = emptyList()
            val np = c.profile.update { p ->
                val st = p.rummy.stats
                val streak = if (won) st.streak + 1 else 0
                val newStats = st.copy(
                    hands = st.hands + 1, handsWon = st.handsWon + if (won) 1 else 0,
                    drops = st.drops + if (myFinish == Finish.DROP) 1 else 0,
                    wrongShows = st.wrongShows + if (myFinish == Finish.WRONG_SHOW) 1 else 0,
                    bestStack = maxOf(st.bestStack, s.chips[me]), streak = streak, bestStreak = maxOf(st.bestStreak, streak),
                )
                earned = buildList {
                    fun award(id: String, cond: Boolean) { if (cond && id !in p.achievements) Achievements.byId(id)?.let { add(it) } }
                    award("rm_win", won)
                    award("rm_fast", won && declared && turns <= 6)
                    award("rm_clean", won && declared && usedNoJoker)
                    award("rm_expert", won && declared && diff == Difficulty.GENIUS)
                }
                p.copy(
                    coins = p.coins + coins + earned.sumOf { it.reward }, xp = p.xp + xp,
                    achievements = p.achievements + earned.map { it.id }, rummy = p.rummy.copy(stats = newStats),
                )
            }
            _ui.update { it.copy(profile = np) }
            if (earned.isNotEmpty()) {
                c.sound.play(Sfx.CHEST)
                toast(earned.joinToString("\n") { Strings.get(R.string.bhabhi_achievement, it.title) + "  +" + it.reward })
            }
        }
    }

    private fun persist() {
        if (isNet) return // Wi-Fi tables are never saved for Continue
        val s = state ?: return
        val u = _ui.value
        val save = RummySave(s, seats, u.groups, u.grouping, myTurns)
        SaveFiles.write(saveFile) { AppJson.encodeToString(RummySave.serializer(), save) }
    }

    fun abandon() = persist()

    override fun onCleared() {
        persist()
        super.onCleared()
    }

    companion object {
        const val SAVE_FILE = "rummy_save.json"
        const val NEXT_HAND_MS = 10_000L
        const val ARRANGE_MS = 30_000L
        fun hasSave(app: Application): Boolean = File(app.filesDir, SAVE_FILE).exists()
    }
}

/**
 * [groups] minus cards I no longer hold, plus the cards that are new in [hand], each put where it helps most:
 * into a group it turns into (or keeps) a valid sequence or set; else beside the nearest card of its suit, never
 * breaking a group that's already valid. A card with nowhere to go starts a group of its own.
 */
internal fun withNewCards(groups: List<List<Int>>, hand: List<RCard>, wild: Int): List<List<Int>> {
    val byId = hand.associateBy { it.id }
    val out = groups.map { g -> g.filter { it in byId } }.filter { it.isNotEmpty() }.map { it.toMutableList() }.toMutableList()
    val missing = hand.filter { c -> out.none { c.id in it } }
    if (out.isEmpty()) return if (missing.isEmpty()) emptyList() else listOf(missing.map { it.id })
    val alone = ArrayList<Int>()
    fun valid(k: GroupKind) = k == GroupKind.PURE || k == GroupKind.IMPURE || k == GroupKind.SET
    for (c in missing) {
        val joker = RummyMelds.isJoker(c, wild)
        var best = -1
        var bestScore = Int.MIN_VALUE
        out.forEachIndexed { i, g ->
            val cards = g.mapNotNull { byId[it] }
            val before = RummyMelds.classify(cards, wild)
            val after = RummyMelds.classify(cards + c, wild)
            val suited = cards.filter { !RummyMelds.isJoker(it, wild) && it.suit == c.suit }
            val ranked = cards.count { !RummyMelds.isJoker(it, wild) && it.rank == c.rank && it.suit != c.suit }
            val score = when {
                valid(after) -> 1000 + (if (after == GroupKind.PURE) 100 else 0) - cards.size
                valid(before) -> return@forEachIndexed // don't break a finished meld
                c.isPrinted -> return@forEachIndexed
                suited.isNotEmpty() -> 100 + suited.size * 10 - suited.minOf { kotlin.math.abs(it.rank - c.rank) }
                ranked > 0 && !joker -> 50 + ranked * 10
                else -> return@forEachIndexed
            }
            if (score > bestScore) { bestScore = score; best = i }
        }
        if (best < 0) { alone += c.id; continue }
        val g = out[best]
        // beside its suit in rank order; otherwise (a set, a joker) at the end
        val after = g.indexOfLast { id -> byId[id]?.let { !RummyMelds.isJoker(it, wild) && it.suit == c.suit && it.rank <= c.rank } == true }
        val first = g.indexOfFirst { id -> byId[id]?.let { !RummyMelds.isJoker(it, wild) && it.suit == c.suit } == true }
        val at = when {
            joker -> g.size
            after >= 0 -> after + 1
            first >= 0 -> first
            else -> g.size
        }
        g.add(at, c.id)
    }
    if (alone.isNotEmpty()) out += alone.toMutableList()
    return out
}
