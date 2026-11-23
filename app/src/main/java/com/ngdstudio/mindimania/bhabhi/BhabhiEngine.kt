package com.ngdstudio.mindimania.bhabhi

import com.ngdstudio.mindimania.engine.Rng
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Bhabhi (also called Thulla or Get Away), a traditional Indian card game. Pure Kotlin, no Android.
 *
 * 3–8 players share one 52-card deck (Ace high), dealt out completely. The A♠ holder opens the first trick with it.
 * Play goes to the next seat (seat + 1) and you must follow the suit led if you can.
 * Nothing stops you from playing another suit while you still hold the led suit, but that is a "wrong thulla":
 * it does not cut the trick (the card is just thrown in and play goes on) and you get [PENALTY_CARDS] cards from the
 * other players as a fine (one at a time, going round from your left; nobody gives away their last card).
 *  - Everyone follows: the trick is thrown away and the highest card of the led suit leads next.
 *  - Someone can't follow: they play any other card (a "thulla"). The trick stops at once and whoever played the
 *    highest card of the led suit picks up every card of that trick, then leads next.
 * Emptying your hand makes you safe. The last player still holding cards is the Bhabhi (the loser).
 * House rule [BhabhiSetup.safeFirstTrick]: no thulla on the opening spade trick (off-suit cards are just thrown away).
 * If everyone left runs out on the same trick, whoever played its highest card is the Bhabhi (they would have led).
 */

/** rank 2..14 (11 J, 12 Q, 13 K, 14 A); suit 0 spades, 1 hearts, 2 diamonds, 3 clubs. */
@Serializable
data class BCard(val id: Int, val rank: Int, val suit: Int) {
    /** Rank for PlayingCardArt (1 = Ace). */
    val artRank: Int get() = if (rank == 14) 1 else rank
    val isAceOfSpades: Boolean get() = rank == 14 && suit == 0
}

@Serializable
data class BPlay(val seat: Int, val card: BCard)

@Serializable
sealed interface BhabhiPhase {
    @Serializable @SerialName("play")
    data object Play : BhabhiPhase

    @Serializable @SerialName("over")
    data class GameOver(val loser: Int) : BhabhiPhase
}

@Serializable
data class BhabhiState(
    val setup: BhabhiSetup,
    val seed: Long,
    val hands: List<List<BCard>>,
    val dealer: Int,
    val leader: Int,
    /** Seat to act, or -1 while a finished trick is being resolved and when the game is over. */
    val turn: Int,
    /** Seats that play in the current trick, in order from the leader (everyone holding cards when it began). */
    val order: List<Int>,
    val trick: List<BPlay> = emptyList(),
    /** 0 = the opening trick (led with the A♠). */
    val trickNo: Int = 0,
    /** Seats that emptied their hand, in finishing order (first = best). */
    val out: List<Int> = emptyList(),
    /** Suits each seat has shown it's out of (public information). */
    val voids: List<Set<Int>> = emptyList(),
    /** Card ids everyone saw go into a seat's hand on a pickup and that are still there (public information). */
    val known: List<List<Int>> = emptyList(),
    /** Cards thrown away with completed tricks (public: everyone saw them played). */
    val discarded: List<BCard> = emptyList(),
    val pickups: List<Int> = emptyList(),
    val thullas: List<Int> = emptyList(),
    /** The previous trick, and who picked it up (-1 = it was thrown away). */
    val lastTrick: List<BPlay> = emptyList(),
    val lastTaker: Int = -1,
    val phase: BhabhiPhase = BhabhiPhase.Play,
) {
    val players: Int get() = setup.players
    val leadSuit: Int? get() = trick.firstOrNull()?.card?.suit
    val isOver: Boolean get() = phase is BhabhiPhase.GameOver
    val loser: Int get() = (phase as? BhabhiPhase.GameOver)?.loser ?: -1
    fun next(seat: Int) = (seat + 1) % players

    /** Seats still holding cards. */
    val active: List<Int> get() = (0 until players).filter { hands[it].isNotEmpty() }

    /** Finishing place of [seat] (1 = first out), or null while it is still playing. The Bhabhi is last. */
    fun placeOf(seat: Int): Int? {
        val i = out.indexOf(seat)
        if (i >= 0) return i + 1
        return if (seat == loser) players else null
    }

    /** The current highest card of the led suit (null when the trick is empty). */
    fun highPlay(): BPlay? = BhabhiEngine.highest(trick)
}

