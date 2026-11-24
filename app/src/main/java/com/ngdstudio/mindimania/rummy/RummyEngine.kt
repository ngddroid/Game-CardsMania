package com.ngdstudio.mindimania.rummy

import com.ngdstudio.mindimania.engine.Rng
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 13-card Points Rummy, the common Indian game. Pure Kotlin, no Android.
 *
 * An endless table for table chips only (chips never leave it and are never bought). Each hand:
 *  - two decks + 2 printed jokers (106 cards); 13 cards each; one card is cut face up as the wild joker (its rank is wild; a cut
 *    printed joker makes Aces wild); one card starts the open pile; the rest is the closed pile.
 *  - On your turn pick the top of the closed or open pile, then discard one card (or declare with it).
 *  - Drop instead of picking: 20 points before your first pick in the hand, 40 later.
 *  - Declare: put a card in the finish slot ([RAction.StartDeclare]), arrange your 13 into groups ([RummyMelds]) and
 *    show them ([RAction.Declare]; bots may do both at once). A valid declaration wins the hand; everyone still in then arranges their cards and loses the points of their arrangement. A wrong
 *    declaration costs [WRONG_SHOW] points and that player is out of the hand; play goes on.
 *  - If everyone else drops (or shows wrong), the last player in wins.
 * Each loser pays points × [POINT_VALUE] chips to the winner. Anyone below a full loss gets a fresh stack before the
 * next deal.
 */

@Serializable
sealed interface RPhase {
    /** [RummyState.turn] picks from a pile (or drops). */
    @Serializable @SerialName("pick")
    data object Pick : RPhase

    /** [RummyState.turn] holds 14 cards: discard one or declare. */
    @Serializable @SerialName("discard")
    data object Discard : RPhase

    /** [seat] put [card] in the finish slot and is arranging its 13 cards before showing them. */
    @Serializable @SerialName("declaring")
    data class Declaring(val seat: Int, val card: RCard) : RPhase

    /** [declarer] declared validly; [pending] seats still have to arrange their cards. */
    @Serializable @SerialName("show")
    data class Showdown(val declarer: Int, val pending: List<Int>) : RPhase

    @Serializable @SerialName("over")
    data class HandOver(val winner: Int) : RPhase
}

/** How a seat finished a hand. */
@Serializable
enum class Finish { NONE, WON, DROP, WRONG_SHOW, ARRANGED }

@Serializable
data class RummyState(
    val setup: RummySetup,
    val seed: Long,
    val handNo: Int,
    val dealer: Int,
    val chips: List<Int>,
    val hands: List<List<RCard>>,
    val closed: List<RCard>,
    val open: List<RCard>,
    /** The card cut face up to name the wild joker. */
    val cut: RCard,
    /** Every card of this rank is a joker (1 = Aces when a printed joker was cut). */
    val wild: Int,
    val turn: Int,
    val phase: RPhase,
    /** Seats dealt into this hand. */
    val inHand: List<Boolean>,
    /** Seats out of this hand (dropped or wrong show) or done. */
    val finish: List<Finish>,
    /** Points each seat lost this hand (when known). */
    val points: List<Int>,
    /** The final arrangement of each seat, for the result screen (card ids). */
    val shown: List<List<List<Int>>>,
    /** Picks each seat made this hand (0 = a drop costs 20). */
    val picks: List<Int>,
    /** The card each seat just picked from the open pile (bots won't throw it straight back). */
    val lastPickedOpen: List<Int>,
    /** Chips each seat won (+) or lost (−) in the last hand. */
    val delta: List<Int> = emptyList(),
    val handsWon: List<Int>,
) {
    val players: Int get() = setup.players
    val handOver: Boolean get() = phase is RPhase.HandOver
    /** Seats still playing this hand. */
    val active: List<Int> get() = (0 until players).filter { inHand[it] && finish[it] == Finish.NONE }
    val openTop: RCard? get() = open.lastOrNull()
}

