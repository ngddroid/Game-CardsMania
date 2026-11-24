package com.ngdstudio.mindimania.ui.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ngdstudio.mindimania.CardsManiaApp
import com.ngdstudio.mindimania.GameLaunch
import com.ngdstudio.mindimania.R
import com.ngdstudio.mindimania.audio.Sfx
import com.ngdstudio.mindimania.audio.Track
import com.ngdstudio.mindimania.data.GameFactory
import com.ngdstudio.mindimania.data.Profile
import com.ngdstudio.mindimania.data.Progression
import com.ngdstudio.mindimania.data.RewardResult
import com.ngdstudio.mindimania.data.SavedGame
import com.ngdstudio.mindimania.data.Settings
import com.ngdstudio.mindimania.data.SortMode
import com.ngdstudio.mindimania.engine.Action
import com.ngdstudio.mindimania.engine.DrawReason
import com.ngdstudio.mindimania.engine.GameEngine
import com.ngdstudio.mindimania.engine.GameEvent
import com.ngdstudio.mindimania.engine.Step
import com.ngdstudio.mindimania.engine.ai.BotAI
import com.ngdstudio.mindimania.engine.model.Card
import com.ngdstudio.mindimania.engine.model.Difficulty
import com.ngdstudio.mindimania.engine.model.GameState
import com.ngdstudio.mindimania.engine.model.Kind
import com.ngdstudio.mindimania.engine.model.Phase
import com.ngdstudio.mindimania.engine.model.Side
import com.ngdstudio.mindimania.engine.model.Suit
import com.ngdstudio.mindimania.i18n.Strings
import com.ngdstudio.mindimania.net.ClientStatus
import com.ngdstudio.mindimania.net.Net
import com.ngdstudio.mindimania.net.NetMsg
import com.ngdstudio.mindimania.net.NetRole
import com.ngdstudio.mindimania.net.RoomClient
import com.ngdstudio.mindimania.net.RoomHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** One rendered moment of the game: the state right after [event]. */
data class Frame(val state: GameState, val event: GameEvent?, val seq: Long)

data class Emote(val text: String, val at: Long)

sealed interface ResultInfo {
    data class Round(val winnerId: Int, val points: Int, val scores: List<Pair<Int, Int>>, val target: Int, val hands: List<Pair<Int, List<Card>>>) : ResultInfo
    data class Match(
        val winnerId: Int,
        val humanWon: Boolean,
        val place: Int,
        val reward: RewardResult?,
        val scores: List<Pair<Int, Int>>,
        val daily: String?,
        val lastStanding: Boolean,
    ) : ResultInfo
}

