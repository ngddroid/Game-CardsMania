package com.ngdstudio.mindimania.net

import com.ngdstudio.mindimania.bhabhi.BCard
import com.ngdstudio.mindimania.bhabhi.BhabhiAction
import com.ngdstudio.mindimania.bhabhi.BhabhiEvent
import com.ngdstudio.mindimania.bhabhi.BhabhiSeat
import com.ngdstudio.mindimania.bhabhi.BhabhiSetup
import com.ngdstudio.mindimania.bhabhi.BhabhiState
import com.ngdstudio.mindimania.engine.Action
import com.ngdstudio.mindimania.engine.GameEvent
import com.ngdstudio.mindimania.engine.model.Card
import com.ngdstudio.mindimania.engine.model.Difficulty
import com.ngdstudio.mindimania.engine.model.Face
import com.ngdstudio.mindimania.engine.model.GameConfig
import com.ngdstudio.mindimania.engine.model.GameMode
import com.ngdstudio.mindimania.engine.model.GameState
import com.ngdstudio.mindimania.engine.model.HouseRules
import com.ngdstudio.mindimania.engine.model.Kind
import com.ngdstudio.mindimania.engine.model.Phase
import com.ngdstudio.mindimania.engine.model.Side
import com.ngdstudio.mindimania.engine.model.Suit
import com.ngdstudio.mindimania.mindi.MCard
import com.ngdstudio.mindimania.mindi.MindiAction
import com.ngdstudio.mindimania.mindi.MindiEvent
import com.ngdstudio.mindimania.mindi.MindiSeat
import com.ngdstudio.mindimania.mindi.MindiSetup
import com.ngdstudio.mindimania.mindi.MindiState
import com.ngdstudio.mindimania.rummy.REvent
import com.ngdstudio.mindimania.rummy.RAction
import com.ngdstudio.mindimania.rummy.RCard
import com.ngdstudio.mindimania.rummy.RPhase
import com.ngdstudio.mindimania.rummy.RummySeat
import com.ngdstudio.mindimania.rummy.RummySetup
import com.ngdstudio.mindimania.rummy.RummyState
import com.ngdstudio.mindimania.teenpatti.TCard
import com.ngdstudio.mindimania.teenpatti.TPAction
import com.ngdstudio.mindimania.teenpatti.TPEvent
import com.ngdstudio.mindimania.teenpatti.TeenPattiSeat
import com.ngdstudio.mindimania.teenpatti.TeenPattiSetup
import com.ngdstudio.mindimania.teenpatti.TeenPattiState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object Net {
    /** v2: Mindi rooms. v3: Bhabhi rooms. v4: Bhabhi wrong-thulla fine (Penalty event). v5: Teen Patti and Rummy rooms. */
    const val PROTOCOL = 6

    /**
     * Teen Patti and Rummy rooms are switched off for now: the host can't pick them. Everything else (messages,
     * redaction, host/guest play) stays in place and tested, so setting this to true offers them again.
     */
    const val TEEN_PATTI_RUMMY_ROOMS = false
    const val SERVICE_TYPE = "_cardsmania._tcp"
    const val PORT_BASE = 47820
    const val PORT_COUNT = 16
    const val HEARTBEAT_MS = 3000L
    const val TIMEOUT_MS = 15000
    const val GUEST_LINE_LIMIT = 16 * 1024
    const val HOST_LINE_LIMIT = 4 * 1024 * 1024
    const val REMOTE_TURN_MS = 30_000L

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "t"
        allowStructuredMapKeys = true
    }

    fun encode(m: NetMsg): String = json.encodeToString(NetMsg.serializer(), m)
    fun decode(s: String): NetMsg? = runCatching { json.decodeFromString(NetMsg.serializer(), s) }.getOrNull()
}

@Serializable
data class RoomGuest(
    val id: String,
    val name: String,
    val avatar: Int,
    val connected: Boolean = true,
    val seat: Int = -1,
)

/** Which game a room plays. */
@Serializable
enum class RoomGame { MANIA, MINDI, BHABHI, TEEN_PATTI, RUMMY }

