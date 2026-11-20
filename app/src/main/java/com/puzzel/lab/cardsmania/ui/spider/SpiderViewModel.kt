package com.puzzel.lab.cardsmania.ui.spider

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
import com.puzzel.lab.cardsmania.data.Profile
import com.puzzel.lab.cardsmania.data.Settings
import com.puzzel.lab.cardsmania.i18n.Strings
import com.puzzel.lab.cardsmania.spider.SpiderEngine
import com.puzzel.lab.cardsmania.spider.SpiderEvent
import com.puzzel.lab.cardsmania.spider.SpiderHint
import com.puzzel.lab.cardsmania.spider.SpiderMove
import com.puzzel.lab.cardsmania.spider.SpiderProfile
import com.puzzel.lab.cardsmania.spider.SpiderResult
import com.puzzel.lab.cardsmania.spider.SpiderRules
import com.puzzel.lab.cardsmania.spider.SpiderSave
import com.puzzel.lab.cardsmania.spider.SpiderSolver
import com.puzzel.lab.cardsmania.spider.SpiderState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class SpiderDialog { NEW_GAME, DAILY, LOOK, RULES, STUCK, STATS }

// ------------------------------------------------------------------ localized move errors
// SpiderEngine.moveError / pickError stay English (unit tests assert them); these mirror their logic for the toasts.

private fun spiderRankText(r: Int): String = when (r) {
    1 -> Strings.get(R.string.spider_rank_ace)
    11 -> Strings.get(R.string.spider_rank_jack)
    12 -> Strings.get(R.string.spider_rank_queen)
    13 -> Strings.get(R.string.spider_rank_king)
    else -> r.toString()
}

/** English needs "an" before Ace and 8; other languages use the same text for all three forms. */
private fun spiderNeedsAn(r: Int) = r == 1 || r == 8

/** Localized version of [SpiderEngine.moveError]: a short reason why [m] is not allowed (null if it is allowed). */
fun spiderMoveErrorText(s: SpiderState, m: SpiderMove): String? {
    if (SpiderEngine.canMove(s, m)) return null
    val notAllowed = { Strings.get(R.string.spider_err_not_allowed) }
    val src = s.columns.getOrNull(m.from) ?: return notAllowed()
    val card = src.getOrNull(m.index) ?: return notAllowed()
    if (!card.up) return Strings.get(R.string.spider_err_face_down)
    if (!SpiderEngine.canPickUp(s, m.from, m.index)) return Strings.get(R.string.spider_err_same_suit)
    val dest = s.columns.getOrNull(m.to) ?: return notAllowed()
    if (m.to == m.from) return null
    if (dest.isEmpty()) return null
    if (card.rank == 13) return Strings.get(R.string.spider_err_king)
    val need = card.rank + 1
    val res = when {
        spiderNeedsAn(card.rank) -> R.string.spider_err_goes_on_an_first
        spiderNeedsAn(need) -> R.string.spider_err_goes_on_an_second
        else -> R.string.spider_err_goes_on
    }
    return Strings.get(res, spiderRankText(card.rank), spiderRankText(need))
}

/** Localized version of [SpiderEngine.pickError]. */
fun spiderPickErrorText(s: SpiderState, col: Int, index: Int): String? {
    val card = s.columns.getOrNull(col)?.getOrNull(index) ?: return null
    if (!card.up) return Strings.get(R.string.spider_err_face_down)
    if (!SpiderEngine.canPickUp(s, col, index)) return Strings.get(R.string.spider_err_same_suit)
    return null
}

/** One-off visual cues for the table. */
sealed interface SpiderCue {
    data class SoftHint(val hint: SpiderHint) : SpiderCue
}

data class SpiderWin(
    val suits: Int,
    val timeMs: Long,
    val moves: Int,
    val score: Int,
    val coins: Int,
    val xp: Int,
    val bestTime: Boolean,
    val daily: String?,
    val achievements: List<Achievement>,
)

data class SpiderUi(
    val ready: Boolean = false,
    val state: SpiderState? = null,
    val elapsedMs: Long = 0L,
    val daily: String? = null,
    val prefs: SpiderProfile = SpiderProfile(),
    val profile: Profile = Profile(),
    val settings: Settings = Settings(),
    val dialog: SpiderDialog? = null,
    val win: SpiderWin? = null,
    val canUndo: Boolean = false,
    val toast: String? = null,
    val toastAt: Long = 0L,
    val dealing: Boolean = false,
    /** Tap-then-tap-target selection: (column, index) of the lifted run. */
    val selected: Pair<Int, Int>? = null,
    /** All cards are open and a finish was found: show the Auto Finish button. */
    val autoFinish: Boolean = false,
    val autoPlaying: Boolean = false,
)

class SpiderViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as CardsManiaApp).container
    private val saveFile = File(app.filesDir, "spider_save.json")

    private val _ui = MutableStateFlow(SpiderUi())
    val ui: StateFlow<SpiderUi> = _ui
    private val _frames = MutableStateFlow<SpiderFrame?>(null)
    val frames: StateFlow<SpiderFrame?> = _frames
    private val _hints = MutableSharedFlow<SpiderHint>(extraBufferCapacity = 4)
    val hints: SharedFlow<SpiderHint> = _hints
    private val _shakes = MutableSharedFlow<Pair<Int, Int>>(extraBufferCapacity = 4)
    val shakes: SharedFlow<Pair<Int, Int>> = _shakes
    private val _cues = MutableSharedFlow<SpiderCue>(extraBufferCapacity = 4)
    val cues: SharedFlow<SpiderCue> = _cues

    private var state: SpiderState? = null
    private val history = ArrayDeque<SpiderState>()
    private var elapsed = 0L
    private var counted = false
    private var daily: String? = null
    private var seq = 0L
    private var lockedUntil = 0L
    private var timerJob: Job? = null
    private var saveJob: Job? = null
    private var started = false
    private var foreground = true
    private var lastFirstId = -1
    private var lastFrom = -1
    private var hintFor: SpiderState? = null
    private var hintSkip = 0
    private var idleJob: Job? = null
    private var finishJob: Job? = null
    private var finishMoves: List<SpiderMove>? = null

    /** Smart-tap cycling: tapping the same run again sends it to the next destination. */
    private class Cycle(val cardId: Int, var afterMoves: Int, val tried: MutableSet<Int>)
    private var cycle: Cycle? = null

    fun start(dailyDate: String?) {
        if (started) return
        started = true
        viewModelScope.launch {
            val p = c.profile.current()
            val s = c.settings.current()
            _ui.update { it.copy(prefs = p.spider, profile = p, settings = s) }
            launch { c.settings.settings.collect { ns -> _ui.update { it.copy(settings = ns) } } }
            launch { c.profile.profile.collect { np -> _ui.update { it.copy(profile = np, prefs = np.spider) } } }
            c.music.play(Track.MENU)
            if (dailyDate != null) {
                startDaily(dailyDate)
                return@launch
            }
            val saved = SaveFiles.read(saveFile)?.let { text ->
                withContext(Dispatchers.Default) { runCatching { AppJson.decodeFromString(SpiderSave.serializer(), text) }.getOrNull() }
            }
            if (saved != null && !saved.state.won) {
                state = saved.state
                history.clear()
                history.addAll(saved.history)
                elapsed = saved.elapsedMs
                counted = saved.counted
                daily = saved.daily
                emit(saved.state, emptyList(), FrameKind.RESTORE)
                _ui.update { it.copy(ready = true, daily = daily) }
                startTimer()
            } else {
                newGame(p.spider.lastSuits)
            }
        }
    }

    // ------------------------------------------------------------------ new games

    fun newGame(suits: Int, replay: Boolean = false) {
        viewModelScope.launch {
            recordAbandon()
            val seed = if (replay) state?.seed ?: System.nanoTime() else withContext(Dispatchers.Default) {
                if (suits == 1) SpiderSolver.winnableSeed(1, System.nanoTime()) else System.nanoTime()
            }
            begin(SpiderEngine.newGame(suits, seed), null)
            if (!replay) c.profile.update { it.copy(spider = it.spider.copy(lastSuits = suits)) }
        }
    }

    fun startDaily(date: String) {
        viewModelScope.launch {
            recordAbandon()
            val seed = withContext(Dispatchers.Default) { SpiderRules.dailySeed(date) }
            begin(SpiderEngine.newGame(1, seed), date)
        }
    }

    private fun begin(s: SpiderState, dailyDate: String?) {
        state = s
        history.clear()
        elapsed = 0L
        counted = false
        daily = dailyDate
        lastFirstId = -1
        lastFrom = -1
        lockedUntil = System.currentTimeMillis() + 1500
        _ui.update { it.copy(ready = true, dialog = null, win = null, daily = dailyDate, elapsedMs = 0L) }
        emit(s, emptyList(), FrameKind.NEW_GAME)
        c.sound.play(Sfx.SHUFFLE)
        viewModelScope.launch {
            repeat(10) {
                c.sound.play(Sfx.CARD_SLIDE, 0.35f)
                delay(110)
            }
        }
        startTimer()
        persist()
    }

    /** Starting a new game over an unfinished, started one counts as a loss (breaks the win streak). */
    private suspend fun recordAbandon() {
        val s = state ?: return
        if (!counted || s.won) return
        c.profile.update { p ->
            val st = p.spider.stat(s.suits)
            p.copy(spider = p.spider.copy(stats = p.spider.stats + (s.suits to st.copy(streak = 0, totalTimeMs = st.totalTimeMs + elapsed))))
        }
        counted = false
    }

    // ------------------------------------------------------------------ input

    private fun locked() = System.currentTimeMillis() < lockedUntil || state == null || _ui.value.win != null || _ui.value.autoPlaying

    fun tapCard(col: Int, index: Int) {
        val s = state ?: return
        if (locked()) return
        touch()
        if (_ui.value.prefs.selectMode) {
            selectTap(s, col, index)
            return
        }
        spiderPickErrorText(s, col, index)?.let {
            invalid(col, index, it)
            return
        }
        val dests = SpiderEngine.destinations(s, col, index)
        if (dests.isEmpty()) {
            invalid(col, index, Strings.get(R.string.spider_toast_no_place))
            return
        }
        val card = s.columns[col][index]
        val cyc = cycle
        val dest = if (cyc != null && cyc.cardId == card.id && cyc.afterMoves == s.moves) {
            // tapped the same run again right after moving it: try the next destination
            cyc.tried += col
            dests.firstOrNull { it !in cyc.tried } ?: run {
                cyc.tried.clear()
                cyc.tried += col
                dests.first()
            }
        } else {
            cycle = Cycle(card.id, -1, mutableSetOf(col))
            dests.first()
        }
        if (doMove(SpiderMove(col, index, dest))) {
            cycle?.let {
                it.tried += dest
                it.afterMoves = state?.moves ?: -1
            }
        }
    }

    /** Tap-then-tap-target controls. */
    private fun selectTap(s: SpiderState, col: Int, index: Int) {
        val sel = _ui.value.selected
        if (sel != null) {
            if (sel.first == col) {
                // tapping the selected column again: re-select a different run start or cancel
                if (index >= 0 && index != sel.second && SpiderEngine.canPickUp(s, col, index)) select(col to index) else select(null)
                return
            }
            val m = SpiderMove(sel.first, sel.second, col)
            if (SpiderEngine.canMove(s, m)) {
                select(null)
                doMove(m)
                return
            }
            if (index >= 0 && SpiderEngine.canPickUp(s, col, index)) {
                select(col to index)
                c.sound.play(Sfx.SELECT, 0.6f)
                return
            }
            select(null)
            invalid(sel.first, sel.second, spiderMoveErrorText(s, m))
            return
        }
        if (index < 0) return
        spiderPickErrorText(s, col, index)?.let {
            invalid(col, index, it)
            return
        }
        select(col to index)
        c.sound.play(Sfx.SELECT, 0.6f)
        c.haptics.tick()
    }

    /** Tap on a column's empty area (no card under the finger). */
    fun tapColumn(col: Int) {
        val s = state ?: return
        if (locked()) return
        touch()
        if (_ui.value.prefs.selectMode && _ui.value.selected != null) selectTap(s, col, -1)
    }

    /** Tap outside the columns. */
    fun tapAway() {
        if (_ui.value.selected != null) select(null)
    }

    private fun select(sel: Pair<Int, Int>?) = _ui.update { it.copy(selected = sel) }

    /** Returns true if the dragged stack was placed on [toCol]. */
    fun dragDrop(col: Int, index: Int, toCol: Int): Boolean {
        if (locked() || toCol < 0 || toCol == col) return false
        val s = state ?: return false
        touch()
        val m = SpiderMove(col, index, toCol)
        if (!SpiderEngine.canMove(s, m)) {
            invalid(col, index, spiderMoveErrorText(s, m))
            return false
        }
        cycle = null
        select(null)
        return doMove(m)
    }

    /** A drag released where nothing can take it ([nearCol] = closest column, or -1). */
    fun invalidDrop(col: Int, index: Int, nearCol: Int) {
        val s = state ?: return
        val reason = if (nearCol >= 0) spiderMoveErrorText(s, SpiderMove(col, index, nearCol)) else null
        invalid(col, index, reason ?: Strings.get(R.string.spider_toast_drop_on_column))
    }

    fun canPickUp(col: Int, index: Int): Boolean = state?.let { SpiderEngine.canPickUp(it, col, index) } == true

    private fun invalid(col: Int, index: Int, reason: String? = null) {
        c.sound.play(Sfx.ERROR, 0.5f)
        c.haptics.light()
        _shakes.tryEmit(col to index)
        if (reason != null) toast(reason)
    }

    private fun doMove(m: SpiderMove): Boolean {
        val s = state ?: return false
        val moved = s.columns[m.from][m.index].id
        val r = SpiderEngine.move(s, m) ?: return false
        pushHistory(s)
        lastFirstId = moved
        lastFrom = m.from
        c.sound.snap(0.8f)
        c.haptics.tick()
        if (r.events.any { it is SpiderEvent.Flipped }) c.sound.play(Sfx.SELECT, 0.4f)
        applyResult(r, FrameKind.MOVE)
        return true
    }

    fun tapStock() {
        val s = state ?: return
        if (locked()) return
        if (s.stock.isEmpty()) return
        touch()
        // empty columns don't block the deal: they get a card like every other column
        val r = SpiderEngine.deal(s) ?: return
        pushHistory(s)
        lastFirstId = -1
        lastFrom = -1
        lockedUntil = System.currentTimeMillis() + 650
        viewModelScope.launch {
            repeat(10) {
                c.sound.play(Sfx.CARD_SLIDE, 0.45f)
                delay(55)
            }
        }
        applyResult(r, FrameKind.DEAL)
    }

    fun undo() {
        val s = state ?: return
        if (locked() || s.won) return
        val prev = history.removeLastOrNull() ?: run {
            toast(Strings.get(R.string.spider_toast_nothing_to_undo))
            return
        }
        // undo is free to use, but counts as a move (standard scoring)
        val restored = prev.copy(moves = s.moves + 1)
        state = restored
        cycle = null
        lastFirstId = -1
        lastFrom = -1
        c.sound.play(Sfx.WHOOSH, 0.6f)
        emit(restored, listOf(SpiderEvent.Undone), FrameKind.UNDO)
        _ui.update { it.copy(canUndo = history.isNotEmpty()) }
        persist()
    }

    fun hint() {
        val s = state ?: return
        if (locked()) return
        if (hintFor === s) hintSkip++ else { hintFor = s; hintSkip = 0 }
        when (val h = SpiderEngine.nextHint(s, lastFirstId, lastFrom, hintSkip)) {
            SpiderHint.None -> {
                c.sound.play(Sfx.ERROR, 0.5f)
                _ui.update { it.copy(dialog = SpiderDialog.STUCK) }
            }
            else -> {
                c.sound.play(Sfx.PRISM, 0.5f)
                _hints.tryEmit(h)
            }
        }
    }

    private fun pushHistory(s: SpiderState) {
        history.addLast(s)
        while (history.size > 400) history.removeFirst()
        _ui.update { it.copy(canUndo = true) }
        if (!counted) {
            counted = true
            val suits = s.suits
            viewModelScope.launch {
                c.profile.update { p ->
                    val st = p.spider.stat(suits)
                    p.copy(spider = p.spider.copy(stats = p.spider.stats + (suits to st.copy(played = st.played + 1))))
                }
            }
        }
    }

    // ------------------------------------------------------------------ results & frames

    private fun applyResult(r: SpiderResult, kind: FrameKind) {
        val completes = r.events.filterIsInstance<SpiderEvent.Completed>()
        state = r.state
        if (completes.isEmpty()) {
            emit(r.state, r.events, kind)
        } else {
            // show the run landing first, then fly it to the completed pile
            val inter = intermediate(r, completes)
            emit(inter, r.events.filter { it !is SpiderEvent.Completed && it !is SpiderEvent.Won }, kind)
            val wait = if (kind == FrameKind.DEAL) 720L else 300L
            lockedUntil = System.currentTimeMillis() + wait + 650
            viewModelScope.launch {
                delay(wait)
                c.sound.play(Sfx.PRISM, 0.8f)
                c.haptics.medium()
                emit(r.state, r.events.filter { it is SpiderEvent.Completed || it is SpiderEvent.Won || it is SpiderEvent.Flipped }, FrameKind.COLLECT)
                delay(450)
                c.sound.play(Sfx.COIN, 0.7f)
            }
        }
        if (r.state.won) onWon(r.state)
        else checkStuck(r.state)
        persist()
    }

    /** The state just before the completed run(s) leave the column. */
    private fun intermediate(r: SpiderResult, completes: List<SpiderEvent.Completed>): SpiderState {
        val s = r.state
        val cols = s.columns.map { it.toMutableList() }
        val runs = s.completed.takeLast(completes.size)
        completes.forEachIndexed { i, e ->
            val col = cols[e.column]
            // the card turned over after the run left goes back to face down
            val ci = r.events.indexOf(e)
            val flippedAfter = r.events.drop(ci + 1).filterIsInstance<SpiderEvent.Flipped>().firstOrNull { it.column == e.column }
            if (flippedAfter != null && col.isNotEmpty() && col.last().id == flippedAfter.cardId) col[col.lastIndex] = col.last().copy(up = false)
            col.addAll(runs[i])
        }
        return s.copy(columns = cols.map { it.toList() }, completed = s.completed.dropLast(completes.size))
    }

    private fun emit(s: SpiderState, events: List<SpiderEvent>, kind: FrameKind) {
        _frames.value = SpiderFrame(s, events, kind, ++seq)
        _ui.update { it.copy(state = s, canUndo = history.isNotEmpty() && !s.won, selected = null) }
        if (kind != FrameKind.COLLECT) checkAutoFinish()
        touch()
    }

    // ------------------------------------------------------------------ idle hint & auto finish

    /** Restarts the idle-hint countdown (any interaction or state change). */
    private fun touch() {
        idleJob?.cancel()
        idleJob = viewModelScope.launch {
            while (isActive) {
                delay(25_000)
                val s = state ?: continue
                val u = _ui.value
                if (!u.prefs.idleHint || !foreground || u.dialog != null || u.win != null || s.won || locked() || u.autoPlaying) continue
                val h = SpiderEngine.nextHint(s, lastFirstId, lastFrom)
                if (h !is SpiderHint.None) _cues.tryEmit(SpiderCue.SoftHint(h))
            }
        }
    }

    private fun checkAutoFinish() {
        val s = state ?: return
        finishJob?.cancel()
        if (s.won || !SpiderEngine.allOpen(s) || _ui.value.autoPlaying) {
            finishMoves = null
            if (_ui.value.autoFinish) _ui.update { it.copy(autoFinish = false) }
            return
        }
        finishJob = viewModelScope.launch {
            val moves = withContext(Dispatchers.Default) { SpiderSolver.finishMoves(s) }
            if (state === s || state?.columns == s.columns) {
                finishMoves = moves
                _ui.update { it.copy(autoFinish = moves != null && moves.isNotEmpty()) }
            }
        }
    }

    /** Plays the found finish move by move. */
    fun autoFinish() {
        val moves = finishMoves ?: return
        if (_ui.value.autoPlaying) return
        _ui.update { it.copy(autoFinish = false, autoPlaying = true, selected = null) }
        viewModelScope.launch {
            for (m in moves) {
                while (System.currentTimeMillis() < lockedUntil) delay(40)
                val s = state ?: break
                if (s.won) break
                if (!SpiderEngine.canMove(s, m)) break
                doMove(m)
                delay(230)
            }
            _ui.update { it.copy(autoPlaying = false) }
            finishMoves = null
            checkAutoFinish()
        }
    }

    private fun checkStuck(s: SpiderState) {
        if (!SpiderEngine.stuck(s)) return
        viewModelScope.launch {
            delay(900)
            if (state === s) _ui.update { it.copy(dialog = SpiderDialog.STUCK) }
        }
    }

    private fun onWon(s: SpiderState) {
        timerJob?.cancel()
        val time = elapsed
        val date = daily
        lockedUntil = Long.MAX_VALUE
        viewModelScope.launch {
            SaveFiles.delete(saveFile)
            var result: SpiderWin? = null
            c.profile.update { p ->
                val st = p.spider.stat(s.suits)
                val bestTime = st.bestTimeMs == 0L || time < st.bestTimeMs
                val newSt = st.copy(
                    won = st.won + 1,
                    streak = st.streak + 1,
                    bestStreak = maxOf(st.bestStreak, st.streak + 1),
                    bestTimeMs = if (bestTime) time else st.bestTimeMs,
                    bestMoves = if (st.bestMoves == 0) s.moves else minOf(st.bestMoves, s.moves),
                    bestScore = maxOf(st.bestScore, s.score),
                    totalTimeMs = st.totalTimeMs + time,
                )
                val firstDaily = date != null && date !in p.spider.dailySolved
                var coins = SpiderRules.winCoins(s.suits) + if (firstDaily) 100 else 0
                val xp = SpiderRules.winXp(s.suits)
                val earned = buildList {
                    fun award(id: String, cond: Boolean) { if (cond && id !in p.achievements) Achievements.byId(id)?.let { add(it) } }
                    award("spider_win", true)
                    award("spider_2", s.suits == 2)
                    award("spider_4", s.suits == 4)
                    award("spider_daily", date != null)
                    award("spider_fast", s.suits == 1 && time < 5 * 60_000L)
                }
                coins += earned.sumOf { it.reward }
                val daily = if (date != null) {
                    val prev = p.spider.dailySolved[date]
                    p.spider.dailySolved + (date to (if (prev == null || prev == 0L) time else minOf(prev, time)))
                } else p.spider.dailySolved
                result = SpiderWin(s.suits, time, s.moves, s.score, coins, xp, bestTime && st.won > 0, date, earned)
                p.copy(
                    coins = p.coins + coins,
                    xp = p.xp + xp,
                    achievements = p.achievements + earned.map { it.id },
                    spider = p.spider.copy(stats = p.spider.stats + (s.suits to newSt), dailySolved = daily),
                )
            }
            counted = false
            delay(400)
            c.sound.play(Sfx.WIN)
            c.haptics.heavy()
            delay(2000)
            _ui.update { it.copy(win = result) }
            if (result?.achievements?.isNotEmpty() == true) c.sound.play(Sfx.CHEST)
        }
    }

    // ------------------------------------------------------------------ dialogs & prefs

    fun showDialog(d: SpiderDialog?) {
        _ui.update { it.copy(dialog = d) }
    }

    fun dismissWin() = _ui.update { it.copy(win = null) }

    fun setBackground(name: String) = updatePrefs { it.copy(background = name) }
    fun setFourColor(on: Boolean) = updatePrefs { it.copy(fourColor = on) }
    fun setTapToMove(on: Boolean) = updatePrefs { it.copy(tapToMove = on) }
    fun setSelectMode(on: Boolean) {
        select(null)
        updatePrefs { it.copy(selectMode = on) }
    }
    fun setIdleHint(on: Boolean) = updatePrefs { it.copy(idleHint = on) }
    fun setLeftHanded(on: Boolean) = updatePrefs { it.copy(leftHanded = on) }
    fun setShowTimer(on: Boolean) = updatePrefs { it.copy(showTimer = on) }

    private fun updatePrefs(t: (SpiderProfile) -> SpiderProfile) {
        _ui.update { it.copy(prefs = t(it.prefs)) }
        viewModelScope.launch { c.profile.update { it.copy(spider = t(it.spider)) } }
    }

    private fun toast(msg: String) = _ui.update { it.copy(toast = msg, toastAt = System.currentTimeMillis()) }

    // ------------------------------------------------------------------ timer & saving

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val s = state ?: continue
                val dialogOpen = _ui.value.dialog != null
                if (foreground && counted && !s.won && !dialogOpen) {
                    elapsed += 1000
                    _ui.update { it.copy(elapsedMs = elapsed) }
                }
            }
        }
    }

    fun setForeground(on: Boolean) {
        foreground = on
        if (!on) persistNow()
    }

    private fun persist() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            persistNow()
        }
    }

    private fun persistNow() {
        val s = state ?: return
        if (s.won) return
        val save = SpiderSave(s, history.toList().takeLast(150), elapsed, daily, counted)
        SaveFiles.write(saveFile) { AppJson.encodeToString(SpiderSave.serializer(), save) }
    }

    override fun onCleared() {
        persistNow()
        super.onCleared()
    }
}