data class GameUi(
    val ready: Boolean = false,
    val idle: Boolean = false,
    val myTurn: Boolean = false,
    val selected: List<Int> = emptyList(),
    val playable: Set<Int> = emptySet(),
    val jumpIn: Set<Int> = emptySet(),
    val hint: Int? = null,
    val maniaArmed: Boolean = false,
    val showMania: Boolean = false,
    val catchTargets: Set<Int> = emptySet(),
    val emotes: Map<Int, Emote> = emptyMap(),
    val banner: String? = null,
    val bannerAt: Long = 0L,
    val peek: List<Card>? = null,
    val paused: Boolean = false,
    val result: ResultInfo? = null,
    val turnDeadline: Long = 0L,
    val turnTotal: Long = 0L,
    val sortMode: SortMode = SortMode.COLOR,
    val settings: Settings = Settings(),
    val profile: Profile = Profile(),
    val daily: String? = null,
    /** LOCAL, HOST or GUEST. */
    val net: NetRole = NetRole.LOCAL,
    /** Remote human seats → connected (multiplayer only). */
    val netSeats: Map<Int, Boolean> = emptyMap(),
    /** Set when a multiplayer game ends unexpectedly (host left, connection lost). */
    val ended: String? = null,
)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as CardsManiaApp).container
    var human = 0
        private set
    private var net = NetRole.LOCAL
    private var roomHost: RoomHost? = null
    private var roomClient: RoomClient? = null
    private var awaitingServer = false
    private var netSpeed: Int? = null

    private val _frame = MutableStateFlow<Frame?>(null)
    val frame: StateFlow<Frame?> = _frame
    private val _ui = MutableStateFlow(GameUi())
    val ui: StateFlow<GameUi> = _ui
    private val _fx = MutableSharedFlow<Frame>(extraBufferCapacity = 128)
    /** Every frame is also emitted here so the canvas can spawn one-shot effects. */
    val fx: SharedFlow<Frame> = _fx

    private var engine: GameEngine? = null
    private val queue = ArrayDeque<Step>()
    private var pumpJob: Job? = null
    private var botJob: Job? = null
    private var timerJob: Job? = null
    private val catchJobs = mutableListOf<Job>()
    private var version = 0L
    private var seq = 0L
    private var started = false
    private var daily: String? = null
    private var tracker = MatchTracker(human)
    private val pausedFlow = MutableStateFlow(false)
    private val rnd = Random(System.nanoTime())
    private val settings: Settings get() = _ui.value.settings
    private val speed: Int get() = netSpeed ?: settings.speed
    private val isNet: Boolean get() = net != NetRole.LOCAL

    val state: GameState? get() = engine?.state

    fun start(launch: GameLaunch?) {
        if (started) return
        started = true
        com.ngdstudio.mindimania.analytics.Analytics.event(
            "game_start", "game" to (launch?.config?.mode?.name?.lowercase() ?: "mania"),
            "net" to (launch?.net ?: NetRole.LOCAL).name.lowercase(), "resume" to (launch?.resume == true), "daily" to (launch?.daily != null),
        )
        viewModelScope.launch {
            val s = c.settings.current()
            val p = c.profile.current()
            _ui.update { it.copy(settings = s, profile = p, sortMode = s.sortMode) }
            launch {
                c.settings.settings.collect { ns -> _ui.update { it.copy(settings = ns) } }
            }
            when (launch?.net) {
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
            val saved = if (launch == null || launch.resume) c.savedGames.load() else null
            if (saved != null) {
                val eng = GameEngine(saved.state)
                engine = eng
                daily = saved.daily
                tracker.humanTurns = saved.humanTurns
                _ui.update { it.copy(ready = true, daily = daily) }
                c.music.play(if (eng.state.side == Side.MOON) Track.NIGHT else Track.DAY)
                val f = Frame(eng.state.copyDeep(), null, ++seq)
                _frame.value = f
                afterIdle()
            } else {
                val config = launch?.config ?: GameFactory.fromSetup(p.setup, p)
                daily = launch?.daily
                val (eng, steps) = GameEngine.newGame(config)
                engine = eng
                c.music.play(Track.DAY)
                _ui.update { it.copy(ready = true, daily = daily) }
                enqueue(steps)
            }
        }
    }

    // ------------------------------------------------------------------ multiplayer

    private fun startHost(launch: GameLaunch) {
        val host = c.multiplayer.host
        val config = launch.config
        if (host == null || config == null) {
            _ui.update { it.copy(ready = true, ended = Strings.get(R.string.mania_end_room_closed)) }
            return
        }
        net = NetRole.HOST
        roomHost = host
        human = 0
        tracker = MatchTracker(human)
        netSpeed = host.room.value.speed
        val (eng, steps) = GameEngine.newGame(config)
        engine = eng
        host.stateProvider = { engine?.state?.copyDeep() }
        c.music.play(Track.DAY)
        _ui.update { it.copy(ready = true, net = NetRole.HOST, netSeats = host.seatStatus.value) }
        viewModelScope.launch { for ((seat, a) in host.actions) onRemoteAction(seat, a) }
        viewModelScope.launch {
            for ((seat, text) in host.emotes) {
                showEmote(seat, text)
                host.broadcast(NetMsg.Emote(seat, text), exceptSeat = seat)
            }
        }
        viewModelScope.launch {
            host.seatStatus.collect { seats ->
                val before = _ui.value.netSeats
                _ui.update { it.copy(netSeats = seats) }
                // A player dropped while it's their turn: let a bot take over now.
                val cur = engine?.state ?: return@collect
                val actor = cur.actingPlayer
                if (_ui.value.idle && before[actor] == true && seats[actor] == false && !cur.isOver) afterIdle()
            }
        }
        enqueue(steps)
    }

    private fun startGuest() {
        val client = c.multiplayer.client
        if (client == null) {
            _ui.update { it.copy(ready = true, ended = Strings.get(R.string.mania_end_not_in_room)) }
            return
        }
        net = NetRole.GUEST
        roomClient = client
        _ui.update { it.copy(net = NetRole.GUEST) }
        c.music.play(Track.DAY)
        val channel = client.game
        viewModelScope.launch {
            client.room.collect { room ->
                if (room == null) return@collect
                val seats = HashMap<Int, Boolean>()
                seats[0] = true
                room.guests.filter { it.seat >= 0 && it.seat != human }.forEach { seats[it.seat] = it.connected }
                _ui.update { it.copy(netSeats = seats) }
            }
        }
        viewModelScope.launch {
            for (msg in channel) {
                when (msg) {
                    is NetMsg.Start -> {
                        human = msg.yourId
                        tracker = MatchTracker(human)
                        netSpeed = msg.speed
                        _ui.update { it.copy(ready = true) }
                    }
                    is NetMsg.Sync -> {
                        human = msg.yourId
                        tracker = MatchTracker(human)
                        netSpeed = msg.speed
                        queue.clear()
                        engine = GameEngine(msg.state.copyDeep(), recordSnapshots = false)
                        val f = Frame(msg.state, null, ++seq)
                        _frame.value = f
                        _fx.tryEmit(f)
                        _ui.update { it.copy(ready = true) }
                        afterIdle()
                    }
                    is NetMsg.Steps -> {
                        awaitingServer = false
                        enqueue(msg.steps.map { Step(it.event, it.state) })
                    }
                    is NetMsg.Emote -> if (msg.playerId != human) showEmote(msg.playerId, msg.text)
                    is NetMsg.ActionRejected -> {
                        awaitingServer = false
                        c.sound.play(Sfx.ERROR, 0.6f)
                        if (queue.isEmpty()) refreshUi()
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

    private fun onRemoteAction(seat: Int, a: Action) {
        val e = engine ?: return
        if (a is Action.CallMania && e.state.maniaVulnerable != seat) {
            // early "MANIA!" press: only sets the flag
            if (e.validate(a) == null) e.apply(a)
            return
        }
        if (a is Action.NextRound) return
        if (!submit(a)) roomHost?.sendToSeat(seat, NetMsg.ActionRejected("That move isn't allowed right now."))
    }

    private fun showEmote(playerId: Int, text: String) {
        _ui.update { it.copy(emotes = it.emotes + (playerId to Emote(text, System.currentTimeMillis()))) }
    }

    /** True if seat [id] is a remote player who is currently connected (host side). */
    private fun remoteConnected(id: Int): Boolean = net == NetRole.HOST && _ui.value.netSeats[id] == true

    // ------------------------------------------------------------------ pump

    private fun enqueue(steps: List<Step>) {
        if (net == NetRole.HOST) roomHost?.broadcastSteps(steps)
        queue.addAll(steps)
        _ui.update { it.copy(idle = false, myTurn = false, playable = emptySet(), jumpIn = emptySet(), hint = null) }
        if (pumpJob?.isActive != true) pumpJob = viewModelScope.launch { pump() }
    }

    private suspend fun pump() {
        while (queue.isNotEmpty()) {
            pausedFlow.first { !it }
            val step = queue.removeFirst()
            val snap = step.snapshot ?: continue
            val f = Frame(snap, step.event, ++seq)
            _frame.value = f
            _fx.tryEmit(f)
            tracker.onEvent(step.event, snap)
            onEventFeedback(step.event, snap)
            val catchUp = if (net == NetRole.GUEST && queue.size > 4) 0.35f else 1f
            delay((Timing.of(step.event, speed) * catchUp).toLong())
        }
        afterIdle()
    }

    private fun speedMs(ms: Long) = (ms * Timing.speedFactor(speed)).toLong()

    private fun afterIdle() {
        if (net == NetRole.GUEST) {
            guestIdle()
            return
        }
        val e = engine ?: return
        val s = e.state
        version++
        catchJobs.forEach { it.cancel() }
        catchJobs.clear()
        timerJob?.cancel()
        refreshUi()
        persist()
        if (s.phase is Phase.RoundOver || s.phase is Phase.MatchOver) turnBuzz(false)
        when (val ph = s.phase) {
            is Phase.RoundOver -> {
                showRoundResult(s, ph.winnerId, ph.points)
                return
            }
            is Phase.MatchOver -> {
                finishMatch(s, ph.winnerId)
                return
            }
            else -> Unit
        }
        scheduleCatches(s)
        val actor = s.actingPlayer
        turnBuzz(actor == human)
        when {
            actor == human -> startTurnTimer()
            s.players[actor].isHuman && remoteConnected(actor) -> startRemoteTimer(actor)
            // bots, and remote players who dropped out (a bot plays for them)
            else -> scheduleBot(actor)
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

    private fun guestIdle() {
        val f = _frame.value ?: return
        engine = GameEngine(f.state.copyDeep(), recordSnapshots = false)
        val s = f.state
        version++
        timerJob?.cancel()
        refreshUi()
        turnBuzz(s.phase !is Phase.RoundOver && s.phase !is Phase.MatchOver && s.actingPlayer == human)
        when (val ph = s.phase) {
            is Phase.RoundOver -> showRoundResult(s, ph.winnerId, ph.points)
            is Phase.MatchOver -> finishMatch(s, ph.winnerId)
            else -> if (s.actingPlayer == human) {
                // countdown for display; the host plays a safe move if it runs out
                _ui.update { it.copy(turnDeadline = System.currentTimeMillis() + Net.REMOTE_TURN_MS, turnTotal = Net.REMOTE_TURN_MS) }
            } else _ui.update { it.copy(turnDeadline = 0L, turnTotal = 0L) }
        }
    }

    private fun startRemoteTimer(seat: Int) {
        val v = version
        timerJob = viewModelScope.launch {
            delay(Net.REMOTE_TURN_MS + 1500)
            if (version != v) return@launch
            val cur = engine?.state ?: return@launch
            if (cur.actingPlayer != seat) return@launch
            val a = BotAI.decide(cur.copyDeep(), seat, Difficulty.NORMAL, rnd) ?: Action.Draw(seat)
            submit(a)
        }
    }

    private fun refreshUi() {
        val e = engine ?: return
        val s = e.state
        val me = s.players[human]
        val myTurn = !s.isOver && s.actingPlayer == human && !me.eliminated
        val playable = if (myTurn) e.playableCards(human).map { it.id }.toSet() else emptySet()
        val jump = if (!myTurn) e.jumpInCards(human).map { it.id }.toSet() else emptySet()
        val showMania = !me.eliminated && ((myTurn && s.phase == Phase.Play && me.hand.size == 2 && playable.isNotEmpty()) ||
            (myTurn && s.phase is Phase.DrawnCard && me.hand.size == 2) || s.maniaVulnerable == human)
        val catchTargets = if (s.maniaVulnerable >= 0 && s.maniaVulnerable != human && !me.eliminated) setOf(s.maniaVulnerable) else emptySet()
        _ui.update { u ->
            u.copy(
                idle = true,
                myTurn = myTurn,
                playable = playable,
                jumpIn = jump,
                selected = u.selected.filter { id -> id in playable || id in jump },
                showMania = showMania,
                maniaArmed = if (me.hand.size > 2 || !showMania) false else u.maniaArmed || me.maniaCalled,
                catchTargets = catchTargets,
            )
        }
    }

    private fun persist() {
        if (isNet) return
        val e = engine ?: return
        val s = e.state
        if (s.phase is Phase.MatchOver) return
        val snap = s.copyDeep()
        val turns = tracker.humanTurns
        c.savedGames.saveLater(SavedGame(snap, daily, turns))
    }

    // ------------------------------------------------------------------ bots

    private fun thinkTime(s: GameState): Long {
        val base = when (s.phase) {
            is Phase.ChooseColor, is Phase.ChooseSwap, is Phase.Challenge -> 650L
            is Phase.DrawnCard -> 450L
            else -> 700L + rnd.nextLong(0, 550)
        }
        return speedMs(base)
    }

    private fun scheduleBot(actor: Int) {
        val e = engine ?: return
        val v = version
        val snapshot = e.state.copyDeep()
        val diff = snapshot.players[actor].setup.difficulty
        val think = thinkTime(snapshot)
        botJob = viewModelScope.launch {
            val decision = async(Dispatchers.Default) { BotAI.decide(snapshot, actor, diff, Random(rnd.nextLong())) }
            delay(think / 2)
            if (version != v) return@launch
            // Other bots may jump in out of turn.
            if (snapshot.config.jumpIn && snapshot.phase == Phase.Play && snapshot.pendingDraw == 0) {
                val cur = engine?.state ?: return@launch
                for (p in cur.players) {
                    if (p.isHuman || p.id == actor || p.eliminated) continue
                    val a = BotAI.jumpIn(cur, p.id, p.setup.difficulty, rnd) ?: continue
                    if (rnd.nextDouble() < 0.6) {
                        submit(a)
                        return@launch
                    }
                }
            }
            delay(think - think / 2)
            val a = decision.await()
            if (version != v || a == null) return@launch
            if (!submit(a)) {
                // Fallback: something changed; let the bot draw or pick the simplest legal move.
                val cur = engine?.state ?: return@launch
                val fb = BotAI.decide(cur.copyDeep(), actor, Difficulty.EASY, rnd) ?: return@launch
                submit(fb)
            }
        }
    }

    private fun scheduleCatches(s: GameState) {
        val vul = s.maniaVulnerable
        if (vul < 0) return
        for (p in s.players) {
            if (p.isHuman || p.id == vul || p.eliminated) continue
            val (chance, ms) = BotAI.catchProfile(p.setup.difficulty)
            if (rnd.nextDouble() >= chance) continue
            val wait = speedMs(ms) + rnd.nextLong(0, 400)
            catchJobs += viewModelScope.launch {
                delay(wait)
                val cur = engine?.state ?: return@launch
                if (cur.maniaVulnerable == vul) submit(Action.Catch(p.id, vul))
            }
        }
    }

    private fun startTurnTimer() {
        val secs = if (isNet) (Net.REMOTE_TURN_MS / 1000).toInt() else settings.turnTimer
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
            val cur = engine?.state ?: return@launch
            if (cur.actingPlayer != human) return@launch
            c.sound.play(Sfx.ERROR)
            val a = when (cur.phase) {
                Phase.Play -> Action.Draw(human)
                else -> BotAI.decide(cur.copyDeep(), human, Difficulty.NORMAL, rnd)
            } ?: return@launch
            submit(a)
        }
    }

    // ------------------------------------------------------------------ submit

    private fun submit(a: Action): Boolean {
        val e = engine ?: return false
        if (e.validate(a) != null) return false
        if (net == NetRole.GUEST) {
            if (awaitingServer) return false
            awaitingServer = true
            timerJob?.cancel()
            _ui.update { it.copy(selected = emptyList(), hint = null, turnDeadline = 0L, idle = false, playable = emptySet(), jumpIn = emptySet()) }
            roomClient?.send(NetMsg.Act(a))
            return true
        }
        version++
        timerJob?.cancel()
        _ui.update { it.copy(selected = emptyList(), hint = null, turnDeadline = 0L) }
        val steps = e.apply(a)
        enqueue(steps)
        return true
    }

    // ------------------------------------------------------------------ human input

    private fun canAct(): Boolean = _ui.value.idle && !_ui.value.paused && engine?.state?.isOver == false

    fun tapCard(id: Int) {
        if (!canAct()) return
        val u = _ui.value
        val s = engine!!.state
        val me = s.players[human]
        val card = me.hand.firstOrNull { it.id == id } ?: return
        val isPlayable = id in u.playable || id in u.jumpIn
        if (!isPlayable) {
            // combo extension: another number with the same value
            if (u.selected.isNotEmpty() && s.config.comboDrop && comboCompatible(u.selected.first(), card)) {
                toggleSelect(id)
                return
            }
            c.sound.play(Sfx.ERROR, 0.6f)
            c.haptics.light()
            return
        }
        when {
            u.selected.firstOrNull() == id -> playSelected()
            id in u.selected -> playSelected()
            u.selected.isNotEmpty() && s.config.comboDrop && comboCompatible(u.selected.first(), card) -> toggleSelect(id)
            else -> {
                c.sound.play(Sfx.SELECT, 0.7f)
                c.haptics.tick()
                _ui.update { it.copy(selected = listOf(id)) }
            }
        }
    }

    private fun comboCompatible(firstId: Int, card: Card): Boolean {
        val s = engine?.state ?: return false
        val first = s.players[human].hand.firstOrNull { it.id == firstId } ?: return false
        val a = first.face(s.side)
        val b = card.face(s.side)
        return a.kind == Kind.NUMBER && b.kind == Kind.NUMBER && a.value == b.value && first.id != card.id && first.id in _ui.value.playable
    }

    private fun toggleSelect(id: Int) {
        c.sound.play(Sfx.SELECT, 0.7f)
        _ui.update { u -> u.copy(selected = if (id in u.selected) u.selected - id else u.selected + id) }
    }

    fun deselect() = _ui.update { it.copy(selected = emptyList()) }

    /** Play the card the user dragged (plus combo extras if selected). Returns true if it was played. */
    fun dragPlay(id: Int): Boolean {
        if (!canAct()) return false
        val u = _ui.value
        if (id !in u.playable && id !in u.jumpIn) {
            c.sound.play(Sfx.ERROR, 0.6f)
            return false
        }
        val extras = u.selected.filter { s -> s != id && (cardOf(s)?.let { comboCompatible(id, it) } ?: false) }
        _ui.update { it.copy(selected = listOf(id) + extras) }
        return playSelected()
    }

    private fun cardOf(id: Int): Card? = engine?.state?.players?.get(human)?.hand?.firstOrNull { it.id == id }

    private fun playSelected(): Boolean {
        val u = _ui.value
        val sel = u.selected
        if (sel.isEmpty()) return false
        val s = engine?.state ?: return false
        val me = s.players[human]
        val remaining = me.hand.size - sel.size
        val call = u.maniaArmed || me.maniaCalled
        val ok = submit(Action.Play(human, sel.first(), sel.drop(1), callMania = call && remaining == 1))
        if (!ok) {
            c.sound.play(Sfx.ERROR, 0.6f)
            _ui.update { it.copy(selected = emptyList()) }
        }
        return ok
    }

    fun drawCard() {
        if (!canAct()) return
        val s = engine?.state ?: return
        when (val ph = s.phase) {
            is Phase.Play -> if (s.current == human) submit(Action.Draw(human))
            is Phase.DrawnCard -> if (ph.playerId == human && !s.config.forcedPlay) submit(Action.Keep(human))
            else -> Unit
        }
    }

    fun keepDrawn() {
        if (!canAct()) return
        submit(Action.Keep(human))
    }

    fun chooseColor(suit: Suit) {
        if (!canAct()) return
        c.sound.play(Sfx.SELECT)
        submit(Action.ChooseColor(human, suit))
    }

    fun chooseSwap(target: Int) {
        if (!canAct()) return
        submit(Action.ChooseSwap(human, target))
    }

    fun challenge(doIt: Boolean) {
        if (!canAct()) return
        submit(Action.Challenge(human, doIt))
    }

    fun callMania() {
        val e = engine ?: return
        val s = e.state
        val me = s.players[human]
        if (s.maniaVulnerable == human) {
            submit(Action.CallMania(human))
            return
        }
        if (me.hand.size == 2 && e.validate(Action.CallMania(human)) == null) {
            // Sets the flag only (no events). Guests just arm it locally: their next Play carries callMania = true.
            if (net != NetRole.GUEST) e.apply(Action.CallMania(human))
            c.sound.play(Sfx.MANIA)
            c.haptics.medium()
            _ui.update { it.copy(maniaArmed = true, banner = "MANIA!", bannerAt = System.currentTimeMillis()) }
        }
    }

    fun catchPlayer(target: Int) {
        val e = engine ?: return
        if (e.state.maniaVulnerable != target) return
        submit(Action.Catch(human, target))
    }

    fun armShield() {
        if (_ui.value.idle) submit(Action.ArmShield(human))
    }

    fun peek() {
        val e = engine ?: return
        if (!_ui.value.idle) return
        if (net == NetRole.GUEST) {
            submit(Action.Peek(human))
            return
        }
        val steps = runCatching { e.apply(Action.Peek(human)) }.getOrNull() ?: return
        val ev = steps.map { it.event }.filterIsInstance<GameEvent.PeekRevealed>().firstOrNull() ?: return
        c.sound.play(Sfx.PRISM)
        _ui.update { it.copy(peek = ev.cards) }
        viewModelScope.launch {
            delay(3200)
            _ui.update { it.copy(peek = null) }
        }
        refreshUi()
    }

    fun hint() {
        val s = engine?.state ?: return
        if (!_ui.value.myTurn) return
        val card = BotAI.hint(s, human) ?: run {
            _ui.update { it.copy(banner = Strings.get(R.string.mania_banner_draw), bannerAt = System.currentTimeMillis()) }
            return
        }
        c.sound.play(Sfx.PRISM, 0.6f)
        _ui.update { it.copy(hint = card.id, selected = listOf(card.id)) }
    }

    fun cycleSort() {
        val next = when (_ui.value.sortMode) {
            SortMode.COLOR -> SortMode.VALUE
            SortMode.VALUE -> SortMode.NONE
            SortMode.NONE -> SortMode.COLOR
        }
        _ui.update { it.copy(sortMode = next) }
        c.sound.play(Sfx.SHUFFLE, 0.5f)
        viewModelScope.launch { c.settings.update { it.copy(sortMode = next) } }
    }

    fun emote(text: String) {
        _ui.update { it.copy(emotes = it.emotes + (human to Emote(text, System.currentTimeMillis()))) }
        c.sound.play(Sfx.SELECT, 0.6f)
        when (net) {
            NetRole.HOST -> roomHost?.broadcast(NetMsg.Emote(human, text))
            NetRole.GUEST -> roomClient?.send(NetMsg.Emote(human, text))
            else -> Unit
        }
        // bots sometimes answer
        val s = engine?.state ?: return
        val bot = s.players.filter { !it.isHuman && !it.eliminated }.randomOrNull(rnd) ?: return
        if (rnd.nextDouble() < 0.6) {
            viewModelScope.launch {
                delay(700)
                val reply = listOf("😂", "😎", "👍", "😤", "🤔", "😏").random(rnd)
                _ui.update { it.copy(emotes = it.emotes + (bot.id to Emote(reply, System.currentTimeMillis()))) }
            }
        }
    }

    fun setPaused(p: Boolean) {
        if (isNet) return // a multiplayer game keeps running while the menu is open
        pausedFlow.value = p
        _ui.update { it.copy(paused = p) }
        if (p) {
            timerJob?.cancel()
            botJob?.cancel()
            version++
        } else if (_ui.value.idle) {
            afterIdle()
        }
    }

    fun nextRound() {
        if (net == NetRole.GUEST) return
        _ui.update { it.copy(result = null) }
        submit(Action.NextRound(human))
    }

    fun onBackground() {
        persist()
    }

    /** Leave the game. A finished match leaves no saved game. */
    fun abandonIfOver() {
        if (isNet) return
        val s = engine?.state ?: return
        if (s.phase is Phase.MatchOver) c.scope.launch { c.savedGames.clear() }
    }

    // ------------------------------------------------------------------ results

    private fun showRoundResult(s: GameState, winner: Int, points: Int) {
        val scores = s.players.map { it.id to it.score }
        val hands = s.players.map { it.id to it.hand.toList() }
        _ui.update { it.copy(result = ResultInfo.Round(winner, points, scores, s.config.targetScore, hands)) }
    }

    private fun finishMatch(s: GameState, winner: Int) {
        if (_ui.value.result is ResultInfo.Match) return
        val won = winner == human
        com.ngdstudio.mindimania.analytics.Analytics.event("game_end", "game" to s.mode.name.lowercase(), "won" to won, "players" to s.players.size, "net" to net.name.lowercase(), "daily" to (daily != null))
        val summary = tracker.summary(s, won, daily)
        val scores = s.players.map { it.id to it.score }.sortedByDescending { it.second }
        val lastStanding = tracker.lastStanding
        viewModelScope.launch {
            if (!isNet) c.savedGames.clear()
            var reward: RewardResult? = null
            val newProfile = c.profile.update { p ->
                val (np, r) = Progression.apply(p, summary)
                reward = r
                np
            }
            _ui.update {
                it.copy(
                    profile = newProfile,
                    result = ResultInfo.Match(winner, won, summary.place, reward, scores, daily, lastStanding)
                )
            }
            if (reward?.newAchievements?.isNotEmpty() == true) {
                delay(600)
                c.sound.play(Sfx.CHEST)
            }
        }
    }

    // ------------------------------------------------------------------ feedback (sound, haptics, emotes)

    private fun bump(id: Int, emoji: String, chance: Double = 0.55) {
        if (id == human || rnd.nextDouble() > chance) return
        _ui.update { it.copy(emotes = it.emotes + (id to Emote(emoji, System.currentTimeMillis()))) }
    }

    private fun onEventFeedback(e: GameEvent, s: GameState) {
        val snd = c.sound
        val hp = c.haptics
        when (e) {
            is GameEvent.RoundStarted -> {
                if (_ui.value.result is ResultInfo.Round) _ui.update { it.copy(result = null) }
                snd.play(Sfx.SHUFFLE)
                _ui.update { it.copy(banner = if (s.config.targetScore > 0) Strings.get(R.string.mania_banner_round, e.round) else Strings.get(R.string.mania_banner_lets_play), bannerAt = System.currentTimeMillis()) }
            }
            is GameEvent.Dealt -> viewModelScope.launch {
                val n = minOf(e.order.size, 30)
                val stagger = speedMs(Timing.DEAL_STAGGER) * e.order.size / n
                repeat(n) {
                    snd.play(Sfx.CARD_SLIDE, 0.45f)
                    delay(stagger)
                }
            }
            is GameEvent.StarterFlipped -> snd.snap()
            is GameEvent.CardPlayed -> {
                snd.snap()
                val f = e.card.face(s.side)
                when (f.kind) {
                    Kind.BLOCK, Kind.BLOCK_ALL -> snd.play(Sfx.BLOCK, 0.8f)
                    Kind.REVERSE -> snd.play(Sfx.REVERSE, 0.8f)
                    Kind.DRAW, Kind.PRISM_DRAW, Kind.PRISM_REBOUND -> snd.play(Sfx.PLUS, 0.9f, rate = (1.1f - f.drawAmount * 0.03f).coerceAtLeast(0.75f))
                    Kind.PRISM, Kind.PRISM_HUNT -> snd.play(Sfx.PRISM, 0.8f)
                    Kind.PURGE -> snd.play(Sfx.PURGE)
                    Kind.COLOR_STORM -> snd.play(Sfx.STORM)
                    else -> Unit
                }
                if (e.playerId == human) hp.light()
                if (e.jumpIn) _ui.update { it.copy(banner = Strings.get(R.string.mania_banner_jump_in), bannerAt = System.currentTimeMillis()) }
                if (f.isDraw && e.playerId != human) bump(e.playerId, listOf("😈", "😂", "😎").random(rnd), 0.35)
            }
            is GameEvent.CardsDrawn -> {
                viewModelScope.launch {
                    val n = minOf(e.cards.size, 8)
                    repeat(n) {
                        snd.play(Sfx.DRAW, 0.6f)
                        delay(speedMs(Timing.DRAW_STAGGER))
                    }
                }
                val penalty = e.reason != DrawReason.NORMAL && e.reason != DrawReason.MATCH
                if (e.playerId == human && penalty) hp.heavy()
                if (penalty && e.cards.size >= 4) bump(e.playerId, listOf("😡", "😱", "😭", "🤬").random(rnd), 0.75)
            }
            is GameEvent.Reshuffled -> snd.play(Sfx.SHUFFLE)
            is GameEvent.ColorChosen -> snd.play(Sfx.PRISM, 0.7f)
            is GameEvent.DirectionChanged -> Unit
            is GameEvent.Blocked -> if (human in e.playerIds) hp.medium()
            is GameEvent.TurnChanged -> if (e.playerId == human) {
                snd.play(Sfx.TURN, 0.6f)
                hp.tick()
            }
            is GameEvent.StackGrew -> {
                snd.play(Sfx.STACK_TICK, 0.9f, rate = (0.9f + e.total * 0.03f).coerceAtMost(1.8f), vary = false)
                if (e.total >= 8) hp.medium()
            }
            is GameEvent.HandsSwapped -> {
                snd.play(Sfx.SWAP)
                bump(e.b, "😲", 0.7)
            }
            is GameEvent.HandsRotated -> snd.play(Sfx.SWAP)
            is GameEvent.Purged -> snd.play(Sfx.WHOOSH)
            is GameEvent.PlayerEliminated -> {
                snd.play(Sfx.OVERLOAD)
                hp.heavy()
                bump(e.playerId, "💀", 0.9)
            }
            is GameEvent.SideFlipped -> {
                snd.play(Sfx.ECLIPSE)
                hp.medium()
                c.music.play(if (e.side == Side.MOON) Track.NIGHT else Track.DAY)
            }
            is GameEvent.ManiaCalled -> {
                snd.play(Sfx.MANIA)
                if (e.playerId != human) bump(e.playerId, "😏", 0.5)
            }
            is GameEvent.Caught -> {
                snd.play(Sfx.CATCH)
                hp.medium()
                bump(e.targetId, "😳", 0.9)
                if (e.catcherId != human) bump(e.catcherId, "👀", 0.6)
            }
            is GameEvent.ChallengeResult -> snd.play(if (e.success) Sfx.CATCH else Sfx.ERROR)
            is GameEvent.StormStarted -> snd.play(Sfx.STORM)
            is GameEvent.ShieldArmed -> snd.play(Sfx.PRISM)
            is GameEvent.ShieldBlocked -> snd.play(Sfx.BLOCK)
            is GameEvent.TokenEarned -> if (e.playerId == human) snd.play(Sfx.COIN)
            is GameEvent.PeekRevealed -> if (net == NetRole.GUEST && e.playerId == human && e.cards.isNotEmpty()) {
                snd.play(Sfx.PRISM)
                _ui.update { it.copy(peek = e.cards) }
                viewModelScope.launch {
                    delay(3200)
                    _ui.update { it.copy(peek = null) }
                }
            }
            is GameEvent.Passed -> Unit
            is GameEvent.RoundEnded -> {
                if (e.winnerId == human) {
                    snd.play(Sfx.WIN)
                    hp.heavy()
                } else {
                    snd.play(Sfx.LOSE)
                    bump(e.winnerId, listOf("🎉", "😎", "🏆").random(rnd), 0.95)
                }
            }
            else -> Unit
        }
    }

    override fun onCleared() {
        persist()
        super.onCleared()
    }
}