/** Everything a lobby shows. The host owns it; guests receive copies. */
@Serializable
data class RoomState(
    val code: String = "",
    val hostName: String = "Host",
    val hostAvatar: Int = 0,
    val mode: GameMode = GameMode.CLASSIC,
    val seats: Int = 4,
    val bots: Int = 0,
    val botDifficulty: Difficulty = Difficulty.NORMAL,
    val handSize: Int = 7,
    val deckCount: Int = 1,
    val targetScore: Int = 0,
    val rules: HouseRules = HouseRules(),
    val speed: Int = 1,
    val guests: List<RoomGuest> = emptyList(),
    val inGame: Boolean = false,
    val protocol: Int = Net.PROTOCOL,
    /**
     * MANIA uses [mode]/[rules]/...; MINDI uses [mindi] and BHABHI uses [bhabhi] (their seat count is always [seats]).
     */
    val game: RoomGame = RoomGame.MANIA,
    val mindi: MindiSetup = MindiSetup(),
    val bhabhi: BhabhiSetup = BhabhiSetup(),
    /** TEEN_PATTI uses [teenPatti] and RUMMY uses [rummy] (endless tables; seats = [seats]). */
    val teenPatti: TeenPattiSetup = TeenPattiSetup(),
    val rummy: RummySetup = RummySetup(),
) {
    val isMindi: Boolean get() = game == RoomGame.MINDI
    val isBhabhi: Boolean get() = game == RoomGame.BHABHI
    val isMania: Boolean get() = game == RoomGame.MANIA
    val isTeenPatti: Boolean get() = game == RoomGame.TEEN_PATTI
    val isRummy: Boolean get() = game == RoomGame.RUMMY
    /** Most seats this game allows. */
    val maxSeats: Int get() = if (isMania || isTeenPatti || isRummy) 6 else 8
    /** Seats still waiting for a player (filled by bots if nobody joins). */
    val openSeats: Int get() = (seats - 1 - bots - guests.size).coerceAtLeast(0)
    val maxBots: Int get() = (seats - 1 - guests.size).coerceAtLeast(0)
    val minSeats: Int get() = maxOf(if (isBhabhi || isTeenPatti) 3 else 2, 1 + guests.size + bots)
    val players: Int get() = 1 + guests.size
}

@Serializable
data class NetStep(val event: GameEvent, val state: GameState)

@Serializable
data class MindiNetStep(val event: MindiEvent, val state: MindiState)

@Serializable
data class BhabhiNetStep(val event: BhabhiEvent, val state: BhabhiState)

@Serializable
data class TPNetStep(val event: TPEvent, val state: TeenPattiState)

@Serializable
data class RummyNetStep(val event: REvent, val state: RummyState)

@Serializable
sealed interface NetMsg {
    // guest -> host
    @Serializable @SerialName("hello")
    data class Hello(val name: String, val avatar: Int, val token: String, val protocol: Int = Net.PROTOCOL) : NetMsg

    @Serializable @SerialName("probe")
    data object Probe : NetMsg

    @Serializable @SerialName("act")
    data class Act(val action: Action) : NetMsg

    @Serializable @SerialName("mact")
    data class MindiAct(val action: MindiAction) : NetMsg

    @Serializable @SerialName("bact")
    data class BhabhiAct(val action: BhabhiAction) : NetMsg

    @Serializable @SerialName("tact")
    data class TeenPattiAct(val action: TPAction) : NetMsg

    @Serializable @SerialName("ract")
    data class RummyAct(val action: RAction) : NetMsg

    @Serializable @SerialName("leave")
    data object Leave : NetMsg

    // host -> guest
    @Serializable @SerialName("welcome")
    data class Welcome(val guestId: String) : NetMsg

    @Serializable @SerialName("rejected")
    data class Rejected(val reason: String) : NetMsg

    @Serializable @SerialName("lobby")
    data class Lobby(val room: RoomState) : NetMsg

    @Serializable @SerialName("start")
    data class Start(val yourId: Int, val config: GameConfig, val speed: Int) : NetMsg

    @Serializable @SerialName("sync")
    data class Sync(val yourId: Int, val config: GameConfig, val speed: Int, val state: GameState) : NetMsg

    @Serializable @SerialName("steps")
    data class Steps(val steps: List<NetStep>) : NetMsg