@Serializable
sealed interface BhabhiEvent {
    @Serializable @SerialName("Dealt")
    data class Dealt(val dealer: Int, val leader: Int) : BhabhiEvent

    /** [thulla] = played off-suit and cut the trick. */
    @Serializable @SerialName("Played")
    data class Played(val seat: Int, val card: BCard, val thulla: Boolean) : BhabhiEvent

    /** Everyone followed suit: the trick is thrown away; [high] played its highest card. */
    @Serializable @SerialName("Discarded")
    data class Discarded(val high: Int, val cards: List<BCard>) : BhabhiEvent

    /**
     * [seat] played a wrong thulla (still held the led suit) and is fined: each [given] play is a card handed over by
     * the seat in it. Everyone sees the cards change hands.
     */
    @Serializable @SerialName("Penalty")
    data class Penalty(val seat: Int, val given: List<BPlay>) : BhabhiEvent

    /** [bySeat]'s thulla made [taker] pick up the whole trick. */
    @Serializable @SerialName("PickedUp")
    data class PickedUp(val taker: Int, val bySeat: Int, val cards: List<BCard>) : BhabhiEvent

    /** [seat] emptied its hand and is safe, finishing in [place]. */
    @Serializable @SerialName("Out")
    data class Out(val seat: Int, val place: Int) : BhabhiEvent

    @Serializable @SerialName("GameOver")
    data class GameOver(val loser: Int) : BhabhiEvent

    /** UI only: a saved game was loaded; redraw the table as it is. */
    @Serializable @SerialName("Restored")
    data class Restored(val trickNo: Int) : BhabhiEvent
}

/** An event plus the state right after it. */
data class BStep(val event: BhabhiEvent, val state: BhabhiState)

@Serializable
sealed interface BhabhiAction {
    val seat: Int

    @Serializable @SerialName("Play")
    data class Play(override val seat: Int, val cardId: Int) : BhabhiAction
}

object BhabhiEngine {
    const val ACE = 14
    const val MIN_PLAYERS = 3
    const val MAX_PLAYERS = 8
    /** Cards a wrong thulla costs. */
    const val PENALTY_CARDS = 5

    /** The 52-card deck: ids 0..51, suits 0..3, ranks 2..14. */
    fun deck(): List<BCard> = buildList {
        var id = 0
        for (s in 0..3) for (r in 2..ACE) add(BCard(id++, r, s))
    }

    fun normalize(setup: BhabhiSetup): BhabhiSetup =
        setup.copy(players = setup.players.coerceIn(MIN_PLAYERS, MAX_PLAYERS), turnTimer = setup.turnTimer.coerceIn(0, 60))

    /** Spades, hearts, clubs, diamonds (colours alternate); high to low inside a suit. */
    private val suitOrder = intArrayOf(0, 1, 3, 2)
    fun sortHand(h: List<BCard>): List<BCard> = h.sortedWith(compareBy<BCard>({ suitOrder.indexOf(it.suit) }, { -it.rank }))

    fun newGame(setup0: BhabhiSetup, seed: Long): List<BStep> {
        val setup = normalize(setup0)
        val n = setup.players
        val rng = Rng(seed)
        val cards = deck().toMutableList()
        rng.shuffle(cards)
        val dealer = rng.nextInt(n)
        val hands = List(n) { ArrayList<BCard>() }
        // one card at a time, starting left of the dealer, until the deck is gone
        cards.forEachIndexed { i, c -> hands[(dealer + 1 + i) % n] += c }
        val leader = hands.indexOfFirst { h -> h.any { it.isAceOfSpades } }
        val sorted = hands.map { sortHand(it) }
        val s = BhabhiState(
            setup = setup, seed = seed, hands = sorted, dealer = dealer, leader = leader, turn = leader,
            order = (0 until n).map { (leader + it) % n },
            voids = List(n) { emptySet() }, known = List(n) { emptyList() },
            pickups = List(n) { 0 }, thullas = List(n) { 0 },
        )
        return listOf(BStep(BhabhiEvent.Dealt(dealer, leader), s))
    }

    /** The play holding the highest card of the led suit. */
    fun highest(trick: List<BPlay>): BPlay? {
        val lead = trick.firstOrNull()?.card?.suit ?: return null
        return trick.filter { it.card.suit == lead }.maxByOrNull { it.card.rank }
    }

