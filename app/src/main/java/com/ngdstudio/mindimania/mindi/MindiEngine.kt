package com.ngdstudio.mindimania.mindi

import com.ngdstudio.mindimania.engine.Rng
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mindi (Mendikot / Dehla Pakad) — a traditional Indian trick-taking game. Pure Kotlin, no Android.
 *
 * Seats 0..n-1, play goes to the next seat (seat + 1). Teams alternate: team = seat % 2, so partners sit opposite.
 * Only the 10s ("mindis") score. Most 10s wins the hand; equal 10s → most tricks; still equal → winner of the last trick.
 */

/** rank 2..14 (11 J, 12 Q, 13 K, 14 A); suit 0 spades, 1 hearts, 2 diamonds, 3 clubs. */
@Serializable
data class MCard(val id: Int, val rank: Int, val suit: Int) {
    val isTen: Boolean get() = rank == 10
    /** Rank for PlayingCardArt (1 = Ace). */
    val artRank: Int get() = if (rank == 14) 1 else rank
}

@Serializable
data class MindiPlay(val seat: Int, val card: MCard, val trump: Boolean)

@Serializable
enum class HandKind { NORMAL, MENDIKOT, WHITEWASH }

@Serializable
data class HandResult(
    val hand: Int,
    val winnerTeam: Int,
    val tens: List<Int>,
    val tricks: List<Int>,
    val kind: HandKind,
    val points: Int,
    val dealer: Int,
    val trumpSuit: Int?,
)

@Serializable
sealed interface MindiPhase {
    @Serializable @SerialName("hide")
    data class ChooseHidden(val seat: Int) : MindiPhase

    @Serializable @SerialName("play")
    data object Play : MindiPhase

    @Serializable @SerialName("handOver")
    data class HandOver(val result: HandResult) : MindiPhase

    @Serializable @SerialName("matchOver")
    data class MatchOver(val result: HandResult, val winnerTeam: Int) : MindiPhase
}

@Serializable
data class MindiState(
    val setup: MindiSetup,
    val seed: Long,
    val rngState: Long,
    val hands: List<List<MCard>>,
    val dealer: Int,
    val turn: Int,
    val leader: Int,
    val trick: List<MindiPlay> = emptyList(),
    /** Trump suit once known (null = no trump yet). */
    val trump: Int? = null,
    /** Hide mode: the face-down card (null once revealed or in other modes). */
    val hidden: MCard? = null,
    val hiddenOwner: Int = -1,
    /** Open mode: the card shown to set trump. Hide mode: the revealed card. */
    val shownTrumpCard: MCard? = null,
    /** Seat that triggered the reveal in the current trick (must trump if possible). */
    val revealer: Int = -1,
    val tricks: List<Int> = listOf(0, 0),
    val tens: List<List<MCard>> = listOf(emptyList(), emptyList()),
    /** Every card played this hand, in order (public information). */
    val played: List<MCard> = emptyList(),
    /** Suits each seat has shown they're out of (public information). */
    val voids: List<Set<Int>> = emptyList(),
    val lastTrick: List<MindiPlay> = emptyList(),
    val lastWinner: Int = -1,
    val trickNo: Int = 0,
    val scores: List<Int> = listOf(0, 0),
    val handNo: Int = 1,
    /** Hands won in a row by [streakTeam]. */
    val streak: Int = 0,
    val streakTeam: Int = -1,
    val kots: List<Int> = listOf(0, 0),
    val history: List<HandResult> = emptyList(),
    val phase: MindiPhase = MindiPhase.Play,
) {
    val players: Int get() = setup.players
    val tricksPerHand: Int get() = handSizeFor(setup)
    val totalTens: Int get() = if (setup.doubleDeck) 8 else 4
    fun team(seat: Int) = seat % 2
    fun partnersOf(seat: Int): List<Int> = (0 until players).filter { it != seat && it % 2 == seat % 2 }
    fun next(seat: Int) = (seat + 1) % players
    val leadSuit: Int? get() = trick.firstOrNull()?.card?.suit
    val isOver: Boolean get() = phase is MindiPhase.HandOver || phase is MindiPhase.MatchOver

    /** The current winning play of the trick (null when the trick is empty). */
    fun winningPlay(): MindiPlay? = MindiEngine.trickWinner(trick)

    companion object {
        fun handSizeFor(s: MindiSetup): Int = when {
            s.doubleDeck && s.players == 8 -> 13
            s.doubleDeck -> 17
            s.players == 4 -> 13
            s.players == 6 -> 8
            else -> 6
        }
    }
}

