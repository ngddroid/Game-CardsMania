package com.puzzel.lab.cardsmania.ui.bhabhi

import com.puzzel.lab.cardsmania.data.SaveFiles
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.puzzel.lab.cardsmania.CardsManiaApp
import com.puzzel.lab.cardsmania.R
import com.puzzel.lab.cardsmania.audio.Sfx
import com.puzzel.lab.cardsmania.audio.Track
import com.puzzel.lab.cardsmania.bhabhi.BCard
import com.puzzel.lab.cardsmania.bhabhi.BStep
import com.puzzel.lab.cardsmania.bhabhi.BhabhiAction
import com.puzzel.lab.cardsmania.bhabhi.BhabhiBot
import com.puzzel.lab.cardsmania.bhabhi.BhabhiEngine
import com.puzzel.lab.cardsmania.bhabhi.BhabhiEvent
import com.puzzel.lab.cardsmania.bhabhi.BhabhiSeat
import com.puzzel.lab.cardsmania.bhabhi.BhabhiSetup
import com.puzzel.lab.cardsmania.bhabhi.BhabhiState
import com.puzzel.lab.cardsmania.data.Achievement
import com.puzzel.lab.cardsmania.data.Achievements
import com.puzzel.lab.cardsmania.data.AppJson
import com.puzzel.lab.cardsmania.data.AvatarRef
import com.puzzel.lab.cardsmania.data.Characters
import com.puzzel.lab.cardsmania.data.DifficultyInfo
import com.puzzel.lab.cardsmania.data.Profile
import com.puzzel.lab.cardsmania.data.Settings
import com.puzzel.lab.cardsmania.engine.model.Difficulty
import com.puzzel.lab.cardsmania.i18n.Strings
import com.puzzel.lab.cardsmania.net.ClientStatus
import com.puzzel.lab.cardsmania.net.Net
import com.puzzel.lab.cardsmania.net.NetMsg
import com.puzzel.lab.cardsmania.net.NetRole
import com.puzzel.lab.cardsmania.net.RoomClient
import com.puzzel.lab.cardsmania.net.RoomHost
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

/** Suit name in the app's language (the engine's [BhabhiEngine.suitName] stays English for its rule messages). */
internal fun bhabhiSuitName(suit: Int): String = Strings.get(
    when (suit) {
        0 -> R.string.bhabhi_suit_spades
        1 -> R.string.bhabhi_suit_hearts
        2 -> R.string.bhabhi_suit_diamonds
        else -> R.string.bhabhi_suit_clubs
    }
)

@Serializable
data class BhabhiSave(val state: BhabhiState, val seats: List<BhabhiSeat>)

/**
 * What the Bhabhi screen should start: a new game with [setup] (null = last setup), or the saved game.
 * Multiplayer: HOST passes the seated [seats] and [seed] from the room; GUEST waits for the host's BhabhiStart.
 */
data class BhabhiLaunch(
    val setup: BhabhiSetup? = null,
    val resume: Boolean = false,
    val net: NetRole = NetRole.LOCAL,
    val seats: List<BhabhiSeat>? = null,
    val seed: Long? = null,
)

data class BhabhiResult(
    val place: Int,
    val bhabhi: Boolean,
    val coins: Int,
    val xp: Int,
    val achievements: List<Achievement>,
)

data class BhabhiUi(
    val ready: Boolean = false,
    val me: Int = 0,
    val seats: List<BhabhiSeat> = emptyList(),
    val state: BhabhiState? = null,
    val idle: Boolean = false,
    val legal: Set<Int> = emptySet(),
    val selected: Int? = null,
    val hint: Int? = null,
    val result: BhabhiResult? = null,
    val emotes: Map<Int, Pair<String, Long>> = emptyMap(),
    val banner: String? = null,
    val bannerAt: Long = 0L,
    val turnDeadline: Long = 0L,
    val turnTotal: Long = 0L,
    /** I got away and asked the rest of the game to play out quickly. */
    val fastForward: Boolean = false,
    val settings: Settings = Settings(),
    val profile: Profile = Profile(),
    val net: NetRole = NetRole.LOCAL,
    /** Remote human seats → connected (multiplayer only). */
    val netSeats: Map<Int, Boolean> = emptyMap(),
    /** Set when a multiplayer game ends unexpectedly (host left, connection lost). */
    val ended: String? = null,
)

class BhabhiViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as CardsManiaApp).container
    private val saveFile = File(app.filesDir, SAVE_FILE)

    private val _ui = MutableStateFlow(BhabhiUi())
    val ui: StateFlow<BhabhiUi> = _ui
    private val _steps = MutableSharedFlow<BStep>(extraBufferCapacity = 256)
    val steps: SharedFlow<BStep> = _steps
    /** Latest step shown, so a newly created world can catch up. */
    var lastShown: BStep? = null
        private set

    private var state: BhabhiState? = null
    private var seats: List<BhabhiSeat> = emptyList()
    private var me = 0
    private var net = NetRole.LOCAL
    private var roomHost: RoomHost? = null
    private var roomClient: RoomClient? = null
    private var awaitingServer = false
    private var netSpeed: Int? = null
    private val isNet: Boolean get() = net != NetRole.LOCAL
    private val queue = ArrayDeque<BStep>()
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
            return if (_ui.value.fastForward) base * 0.3f else base
        }

    fun start(launch: BhabhiLaunch) {
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
                    withContext(Dispatchers.Default) { runCatching { AppJson.decodeFromString(BhabhiSave.serializer(), text) }.getOrNull() }
                }
                if (saved != null && !saved.state.isOver) {
                    state = saved.state
                    seats = saved.seats
                    _ui.update { it.copy(ready = true, seats = seats, state = saved.state) }
                    val step = BStep(BhabhiEvent.Restored(saved.state.trickNo), saved.state)
                    lastShown = step
                    _steps.tryEmit(step)
                    afterIdle()
                    return@launch
                }
            }
            val setup = BhabhiEngine.normalize(launch.setup ?: p.bhabhi.setup)
            val seed = System.nanoTime()
            seats = buildSeats(setup, p, seed)
            val steps = BhabhiEngine.newGame(setup, seed)
            state = steps.last().state
            _ui.update { it.copy(ready = true, seats = seats) }
            enqueue(steps)
        }
    }

    private fun buildSeats(setup: BhabhiSetup, p: Profile, seed: Long): List<BhabhiSeat> {
        val chars = Characters.all.shuffled(java.util.Random(seed))
        val you = BhabhiSeat(p.name.ifBlank { Strings.get(R.string.bhabhi_you) }, AvatarRef.player(p.avatar), isHuman = true)
        return listOf(you) + (1 until setup.players).map { i ->
            val ch = chars[(i - 1) % chars.size]
            BhabhiSeat(ch.name, ch.id, isHuman = false, difficulty = setup.difficulty)
        }
    }

    // ------------------------------------------------------------------ multiplayer

    private fun startHost(launch: BhabhiLaunch) {
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
        val steps = BhabhiEngine.newGame(setup, launch.seed ?: System.nanoTime())
        state = steps.last().state
        host.bhabhiStateProvider = { state }
        _ui.update { it.copy(ready = true, me = me, seats = seats, net = NetRole.HOST, netSeats = host.seatStatus.value) }
        viewModelScope.launch { for ((seat, a) in host.bhabhiActions) onRemoteAction(seat, a) }
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
        val channel = client.game
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
            for (msg in channel) {
                when (msg) {
                    is NetMsg.BhabhiStart -> {
                        me = msg.yourSeat
                        seats = msg.seats
                        netSpeed = msg.speed
                        _ui.update { it.copy(ready = true, me = me, seats = seats) }
                    }
                    is NetMsg.BhabhiSync -> {
                        me = msg.yourSeat
                        seats = msg.seats
                        netSpeed = msg.speed
                        queue.clear()
                        state = msg.state
                        _ui.update { it.copy(ready = true, me = me, seats = seats, state = msg.state) }
                        val step = BStep(BhabhiEvent.Restored(msg.state.trickNo), msg.state)
                        lastShown = step
                        _steps.tryEmit(step)
                        afterIdle()
                    }
                    is NetMsg.BhabhiSteps -> {
                        awaitingServer = false
                        val list = msg.steps.map { BStep(it.event, it.state) }
                        if (list.isNotEmpty()) state = list.last().state
                        enqueue(list)
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

    private fun onRemoteAction(seat: Int, a: BhabhiAction) {
        if (!submit(a)) roomHost?.sendToSeat(seat, NetMsg.ActionRejected("That move isn't allowed right now."))
    }

    private fun showEmote(seat: Int, text: String) {
        _ui.update { it.copy(emotes = it.emotes + (seat to (text to System.currentTimeMillis()))) }
    }

    /** True if [seat] is a connected remote player (host side). */
    private fun remoteConnected(seat: Int) = net == NetRole.HOST && seat != me && seats.getOrNull(seat)?.isHuman == true && _ui.value.netSeats[seat] == true

    /** Online turns always have a clock so one idle phone can't stall the table: the room's timer, else 30 s. */
    private fun netTurnMs(): Long = (state?.setup?.turnTimer ?: 0).takeIf { it > 0 }?.let { it * 1000L } ?: Net.REMOTE_TURN_MS

    private fun startRemoteTimer(seat: Int) {
        val v = version
        timerJob = viewModelScope.launch {
            delay(netTurnMs() + 1500)
            if (version != v) return@launch
            val s = state ?: return@launch
            if (s.turn != seat) return@launch
            val a = BhabhiBot.decide(s, seat, Difficulty.NORMAL, rnd) ?: return@launch
            submit(a)
        }
    }

    /** Someone else must act: guests just wait for the host; the host waits for remote players or runs a bot. */
    private fun othersTurn(seat: Int) {
        when {
            net == NetRole.GUEST -> Unit
            remoteConnected(seat) -> startRemoteTimer(seat)
            else -> scheduleBot(seat)
        }
    }

    // ------------------------------------------------------------------ step pump

    private fun enqueue(steps: List<BStep>) {
        if (net == NetRole.HOST) roomHost?.broadcastBhabhi(steps)
        queue.addAll(steps)
        _ui.update { it.copy(idle = false, legal = emptySet(), selected = null, hint = null, turnDeadline = 0L) }
        if (pumpJob?.isActive != true) pumpJob = viewModelScope.launch { pump() }
    }

    private fun duration(e: BhabhiEvent): Long = when (e) {
        is BhabhiEvent.Dealt -> 52L * 14 + 900
        is BhabhiEvent.Played -> if (e.thulla) 1000 else 430
        is BhabhiEvent.Discarded -> 950
        is BhabhiEvent.PickedUp -> 1250
        is BhabhiEvent.Out -> 900
        is BhabhiEvent.GameOver -> 1700
        is BhabhiEvent.Restored -> 300
    }

    private suspend fun pump() {
        while (queue.isNotEmpty()) {
            val step = queue.removeFirst()
            lastShown = step
            _ui.update { it.copy(state = step.state) }
            _steps.tryEmit(step)
            feedback(step)
            // a guest that fell behind (slow Wi-Fi) catches up quickly
            val catchUp = if (net == NetRole.GUEST && queue.size > 6) 0.35f else 1f
            delay((duration(step.event) * speedFactor * catchUp).toLong())
        }
        afterIdle()
    }

    private fun afterIdle() {
        val s = state ?: return
        version++
        timerJob?.cancel()
        persist()
        if (s.isOver) {
            _ui.update { it.copy(idle = true, legal = emptySet()) }
            finishGame(s)
            return
        }
        if (s.turn == me) {
            _ui.update { it.copy(idle = true, legal = BhabhiEngine.legalCards(s, me).map { c -> c.id }.toSet()) }
            c.sound.play(Sfx.TURN, 0.5f)
            startTimer()
        } else {
            _ui.update { it.copy(idle = true, legal = emptySet(), turnDeadline = 0L) }
            othersTurn(s.turn)
        }
    }

    private fun scheduleBot(seat: Int) {
        val s = state ?: return
        val v = version
        val diff = seats.getOrNull(seat)?.difficulty ?: Difficulty.NORMAL
        botJob = viewModelScope.launch {
            val think = ((if (s.trick.isEmpty()) 650L else 480L) + rnd.nextLong(0, 420)) * speedFactor
            val started = System.currentTimeMillis()
            val decision = withContext(Dispatchers.Default) { BhabhiBot.decide(s, seat, diff, Random(rnd.nextLong())) }
            delay((think.toLong() - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            if (version != v || decision == null) return@launch
            submit(decision)
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
            // guests: the host plays a safe move for us when the time runs out
            if (net == NetRole.GUEST) return@launch
            val a = BhabhiBot.decide(s, me, Difficulty.NORMAL, rnd) ?: return@launch
            c.sound.play(Sfx.ERROR, 0.6f)
            toast(Strings.get(R.string.bhabhi_toast_time_up))
            submit(a)
        }
    }

    private fun submit(a: BhabhiAction): Boolean {
        val s = state ?: return false
        if (BhabhiEngine.validate(s, a) != null) return false
        if (net == NetRole.GUEST) {
            if (awaitingServer) return false
            awaitingServer = true
            timerJob?.cancel()
            _ui.update { it.copy(idle = false, legal = emptySet(), selected = null, hint = null, turnDeadline = 0L) }
            roomClient?.send(NetMsg.BhabhiAct(a))
            return true
        }
        version++
        timerJob?.cancel()
        val steps = BhabhiEngine.apply(s, a)
        state = steps.last().state
        enqueue(steps)
        return true
    }

    // ------------------------------------------------------------------ input

    private fun canAct() = _ui.value.idle && state?.isOver == false && state?.turn == me

    fun tapCard(id: Int) {
        if (!canAct()) return
        val u = _ui.value
        if (id !in u.legal) {
            c.sound.play(Sfx.ERROR, 0.5f)
            c.haptics.light()
            val s = state ?: return
            val lead = s.leadSuit
            when {
                s.trick.isEmpty() && s.trickNo == 0 -> toast(Strings.get(R.string.bhabhi_toast_open))
                lead != null -> toast(Strings.get(R.string.bhabhi_toast_follow, bhabhiSuitName(lead)))
            }
            return
        }
        if (u.selected == id) {
            confirm(id)
        } else {
            c.sound.play(Sfx.SELECT, 0.6f)
            c.haptics.tick()
            _ui.update { it.copy(selected = id) }
        }
    }

    fun dragPlay(id: Int): Boolean {
        if (!canAct() || id !in _ui.value.legal) return false
        return confirm(id)
    }

    private fun confirm(id: Int): Boolean {
        val ok = submit(BhabhiAction.Play(me, id))
        if (ok) c.haptics.light()
        return ok
    }

    fun deselect() = _ui.update { it.copy(selected = null) }

    fun hint() {
        val s = state ?: return
        if (!canAct()) return
        val card: BCard = BhabhiBot.hint(s, me) ?: return
        c.sound.play(Sfx.PRISM, 0.6f)
        _ui.update { it.copy(hint = card.id, selected = card.id) }
    }

    /** I'm safe: let the bots finish the game quickly (not in multiplayer: friends are still playing). */
    fun fastForward() {
        if (isNet) return
        val s = state ?: return
        if (s.hands[me].isNotEmpty() || s.isOver) return
        _ui.update { it.copy(fastForward = true) }
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
        val others = s.active.filter { it != me }
        if (others.isNotEmpty() && rnd.nextDouble() < 0.6) viewModelScope.launch {
            delay(700)
            val who = others.random(rnd)
            val reply = listOf("😏", "😂", "🙈", "😤", "🤞").random(rnd)
            _ui.update { it.copy(emotes = it.emotes + (who to (reply to System.currentTimeMillis()))) }
        }
    }

    private fun toast(msg: String) = _ui.update { it.copy(banner = msg, bannerAt = System.currentTimeMillis()) }

    // ------------------------------------------------------------------ feedback

    /** A bot reacts with an emoji. Never for real players: their emotes are their own. */
    private fun bump(seat: Int, emoji: String, chance: Double) {
        if (seat == me || seats.getOrNull(seat)?.isHuman == true || rnd.nextDouble() > chance) return
        _ui.update { it.copy(emotes = it.emotes + (seat to (emoji to System.currentTimeMillis()))) }
    }

    private fun feedback(step: BStep) {
        when (val e = step.event) {
            is BhabhiEvent.Dealt -> viewModelScope.launch {
                c.sound.play(Sfx.SHUFFLE)
                repeat(16) {
                    c.sound.play(Sfx.CARD_SLIDE, 0.35f)
                    delay(60)
                }
            }
            is BhabhiEvent.Played -> {
                c.sound.snap(0.9f)
                if (e.thulla) viewModelScope.launch {
                    delay((300 * speedFactor).toLong())
                    c.sound.play(Sfx.BLOCK)
                    c.haptics.medium()
                    bump(e.seat, listOf("😈", "😏", "💥").random(rnd), 0.6)
                }
            }
            is BhabhiEvent.Discarded -> viewModelScope.launch {
                delay((380 * speedFactor).toLong())
                c.sound.play(Sfx.WHOOSH, 0.5f)
            }
            is BhabhiEvent.PickedUp -> viewModelScope.launch {
                delay((520 * speedFactor).toLong())
                c.sound.play(Sfx.PLUS, 0.7f)
                if (e.taker == me) c.haptics.medium() else bump(e.taker, listOf("😩", "😭", "😤", "🤦").random(rnd), 0.7)
            }
            is BhabhiEvent.Out -> {
                if (e.seat == me) {
                    c.sound.play(Sfx.WIN, 0.8f)
                    c.haptics.medium()
                } else {
                    c.sound.play(Sfx.COIN, 0.6f)
                    bump(e.seat, listOf("😎", "🥳", "✌️").random(rnd), 0.8)
                }
            }
            is BhabhiEvent.GameOver -> {
                if (e.loser == me) c.sound.play(Sfx.LOSE, 0.8f) else c.sound.play(Sfx.PRISM, 0.7f)
                bump(e.loser, "😭", 1.0)
            }
            is BhabhiEvent.Restored -> Unit
        }
    }

    // ------------------------------------------------------------------ results

    private fun finishGame(s: BhabhiState) {
        if (_ui.value.result != null) return
        val bhabhi = s.loser == me
        val place = s.placeOf(me) ?: s.players
        viewModelScope.launch {
            // a multiplayer game never touches this phone's saved local game
            if (!isNet) SaveFiles.delete(saveFile)
            var result: BhabhiResult? = null
            val newProfile = c.profile.update { p ->
                val st = p.bhabhi.stats
                val streak = if (bhabhi) 0 else st.streak + 1
                val newStats = st.copy(
                    games = st.games + 1,
                    escapes = st.escapes + if (bhabhi) 0 else 1,
                    firsts = st.firsts + if (place == 1) 1 else 0,
                    bhabhis = st.bhabhis + if (bhabhi) 1 else 0,
                    thullas = st.thullas + s.thullas[me],
                    pickups = st.pickups + s.pickups[me],
                    streak = streak,
                    bestStreak = maxOf(st.bestStreak, streak),
                )
                val diff = seats.filter { !it.isHuman }.maxOfOrNull { it.difficulty } ?: Difficulty.NORMAL
                var coins = if (bhabhi) 20 else ((40 + (s.players - place) * 30) * DifficultyInfo.multiplier(diff)).toInt()
                val xp = if (bhabhi) 25 else 50 + (s.players - place) * 20
                val earned = buildList {
                    fun award(id: String, cond: Boolean) { if (cond && id !in p.achievements) Achievements.byId(id)?.let { add(it) } }
                    award("bhabhi_escape", !bhabhi)
                    award("bhabhi_first", place == 1)
                    award("bhabhi_thulla", s.thullas[me] >= 3)
                    award("bhabhi_clean", !bhabhi && s.pickups[me] == 0)
                    award("bhabhi_big", !bhabhi && s.players >= 7)
                    award("bhabhi_expert", place == 1 && diff == Difficulty.GENIUS)
                }
                coins += earned.sumOf { it.reward }
                result = BhabhiResult(place, bhabhi, coins, xp, earned)
                p.copy(
                    coins = p.coins + coins,
                    xp = p.xp + xp,
                    achievements = p.achievements + earned.map { it.id },
                    bhabhi = p.bhabhi.copy(stats = newStats),
                )
            }
            _ui.update { it.copy(profile = newProfile) }
            delay(1000)
            _ui.update { it.copy(result = result) }
            if (result?.achievements?.isNotEmpty() == true) c.sound.play(Sfx.CHEST)
        }
    }

    private fun persist() {
        if (isNet) return // multiplayer games are never saved for "Continue"
        val s = state ?: return
        if (s.isOver) return
        val save = BhabhiSave(s, seats)
        SaveFiles.write(saveFile) { AppJson.encodeToString(BhabhiSave.serializer(), save) }
    }

    fun abandon() {
        // a quit game stays saved so it can be continued later
        persist()
    }

    override fun onCleared() {
        persist()
        super.onCleared()
    }

    companion object {
        const val SAVE_FILE = "bhabhi_save.json"
        fun hasSave(app: Application): Boolean = File(app.filesDir, SAVE_FILE).exists()
    }
}