    /** A Mindi match starts; [yourSeat] is the guest's seat. */
    @Serializable @SerialName("mstart")
    data class MindiStart(val yourSeat: Int, val setup: MindiSetup, val seats: List<MindiSeat>, val speed: Int) : NetMsg

    /** Re-join in the middle of a Mindi match: the current (redacted) state. */
    @Serializable @SerialName("msync")
    data class MindiSync(val yourSeat: Int, val setup: MindiSetup, val seats: List<MindiSeat>, val speed: Int, val state: MindiState) : NetMsg

    @Serializable @SerialName("msteps")
    data class MindiSteps(val steps: List<MindiNetStep>) : NetMsg

    /** A Bhabhi game starts; [yourSeat] is the guest's seat. */
    @Serializable @SerialName("bstart")
    data class BhabhiStart(val yourSeat: Int, val setup: BhabhiSetup, val seats: List<BhabhiSeat>, val speed: Int) : NetMsg

    /** Re-join in the middle of a Bhabhi game: the current (redacted) state. */
    @Serializable @SerialName("bsync")
    data class BhabhiSync(val yourSeat: Int, val setup: BhabhiSetup, val seats: List<BhabhiSeat>, val speed: Int, val state: BhabhiState) : NetMsg

    @Serializable @SerialName("bsteps")
    data class BhabhiSteps(val steps: List<BhabhiNetStep>) : NetMsg

    /** A Teen Patti table opens; [yourSeat] is the guest's seat. */
    @Serializable @SerialName("tstart")
    data class TeenPattiStart(val yourSeat: Int, val setup: TeenPattiSetup, val seats: List<TeenPattiSeat>, val speed: Int) : NetMsg

    /** Re-join in the middle of a Teen Patti table: the current (redacted) state. */
    @Serializable @SerialName("tsync")
    data class TeenPattiSync(val yourSeat: Int, val setup: TeenPattiSetup, val seats: List<TeenPattiSeat>, val speed: Int, val state: TeenPattiState) : NetMsg

    @Serializable @SerialName("tsteps")
    data class TeenPattiSteps(val steps: List<TPNetStep>) : NetMsg

    /** A Rummy table opens; [yourSeat] is the guest's seat. */
    @Serializable @SerialName("rstart")
    data class RummyStart(val yourSeat: Int, val setup: RummySetup, val seats: List<RummySeat>, val speed: Int) : NetMsg

    /** Re-join in the middle of a Rummy table: the current (redacted) state. */
    @Serializable @SerialName("rsync")
    data class RummySync(val yourSeat: Int, val setup: RummySetup, val seats: List<RummySeat>, val speed: Int, val state: RummyState) : NetMsg

    @Serializable @SerialName("rsteps")
    data class RummySteps(val steps: List<RummyNetStep>) : NetMsg

    /** Endless tables: the seats changed (a broke bot left, a new one sat down). */
    @Serializable @SerialName("seats")
    data class SeatsChanged(val tpSeats: List<TeenPattiSeat> = emptyList(), val rummySeats: List<RummySeat> = emptyList()) : NetMsg

    @Serializable @SerialName("bad")
    data class ActionRejected(val reason: String) : NetMsg

    @Serializable @SerialName("ended")
    data class Ended(val reason: String) : NetMsg

    @Serializable @SerialName("kicked")
    data object Kicked : NetMsg

    // both ways
    @Serializable @SerialName("emote")
    data class Emote(val playerId: Int, val text: String) : NetMsg

    @Serializable @SerialName("ping")
    data object Ping : NetMsg
}

/** Re-target a Mindi action at [seat] (the host never trusts the seat a guest claims). */
fun MindiAction.withSeat(seat: Int): MindiAction = when (this) {
    is MindiAction.Hide -> copy(seat = seat)
    is MindiAction.Play -> copy(seat = seat)
    is MindiAction.NextHand -> copy(seat = seat)
}

/**
 * What a Mindi player at [viewer] may see: other hands become blank placeholders (same count, no rank/suit),
 * the hidden trump card is blanked unless the viewer owns it, and the RNG/seed are cleared so the deal
 * can't be reconstructed. Events carry only public information, so they pass through unchanged.
 */
