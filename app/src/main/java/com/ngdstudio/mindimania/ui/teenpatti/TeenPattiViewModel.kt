package com.ngdstudio.mindimania.ui.teenpatti

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
import com.ngdstudio.mindimania.teenpatti.TPAction
import com.ngdstudio.mindimania.teenpatti.TPEvent
import com.ngdstudio.mindimania.teenpatti.TPKind
import com.ngdstudio.mindimania.teenpatti.TPOptions
import com.ngdstudio.mindimania.teenpatti.TPPhase
import com.ngdstudio.mindimania.teenpatti.TPStep
import com.ngdstudio.mindimania.teenpatti.TeenPattiBot
import com.ngdstudio.mindimania.teenpatti.TeenPattiEngine
import com.ngdstudio.mindimania.teenpatti.TeenPattiSeat
import com.ngdstudio.mindimania.teenpatti.TeenPattiSetup
import com.ngdstudio.mindimania.teenpatti.TeenPattiState
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

/** Match-long notes for rewards, kept with the save. */
@Serializable
data class TeenPattiNotes(
    val bestKind: Int = -1,
    val blindWins: Int = 0,
    val biggestPot: Int = 0,
    val trail: Boolean = false,
    val pure: Boolean = false,
    /** I won a hand at a show holding only a high card, or by everyone packing on a high card. */
    val bluffWin: Boolean = false,
)

@Serializable
data class TeenPattiSave(val state: TeenPattiState, val seats: List<TeenPattiSeat>, val notes: TeenPattiNotes = TeenPattiNotes())

/**
 * What the Teen Patti screen should start: a new table with [setup] (null = last setup), or the saved one.
 * Wi-Fi: HOST passes the room's [seats] and [seed]; GUEST waits for the host's TeenPattiStart.
 */
data class TeenPattiLaunch(
    val setup: TeenPattiSetup? = null,
    val resume: Boolean = false,
    val net: NetRole = NetRole.LOCAL,
    val seats: List<TeenPattiSeat>? = null,
    val seed: Long? = null,
)

data class TeenPattiResult(
    val place: Int,
    val chips: Int,
    val coins: Int,
    val xp: Int,
    val achievements: List<Achievement>,
)

data class TeenPattiUi(
    val ready: Boolean = false,
    val me: Int = 0,
    val seats: List<TeenPattiSeat> = emptyList(),
    val state: TeenPattiState? = null,
    val idle: Boolean = false,
    val options: TPOptions = TPOptions(),
    val result: TeenPattiResult? = null,
    val emotes: Map<Int, Pair<String, Long>> = emptyMap(),
    val banner: String? = null,
    val bannerAt: Long = 0L,
    val turnDeadline: Long = 0L,
    val turnTotal: Long = 0L,
    /** I packed (or lost a sideshow): the rest of the hand plays out quickly. */
    val fastForward: Boolean = false,
    val settings: Settings = Settings(),
    val profile: Profile = Profile(),
    val net: NetRole = NetRole.LOCAL,
    /** Remote human seats → connected (Wi-Fi only). */
    val netSeats: Map<Int, Boolean> = emptyMap(),
    /** Set when a Wi-Fi table ends unexpectedly (host left, connection lost). */
    val ended: String? = null,
)

class TeenPattiViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as CardsManiaApp).container
    private val saveFile = File(app.filesDir, SAVE_FILE)

    private val _ui = MutableStateFlow(TeenPattiUi())
    val ui: StateFlow<TeenPattiUi> = _ui
    private val _steps = MutableSharedFlow<TPStep>(extraBufferCapacity = 256)
    val steps: SharedFlow<TPStep> = _steps
    /** Latest step shown, so a newly created world can catch up. */
    var lastShown: TPStep? = null
        private set

    private var state: TeenPattiState? = null
    private var seats: List<TeenPattiSeat> = emptyList()
    private var notes = TeenPattiNotes()
    private var me = 0
    private var net = NetRole.LOCAL
    private var roomHost: RoomHost? = null
    private var roomClient: RoomClient? = null
    private var awaitingServer = false
    private var netSpeed: Int? = null
    private val isNet: Boolean get() = net != NetRole.LOCAL
    private val queue = ArrayDeque<TPStep>()
    private var pumpJob: Job? = null
    private var botJob: Job? = null
    private var timerJob: Job? = null
    private var version = 0L
    private var started = false
    private val rnd = Random(System.nanoTime())

    private val speedFactor: Float
        get() {
            val base = when (netSpeed ?: _ui.value.settings.speed) {
                0 -> 1.3f
                2 -> 0.65f
                else -> 1f
            }
            // the table keeps its normal pace even after I pack: I watch the rest of the hand
            return base
        }

    fun start(launch: TeenPattiLaunch) {
        if (started) return
        started = true
        com.ngdstudio.mindimania.analytics.Analytics.event("game_start", "game" to "teen_patti", "net" to launch.net.name.lowercase(), "resume" to launch.resume)
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
                    withContext(Dispatchers.Default) { runCatching { AppJson.decodeFromString(TeenPattiSave.serializer(), text) }.getOrNull() }
                }
                if (saved != null && !saved.state.isOver) {
                    state = saved.state
                    seats = saved.seats
                    notes = saved.notes
                    _ui.update { it.copy(ready = true, seats = seats, state = saved.state) }
                    val step = TPStep(TPEvent.Restored(saved.state.handNo), saved.state)
                    lastShown = step
                    _steps.tryEmit(step)
                    afterIdle()
                    return@launch
                }
            }
            val setup = TeenPattiEngine.normalize(launch.setup ?: p.teenPatti.setup)
            val seed = System.nanoTime()
            seats = buildSeats(setup, p, seed)
            c.profile.update { pr -> pr.copy(teenPatti = pr.teenPatti.copy(stats = pr.teenPatti.stats.copy(games = pr.teenPatti.stats.games + 1))) }
            val steps = withContext(Dispatchers.Default) { TeenPattiEngine.newGame(setup, seed) }
            state = steps.last().state
            _ui.update { it.copy(ready = true, seats = seats) }
            enqueue(steps)
        }
    }

    private fun buildSeats(setup: TeenPattiSetup, p: Profile, seed: Long): List<TeenPattiSeat> {
        val chars = Characters.all.shuffled(java.util.Random(seed))
        val you = TeenPattiSeat(p.name.ifBlank { Strings.get(R.string.tp_you) }, AvatarRef.player(p.avatar), isHuman = true)
        return listOf(you) + (1 until setup.players).map { i ->
            val ch = chars[(i - 1) % chars.size]
            TeenPattiSeat(ch.name, ch.id, isHuman = false, difficulty = setup.difficulty)
        }
    }

    // ------------------------------------------------------------------ Wi-Fi

    private fun startHost(launch: TeenPattiLaunch) {
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
        val steps = TeenPattiEngine.newGame(setup, launch.seed ?: System.nanoTime())
        state = steps.last().state
        host.tpStateProvider = { state }
        _ui.update { it.copy(ready = true, me = me, seats = seats, net = NetRole.HOST, netSeats = host.seatStatus.value) }
        viewModelScope.launch { for ((seat, a) in host.tpActions) onRemoteAction(seat, a) }
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
                // someone dropped while it's their turn: a bot takes over right away
                val s = state ?: return@collect
                val actor = s.turn
                if (_ui.value.idle && actor >= 0 && before[actor] == true && st[actor] == false) afterIdle()
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
                    is NetMsg.TeenPattiStart -> {
                        me = msg.yourSeat
                        seats = msg.seats
                        netSpeed = msg.speed
                        _ui.update { it.copy(ready = true, me = me, seats = seats) }
                    }
                    is NetMsg.TeenPattiSync -> {
                        me = msg.yourSeat
                        seats = msg.seats
                        netSpeed = msg.speed
                        queue.clear()
                        state = msg.state
                        _ui.update { it.copy(ready = true, me = me, seats = seats, state = msg.state) }
                        val step = TPStep(TPEvent.Restored(msg.state.handNo), msg.state)
                        lastShown = step
                        _steps.tryEmit(step)
                        afterIdle()
                    }
                    is NetMsg.TeenPattiSteps -> {
                        awaitingServer = false
                        val list = msg.steps.map { TPStep(it.event, it.state) }
                        if (list.isNotEmpty()) state = list.last().state
                        enqueue(list)
                    }
                    is NetMsg.SeatsChanged -> if (msg.tpSeats.isNotEmpty()) {
                        seats = msg.tpSeats
                        _ui.update { it.copy(seats = seats) }
                    }
                    is NetMsg.Emote -> if (msg.playerId != me) showEmote(msg.playerId, msg.text)
                    is NetMsg.ActionRejected -> {
                        awaitingServer = false
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

    private fun onRemoteAction(seat: Int, a: TPAction) {
        if (!submit(a)) roomHost?.sendToSeat(seat, NetMsg.ActionRejected("That move isn't allowed right now."))
    }

    private fun showEmote(seat: Int, text: String) {
        _ui.update { it.copy(emotes = it.emotes + (seat to (text to System.currentTimeMillis()))) }
    }

    /** True if [seat] is a connected remote player (host side). */
    private fun remoteConnected(seat: Int) = net == NetRole.HOST && seat != me && seats.getOrNull(seat)?.isHuman == true && _ui.value.netSeats[seat] == true

    /** Wi-Fi turns always have a clock so one idle phone can't stall the table: the room's timer, else 30 s. */
    private fun netTurnMs(): Long = (state?.setup?.turnTimer ?: 0).takeIf { it > 0 }?.let { it * 1000L } ?: Net.REMOTE_TURN_MS

    /** A remote player ran out of time: decline a sideshow, otherwise pack (like a timed-out turn here). */
    private fun startRemoteTimer(seat: Int) {
        val v = version
        timerJob = viewModelScope.launch {
            delay(netTurnMs() + 1500)
            if (version != v) return@launch
            val s = state ?: return@launch
            if (s.turn != seat) return@launch
            val o = TeenPattiEngine.options(s, seat)
            submit(if (o.mustReply) TPAction.SideshowReply(seat, false) else TPAction.Pack(seat))
        }
    }

    /** Someone else must act: guests wait for the host; the host waits for a remote player or runs a bot. */
    private fun othersTurn(seat: Int) {
        when {
            net == NetRole.GUEST -> Unit
            remoteConnected(seat) -> startRemoteTimer(seat)
            else -> scheduleBot(seat)
        }
    }

    // ------------------------------------------------------------------ step pump

    private fun enqueue(steps: List<TPStep>) {
        if (net == NetRole.HOST) roomHost?.broadcastTeenPatti(steps)
        queue.addAll(steps)
        _ui.update { it.copy(idle = false, options = TPOptions(), turnDeadline = 0L) }
        if (pumpJob?.isActive != true) pumpJob = viewModelScope.launch { pump() }
    }

    private fun duration(e: TPEvent): Long = when (e) {
        is TPEvent.Dealt -> 300L + 3 * 6 * 70 + 500
        is TPEvent.Saw -> 300
        is TPEvent.Chaal -> if (e.raised) 650 else 480
        is TPEvent.Packed -> 550
        is TPEvent.SideshowAsked -> 700
        is TPEvent.SideshowResult -> if (e.accepted) 1900 else 800
        is TPEvent.ShowCalled -> 700
        is TPEvent.AllIn -> 900
        // the cards turn up, then wild cards swap for what they play as
        is TPEvent.Showdown -> 1500L + e.seats.size * 260
        is TPEvent.PotWon -> 1900
        is TPEvent.MatchOver -> 600
        is TPEvent.Refilled -> 1300
        is TPEvent.Restored -> 300
    }

    private suspend fun pump() {
        while (queue.isNotEmpty()) {
            val step = queue.removeFirst()
            lastShown = step
            note(step)
            _ui.update { it.copy(state = step.state) }
            _steps.tryEmit(step)
            feedback(step)
            delay((duration(step.event) * speedFactor).toLong())
        }
        afterIdle()
    }

    /** Options for me: what the buttons show (my turn) or only SEE (someone else's turn). */
    private fun myOptions(s: TeenPattiState): TPOptions = TeenPattiEngine.options(s, me)

    private fun afterIdle() {
        val s = state ?: return
        version++
        timerJob?.cancel()
        persist()
        turnBuzz(!s.isOver && s.phase !is TPPhase.HandOver && s.turn == me)
        when {
            s.isOver -> {
                _ui.update { it.copy(idle = true, options = TPOptions()) }
                finishGame(s)
            }
            s.phase is TPPhase.HandOver -> {
                _ui.update { it.copy(idle = true, options = TPOptions()) }
                // on Wi-Fi only the host deals the next hand
                if (net == NetRole.GUEST) return
                val v = version
                viewModelScope.launch {
                    // give everyone time to read the opened hands (and what the wild cards became)
                    delay(((if (s.shown.isNotEmpty()) 2300 else 700) * speedFactor).toLong())
                    if (version != v) return@launch
                    val steps = TeenPattiEngine.nextHand(s)
                    state = steps.last().state
                    version++
                    enqueue(steps)
                }
            }
            s.turn == me -> {
                _ui.update { it.copy(idle = true, options = myOptions(s)) }
                c.sound.play(Sfx.TURN, 0.5f)
                startTimer()
            }
            else -> {
                _ui.update { it.copy(idle = true, options = myOptions(s), turnDeadline = 0L) }
                othersTurn(s.turn)
            }
        }
    }

    /**
     * Buzz when the turn passes to me (not again while it stays mine, e.g. a second step of the same turn), and once
     * more if nothing has happened [TURN_REMIND_MS] later: I may have put the phone down.
     */
    private var turnBuzzed = false
    private var turnRemind: kotlinx.coroutines.Job? = null

    private fun turnBuzz(mine: Boolean) {
        turnRemind?.cancel()
        if (mine && !turnBuzzed) {
            c.haptics.turn()
            val v = version
            turnRemind = viewModelScope.launch {
                kotlinx.coroutines.delay(com.ngdstudio.mindimania.audio.Haptics.TURN_REMIND_MS)
                if (version == v) c.haptics.turn()
            }
        }
        turnBuzzed = mine
    }

    private fun scheduleBot(seat: Int) {
        val s = state ?: return
        val v = version
        val diff = seats.getOrNull(seat)?.difficulty ?: Difficulty.NORMAL
        botJob = viewModelScope.launch {
            // bots take their time, like real players thinking it over
            val think = ((if (s.phase is TPPhase.Sideshow) 1800L else 1500L) + rnd.nextLong(0, 1000)) * speedFactor
            val started = System.currentTimeMillis()
            val decision = withContext(Dispatchers.Default) { TeenPattiBot.decide(s, seat, diff, Random(rnd.nextLong())) }
            // looking at the cards is quick; the real decision takes a moment
            val wait = if (decision is TPAction.See) think * 0.5f else think
            delay((wait.toLong() - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            if (version != v || decision == null) return@launch
            if (!submit(decision)) submit(TPAction.Pack(seat))
        }
    }

    private fun startTimer() {
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
            // a guest's time is kept by the host, which plays the timed-out move
            if (net == NetRole.GUEST) return@launch
            val o = myOptions(s)
            // time's up: decline a sideshow, otherwise pack
            val a = if (o.mustReply) TPAction.SideshowReply(me, false) else TPAction.Pack(me)
            c.sound.play(Sfx.ERROR, 0.6f)
            toast(Strings.get(R.string.tp_toast_time_up))
            submit(a)
        }
    }

    private fun submit(a: TPAction): Boolean {
        val s = state ?: return false
        if (TeenPattiEngine.validate(s, a) != null) return false
        if (net == NetRole.GUEST) {
            // the host decides; looking at my cards can go any time, everything else waits for the answer
            if (a !is TPAction.See) {
                if (awaitingServer) return false
                awaitingServer = true
                timerJob?.cancel()
                _ui.update { it.copy(idle = false, options = TPOptions(), turnDeadline = 0L) }
            }
            roomClient?.send(NetMsg.TeenPattiAct(a))
            return true
        }
        // looking at my own cards while someone else thinks must not cancel their move
        val keepsBot = a is TPAction.See && a.seat == me && s.turn != me
        if (!keepsBot) {
            version++
            timerJob?.cancel()
        }
        val steps = TeenPattiEngine.apply(s, a)
        state = steps.last().state
        if (keepsBot) {
            if (net == NetRole.HOST) roomHost?.broadcastTeenPatti(steps)
            // just flip my cards: the step pump may be idle while a bot thinks
            for (st in steps) {
                lastShown = st
                _steps.tryEmit(st)
                feedback(st)
            }
            _ui.update { it.copy(state = state, options = myOptions(state!!)) }
            persist()
            return true
        }
        enqueue(steps)
        return true
    }

    // ------------------------------------------------------------------ input

    private fun canAct() = _ui.value.idle && state?.handOver == false && state?.turn == me

    fun see() {
        val s = state ?: return
        if (!TeenPattiEngine.options(s, me).canSee) return
        // only between animations, so the flip never lands before the deal has finished
        if (queue.isNotEmpty() || (s.turn == me && !_ui.value.idle)) return
        c.haptics.tick()
        submit(TPAction.See(me))
    }

    fun tapCards() {
        val o = _ui.value.options
        if (o.canSee || state?.let { TeenPattiEngine.options(it, me).canSee } == true) see()
    }

    fun chaal(raise: Boolean) = act(TPAction.Chaal(me, raise))
    fun pack() = act(TPAction.Pack(me))
    fun show() = act(TPAction.Show(me))
    fun allIn() = act(TPAction.AllIn(me))
    fun sideshow() = act(TPAction.Sideshow(me))
    fun reply(accept: Boolean) = act(TPAction.SideshowReply(me, accept))

    private fun act(a: TPAction) {
        if (!canAct()) return
        if (submit(a)) c.haptics.light() else c.sound.play(Sfx.ERROR, 0.5f)
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
        val others = (0 until s.players).filter { it != me && s.inHand[it] }
        if (others.isNotEmpty() && rnd.nextDouble() < 0.6) viewModelScope.launch {
            delay(700)
            val who = others.random(rnd)
            val reply = listOf("😏", "😂", "🙈", "😎", "🤞").random(rnd)
            _ui.update { it.copy(emotes = it.emotes + (who to (reply to System.currentTimeMillis()))) }
        }
    }

    private fun toast(msg: String) = _ui.update { it.copy(banner = msg, bannerAt = System.currentTimeMillis()) }

    // ------------------------------------------------------------------ feedback

    /** A bot reacts with an emoji. */
    private fun bump(seat: Int, emoji: String, chance: Double) {
        if (seat == me || seats.getOrNull(seat)?.isHuman == true || rnd.nextDouble() > chance) return
        _ui.update { it.copy(emotes = it.emotes + (seat to (emoji to System.currentTimeMillis()))) }
    }

    /** Remembers what the rewards need (best hand held, blind wins, the biggest pot I won). */
    private fun note(step: TPStep) {
        val s = step.state
        when (val e = step.event) {
            is TPEvent.Dealt -> if (s.inHand[me]) {
                val k = TeenPattiEngine.kind(s.hands[me])
                notes = notes.copy(
                    bestKind = maxOf(notes.bestKind, k.ordinal),
                    trail = notes.trail || k == TPKind.TRAIL,
                    pure = notes.pure || k == TPKind.PURE_SEQUENCE,
                )
            }
            is TPEvent.Refilled -> refilled(e)
            is TPEvent.PotWon -> {
                val i = e.winners.indexOf(me)
                if (i >= 0) {
                    // only where a high card is the weakest hand (not Muflis, 999 or the wild-card games)
                    val classicRanks = s.variant == com.ngdstudio.mindimania.teenpatti.TPVariant.CLASSIC || s.variant == com.ngdstudio.mindimania.teenpatti.TPVariant.FOUR_X_BOOT
                    val high = classicRanks && TeenPattiEngine.kind(s.hands[me]) == TPKind.HIGH_CARD
                    notes = notes.copy(
                        blindWins = notes.blindWins + if (!s.seen[me]) 1 else 0,
                        biggestPot = maxOf(notes.biggestPot, e.amounts[i]),
                        bluffWin = notes.bluffWin || (high && s.seen[me]),
                    )
                }
                if (s.endless) settleHand(s, e)
            }
            else -> Unit
        }
    }

    /** Endless table: a broke bot leaves and a new one sits down; a broke me gets a fresh stack. */
    private fun refilled(e: TPEvent.Refilled) {
        for (seat in e.seats) {
            if (seat == me) {
                toast(Strings.get(R.string.tp_toast_refill, e.chips))
                viewModelScope.launch {
                    c.profile.update { p -> p.copy(teenPatti = p.teenPatti.copy(stats = p.teenPatti.stats.copy(refills = p.teenPatti.stats.refills + 1))) }
                }
                continue
            }
            val old = seats.getOrNull(seat) ?: continue
            // a friend on Wi-Fi keeps their seat (with a fresh stack); only a bot is replaced, and only by the host
            if (old.isHuman || net == NetRole.GUEST) continue
            val taken = seats.map { it.avatar }.toSet()
            val ch = Characters.all.filter { it.id !in taken }.randomOrNull(rnd) ?: continue
            seats = seats.toMutableList().also { it[seat] = TeenPattiSeat(ch.name, ch.id, isHuman = false, difficulty = old.difficulty) }
            toast(Strings.get(R.string.tp_toast_new_player, old.name, ch.name))
        }
        _ui.update { it.copy(seats = seats) }
        if (net == NetRole.HOST) roomHost?.updateSeats(tp = seats)
    }

    /**
     * Endless table: rewards and stats are settled after every hand (there is no match end).
     * A hand I win pays a few coins (more against harder bots); every hand gives a little XP.
     */
    private fun settleHand(s: TeenPattiState, e: TPEvent.PotWon) {
        if (!s.inHand[me]) return
        val i = e.winners.indexOf(me)
        val won = i >= 0
        val diff = seats.filter { !it.isHuman }.maxOfOrNull { it.difficulty } ?: Difficulty.NORMAL
        val coins = if (won) ((8 + e.amounts[i] / 150).coerceAtMost(40) * DifficultyInfo.multiplier(diff)).toInt() else 0
        val xp = if (won) 12 else 3
        val kind = TeenPattiEngine.kind(s.hands[me])
        val showWin = won && e.reason != com.ngdstudio.mindimania.teenpatti.WinReason.LAST_STANDING
        // the table never ends, so each hand counts as one game (like Rummy)
        com.ngdstudio.mindimania.analytics.Analytics.event("game_end", "game" to "teen_patti", "won" to won, "players" to s.players, "variant" to s.setup.variant.name.lowercase(), "net" to net.name.lowercase())
        viewModelScope.launch {
            var earned: List<Achievement> = emptyList()
            val np = c.profile.update { p ->
                val st = p.teenPatti.stats
                val streak = if (won) st.streak + 1 else 0
                val newStats = st.copy(
                    hands = st.hands + 1,
                    handsWon = st.handsWon + if (won) 1 else 0,
                    blindWins = st.blindWins + if (won && !s.seen[me]) 1 else 0,
                    biggestPot = if (won) maxOf(st.biggestPot, e.amounts[i]) else st.biggestPot,
                    bestKind = maxOf(st.bestKind, kind.ordinal),
                    bestStack = maxOf(st.bestStack, s.chips[me]),
                    streak = streak,
                    bestStreak = maxOf(st.bestStreak, streak),
                )
                earned = buildList {
                    fun award(id: String, cond: Boolean) { if (cond && id !in p.achievements) Achievements.byId(id)?.let { add(it) } }
                    award("tp_win", s.chips[me] >= TeenPattiEngine.START_CHIPS * 2)
                    award("tp_trail", kind == TPKind.TRAIL)
                    award("tp_pure", kind == TPKind.PURE_SEQUENCE)
                    award("tp_blind", won && !s.seen[me])
                    award("tp_bluff", notes.bluffWin)
                    award("tp_expert", showWin && diff == Difficulty.GENIUS)
                }
                p.copy(
                    coins = p.coins + coins + earned.sumOf { it.reward },
                    xp = p.xp + xp,
                    achievements = p.achievements + earned.map { it.id },
                    teenPatti = p.teenPatti.copy(stats = newStats),
                )
            }
            _ui.update { it.copy(profile = np) }
            if (earned.isNotEmpty()) {
                c.sound.play(Sfx.CHEST)
                toast(earned.joinToString("\n") { Strings.get(R.string.bhabhi_achievement, it.title) + "  +" + it.reward })
            }
        }
    }

    private fun feedback(step: TPStep) {
        when (val e = step.event) {
            is TPEvent.Dealt -> viewModelScope.launch {
                c.sound.play(Sfx.SHUFFLE)
                delay((300 * speedFactor).toLong())
                repeat(step.state.inHand.count { it } * 3) {
                    c.sound.play(Sfx.CARD_SLIDE, 0.35f)
                    delay((70 * speedFactor).toLong())
                }
            }
            is TPEvent.Saw -> if (e.seat == me) c.sound.play(Sfx.CARD_SLIDE, 0.7f)
            is TPEvent.Chaal -> {
                c.sound.play(Sfx.COIN, if (e.raised) 0.8f else 0.5f)
                if (e.raised) bump(e.seat, listOf("😎", "💪", "🔥").random(rnd), 0.35)
            }
            is TPEvent.Packed -> {
                c.sound.play(Sfx.WHOOSH, 0.5f)
                bump(e.seat, listOf("😒", "🙄", "😩").random(rnd), 0.4)
            }
            is TPEvent.SideshowAsked -> {
                c.sound.play(Sfx.COIN, 0.6f)
                if (e.to == me) c.haptics.medium()
            }
            is TPEvent.SideshowResult -> viewModelScope.launch {
                if (!e.accepted) return@launch
                delay((700 * speedFactor).toLong())
                c.sound.play(Sfx.BLOCK, 0.6f)
                if (e.loser == me) c.haptics.medium() else bump(e.loser, listOf("😤", "😭", "🤦").random(rnd), 0.7)
            }
            is TPEvent.ShowCalled -> c.sound.play(Sfx.WHOOSH, 0.8f)
            is TPEvent.AllIn -> {
                c.sound.play(Sfx.BLOCK)
                c.haptics.medium()
                bump(e.seat, "😬", 0.6)
            }
            is TPEvent.Showdown -> viewModelScope.launch {
                e.seats.forEach { _ ->
                    c.sound.snap(0.9f)
                    delay((260 * speedFactor).toLong())
                }
            }
            is TPEvent.PotWon -> viewModelScope.launch {
                delay(((if (step.state.shown.isNotEmpty()) 1300L else 300L) * speedFactor).toLong())
                if (me in e.winners) {
                    c.sound.play(Sfx.WIN, 0.7f)
                    c.haptics.medium()
                } else {
                    c.sound.play(Sfx.COIN, 0.6f)
                    e.winners.forEach { bump(it, listOf("😎", "🥳", "🤑").random(rnd), 0.7) }
                }
            }
            is TPEvent.MatchOver -> if (step.state.ranking.first() == me) c.sound.play(Sfx.PRISM, 0.8f) else c.sound.play(Sfx.LOSE, 0.7f)
            is TPEvent.Refilled -> c.sound.play(Sfx.COIN, 0.7f)
            is TPEvent.Restored -> Unit
        }
    }

    // ------------------------------------------------------------------ results

    private fun finishGame(s: TeenPattiState) {
        if (_ui.value.result != null) return
        val place = s.placeOf(me)
        val won = place == 1
        com.ngdstudio.mindimania.analytics.Analytics.event("game_end", "game" to "teen_patti_match", "won" to won, "place" to place, "players" to s.players, "net" to net.name.lowercase())
        viewModelScope.launch {
            SaveFiles.delete(saveFile)
            var result: TeenPattiResult? = null
            val newProfile = c.profile.update { p ->
                val st = p.teenPatti.stats
                val streak = if (won) st.streak + 1 else 0
                val newStats = st.copy(
                    games = st.games + 1,
                    wins = st.wins + if (won) 1 else 0,
                    hands = st.hands + s.handNo,
                    handsWon = st.handsWon + s.handsWon[me],
                    blindWins = st.blindWins + notes.blindWins,
                    biggestPot = maxOf(st.biggestPot, notes.biggestPot),
                    bestKind = maxOf(st.bestKind, notes.bestKind),
                    streak = streak,
                    bestStreak = maxOf(st.bestStreak, streak),
                )
                val diff = seats.filter { !it.isHuman }.maxOfOrNull { it.difficulty } ?: Difficulty.NORMAL
                // coins come from where you finished, never from the table chips
                var coins = if (place == s.players) 20 else ((40 + (s.players - place) * 30) * DifficultyInfo.multiplier(diff)).toInt()
                val xp = if (place == s.players) 25 else 50 + (s.players - place) * 20
                val earned = buildList {
                    fun award(id: String, cond: Boolean) { if (cond && id !in p.achievements) Achievements.byId(id)?.let { add(it) } }
                    award("tp_win", won)
                    award("tp_trail", notes.trail)
                    award("tp_pure", notes.pure)
                    award("tp_blind", notes.blindWins > 0)
                    award("tp_bluff", notes.bluffWin)
                    award("tp_expert", won && diff == Difficulty.GENIUS)
                }
                coins += earned.sumOf { it.reward }
                result = TeenPattiResult(place, s.chips[me], coins, xp, earned)
                p.copy(
                    coins = p.coins + coins,
                    xp = p.xp + xp,
                    achievements = p.achievements + earned.map { it.id },
                    teenPatti = p.teenPatti.copy(stats = newStats),
                )
            }
            _ui.update { it.copy(profile = newProfile) }
            delay(900)
            _ui.update { it.copy(result = result) }
            if (result?.achievements?.isNotEmpty() == true) c.sound.play(Sfx.CHEST)
        }
    }

    private fun persist() {
        if (isNet) return // Wi-Fi tables are never saved for Continue
        val s = state ?: return
        if (s.isOver) return
        val save = TeenPattiSave(s, seats, notes)
        SaveFiles.write(saveFile) { AppJson.encodeToString(TeenPattiSave.serializer(), save) }
    }

    fun abandon() = persist()

    override fun onCleared() {
        persist()
        super.onCleared()
    }

    companion object {
        const val SAVE_FILE = "teenpatti_save.json"
        fun hasSave(app: Application): Boolean = File(app.filesDir, SAVE_FILE).exists()
    }
}