@Serializable
sealed interface MindiEvent {
    @Serializable @SerialName("Dealt")
    data class Dealt(val dealer: Int, val hand: Int) : MindiEvent
    @Serializable @SerialName("TrumpShown")
    data class TrumpShown(val card: MCard) : MindiEvent
    @Serializable @SerialName("HiddenChosen")
    data class HiddenChosen(val seat: Int) : MindiEvent
    @Serializable @SerialName("TrumpRevealed")
    data class TrumpRevealed(val bySeat: Int, val owner: Int, val card: MCard) : MindiEvent
    @Serializable @SerialName("TrumpCut")
    data class TrumpCut(val bySeat: Int, val suit: Int) : MindiEvent
    @Serializable @SerialName("Played")
    data class Played(val seat: Int, val card: MCard, val trump: Boolean) : MindiEvent
    @Serializable @SerialName("TrickWon")
    data class TrickWon(val winner: Int, val team: Int, val cards: List<MCard>, val tens: Int) : MindiEvent
    @Serializable @SerialName("HandEnded")
    data class HandEnded(val result: HandResult) : MindiEvent
    @Serializable @SerialName("MatchEnded")
    data class MatchEnded(val winnerTeam: Int) : MindiEvent
    /** UI only: a saved match was loaded; redraw the table as it is. */
    @Serializable @SerialName("Restored")
    data class Restored(val hand: Int) : MindiEvent
}

/** An event plus the state right after it. */
data class MStep(val event: MindiEvent, val state: MindiState)

@Serializable
sealed interface MindiAction {
    val seat: Int
    @Serializable @SerialName("Hide")
    data class Hide(override val seat: Int, val cardId: Int) : MindiAction
    @Serializable @SerialName("Play")
    data class Play(override val seat: Int, val cardId: Int) : MindiAction
    @Serializable @SerialName("NextHand")
    data class NextHand(override val seat: Int = 0) : MindiAction
}

object MindiEngine {

    fun deck(setup: MindiSetup): List<MCard> {
        var id = 0
        val list = ArrayList<MCard>()
        val decks = if (setup.doubleDeck) 2 else 1
        repeat(decks) { d ->
            for (s in 0..3) for (r in 2..14) {
                val drop = when {
                    !setup.doubleDeck && setup.players != 4 -> r == 2 // 6/8 players: remove the four 2s
                    setup.doubleDeck && setup.players == 6 -> d == 1 && r == 2 && (s == 2 || s == 3) // 102 cards
                    else -> false
                }
                if (!drop) list += MCard(id++, r, s)
            }
        }
        return list
    }

    fun normalize(setup: MindiSetup): MindiSetup {
        val p = setup.players.let { if (it in listOf(4, 6, 8)) it else 4 }
        return setup.copy(players = p, doubleDeck = setup.doubleDeck && p >= 6, target = setup.target.coerceIn(1, 15))
    }

    fun newMatch(setup0: MindiSetup, seed: Long): List<MStep> {
        val setup = normalize(setup0)
        val rng = Rng(seed)
        val dealer = rng.nextInt(setup.players)
        val base = MindiState(
            setup = setup, seed = seed, rngState = rng.state, hands = emptyList(), dealer = dealer,
            turn = 0, leader = 0,
        )
        return deal(base, dealer, base.scores, base.handNo, base.history)
    }