object MindiRedact {
    const val PLACEHOLDER_BASE = 900_000
    val HIDDEN_TRUMP = MCard(-999, 0, -1)

    fun isPlaceholder(c: MCard) = c.rank == 0 && c.suit == -1

    fun state(s: MindiState, viewer: Int): MindiState = s.copy(
        seed = 0L,
        rngState = 0L,
        hands = s.hands.mapIndexed { seat, hand ->
            if (seat == viewer) hand else hand.mapIndexed { i, _ -> MCard(PLACEHOLDER_BASE + seat * 1000 + i, 0, -1) }
        },
        hidden = s.hidden?.let { if (s.hiddenOwner == viewer) it else HIDDEN_TRUMP },
    )

    fun step(event: MindiEvent, s: MindiState, viewer: Int) = MindiNetStep(event, state(s, viewer))

    /** Sanity helper for tests: every card id a viewer can see in hands/hidden that isn't theirs. */
    fun leaks(s: MindiState, viewer: Int): List<MCard> {
        val out = ArrayList<MCard>()
        s.hands.forEachIndexed { seat, hand -> if (seat != viewer) out += hand.filter { !isPlaceholder(it) } }
        val h = s.hidden
        if (h != null && s.hiddenOwner != viewer && !isPlaceholder(h)) out += h
        return out
    }

}

/** Re-target a Bhabhi action at [seat] (the host never trusts the seat a guest claims). */
fun BhabhiAction.withSeat(seat: Int): BhabhiAction = when (this) {
    is BhabhiAction.Play -> copy(seat = seat)
}

/**
 * What a Bhabhi player at [viewer] may see: other hands become blank placeholders (same count, no rank/suit) and
 * the seed is cleared so the deal can't be reconstructed. Everything else in the state is public: cards that were
 * played, thrown away or picked up (`known`), and the suits each seat showed it lacks. Events only carry cards that
 * everyone saw on the table, so they pass through unchanged.
 */
object BhabhiRedact {
    const val PLACEHOLDER_BASE = 900_000

    fun isPlaceholder(c: BCard) = c.suit == -1

    fun state(s: BhabhiState, viewer: Int): BhabhiState = s.copy(
        seed = 0L,
        hands = s.hands.mapIndexed { seat, hand ->
            if (seat == viewer) hand else hand.mapIndexed { i, _ -> BCard(PLACEHOLDER_BASE + seat * 1000 + i, 0, -1) }
        },
    )

    fun step(event: BhabhiEvent, s: BhabhiState, viewer: Int) = BhabhiNetStep(event, state(s, viewer))

    /** Sanity helper for tests: every real card a viewer can see in another seat's hand. */
    fun leaks(s: BhabhiState, viewer: Int): List<BCard> {
        val out = ArrayList<BCard>()
        s.hands.forEachIndexed { seat, hand -> if (seat != viewer) out += hand.filter { !isPlaceholder(it) } }
        return out
    }
}

/** Re-target a Teen Patti action at [seat] (the host never trusts the seat a guest claims). */
fun TPAction.withSeat(seat: Int): TPAction = when (this) {
    is TPAction.See -> copy(seat = seat)
    is TPAction.Pack -> copy(seat = seat)
    is TPAction.Chaal -> copy(seat = seat)
    is TPAction.Show -> copy(seat = seat)
    is TPAction.AllIn -> copy(seat = seat)
    is TPAction.Sideshow -> copy(seat = seat)
    is TPAction.SideshowReply -> copy(seat = seat)
}

/**
 * What a Teen Patti player at [viewer] may see. Other hands are blank placeholders (3 each) until they are opened at a
 * show or an all-in (`state.shown`). In an accepted sideshow only its two players see each other's cards, so for that
 * one step the other player's hand is kept for them. The seed is cleared so no deal can be worked out.
 */
object TeenPattiRedact {
    const val PLACEHOLDER_BASE = 900_000

    fun isPlaceholder(c: TCard) = c.suit == -1

    fun state(s: TeenPattiState, viewer: Int, alsoSee: Int = -1): TeenPattiState = s.copy(
        seed = 0L,
        hands = s.hands.mapIndexed { seat, hand ->
            if (seat == viewer || seat == alsoSee || seat in s.shown) hand
            else hand.mapIndexed { i, _ -> TCard(PLACEHOLDER_BASE + seat * 1000 + i, 0, -1) }
        },
    )