@Serializable
sealed interface REvent {
    @Serializable @SerialName("Dealt")
    data class Dealt(val handNo: Int, val dealer: Int, val cut: RCard, val wild: Int) : REvent

    @Serializable @SerialName("PickClosed")
    data class PickedClosed(val seat: Int, val card: RCard) : REvent

    @Serializable @SerialName("PickOpen")
    data class PickedOpen(val seat: Int, val card: RCard) : REvent

    @Serializable @SerialName("Discard")
    data class Discarded(val seat: Int, val card: RCard) : REvent

    @Serializable @SerialName("Drop")
    data class Dropped(val seat: Int, val points: Int) : REvent

    @Serializable @SerialName("Wrong")
    data class WrongShow(val seat: Int, val card: RCard, val points: Int) : REvent

    /** [seat] put [card] in the finish slot and is arranging before declaring. */
    @Serializable @SerialName("DeclareStarted")
    data class DeclareStarted(val seat: Int, val card: RCard) : REvent

    @Serializable @SerialName("Declared")
    data class Declared(val seat: Int, val card: RCard) : REvent

    @Serializable @SerialName("Arranged")
    data class Arranged(val seat: Int, val points: Int) : REvent

    @Serializable @SerialName("Reshuffled")
    data class Reshuffled(val cards: Int) : REvent

    /** [delta] = chips each seat won or lost. */
    @Serializable @SerialName("Won")
    data class HandWon(val winner: Int, val delta: List<Int>) : REvent

    @Serializable @SerialName("Refilled")
    data class Refilled(val seats: List<Int>, val chips: Int) : REvent

    @Serializable @SerialName("Restored")
    data class Restored(val handNo: Int) : REvent
}

data class RStep(val event: REvent, val state: RummyState)

@Serializable
sealed interface RAction {
    val seat: Int

    @Serializable @SerialName("PickClosed")
    data class PickClosed(override val seat: Int) : RAction

    @Serializable @SerialName("PickOpen")
    data class PickOpen(override val seat: Int) : RAction

    @Serializable @SerialName("Discard")
    data class Discard(override val seat: Int, val cardId: Int) : RAction

    @Serializable @SerialName("Drop")
    data class Drop(override val seat: Int) : RAction

    /** Put [cardId] in the finish slot; then arrange and [Declare]. */
    @Serializable @SerialName("StartDeclare")
    data class StartDeclare(override val seat: Int, val cardId: Int) : RAction

    /**
     * Show [groups] (the 13 cards as groups of card ids) and finish with [cardId]: the card already in the finish slot,
     * or (straight from the discard step) a card from the hand.
     */
    @Serializable @SerialName("Declare")
    data class Declare(override val seat: Int, val cardId: Int, val groups: List<List<Int>>) : RAction

    /** After someone declared: how [seat] lays out its 13 cards. */
    @Serializable @SerialName("Arrange")
    data class Arrange(override val seat: Int, val groups: List<List<Int>>) : RAction
}

object RummyEngine {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 6
    const val HAND_SIZE = 13
    const val START_CHIPS = 4000
    /** Chips per point lost. */
    const val POINT_VALUE = 5
    const val FIRST_DROP = 20
    const val MIDDLE_DROP = 40
    const val WRONG_SHOW = 80

    /** Two decks (ids 0..103: deck × 52 + suit × 13 + rank − 1) and two printed jokers (104, 105). */
    fun deck(): List<RCard> = buildList {
        for (d in 0..1) for (s in 0..3) for (r in 1..13) add(RCard(d * 52 + s * 13 + r - 1, r, s))
        add(RCard(104, 0, -1))
        add(RCard(105, 0, -1))
    }

    fun normalize(setup: RummySetup) = setup.copy(players = setup.players.coerceIn(MIN_PLAYERS, MAX_PLAYERS), turnTimer = setup.turnTimer.coerceIn(0, 90))

