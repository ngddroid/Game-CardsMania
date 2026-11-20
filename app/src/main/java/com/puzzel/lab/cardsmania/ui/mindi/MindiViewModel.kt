package com.puzzel.lab.cardsmania.ui.mindi

import com.puzzel.lab.cardsmania.data.SaveFiles
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.puzzel.lab.cardsmania.CardsManiaApp
import com.puzzel.lab.cardsmania.R
import com.puzzel.lab.cardsmania.audio.Sfx
import com.puzzel.lab.cardsmania.audio.Track
import com.puzzel.lab.cardsmania.data.Achievement
import com.puzzel.lab.cardsmania.data.Achievements
import com.puzzel.lab.cardsmania.data.AppJson
import com.puzzel.lab.cardsmania.data.AvatarRef
import com.puzzel.lab.cardsmania.data.Characters
import com.puzzel.lab.cardsmania.data.DifficultyInfo
import com.puzzel.lab.cardsmania.data.GameFactory
import com.puzzel.lab.cardsmania.data.Profile
import com.puzzel.lab.cardsmania.data.Settings
import com.puzzel.lab.cardsmania.engine.model.Difficulty
import com.puzzel.lab.cardsmania.i18n.Strings
import com.puzzel.lab.cardsmania.mindi.HandKind
import com.puzzel.lab.cardsmania.mindi.HandResult
import com.puzzel.lab.cardsmania.mindi.MCard
import com.puzzel.lab.cardsmania.mindi.MStep
import com.puzzel.lab.cardsmania.mindi.MindiAction
import com.puzzel.lab.cardsmania.mindi.MindiBot
import com.puzzel.lab.cardsmania.mindi.MindiEngine
import com.puzzel.lab.cardsmania.mindi.MindiEvent
import com.puzzel.lab.cardsmania.mindi.MindiPhase
import com.puzzel.lab.cardsmania.mindi.MindiSetup
import com.puzzel.lab.cardsmania.mindi.MindiState
import com.puzzel.lab.cardsmania.mindi.MindiTrumpMode
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

typealias MindiSeat = com.puzzel.lab.cardsmania.mindi.MindiSeat

@Serializable
data class MindiSave(val state: MindiState, val seats: List<MindiSeat>, val daily: String? = null)

/**
 * What the Mindi screen should start. Multiplayer: HOST passes the seated [seats] and [seed] from the room;
 * GUEST waits for the host's MindiStart.
 */
data class MindiLaunch(
    val setup: MindiSetup? = null,
    val resume: Boolean = false,
    val daily: String? = null,
    val net: NetRole = NetRole.LOCAL,
    val seats: List<MindiSeat>? = null,
    val seed: Long? = null,
)

data class MindiWin(
    val won: Boolean,
    val scores: List<Int>,
    val coins: Int,
    val xp: Int,
    val stars: Int,
    val achievements: List<Achievement>,
)

data class MindiUi(
    val ready: Boolean = false,
    val me: Int = 0,
    val seats: List<MindiSeat> = emptyList(),
    val state: MindiState? = null,
    val idle: Boolean = false,
    val legal: Set<Int> = emptySet(),
    val selected: Int? = null,
    val hint: Int? = null,
    val hidePick: Boolean = false,
    val handResult: HandResult? = null,
    val matchResult: MindiWin? = null,
    val showLastTrick: Boolean = false,
    val emotes: Map<Int, Pair<String, Long>> = emptyMap(),
    val banner: String? = null,
    val bannerAt: Long = 0L,
    val turnDeadline: Long = 0L,
    val turnTotal: Long = 0L,
    val daily: String? = null,
    val settings: Settings = Settings(),
    val profile: Profile = Profile(),
    val net: NetRole = NetRole.LOCAL,
    /** Remote human seats → connected (multiplayer only). */
    val netSeats: Map<Int, Boolean> = emptyMap(),
    /** Set when a multiplayer match ends unexpectedly (host left, connection lost). */
    val ended: String? = null,
)

class MindiViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as CardsManiaApp).container
    private val saveFile = File(app.filesDir, "mindi_save.json")

    private val _ui = MutableStateFlow(MindiUi())
    val ui: StateFlow<MindiUi> = _ui
    private val _steps = MutableSharedFlow<MStep>(extraBufferCapacity = 256)
    val steps: SharedFlow<MStep> = _steps
    /** Latest step shown, so a newly created world can catch up. */
    var lastShown: MStep? = null
        private set

    private var state: MindiState? = null
    private var seats: List<MindiSeat> = emptyList()
    private var me = 0
    private var net = NetRole.LOCAL
    private var roomHost: RoomHost? = null
    private var roomClient: RoomClient? = null
    private var awaitingServer = false
    private var netSpeed: Int? = null
    private val isNet: Boolean get() = net != NetRole.LOCAL
    private val queue = ArrayDeque<MStep>()
    private var pumpJob: Job? = null
    private var botJob: Job? = null
    private var timerJob: Job? = null
    private var version = 0L
    private var started = false
    private var daily: String? = null
    private val rnd = Random(System.nanoTime())
    private var tensThisMatch = 0
    private var bestKindThisMatch = HandKind.NORMAL
    private var maxTricksInHand = 0

    private val speedFactor: Float
        get() = when (netSpeed ?: _ui.value.settings.speed) {
            0 -> 1.3f
            2 -> 0.65f
            else -> 1f
        }

    fun start(launch: MindiLaunch) {
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
                    withContext(Dispatchers.Default) { runCatching { AppJson.decodeFromString(MindiSave.serializer(), text) }.getOrNull() }
                }
                if (saved != null && saved.state.phase !is MindiPhase.MatchOver) {
                    state = saved.state
                    seats = saved.seats
                    daily = saved.daily
                    _ui.update { it.copy(ready = true, seats = seats, state = saved.state, daily = daily) }
                    val step = MStep(MindiEvent.Restored(saved.state.handNo), saved.state)
                    lastShown = step
                    _steps.tryEmit(step)
                    afterIdle()
                    return@launch
                }
            }
            val setup = launch.setup ?: p.mindi.setup
            daily = launch.daily
            val seed = launch.daily?.let { dailySeed(it) } ?: System.nanoTime()
            seats = buildSeats(setup, p, seed)
            val steps = MindiEngine.newMatch(setup, seed)
            state = steps.last().state
            _ui.update { it.copy(ready = true, seats = seats, daily = daily) }
            enqueue(steps)
        }
    }

    private fun buildSeats(setup: MindiSetup, p: Profile, seed: Long): List<MindiSeat> {
        val chars = Characters.all.shuffled(java.util.Random(seed))
        val you = MindiSeat(p.name.ifBlank { Strings.get(R.string.mindi_you) }, AvatarRef.player(p.avatar), isHuman = true)
        return listOf(you) + (1 until setup.players).map { i ->
            val ch = chars[(i - 1) % chars.size]
            MindiSeat(ch.name, ch.id, isHuman = false, difficulty = setup.difficulty)
        }
    }

    // ------------------------------------------------------------------ multiplayer

    private fun startHost(launch: MindiLaunch) {
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
        val steps = MindiEngine.newMatch(setup, launch.seed ?: System.nanoTime())
        state = steps.last().state
        host.mindiStateProvider = { state }
        _ui.update { it.copy(ready = true, me = me, seats = seats, net = NetRole.HOST, netSeats = host.seatStatus.value) }
        viewModelScope.launch { for ((seat, a) in host.mindiActions) onRemoteAction(seat, a) }
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
                val actor = actorOf(s)
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
                    is NetMsg.MindiStart -> {
                        me = msg.yourSeat
                        seats = msg.seats
                        netSpeed = msg.speed
                        _ui.update { it.copy(ready = true, me = me, seats = seats) }
                    }
                    is NetMsg.MindiSync -> {
                        me = msg.yourSeat
                        seats = msg.seats
                        netSpeed = msg.speed
                        queue.clear()
                        state = msg.state
                        _ui.update { it.copy(ready = true, me = me, seats = seats, state = msg.state) }
                        val step = MStep(MindiEvent.Restored(msg.state.handNo), msg.state)
                        lastShown = step
                        _steps.tryEmit(step)
                        afterIdle()
                    }
                    is NetMsg.MindiSteps -> {
                        awaitingServer = false
                        val list = msg.steps.map { MStep(it.event, it.state) }
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

    private fun onRemoteAction(seat: Int, a: MindiAction) {
        if (a is MindiAction.NextHand) return // only the host deals the next hand
        if (!submit(a)) roomHost?.sendToSeat(seat, NetMsg.ActionRejected("That move isn't allowed right now."))
    }

    private fun showEmote(seat: Int, text: String) {
        _ui.update { it.copy(emotes = it.emotes + (seat to (text to System.currentTimeMillis()))) }
    }

    private fun actorOf(s: MindiState): Int = when (val ph = s.phase) {
        is MindiPhase.ChooseHidden -> ph.seat
        MindiPhase.Play -> s.turn
        else -> -1
    }

    /** True if [seat] is a connected remote player (host side). */
    private fun remoteConnected(seat: Int) = net == NetRole.HOST && seats.getOrNull(seat)?.isHuman == true && _ui.value.netSeats[seat] == true

    /** Online turns always have a clock so one idle phone can't stall the table: the room's timer, else 30 s. */
    private fun netTurnMs(): Long = (state?.setup?.turnTimer ?: 0).takeIf { it > 0 }?.let { it * 1000L } ?: Net.REMOTE_TURN_MS

    private fun startRemoteTimer(seat: Int) {
        val v = version
        timerJob = viewModelScope.launch {
            delay(netTurnMs() + 1500)
            if (version != v) return@launch
            val s = state ?: return@launch
            if (actorOf(s) != seat) return@launch
            val a = MindiBot.decide(s, seat, Difficulty.NORMAL, rnd) ?: return@launch
            submit(a)
        }
    }

    // ------------------------------------------------------------------ pump

    private fun enqueue(steps: List<MStep>) {
        if (net == NetRole.HOST) roomHost?.broadcastMindi(steps)
        queue.addAll(steps)
        _ui.update { it.copy(idle = false, legal = emptySet(), selected = null, hint = null, turnDeadline = 0L) }
        if (pumpJob?.isActive != true) pumpJob = viewModelScope.launch { pump() }
    }

    private fun duration(e: MindiEvent): Long = when (e) {
        is MindiEvent.Dealt -> 18L * 60 + 700
        is MindiEvent.TrumpShown -> 1300
        is MindiEvent.HiddenChosen -> 800
        is MindiEvent.TrumpRevealed -> 1500
        is MindiEvent.TrumpCut -> 900
        is MindiEvent.Played -> 430
        is MindiEvent.TrickWon -> 1000
        is MindiEvent.HandEnded -> 1700
        is MindiEvent.MatchEnded -> 200
        is MindiEvent.Restored -> 300
    }

    private suspend fun pump() {
        while (queue.isNotEmpty()) {
            val step = queue.removeFirst()
            lastShown = step
            _ui.update { it.copy(state = step.state) }
            _steps.tryEmit(step)
            feedback(step)
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
        when (val ph = s.phase) {
            is MindiPhase.HandOver -> {
                _ui.update { it.copy(idle = true, handResult = ph.result, legal = emptySet()) }
                return
            }
            is MindiPhase.MatchOver -> {
                _ui.update { it.copy(idle = true, handResult = ph.result, legal = emptySet()) }
                finishMatch(s, ph.winnerTeam)
                return
            }
            is MindiPhase.ChooseHidden -> {
                if (ph.seat == me) {
                    _ui.update { it.copy(idle = true, hidePick = true, legal = s.hands[me].map { c -> c.id }.toSet()) }
                    startTimer()
                } else {
                    _ui.update { it.copy(idle = true, hidePick = false, legal = emptySet(), turnDeadline = 0L) }
                    othersTurn(ph.seat)
                }
            }
            MindiPhase.Play -> {
                if (s.turn == me) {
                    _ui.update { it.copy(idle = true, hidePick = false, legal = MindiEngine.legalCards(s, me).map { c -> c.id }.toSet()) }
                    c.sound.play(Sfx.TURN, 0.5f)
                    startTimer()
                } else {
                    _ui.update { it.copy(idle = true, hidePick = false, legal = emptySet(), turnDeadline = 0L) }
                    othersTurn(s.turn)
                }
            }
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

    private fun scheduleBot(seat: Int) {
        val s = state ?: return
        val v = version
        val diff = seats.getOrNull(seat)?.difficulty ?: Difficulty.NORMAL
        botJob = viewModelScope.launch {
            val think = ((if (s.trick.isEmpty()) 650L else 500L) + rnd.nextLong(0, 450)) * speedFactor
            val decision = withContext(Dispatchers.Default) { MindiBot.decide(s, seat, diff, Random(rnd.nextLong())) }
            delay(think.toLong())
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
            val a = MindiBot.decide(s, me, Difficulty.NORMAL, rnd) ?: return@launch
            c.sound.play(Sfx.ERROR, 0.6f)
            submit(a)
        }
    }

    private fun submit(a: MindiAction): Boolean {
        val s = state ?: return false
        if (MindiEngine.validate(s, a) != null) return false
        if (net == NetRole.GUEST) {
            if (awaitingServer) return false
            awaitingServer = true
            timerJob?.cancel()
            _ui.update { it.copy(idle = false, legal = emptySet(), selected = null, hint = null, turnDeadline = 0L) }
            roomClient?.send(NetMsg.MindiAct(a))
            return true
        }
        version++
        timerJob?.cancel()
        val steps = MindiEngine.apply(s, a)
        state = steps.last().state
        enqueue(steps)
        return true
    }

    // ------------------------------------------------------------------ input

    private fun canAct() = _ui.value.idle && state?.isOver == false

    fun tapCard(id: Int) {
        if (!canAct()) return
        val u = _ui.value
        if (id !in u.legal) {
            c.sound.play(Sfx.ERROR, 0.5f)
            c.haptics.light()
            val s = state ?: return
            val lead = s.leadSuit
            if (lead != null && s.hands[me].any { it.suit == lead }) toast(Strings.get(R.string.mindi_toast_follow_suit, suitName(lead)))
            else if (s.revealer == me) toast(Strings.get(R.string.mindi_toast_must_trump))
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
        val s = state ?: return false
        val a = if (s.phase is MindiPhase.ChooseHidden) MindiAction.Hide(me, id) else MindiAction.Play(me, id)
        val ok = submit(a)
        if (ok) c.haptics.light()
        return ok
    }

    fun deselect() = _ui.update { it.copy(selected = null) }

    fun hint() {
        val s = state ?: return
        if (!canAct()) return
        val card: MCard? = when (val ph = s.phase) {
            is MindiPhase.ChooseHidden -> if (ph.seat == me) MindiBot.chooseHidden(s, me, Difficulty.HARD, rnd) else null
            else -> MindiBot.hint(s, me)
        }
        if (card == null) return
        c.sound.play(Sfx.PRISM, 0.6f)
        _ui.update { it.copy(hint = card.id, selected = card.id) }
    }

    fun toggleLastTrick(show: Boolean) = _ui.update { it.copy(showLastTrick = show) }

    fun nextHand() {
        if (net == NetRole.GUEST) return
        _ui.update { it.copy(handResult = null) }
        submit(MindiAction.NextHand())
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
        val partner = s.partnersOf(me).firstOrNull() ?: return
        if (rnd.nextDouble() < 0.6) viewModelScope.launch {
            delay(700)
            val reply = listOf("👍", "😎", "🤝", "😄", "💪").random(rnd)
            _ui.update { it.copy(emotes = it.emotes + (partner to (reply to System.currentTimeMillis()))) }
        }
    }

    private fun toast(msg: String) = _ui.update { it.copy(banner = msg, bannerAt = System.currentTimeMillis()) }

    // ------------------------------------------------------------------ feedback

    private fun bump(seat: Int, emoji: String, chance: Double) {
        if (seat == me || rnd.nextDouble() > chance) return
        _ui.update { it.copy(emotes = it.emotes + (seat to (emoji to System.currentTimeMillis()))) }
    }

    private fun feedback(step: MStep) {
        val s = step.state
        when (val e = step.event) {
            is MindiEvent.Dealt -> viewModelScope.launch {
                if (_ui.value.handResult != null) _ui.update { it.copy(handResult = null) }
                c.sound.play(Sfx.SHUFFLE)
                repeat(14) {
                    c.sound.play(Sfx.CARD_SLIDE, 0.35f)
                    delay(70)
                }
            }
            is MindiEvent.TrumpShown -> c.sound.play(Sfx.PRISM)
            is MindiEvent.HiddenChosen -> c.sound.play(Sfx.WHOOSH, 0.7f)
            is MindiEvent.TrumpRevealed -> {
                c.sound.play(Sfx.ECLIPSE, 0.8f)
                c.haptics.medium()
            }
            is MindiEvent.TrumpCut -> {
                c.sound.play(Sfx.BLOCK)
                c.haptics.medium()
            }
            is MindiEvent.Played -> {
                c.sound.snap(0.9f)
                if (e.trump) c.sound.play(Sfx.PLUS, 0.6f)
            }
            is MindiEvent.TrickWon -> viewModelScope.launch {
                delay((420 * speedFactor).toLong())
                c.sound.play(Sfx.WHOOSH, 0.6f)
                if (e.tens > 0) {
                    delay(300)
                    c.sound.play(Sfx.COIN)
                    if (s.team(e.winner) == s.team(me)) {
                        tensThisMatch += e.tens
                        bump(s.partnersOf(me).firstOrNull() ?: -1, "😎", 0.4)
                    } else bump(e.winner, listOf("😏", "😂", "🤑").random(rnd), 0.6)
                }
            }
            is MindiEvent.HandEnded -> {
                val r = e.result
                if (r.winnerTeam == s.team(me)) {
                    c.sound.play(Sfx.WIN, 0.8f)
                    if (r.kind.ordinal > bestKindThisMatch.ordinal) bestKindThisMatch = r.kind
                    maxTricksInHand = maxOf(maxTricksInHand, r.tricks[r.winnerTeam])
                } else c.sound.play(Sfx.LOSE, 0.8f)
            }
            is MindiEvent.MatchEnded -> Unit
            is MindiEvent.Restored -> Unit
        }
    }

    // ------------------------------------------------------------------ results

    private fun finishMatch(s: MindiState, winnerTeam: Int) {
        if (_ui.value.matchResult != null) return
        val won = winnerTeam == s.team(me)
        val date = daily
        val lastHand = s.history.lastOrNull()
        viewModelScope.launch {
            if (!isNet) SaveFiles.delete(saveFile)
            var result: MindiWin? = null
            val handsWon = s.history.count { it.winnerTeam == s.team(me) }
            val newProfile = c.profile.update { p ->
                val st = p.mindi.stats
                val streak = if (won) st.streak + 1 else 0
                val newStats = st.copy(
                    matches = st.matches + 1,
                    matchWins = st.matchWins + if (won) 1 else 0,
                    hands = st.hands + s.history.size,
                    handWins = st.handWins + handsWon,
                    mendikots = st.mendikots + s.history.count { it.winnerTeam == s.team(me) && it.kind == HandKind.MENDIKOT },
                    whitewashes = st.whitewashes + s.history.count { it.winnerTeam == s.team(me) && it.kind == HandKind.WHITEWASH },
                    tensCaptured = st.tensCaptured + tensThisMatch,
                    streak = streak,
                    bestStreak = maxOf(st.bestStreak, streak),
                )
                val diff = seats.drop(1).maxOfOrNull { it.difficulty } ?: Difficulty.NORMAL
                val stars = if (date != null && lastHand != null) dailyStars(s, lastHand) else 0
                var coins = if (won) (140 * DifficultyInfo.multiplier(diff)).toInt() + s.setup.players * 10 else 30 + handsWon * 10
                coins += s.history.count { it.winnerTeam == s.team(me) && it.kind != HandKind.NORMAL } * 60
                val prevStars = if (date != null) p.mindi.daily[date] ?: 0 else 0
                if (date != null && stars > prevStars) coins += (stars - prevStars) * 100
                val xp = (if (won) 150 else 50) + handsWon * 15
                val earned = buildList {
                    fun award(id: String, cond: Boolean) { if (cond && id !in p.achievements) Achievements.byId(id)?.let { add(it) } }
                    award("mindi_win", won)
                    award("mindi_mendikot", s.history.any { it.winnerTeam == s.team(me) && it.kind == HandKind.MENDIKOT })
                    award("mindi_whitewash", s.history.any { it.winnerTeam == s.team(me) && it.kind == HandKind.WHITEWASH })
                    award("mindi_hide", won && s.setup.trumpMode == MindiTrumpMode.HIDE)
                    award("mindi_cut", won && s.setup.trumpMode == MindiTrumpMode.CUT)
                    award("mindi_big", won && s.setup.players >= 6)
                    award("mindi_expert", won && diff == Difficulty.GENIUS)
                    award("mindi_daily", date != null && stars >= 3)
                }
                coins += earned.sumOf { it.reward }
                result = MindiWin(won, s.scores, coins, xp, stars, earned)
                p.copy(
                    coins = p.coins + coins,
                    xp = p.xp + xp,
                    achievements = p.achievements + earned.map { it.id },
                    mindi = p.mindi.copy(
                        stats = newStats,
                        daily = if (date != null && stars > prevStars) p.mindi.daily + (date to stars) else p.mindi.daily,
                    ),
                )
            }
            _ui.update { it.copy(profile = newProfile) }
            delay(1200)
            _ui.update { it.copy(matchResult = result) }
            if (result?.achievements?.isNotEmpty() == true) c.sound.play(Sfx.CHEST)
        }
    }

    /** Daily goals: ★ win the hand, ★★ with 3+ tens, ★★★ with 9+ tricks or a Mendikot. */
    private fun dailyStars(s: MindiState, r: HandResult): Int {
        val team = s.team(me)
        if (r.winnerTeam != team) return 0
        var stars = 1
        if (r.tens[team] >= 3) stars++
        if (r.tricks[team] >= 9 || r.kind != HandKind.NORMAL) stars++
        return stars
    }

    private fun persist() {
        if (isNet) return // multiplayer matches are never saved for "Continue"
        val s = state ?: return
        if (s.phase is MindiPhase.MatchOver) return
        val save = MindiSave(s, seats, daily)
        SaveFiles.write(saveFile) { AppJson.encodeToString(MindiSave.serializer(), save) }
    }

    fun abandon() {
        // a quit match stays saved so it can be continued later
        persist()
    }

    override fun onCleared() {
        persist()
        super.onCleared()
    }

    companion object {
        fun suitName(s: Int) = when (s) {
            0 -> Strings.get(R.string.mindi_suit_spades)
            1 -> Strings.get(R.string.mindi_suit_hearts)
            2 -> Strings.get(R.string.mindi_suit_diamonds)
            else -> Strings.get(R.string.mindi_suit_clubs)
        }

        fun suitSymbol(s: Int) = when (s) {
            0 -> "♠"
            1 -> "♥"
            2 -> "♦"
            else -> "♣"
        }

        fun dailySeed(date: String): Long = date.replace("-", "").toLong() * 31L + 1009L

        fun dailySetup(): MindiSetup = MindiSetup(players = 4, trumpMode = MindiTrumpMode.HIDE, target = 1, difficulty = Difficulty.HARD)

        fun today(): String = GameFactory.today()

        fun hasSave(app: Application): Boolean = File(app.filesDir, "mindi_save.json").exists()
    }
}