    fun step(event: TPEvent, s: TeenPattiState, viewer: Int): TPNetStep {
        // a sideshow's two players compare cards privately
        val other = (event as? TPEvent.SideshowResult)?.takeIf { it.accepted && (it.from == viewer || it.to == viewer) }
            ?.let { if (it.from == viewer) it.to else it.from } ?: -1
        return TPNetStep(event, state(s, viewer, other))
    }

    /** Sanity helper for tests: real cards a viewer can see in another seat's hand that isn't open to them. */
    fun leaks(s: TeenPattiState, viewer: Int, allowed: Set<Int> = emptySet()): List<TCard> {
        val out = ArrayList<TCard>()
        s.hands.forEachIndexed { seat, hand -> if (seat != viewer && seat !in s.shown && seat !in allowed) out += hand.filter { !isPlaceholder(it) } }
        return out
    }
}

/** Re-target a Rummy action at [seat] (the host never trusts the seat a guest claims). */
fun RAction.withSeat(seat: Int): RAction = when (this) {
    is RAction.PickClosed -> copy(seat = seat)
    is RAction.PickOpen -> copy(seat = seat)
    is RAction.Discard -> copy(seat = seat)
    is RAction.Drop -> copy(seat = seat)
    is RAction.StartDeclare -> copy(seat = seat)
    is RAction.Declare -> copy(seat = seat)
    is RAction.Arrange -> copy(seat = seat)
}

/**
 * What a Rummy player at [viewer] may see: other hands and the closed pile become blank placeholders (counts kept)
 * until the hand is over, when everyone's cards are shown on the result screen. A card someone else draws blind is
 * hidden in the event too. The open pile, the cut joker and everything thrown are public. The seed is cleared.
 */
object RummyRedact {
    const val PLACEHOLDER_BASE = 900_000

    /** Placeholders have rank −1 (rank 0 is a printed joker). */
    fun isPlaceholder(c: RCard) = c.rank == -1
    private fun blank(id: Int) = RCard(id, -1, -1)

    fun state(s: RummyState, viewer: Int): RummyState {
        val open = s.phase is RPhase.HandOver
        val ph = s.phase
        return s.copy(
            seed = 0L,
            hands = s.hands.mapIndexed { seat, hand ->
                if (seat == viewer || open) hand else hand.mapIndexed { i, _ -> blank(PLACEHOLDER_BASE + seat * 1000 + i) }
            },
            closed = s.closed.mapIndexed { i, _ -> blank(PLACEHOLDER_BASE + 99_000 + i) },
            // the card in the finish slot lies face down until it is declared
            phase = if (ph is RPhase.Declaring && ph.seat != viewer) ph.copy(card = blank(PLACEHOLDER_BASE + 97_000)) else ph,
        )
    }

    fun event(e: REvent, viewer: Int): REvent = when (e) {
        is REvent.PickedClosed -> if (e.seat == viewer) e else e.copy(card = blank(PLACEHOLDER_BASE + 98_000))
        is REvent.DeclareStarted -> if (e.seat == viewer) e else e.copy(card = blank(PLACEHOLDER_BASE + 97_000))
        else -> e
    }

    fun step(event: REvent, s: RummyState, viewer: Int) = RummyNetStep(event(event, viewer), state(s, viewer))

    /** Sanity helper for tests: real cards a viewer can see in another hand or the closed pile before the hand ends. */
    fun leaks(s: RummyState, viewer: Int): List<RCard> {
        if (s.phase is RPhase.HandOver) return emptyList()
        val out = ArrayList<RCard>()
        s.hands.forEachIndexed { seat, hand -> if (seat != viewer) out += hand.filter { !isPlaceholder(it) } }
        out += s.closed.filter { !isPlaceholder(it) }
        (s.phase as? RPhase.Declaring)?.takeIf { it.seat != viewer && !isPlaceholder(it.card) }?.let { out += it.card }
        return out
    }
}