    fun newGame(setup0: RummySetup, seed: Long): List<RStep> {
        val setup = normalize(setup0)
        val n = setup.players
        val s0 = RummyState(
            setup = setup, seed = seed, handNo = 0, dealer = Rng(seed).nextInt(n),
            chips = List(n) { START_CHIPS }, hands = List(n) { emptyList() }, closed = emptyList(), open = emptyList(),
            cut = RCard(-1, 1, 0), wild = 1, turn = -1, phase = RPhase.HandOver(-1), inHand = List(n) { false },
            finish = List(n) { Finish.NONE }, points = List(n) { 0 }, shown = List(n) { emptyList() },
            picks = List(n) { 0 }, lastPickedOpen = List(n) { -1 }, handsWon = List(n) { 0 },
        )
        return nextHand(s0)
    }

    /** Deals the next hand (after a hand is over). Broke players get a fresh stack first. */
    fun nextHand(prev: RummyState): List<RStep> {
        require(prev.phase is RPhase.HandOver) { "The hand is still being played" }
        val steps = ArrayList<RStep>()
        var s0 = prev
        val n = s0.players
        val broke = (0 until n).filter { s0.chips[it] < WRONG_SHOW * POINT_VALUE }
        if (broke.isNotEmpty() && s0.handNo > 0) {
            s0 = s0.copy(chips = s0.chips.mapIndexed { i, c -> if (i in broke) START_CHIPS else c })
            steps += RStep(REvent.Refilled(broke, START_CHIPS), s0)
        }
        val handNo = s0.handNo + 1
        val rng = Rng(s0.seed xor (handNo * -0x61c8864680b583ebL))
        val cards = deck().toMutableList()
        rng.shuffle(cards)
        val dealer = if (s0.handNo == 0) s0.dealer else (s0.dealer + 1) % n
        var k = 0
        val hands = List(n) { cards.subList(k, k + HAND_SIZE).toList().also { k += HAND_SIZE } }
        val cut = cards[k++]
        val wild = if (cut.isPrinted) 1 else cut.rank
        val open = listOf(cards[k++])
        val closed = cards.subList(k, cards.size).toList()
        val s = s0.copy(
            handNo = handNo, dealer = dealer, hands = hands, closed = closed, open = open, cut = cut, wild = wild,
            turn = (dealer + 1) % n, phase = RPhase.Pick, inHand = List(n) { true }, finish = List(n) { Finish.NONE },
            points = List(n) { 0 }, shown = List(n) { emptyList() }, picks = List(n) { 0 }, lastPickedOpen = List(n) { -1 },
            delta = List(n) { 0 },
        )
        steps += RStep(REvent.Dealt(handNo, dealer, cut, wild), s)
        return steps
    }

    /** null if [a] is legal, otherwise a short reason. */
    fun validate(s: RummyState, a: RAction): String? {
        val ph = s.phase
        if (ph is RPhase.HandOver) return "The hand is over"
        if (ph is RPhase.Showdown) {
            if (a !is RAction.Arrange) return "Arrange your cards"
            if (a.seat !in ph.pending) return "Nothing to arrange"
            return checkGroups(s.hands[a.seat], a.groups)
        }
        if (ph is RPhase.Declaring) {
            if (a !is RAction.Declare) return "Arrange your cards and declare"
            if (a.seat != ph.seat) return "Not your turn"
            if (a.cardId != ph.card.id) return "That isn't the card in the finish slot"
            return checkGroups(s.hands[a.seat], a.groups)
        }
        if (a is RAction.Arrange) return "Nobody has declared"
        if (a.seat != s.turn) return "Not your turn"
        return when (a) {
            is RAction.PickClosed, is RAction.PickOpen -> if (ph != RPhase.Pick) "You already picked" else if (a is RAction.PickOpen && s.open.isEmpty()) "The open pile is empty" else null
            is RAction.Drop -> if (ph != RPhase.Pick) "You can only drop before picking" else null
            is RAction.Discard -> if (ph != RPhase.Discard) "Pick a card first" else if (s.hands[a.seat].none { it.id == a.cardId }) "Not in your hand" else null
            is RAction.StartDeclare -> when {
                ph != RPhase.Discard -> "Pick a card first"
                s.hands[a.seat].none { it.id == a.cardId } -> "Not in your hand"
                else -> null
            }
            is RAction.Declare -> when {
                ph != RPhase.Discard -> "Pick a card first"
                s.hands[a.seat].none { it.id == a.cardId } -> "Not in your hand"
                else -> checkGroups(s.hands[a.seat].filter { it.id != a.cardId }, a.groups)
            }
            is RAction.Arrange -> "Nobody has declared"
        }
    }