    /** The correct moves: the A♠ to open, otherwise follow suit if you can (bots only ever play these). */
    fun legalCards(s: BhabhiState, seat: Int): List<BCard> {
        if (s.isOver || seat != s.turn) return emptyList()
        val hand = s.hands[seat]
        if (s.trick.isEmpty()) {
            if (s.trickNo == 0) return hand.filter { it.isAceOfSpades }.ifEmpty { hand }
            return hand
        }
        val lead = s.leadSuit!!
        return hand.filter { it.suit == lead }.ifEmpty { hand }
    }

    /**
     * Cards a person may put down: anything in hand (a card of another suit while holding the led suit is a wrong
     * thulla and gets fined), except that the opening trick must be led with the A♠.
     */
    fun playableCards(s: BhabhiState, seat: Int): List<BCard> {
        if (s.isOver || seat != s.turn) return emptyList()
        val hand = s.hands[seat]
        if (s.trick.isEmpty() && s.trickNo == 0) return hand.filter { it.isAceOfSpades }.ifEmpty { hand }
        return hand
    }

    /** True when playing [card] now would be a wrong thulla ([seat] still holds the led suit). */
    fun isWrongThulla(s: BhabhiState, seat: Int, card: BCard): Boolean {
        val lead = s.leadSuit ?: return false
        return card.suit != lead && s.hands[seat].any { it.suit == lead }
    }

    /** True when [seat] can't follow the current trick, so any card it plays is a thulla (or a safe discard). */
    fun mustThulla(s: BhabhiState, seat: Int): Boolean {
        val lead = s.leadSuit ?: return false
        return s.hands[seat].none { it.suit == lead }
    }

    /** True when an off-suit card in the current trick cuts it (false on the protected opening trick). */
    fun thullaAllowed(s: BhabhiState): Boolean = !(s.trickNo == 0 && s.setup.safeFirstTrick)

    /** null if [a] is legal, otherwise a short reason. */
    fun validate(s: BhabhiState, a: BhabhiAction): String? {
        if (s.isOver) return "The game is over"
        when (a) {
            is BhabhiAction.Play -> {
                if (a.seat != s.turn) return "Not your turn"
                val card = s.hands[a.seat].firstOrNull { it.id == a.cardId } ?: return "That card isn't in your hand"
                if (card !in playableCards(s, a.seat)) return "Open with the A♠"
            }
        }
        return null
    }

    fun apply(s0: BhabhiState, a: BhabhiAction): List<BStep> {
        val err = validate(s0, a)
        require(err == null) { err ?: "" }
        a as BhabhiAction.Play
        val seat = a.seat
        val steps = ArrayList<BStep>()
        val hands = s0.hands.map { it.toMutableList() }
        val card = hands[seat].first { it.id == a.cardId }
        hands[seat].remove(card)
        val known = s0.known.map { it.toMutableList() }
        known[seat].remove(card.id)
        val lead = s0.leadSuit
        val offSuit = lead != null && card.suit != lead
        val wrong = isWrongThulla(s0, seat, card)
        val voids = s0.voids.map { it.toMutableSet() }
        if (offSuit && !wrong) voids[seat] += lead!!
        val isThulla = offSuit && !wrong && thullaAllowed(s0)
        val thullas = if (isThulla) s0.thullas.mapIndexed { i, v -> if (i == seat) v + 1 else v } else s0.thullas
        val trick = s0.trick + BPlay(seat, card)
        var s = s0.copy(
            hands = hands.map { it.toList() }, trick = trick, voids = voids.map { it.toSet() },
            known = known.map { it.toList() }, thullas = thullas,
        )
        val idx = s.order.indexOf(seat)
        val goesOn = !isThulla && idx < s.order.lastIndex
        s = s.copy(turn = if (goesOn) s.order[idx + 1] else -1)
        steps += BStep(BhabhiEvent.Played(seat, card, isThulla), s)
        if (wrong) {
            val (fined, given) = fine(s, seat)
            s = fined
            steps += BStep(BhabhiEvent.Penalty(seat, given), s)
        }
        if (goesOn) return steps

        // ---- the trick is over
        val high = highest(trick)!!
        val cards = trick.map { it.card }
        if (isThulla) {
            val taker = high.seat
            val h2 = s.hands.toMutableList()
            h2[taker] = sortHand(h2[taker] + cards)
            val k2 = s.known.toMutableList()
            k2[taker] = k2[taker] + cards.map { it.id }
            s = s.copy(
                hands = h2, known = k2, trick = emptyList(), lastTrick = trick, lastTaker = taker,
                pickups = s.pickups.mapIndexed { i, v -> if (i == taker) v + 1 else v },
            )
            steps += BStep(BhabhiEvent.PickedUp(taker, seat, cards), s)
        } else {
            s = s.copy(trick = emptyList(), discarded = s.discarded + cards, lastTrick = trick, lastTaker = -1)
            steps += BStep(BhabhiEvent.Discarded(high.seat, cards), s)
        }

        // everyone in this trick who now holds nothing got away, in the order they played
        val newlyOut = trick.map { it.seat }.filter { s.hands[it].isEmpty() && it !in s.out }
        val active = s.active
        if (active.isEmpty()) {
            // everyone left ran out on this same trick: the highest card would have had to lead, so that's the Bhabhi
            val loser = high.seat
            for (o in newlyOut) if (o != loser) {
                s = s.copy(out = s.out + o)
                steps += BStep(BhabhiEvent.Out(o, s.out.size), s)
            }
            s = s.copy(phase = BhabhiPhase.GameOver(loser))
            steps += BStep(BhabhiEvent.GameOver(loser), s)
            return steps
        }
        for (o in newlyOut) {
            s = s.copy(out = s.out + o)
            steps += BStep(BhabhiEvent.Out(o, s.out.size), s)
        }
        if (active.size == 1) {
            s = s.copy(phase = BhabhiPhase.GameOver(active[0]))
            steps += BStep(BhabhiEvent.GameOver(active[0]), s)
            return steps
        }
        val nextLeader = when {
            s.hands[high.seat].isNotEmpty() -> high.seat // the taker after a thulla, or the highest card
            else -> nextActive(s, high.seat)
        }
        s = s.copy(trickNo = s.trickNo + 1, leader = nextLeader, turn = nextLeader, order = orderFrom(s, nextLeader))
        // the last step carries the final state (who leads next), like the other engines
        steps[steps.lastIndex] = steps.last().copy(state = s)
        return steps
    }