    /** Shuffles and deals a hand; sets up the trump according to the mode. */
    private fun deal(prev: MindiState, dealer: Int, scores: List<Int>, handNo: Int, history: List<HandResult>): List<MStep> {
        val setup = prev.setup
        val rng = Rng(prev.rngState)
        val cards = deck(setup).toMutableList()
        rng.shuffle(cards)
        val n = setup.players
        val size = MindiState.handSizeFor(setup)
        val hands = List(n) { ArrayList<MCard>() }
        var k = 0
        repeat(size) { for (i in 1..n) hands[(dealer + i) % n] += cards[k++] }
        val first = (dealer + 1) % n
        var s = prev.copy(
            rngState = rng.state,
            hands = hands.map { sortHand(it, null) },
            dealer = dealer, turn = first, leader = first,
            trick = emptyList(), trump = null, hidden = null, hiddenOwner = -1, shownTrumpCard = null, revealer = -1,
            tricks = listOf(0, 0), tens = listOf(emptyList(), emptyList()), played = emptyList(),
            voids = List(n) { emptySet() }, lastTrick = emptyList(), lastWinner = -1, trickNo = 0,
            scores = scores, handNo = handNo, history = history, phase = MindiPhase.Play,
        )
        val steps = ArrayList<MStep>()
        steps += MStep(MindiEvent.Dealt(dealer, handNo), s)
        when (setup.trumpMode) {
            MindiTrumpMode.OPEN -> {
                val shown = cards[rng.nextInt(cards.size)]
                s = s.copy(trump = shown.suit, shownTrumpCard = shown, rngState = rng.state, hands = s.hands.map { sortHand(it, shown.suit) })
                steps += MStep(MindiEvent.TrumpShown(shown), s)
            }
            MindiTrumpMode.HIDE -> {
                s = s.copy(phase = MindiPhase.ChooseHidden(first), hiddenOwner = first)
                steps[steps.lastIndex] = MStep(steps.last().event, s)
            }
            MindiTrumpMode.CUT -> Unit
        }
        return steps
    }

    fun sortHand(hand: List<MCard>, trump: Int?): List<MCard> {
        // alternate colours: ♠ ♥ ♣ ♦ (black, red, black, red); trump suit first
        val order = listOf(0, 1, 3, 2)
        return hand.sortedWith(compareBy<MCard>({ if (it.suit == trump) -1 else order.indexOf(it.suit) }, { -it.rank }, { it.id }))
    }

    // ------------------------------------------------------------------ rules

    fun trickWinner(trick: List<MindiPlay>): MindiPlay? {
        if (trick.isEmpty()) return null
        val trumps = trick.filter { it.trump }
        val pool = if (trumps.isNotEmpty()) trumps else trick.filter { it.card.suit == trick.first().card.suit }
        var best = pool.first()
        for (p in pool) if (p.card.rank > best.card.rank) best = p // ties: first played wins
        return best
    }

    /** Would [seat] trigger the hidden-trump reveal by not being able to follow suit? */
    fun revealPending(s: MindiState, seat: Int): Boolean {
        if (s.setup.trumpMode != MindiTrumpMode.HIDE || s.hidden == null || s.phase != MindiPhase.Play || s.turn != seat) return false
        val lead = s.leadSuit
        val hand = s.hands[seat]
        if (lead == null) return hand.isEmpty() // owner with only the hidden card left
        return hand.none { it.suit == lead }
    }

    fun legalCards(s: MindiState, seat: Int): List<MCard> {
        if (s.phase != MindiPhase.Play || s.turn != seat) return emptyList()
        val state = if (revealPending(s, seat)) reveal(s, seat).state else s
        val hand = state.hands[seat]
        val lead = state.leadSuit ?: return hand
        val follow = hand.filter { it.suit == lead }
        if (follow.isNotEmpty()) return follow
        if (state.revealer == seat && state.setup.mustTrumpAfterReveal && state.trump != null) {
            val trumps = hand.filter { it.suit == state.trump }
            if (trumps.isNotEmpty()) return trumps
        }
        return hand
    }