    /** null if [groups] uses every card of [hand] exactly once. */
    private fun checkGroups(hand: List<RCard>, groups: List<List<Int>>): String? {
        val ids = groups.flatten()
        if (ids.size != hand.size || ids.toSet() != hand.map { it.id }.toSet()) return "Groups must use every card once"
        return null
    }

    fun groupsOf(hand: List<RCard>, groups: List<List<Int>>): List<List<RCard>> {
        val byId = hand.associateBy { it.id }
        return groups.map { g -> g.mapNotNull { byId[it] } }.filter { it.isNotEmpty() }
    }

    fun apply(s0: RummyState, a: RAction): List<RStep> {
        val err = validate(s0, a)
        require(err == null) { err ?: "" }
        val steps = ArrayList<RStep>()
        var s = s0
        val seat = a.seat
        when (a) {
            is RAction.PickClosed -> {
                if (s.closed.isEmpty()) {
                    // turn the open pile (all but its top card) into a new closed pile
                    val keep = s.open.last()
                    val pile = s.open.dropLast(1).toMutableList()
                    Rng(s.seed xor (s.handNo * 7919L + pile.size)).shuffle(pile)
                    s = s.copy(closed = pile, open = listOf(keep))
                    steps += RStep(REvent.Reshuffled(pile.size), s)
                }
                val c = s.closed.last()
                s = s.copy(closed = s.closed.dropLast(1), hands = s.hands.set(seat, s.hands[seat] + c), phase = RPhase.Discard,
                    picks = s.picks.set(seat, s.picks[seat] + 1), lastPickedOpen = s.lastPickedOpen.set(seat, -1))
                steps += RStep(REvent.PickedClosed(seat, c), s)
            }
            is RAction.PickOpen -> {
                val c = s.open.last()
                s = s.copy(open = s.open.dropLast(1), hands = s.hands.set(seat, s.hands[seat] + c), phase = RPhase.Discard,
                    picks = s.picks.set(seat, s.picks[seat] + 1), lastPickedOpen = s.lastPickedOpen.set(seat, c.id))
                steps += RStep(REvent.PickedOpen(seat, c), s)
            }
            is RAction.Discard -> {
                val c = s.hands[seat].first { it.id == a.cardId }
                s = s.copy(hands = s.hands.set(seat, s.hands[seat] - c), open = s.open + c, phase = RPhase.Pick)
                s = s.copy(turn = nextActive(s, seat))
                steps += RStep(REvent.Discarded(seat, c), s)
            }
            is RAction.Drop -> {
                val pts = if (s.picks[seat] == 0) FIRST_DROP else MIDDLE_DROP
                s = s.copy(finish = s.finish.set(seat, Finish.DROP), points = s.points.set(seat, pts))
                s = s.copy(turn = nextActive(s, seat))
                steps += RStep(REvent.Dropped(seat, pts), s)
                lastStanding(s, steps)
            }
            is RAction.StartDeclare -> {
                val c = s.hands[seat].first { it.id == a.cardId }
                s = s.copy(hands = s.hands.set(seat, s.hands[seat] - c), phase = RPhase.Declaring(seat, c))
                steps += RStep(REvent.DeclareStarted(seat, c), s)
            }
            is RAction.Declare -> {
                val ph0 = s.phase
                // the finish card is either already in the slot or still in the hand
                val c = if (ph0 is RPhase.Declaring) ph0.card else s.hands[seat].first { it.id == a.cardId }
                val rest = if (ph0 is RPhase.Declaring) s.hands[seat] else s.hands[seat] - c
                val ev = RummyMelds.evaluate(groupsOf(rest, a.groups), s.wild)
                if (!ev.valid) {
                    // a wrong show: 80 points, out of the hand; the card goes to the open pile and play goes on
                    s = s.copy(hands = s.hands.set(seat, rest), open = s.open + c, finish = s.finish.set(seat, Finish.WRONG_SHOW),
                        points = s.points.set(seat, WRONG_SHOW), shown = s.shown.set(seat, a.groups), phase = RPhase.Pick)
                    s = s.copy(turn = nextActive(s, seat))
                    steps += RStep(REvent.WrongShow(seat, c, WRONG_SHOW), s)
                    lastStanding(s, steps)
                } else {
                    val pending = s.active.filter { it != seat }
                    s = s.copy(hands = s.hands.set(seat, rest), finish = s.finish.set(seat, Finish.WON), points = s.points.set(seat, 0),
                        shown = s.shown.set(seat, a.groups), phase = RPhase.Showdown(seat, pending), turn = -1)
                    steps += RStep(REvent.Declared(seat, c), s)
                    if (pending.isEmpty()) settle(s, seat, steps)
                }
            }
            is RAction.Arrange -> {
                val ph = s.phase as RPhase.Showdown
                val pts = RummyMelds.evaluate(groupsOf(s.hands[seat], a.groups), s.wild).points
                s = s.copy(finish = s.finish.set(seat, Finish.ARRANGED), points = s.points.set(seat, pts), shown = s.shown.set(seat, a.groups),
                    phase = ph.copy(pending = ph.pending - seat))
                steps += RStep(REvent.Arranged(seat, pts), s)
                if ((s.phase as RPhase.Showdown).pending.isEmpty()) settle(s, ph.declarer, steps)
            }
        }
        return steps
    }