    /**
     * Wrong-thulla fine: going round from [seat]'s left, each other player still holding at least 2 cards hands over
     * one random card, until [PENALTY_CARDS] are given (fewer when nobody else can spare one). The cards are public.
     */
    fun fine(s: BhabhiState, seat: Int): Pair<BhabhiState, List<BPlay>> {
        val rng = Rng(s.seed xor (s.trickNo * 1_000_003L + s.trick.size * 7919L + seat))
        val hands = s.hands.map { it.toMutableList() }
        val known = s.known.map { it.toMutableList() }
        val given = ArrayList<BPlay>()
        var from = seat
        var idle = 0
        while (given.size < PENALTY_CARDS && idle < s.players) {
            from = s.next(from)
            if (from == seat || hands[from].size < 2) {
                idle++
                continue
            }
            idle = 0
            val c = hands[from].removeAt(rng.nextInt(hands[from].size))
            known[from].remove(c.id)
            given += BPlay(from, c)
        }
        val cards = given.map { it.card }
        val mine = sortHand(hands[seat] + cards)
        hands[seat].clear()
        hands[seat].addAll(mine)
        known[seat] += cards.map { it.id }
        // the fined seat now visibly holds these suits again
        val voids = s.voids.mapIndexed { i, v -> if (i == seat) v - cards.map { it.suit }.toSet() else v }
        val out = s.copy(hands = hands.map { it.toList() }, known = known.map { it.toList() }, voids = voids)
        return out to given
    }

    /** First seat after [from] (clockwise) that still holds cards. */
    fun nextActive(s: BhabhiState, from: Int): Int {
        var x = s.next(from)
        repeat(s.players) {
            if (s.hands[x].isNotEmpty()) return x
            x = s.next(x)
        }
        return from
    }

    /** Seats holding cards, in playing order starting from [leader]. */
    fun orderFrom(s: BhabhiState, leader: Int): List<Int> =
        (0 until s.players).map { (leader + it) % s.players }.filter { s.hands[it].isNotEmpty() }

    /** Cards [seat] can't see: everyone else's hands (not thrown away, not on the table). */
    fun unseen(s: BhabhiState, seat: Int): List<BCard> {
        val visible = HashSet<Int>()
        s.hands[seat].forEach { visible += it.id }
        s.discarded.forEach { visible += it.id }
        s.trick.forEach { visible += it.card.id }
        return deck().filter { it.id !in visible }
    }

    fun suitName(s: Int) = when (s) {
        0 -> "Spades"
        1 -> "Hearts"
        2 -> "Diamonds"
        else -> "Clubs"
    }

    fun suitSymbol(s: Int) = when (s) {
        0 -> "♠"
        1 -> "♥"
        2 -> "♦"
        else -> "♣"
    }
}