    fun validate(s: MindiState, a: MindiAction): String? = when (a) {
        is MindiAction.Hide -> {
            val ph = s.phase
            when {
                ph !is MindiPhase.ChooseHidden || ph.seat != a.seat -> "not hiding now"
                s.hands[a.seat].none { it.id == a.cardId } -> "card not in hand"
                else -> null
            }
        }
        is MindiAction.Play -> when {
            s.phase != MindiPhase.Play -> "not playing now"
            s.turn != a.seat -> "not your turn"
            legalCards(s, a.seat).none { it.id == a.cardId } -> "card not allowed"
            else -> null
        }
        is MindiAction.NextHand -> if (s.phase is MindiPhase.HandOver) null else "hand not over"
    }

    private data class Revealed(val state: MindiState, val step: MStep)

    private fun reveal(s: MindiState, by: Int): Revealed {
        val card = s.hidden!!
        val owner = s.hiddenOwner
        val hands = s.hands.mapIndexed { i, h -> if (i == owner) sortHand(h + card, card.suit) else sortHand(h, card.suit) }
        val ns = s.copy(trump = card.suit, hidden = null, shownTrumpCard = card, revealer = by, hands = hands)
        return Revealed(ns, MStep(MindiEvent.TrumpRevealed(by, owner, card), ns))
    }

    fun apply(s0: MindiState, a: MindiAction): List<MStep> {
        validate(s0, a)?.let { throw IllegalStateException("${a::class.simpleName}: $it") }
        return when (a) {
            is MindiAction.Hide -> {
                val card = s0.hands[a.seat].first { it.id == a.cardId }
                val s = s0.copy(
                    hidden = card, hiddenOwner = a.seat,
                    hands = s0.hands.mapIndexed { i, h -> if (i == a.seat) h - card else h },
                    phase = MindiPhase.Play,
                )
                listOf(MStep(MindiEvent.HiddenChosen(a.seat), s))
            }
            is MindiAction.Play -> play(s0, a.seat, a.cardId)
            is MindiAction.NextHand -> {
                val ph = s0.phase as MindiPhase.HandOver
                deal(s0, nextDealer(s0, ph.result), s0.scores, s0.handNo + 1, s0.history)
            }
        }
    }

    private fun play(s0: MindiState, seat: Int, cardId: Int): List<MStep> {
        val steps = ArrayList<MStep>()
        var s = s0
        if (revealPending(s, seat)) {
            val r = reveal(s, seat)
            s = r.state
            steps += r.step
        }
        val card = s.hands[seat].first { it.id == cardId }
        val lead = s.leadSuit
        var trump = s.trump
        var voids = s.voids
        if (lead != null && card.suit != lead) {
            voids = voids.mapIndexed { i, v -> if (i == seat) v + lead else v }
            if (s.setup.trumpMode == MindiTrumpMode.CUT && trump == null) {
                trump = card.suit
                s = s.copy(trump = trump, hands = s.hands.map { sortHand(it, trump) })
                steps += MStep(MindiEvent.TrumpCut(seat, card.suit), s)
            }
        }
        // A card counts as trump only if trump was known when it was played. If the suit led turns out to be the
        // (just revealed) trump suit, following cards are ordinary follows, not trumps.
        val isTrump = trump != null && card.suit == trump && (lead == null || lead != trump || s.trick.first().trump)
        val playRec = MindiPlay(seat, card, isTrump)
        s = s.copy(
            hands = s.hands.mapIndexed { i, h -> if (i == seat) h.filter { it.id != card.id } else h },
            trick = s.trick + playRec,
            played = s.played + card,
            voids = voids,
            turn = s.next(seat),
        )
        steps += MStep(MindiEvent.Played(seat, card, isTrump), s)
        if (s.trick.size == s.players) steps += finishTrick(s) else autoReveal(s, steps)
        return steps
    }