    /** If only one player is still in, they win the hand. */
    private fun lastStanding(s: RummyState, steps: MutableList<RStep>) {
        val left = s.active
        if (left.size == 1) {
            val w = left[0]
            val s2 = s.copy(finish = s.finish.set(w, Finish.WON), points = s.points.set(w, 0), turn = -1,
                shown = s.shown.set(w, listOf(s.hands[w].map { it.id })))
            settle(s2, w, steps)
        }
    }

    /** Losers pay their points × [POINT_VALUE] (never more than they have) to [winner]. */
    private fun settle(s0: RummyState, winner: Int, steps: MutableList<RStep>) {
        val n = s0.players
        val delta = MutableList(n) { 0 }
        for (i in 0 until n) {
            if (i == winner || !s0.inHand[i]) continue
            val pay = (s0.points[i] * POINT_VALUE).coerceAtMost(s0.chips[i])
            delta[i] -= pay
            delta[winner] += pay
        }
        val s = s0.copy(
            chips = s0.chips.mapIndexed { i, c -> c + delta[i] }, delta = delta, phase = RPhase.HandOver(winner), turn = -1,
            handsWon = s0.handsWon.set(winner, s0.handsWon[winner] + 1),
        )
        steps += RStep(REvent.HandWon(winner, delta), s)
    }

    /** Next seat after [from] still in the hand. */
    fun nextActive(s: RummyState, from: Int): Int {
        val n = s.players
        var x = (from + 1) % n
        repeat(n) {
            if (s.inHand[x] && s.finish[x] == Finish.NONE) return x
            x = (x + 1) % n
        }
        return from
    }

    private fun <T> List<T>.set(i: Int, v: T): List<T> = toMutableList().also { it[i] = v }
}
