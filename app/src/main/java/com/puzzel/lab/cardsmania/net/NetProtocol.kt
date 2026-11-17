package com.puzzel.lab.cardsmania.net

import com.puzzel.lab.cardsmania.engine.Action
import com.puzzel.lab.cardsmania.engine.GameEvent
import com.puzzel.lab.cardsmania.engine.model.Card
import com.puzzel.lab.cardsmania.engine.model.Difficulty
import com.puzzel.lab.cardsmania.engine.model.Face
import com.puzzel.lab.cardsmania.engine.model.GameConfig
import com.puzzel.lab.cardsmania.engine.model.GameMode
import com.puzzel.lab.cardsmania.engine.model.GameState
import com.puzzel.lab.cardsmania.engine.model.HouseRules
import com.puzzel.lab.cardsmania.engine.model.Kind
import com.puzzel.lab.cardsmania.engine.model.Phase
import com.puzzel.lab.cardsmania.engine.model.Side
import com.puzzel.lab.cardsmania.engine.model.Suit
import com.puzzel.lab.cardsmania.mindi.MCard
import com.puzzel.lab.cardsmania.mindi.MindiAction
import com.puzzel.lab.cardsmania.mindi.MindiEvent
import com.puzzel.lab.cardsmania.mindi.MindiSeat
import com.puzzel.lab.cardsmania.mindi.MindiSetup
import com.puzzel.lab.cardsmania.mindi.MindiState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object Net {
    /** v2: Mindi rooms. */
    const val PROTOCOL = 2
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
enum class RoomGame { MANIA, MINDI }

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
    /** MANIA uses [mode]/[rules]/...; MINDI uses [mindi] (its seat count is always [seats]). */
    val game: RoomGame = RoomGame.MANIA,
    val mindi: MindiSetup = MindiSetup(),
) {
    val isMindi: Boolean get() = game == RoomGame.MINDI
    /** Most seats this game allows. */
    val maxSeats: Int get() = if (isMindi) 8 else 6
    /** Seats still waiting for a player (filled by bots if nobody joins). */
    val openSeats: Int get() = (seats - 1 - bots - guests.size).coerceAtLeast(0)
    val maxBots: Int get() = (seats - 1 - guests.size).coerceAtLeast(0)
    val minSeats: Int get() = maxOf(2, 1 + guests.size + bots)
    val players: Int get() = 1 + guests.size
}

@Serializable
data class NetStep(val event: GameEvent, val state: GameState)

@Serializable
data class MindiNetStep(val event: MindiEvent, val state: MindiState)

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