    /** Hide mode: reveal the trump as soon as the player to act can't follow suit (before they choose a card). */
    private fun autoReveal(s: MindiState, steps: MutableList<MStep>) {
        if (revealPending(s, s.turn)) steps += reveal(s, s.turn).step
    }

    private fun finishTrick(s0: MindiState): List<MStep> {
        val steps = ArrayList<MStep>()
        val win = trickWinner(s0.trick)!!
        val team = s0.team(win.seat)
        val tensWon = s0.trick.map { it.card }.filter { it.isTen }
        val tricks = s0.tricks.mapIndexed { i, t -> if (i == team) t + 1 else t }
        val tens = s0.tens.mapIndexed { i, t -> if (i == team) t + tensWon else t }
        var s = s0.copy(
            tricks = tricks, tens = tens, lastTrick = s0.trick, lastWinner = win.seat, trick = emptyList(),
            trickNo = s0.trickNo + 1, turn = win.seat, leader = win.seat, revealer = -1,
        )
        steps += MStep(MindiEvent.TrickWon(win.seat, team, s0.trick.map { it.card }, tensWon.size), s)
        if (s.trickNo >= s.tricksPerHand) {
            // a hidden card that was never revealed: the deal ends without it ever counting
            val result = scoreHand(s)
            val scores = s.scores.mapIndexed { i, v -> if (i == result.winnerTeam) v + result.points else v }
            val streak = if (s.streakTeam == result.winnerTeam) s.streak + 1 else 1
            val kots = s.kots.mapIndexed { i, v -> if (i == result.winnerTeam && result.kind != HandKind.NORMAL) v + 1 else v }
            val over = scores[result.winnerTeam] >= s.setup.target
            s = s.copy(
                scores = scores, streak = streak, streakTeam = result.winnerTeam, kots = kots, history = s.history + result,
                phase = if (over) MindiPhase.MatchOver(result, result.winnerTeam) else MindiPhase.HandOver(result),
            )
            steps += MStep(MindiEvent.HandEnded(result), s)
            if (over) steps += MStep(MindiEvent.MatchEnded(result.winnerTeam), s)
        } else {
            // e.g. the hidden card's owner leads with only the hidden card left
            autoReveal(s, steps)
        }
        return steps
    }

    fun scoreHand(s: MindiState): HandResult {
        val t0 = s.tens[0].size
        val t1 = s.tens[1].size
        val winner = when {
            t0 != t1 -> if (t0 > t1) 0 else 1
            s.tricks[0] != s.tricks[1] -> if (s.tricks[0] > s.tricks[1]) 0 else 1
            else -> s.team(s.lastWinner)
        }
        val kind = when {
            s.tricks[winner] == s.tricksPerHand -> HandKind.WHITEWASH
            s.tens[winner].size == s.totalTens -> HandKind.MENDIKOT
            else -> HandKind.NORMAL
        }
        val points = when (kind) {
            HandKind.NORMAL -> 1
            HandKind.MENDIKOT -> 2
            HandKind.WHITEWASH -> 3
        }
        return HandResult(s.handNo, winner, listOf(t0, t1), s.tricks, kind, points, s.dealer, s.trump)
    }

    /** "The losers deal": same dealer if their team lost, partner after a whitewash, next player if they won. */
    fun nextDealer(s: MindiState, r: HandResult): Int {
        val dealerTeam = s.team(r.dealer)
        return when {
            dealerTeam != r.winnerTeam && r.kind == HandKind.WHITEWASH -> (r.dealer + 2) % s.players
            dealerTeam != r.winnerTeam -> r.dealer
            else -> (r.dealer + 1) % s.players
        }
    }

    /** Cards that [seat] can't see: everything except their own hand, the played cards and (owner only) the hidden card. */
    fun unseen(s: MindiState, seat: Int): List<MCard> {
        val known = HashSet<Int>()
        s.hands[seat].forEach { known += it.id }
        s.played.forEach { known += it.id }
        if (s.hiddenOwner == seat) s.hidden?.let { known += it.id }
        return deck(s.setup).filter { it.id !in known }
    }
}