/** Re-target an action at [id] (the host never trusts the seat a guest claims). */
fun Action.withPlayer(id: Int): Action = when (this) {
    is Action.Play -> copy(playerId = id)
    is Action.Draw -> copy(playerId = id)
    is Action.Keep -> copy(playerId = id)
    is Action.ChooseColor -> copy(playerId = id)
    is Action.ChooseSwap -> copy(playerId = id)
    is Action.Challenge -> copy(playerId = id)
    is Action.CallMania -> copy(playerId = id)
    is Action.Catch -> copy(playerId = id)
    is Action.ArmShield -> copy(playerId = id)
    is Action.Peek -> copy(playerId = id)
    is Action.NextRound -> copy(playerId = id)
}

/**
 * Hides information a player at [viewer] must not see:
 * other hands and the draw pile lose their face (Flip mode keeps the publicly visible side),
 * your own cards lose their hidden side, the RNG state is cleared, and challenge legality is hidden.
 */
object Redact {
    val HIDDEN = Face(Suit.WILD, Kind.PRISM, -1)

    fun hidden(c: Card, s: GameState): Card = if (s.mode == GameMode.ECLIPSE) {
        if (s.side == Side.SUN) c.copy(sun = HIDDEN) else c.copy(moon = HIDDEN)
    } else c.copy(sun = HIDDEN)

    fun own(c: Card, s: GameState): Card = if (s.mode == GameMode.ECLIPSE) {
        if (s.side == Side.SUN) c.copy(moon = HIDDEN) else c.copy(sun = HIDDEN)
    } else c

    fun state(s: GameState, viewer: Int): GameState {
        val c = s.copyDeep()
        c.players.forEach { p ->
            val mapped = p.hand.map { if (p.id == viewer) own(it, s) else hidden(it, s) }
            p.hand.clear()
            p.hand.addAll(mapped)
        }
        c.drawPile = c.drawPile.map { hidden(it, s) }.toMutableList()
        c.rngState = 0L
        c.lastPlayIllegal = false
        val ph = c.phase
        if (ph is Phase.Challenge) c.phase = ph.copy(illegal = false)
        return c
    }

    fun event(e: GameEvent, s: GameState, viewer: Int): GameEvent = when (e) {
        is GameEvent.Dealt -> e.copy(order = e.order.map { (pid, card) -> pid to (if (pid == viewer) own(card, s) else hidden(card, s)) })
        is GameEvent.CardsDrawn -> if (e.playerId == viewer) e.copy(cards = e.cards.map { own(it, s) }) else e.copy(cards = e.cards.map { hidden(it, s) })
        is GameEvent.PeekRevealed -> if (e.playerId == viewer) e else e.copy(cards = emptyList())
        is GameEvent.StarterReturned -> e.copy(card = hidden(e.card, s))
        else -> e
    }
}

/** Room codes: IPv4 + port offset packed into 8 easy-to-read characters, e.g. "K7QD-2MXA". */
object RoomCode {
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    fun encode(ip: String, port: Int): String {
        val parts = ip.split(".").map { it.toInt() }
        require(parts.size == 4)
        var v = 0L
        for (b in parts) v = (v shl 8) or (b.toLong() and 0xFF)
        val off = (port - Net.PORT_BASE).coerceIn(0, Net.PORT_COUNT - 1).toLong()
        v = (v shl 4) or off // 36 bits
        val sb = StringBuilder()
        repeat(8) {
            sb.append(ALPHABET[(v and 31).toInt()])
            v = v shr 5
        }
        val s = sb.reverse().toString()
        return s.substring(0, 4) + "-" + s.substring(4)
    }

    fun decode(code: String): Pair<String, Int>? {
        val clean = code.uppercase().replace("-", "").replace(" ", "")
        if (clean.length != 8) return null
        var v = 0L
        for (ch in clean) {
            val i = ALPHABET.indexOf(ch)
            if (i < 0) return null
            v = (v shl 5) or i.toLong()
        }
        val off = (v and 15).toInt()
        val ipv = v shr 4
        if (ipv > 0xFFFFFFFFL) return null
        val ip = listOf(24, 16, 8, 0).joinToString(".") { ((ipv shr it) and 0xFF).toString() }
        return ip to (Net.PORT_BASE + off)
    }
}
