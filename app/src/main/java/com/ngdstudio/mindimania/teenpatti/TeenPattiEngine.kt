package com.ngdstudio.mindimania.teenpatti

import com.ngdstudio.mindimania.engine.Rng
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Teen Patti (three cards), a traditional Indian card game. Pure Kotlin, no Android.
 *
 * Played for table chips only: everyone starts a match with [TeenPattiEngine.START_CHIPS], the chips never leave the
 * match, and whoever holds the most after the last hand wins. Nothing is bought, sold or carried over.
 *
 * Each hand: everyone with at least the boot pays it into the pot and gets 3 cards face down. Play goes round from
 * the dealer's left. On your turn you may play **blind** (without looking) or look first (**seen**):
 *  - chaal: a blind player pays the current stake, a seen player twice the stake;
 *  - raise: pay double that, and the stake doubles (up to [TeenPattiEngine.MAX_STAKE]);
 *  - pack: fold and lose what you put in;
 *  - show (only two players left): pay a chaal and both hands are compared; on a tie the player who asked loses;
 *  - sideshow (seen, 3+ players left, the previous player is seen too): pay a chaal and ask that player to compare
 *    privately; if they accept, the lower hand packs (a tie: the asker packs);
 *  - all in: a player who can't afford a chaal puts in the rest of their chips and every hand still in is shown.
 * The last player in, or the best hand at a show, takes the pot. Equal hands at an all-in show split it.
 *
 * Hand ranking, best first: trail (three of a kind), pure sequence, sequence, colour, pair, high card.
 * A-K-Q is the top sequence; A-2-3 is second ([TeenPattiSetup.a23Second]) or the lowest. Suits never break ties.
 */

/** rank 2..14 (11 J, 12 Q, 13 K, 14 A); suit 0 spades, 1 hearts, 2 diamonds, 3 clubs. */
@Serializable
data class TCard(val id: Int, val rank: Int, val suit: Int) {
    /** Rank for PlayingCardArt (1 = Ace). */
    val artRank: Int get() = if (rank == 14) 1 else rank
}

/** Hand kinds, weakest first (ordinal = strength). */
enum class TPKind { HIGH_CARD, PAIR, COLOR, SEQUENCE, PURE_SEQUENCE, TRAIL }

/**
 * Traditional Teen Patti variations (house rules). Each hand is played with one of them; MIXED picks a new one for
 * every hand.
 *  - MUFLIS: the ranking is turned upside down, the lowest hand wins.
 *  - JOKER: after the deal one card is turned up; every card of that rank is wild.
 *  - LOWEST_JOKER: the lowest-ranked card(s) in each hand are wild.
 *  - AK47: every A, K, 4 and 7 is wild.
 *  - NINE99: 10/J/Q/K count 0, A counts 1; the three cards make the biggest 3-digit number they can, closest to 999 wins.
 *  - FOUR_X_BOOT: classic rules with four times the boot.
 * A wild card stands for whatever card makes the best hand.
 */
@Serializable
enum class TPVariant { CLASSIC, MUFLIS, JOKER, LOWEST_JOKER, AK47, NINE99, FOUR_X_BOOT, MIXED }

@Serializable
sealed interface TPPhase {
    @Serializable @SerialName("bet")
    data object Betting : TPPhase

    /** [from] asked [to] for a sideshow; [to] must accept or decline. */
    @Serializable @SerialName("side")
    data class Sideshow(val from: Int, val to: Int) : TPPhase

    @Serializable @SerialName("handover")
    data class HandOver(val winners: List<Int>, val pot: Int) : TPPhase

    @Serializable @SerialName("over")
    data object MatchOver : TPPhase
}

/** How a pot was won. */
@Serializable
enum class WinReason { LAST_STANDING, SHOW, ALL_IN }

@Serializable
data class TeenPattiState(
    val setup: TeenPattiSetup,
    val seed: Long,
    /** 1-based number of the hand being played. */
    val handNo: Int,
    val dealer: Int,
    val chips: List<Int>,
    /** 3 cards per seat dealt into this hand (empty for a seat with no chips left). */
    val hands: List<List<TCard>>,
    val inHand: List<Boolean>,
    val folded: List<Boolean>,
    val seen: List<Boolean>,
    /** Chips each seat put into this pot. */
    val put: List<Int>,
    val pot: Int,
    /** Current blind stake (a seen player pays twice this). */
    val stake: Int,
    /** Seat to act (the sideshow target while one is asked), -1 once the hand is over. */
    val turn: Int,
    /** Blind chaals each seat made this hand. */
    val blindChaals: List<Int>,
    /** Betting actions so far this hand (chaals, raises, shows, sideshows). */
    val actions: Int = 0,
    /** Seats whose cards everyone saw at the end of this hand. */
    val shown: List<Int> = emptyList(),
    val handsWon: List<Int>,
    val phase: TPPhase = TPPhase.Betting,
    /** The variation this hand is played with (never MIXED). */
    val variant: TPVariant = TPVariant.CLASSIC,
    /** JOKER only: the card turned up after the deal (not in anyone's hand). */
    val joker: TCard? = null,
    /** What everyone paid to be dealt into this hand. */
    val boot: Int = TeenPattiEngine.BOOT,
) {
    val players: Int get() = setup.players
    /** Hand after hand until the player leaves (setup.hands = 0). */
    val endless: Boolean get() = setup.hands <= 0
    val isOver: Boolean get() = phase is TPPhase.MatchOver
    val handOver: Boolean get() = phase is TPPhase.HandOver || phase is TPPhase.MatchOver

    /** Seats still in this hand (dealt in, not packed). */
    val active: List<Int> get() = (0 until players).filter { inHand[it] && !folded[it] }

    /** Seats by chips, most first (ties: the earlier seat). */
    val ranking: List<Int> get() = (0 until players).sortedWith(compareByDescending<Int> { chips[it] }.thenBy { it })

    fun placeOf(seat: Int): Int = ranking.indexOf(seat) + 1
}

@Serializable
sealed interface TPEvent {
    /** A new hand: everyone in paid [boot]. */
    @Serializable @SerialName("Dealt")
    data class Dealt(val handNo: Int, val dealer: Int, val boot: Int, val variant: TPVariant = TPVariant.CLASSIC, val joker: TCard? = null) : TPEvent

    @Serializable @SerialName("Saw")
    data class Saw(val seat: Int) : TPEvent

    @Serializable @SerialName("Chaal")
    data class Chaal(val seat: Int, val amount: Int, val blind: Boolean, val raised: Boolean) : TPEvent

    @Serializable @SerialName("Packed")
    data class Packed(val seat: Int) : TPEvent

    @Serializable @SerialName("SideAsk")
    data class SideshowAsked(val from: Int, val to: Int, val amount: Int) : TPEvent

    /** [loser] packs after an accepted sideshow; -1 when it was declined. */
    @Serializable @SerialName("SideResult")
    data class SideshowResult(val from: Int, val to: Int, val accepted: Boolean, val loser: Int) : TPEvent

    @Serializable @SerialName("Show")
    data class ShowCalled(val seat: Int, val amount: Int) : TPEvent

    @Serializable @SerialName("AllIn")
    data class AllIn(val seat: Int, val amount: Int) : TPEvent

    /** Everyone sees the hands of [seats]; [winners] have the best. */
    @Serializable @SerialName("Showdown")
    data class Showdown(val seats: List<Int>, val winners: List<Int>) : TPEvent

    /** [amounts] pairs with [winners]. */
    @Serializable @SerialName("PotWon")
    data class PotWon(val winners: List<Int>, val amounts: List<Int>, val reason: WinReason) : TPEvent

    @Serializable @SerialName("MatchOver")
    data class MatchOver(val ranking: List<Int>) : TPEvent

    /** Endless table: [seats] had run out of chips and start the next hand with a fresh [chips] stack. */
    @Serializable @SerialName("Refilled")
    data class Refilled(val seats: List<Int>, val chips: Int) : TPEvent

    /** UI only: a saved game was loaded; redraw the table as it is. */
    @Serializable @SerialName("Restored")
    data class Restored(val handNo: Int) : TPEvent
}

/** An event plus the state right after it. */
data class TPStep(val event: TPEvent, val state: TeenPattiState)

@Serializable
sealed interface TPAction {
    val seat: Int

    @Serializable @SerialName("See")
    data class See(override val seat: Int) : TPAction

    @Serializable @SerialName("Pack")
    data class Pack(override val seat: Int) : TPAction

    @Serializable @SerialName("Chaal")
    data class Chaal(override val seat: Int, val raise: Boolean = false) : TPAction

    @Serializable @SerialName("Show")
    data class Show(override val seat: Int) : TPAction

    @Serializable @SerialName("AllIn")
    data class AllIn(override val seat: Int) : TPAction

    @Serializable @SerialName("Side")
    data class Sideshow(override val seat: Int) : TPAction

    @Serializable @SerialName("SideReply")
    data class SideshowReply(override val seat: Int, val accept: Boolean) : TPAction
}

/** What [seat] may do right now, with the cost of each move (used by the buttons and the bots). */
data class TPOptions(
    val canSee: Boolean = false,
    val myTurn: Boolean = false,
    val canChaal: Boolean = false,
    val chaalCost: Int = 0,
    val canRaise: Boolean = false,
    val raiseCost: Int = 0,
    val canShow: Boolean = false,
    val canSideshow: Boolean = false,
    /** Can't afford a chaal: the only ways on are all in or pack. */
    val canAllIn: Boolean = false,
    val canPack: Boolean = false,
    /** [seat] was asked for a sideshow and must reply. */
    val mustReply: Boolean = false,
)

object TeenPattiEngine {
    const val MIN_PLAYERS = 3
    const val MAX_PLAYERS = 6
    const val START_CHIPS = 2000
    const val BOOT = 20
    /** The blind stake never goes above this (a seen chaal is at most twice it). */
    const val MAX_STAKE = BOOT * 16
    val HAND_CHOICES = listOf(5, 10, 15)
    /** The variations a hand can actually be played with (MIXED picks one of these). */
    val PLAYABLE = TPVariant.entries.filter { it != TPVariant.MIXED }

    fun deck(): List<TCard> = buildList {
        var id = 0
        for (s in 0..3) for (r in 2..14) add(TCard(id++, r, s))
    }

    fun normalize(setup: TeenPattiSetup): TeenPattiSetup = setup.copy(
        players = setup.players.coerceIn(MIN_PLAYERS, MAX_PLAYERS),
        hands = setup.hands.coerceIn(0, 50),
        turnTimer = setup.turnTimer.coerceIn(0, 60),
    )

    // ------------------------------------------------------------------ hand ranking

    /** Kind of a 3-card hand as dealt (no wild cards). */
    fun kind(cards: List<TCard>): TPKind = kindOf(score(cards, true))

    fun kindOf(score: Long): TPKind = TPKind.entries[(score / KIND_STEP).toInt().coerceIn(0, TPKind.entries.lastIndex)]

    private const val KIND_STEP = 100_000L

    /**
     * A number that orders hands: higher is better, equal means a tie (suits never break ties).
     * kind × 100 000 + a tiebreak within the kind.
     */
    fun score(cards: List<TCard>, a23Second: Boolean): Long {
        require(cards.size == 3)
        return scoreRanks(cards[0].rank, cards[1].rank, cards[2].rank, cards.all { it.suit == cards[0].suit }, a23Second)
    }

    private fun scoreRanks(a: Int, b: Int, c: Int, flush: Boolean, a23Second: Boolean): Long {
        // sort the three ranks high to low without allocating
        var r1 = a; var r2 = b; var r3 = c
        if (r1 < r2) { val t = r1; r1 = r2; r2 = t }
        if (r2 < r3) { val t = r2; r2 = r3; r3 = t }
        if (r1 < r2) { val t = r1; r1 = r2; r2 = t }
        if (r1 == r3) return TPKind.TRAIL.ordinal * KIND_STEP + r1
        val a23 = r1 == 14 && r2 == 3 && r3 == 2
        val run = (r1 - r2 == 1 && r2 - r3 == 1) || a23
        if (run) {
            // A-K-Q = 28, A-2-3 = 27 (or 6, below 4-3-2 = 8), otherwise twice the top card
            val v = when {
                a23 -> if (a23Second) 27L else 6L
                else -> r1 * 2L
            }
            return (if (flush) TPKind.PURE_SEQUENCE else TPKind.SEQUENCE).ordinal * KIND_STEP + v
        }
        val high = r1 * 225L + r2 * 15L + r3
        if (flush) return TPKind.COLOR.ordinal * KIND_STEP + high
        if (r1 == r2) return TPKind.PAIR.ordinal * KIND_STEP + r1 * 15L + r3
        if (r2 == r3) return TPKind.PAIR.ordinal * KIND_STEP + r2 * 15L + r1
        return TPKind.HIGH_CARD.ordinal * KIND_STEP + high
    }

    /** Ranks that are wild in [hand] under [variant] ([joker] = the turned-up card in JOKER). */
    fun wildRanks(variant: TPVariant, hand: List<TCard>, joker: TCard?): Set<Int> = when (variant) {
        TPVariant.JOKER -> joker?.let { setOf(it.rank) } ?: emptySet()
        TPVariant.AK47 -> AK47_RANKS
        TPVariant.LOWEST_JOKER -> hand.minOfOrNull { it.rank }?.let { setOf(it) } ?: emptySet()
        else -> emptySet()
    }

    private val AK47_RANKS = setOf(14, 13, 4, 7)

    /** 999: 10 and pictures count 0, Ace 1; the digits in their best order (0..999). */
    fun nine99(cards: List<TCard>): Int {
        val d = cards.map { if (it.rank == 14) 1 else if (it.rank >= 10) 0 else it.rank }.sortedDescending()
        return d[0] * 100 + d[1] * 10 + d[2]
    }

    /** The score of [cards] under [variant]: higher always wins (Muflis is turned upside down here). */
    fun handScore(cards: List<TCard>, variant: TPVariant, a23Second: Boolean, joker: TCard?): Long = when (variant) {
        TPVariant.MUFLIS -> -score(cards, a23Second)
        TPVariant.NINE99 -> nine99(cards).toLong()
        TPVariant.JOKER, TPVariant.AK47, TPVariant.LOWEST_JOKER -> bestWithWilds(cards, wildRanks(variant, cards, joker), a23Second)
        else -> score(cards, a23Second)
    }

    /**
     * What each wild card in [cards] plays as in the best hand (wild card id → the card it stands for, id -1), or empty
     * when the variation has no wild cards or the hand holds none. Used to show the substitutes when hands are opened.
     */
    fun wildsAs(cards: List<TCard>, variant: TPVariant, a23Second: Boolean, joker: TCard?): Map<Int, TCard> {
        if (variant != TPVariant.JOKER && variant != TPVariant.AK47 && variant != TPVariant.LOWEST_JOKER) return emptyMap()
        val wild = wildRanks(variant, cards, joker)
        val w = cards.filter { it.rank in wild }
        val nat = cards.filter { it.rank !in wild }
        if (w.isEmpty()) return emptyMap()
        val out = LinkedHashMap<Int, TCard>()
        when (nat.size) {
            0 -> w.forEachIndexed { i, c -> out[c.id] = TCard(-1, 14, i) } // A-A-A
            1 -> {
                // a trail of the natural card: the wilds take the other suits
                val suits = (0..3).filter { it != nat[0].suit }
                w.forEachIndexed { i, c -> out[c.id] = TCard(-1, nat[0].rank, suits[i]) }
            }
            else -> {
                val (x, y) = nat
                val sameSuit = x.suit == y.suit
                var best = Long.MIN_VALUE
                var bestRank = 2
                var bestFlush = false
                for (r in 2..14) {
                    val plain = scoreRanks(x.rank, y.rank, r, false, a23Second)
                    if (plain > best) { best = plain; bestRank = r; bestFlush = false }
                    if (sameSuit && r != x.rank && r != y.rank) {
                        val fl = scoreRanks(x.rank, y.rank, r, true, a23Second)
                        if (fl > best) { best = fl; bestRank = r; bestFlush = true }
                    }
                }
                // a suit that makes the flush when it helps, otherwise one that breaks it (and never copies a held card)
                val suit = if (bestFlush) x.suit else (0..3).first { it != x.suit && (bestRank != y.rank || it != y.suit) }
                out[w[0].id] = TCard(-1, bestRank, suit)
            }
        }
        return out
    }

    /** [seat]'s wild cards and what they stand for in this hand's variation. */
    fun wildsAs(s: TeenPattiState, seat: Int): Map<Int, TCard> =
        s.hands.getOrNull(seat)?.let { wildsAs(it, s.variant, s.setup.a23Second, s.joker) } ?: emptyMap()

    /** Best hand when cards of [wild] ranks may stand for any card. */
    fun bestWithWilds(cards: List<TCard>, wild: Set<Int>, a23Second: Boolean): Long {
        val nat = cards.filter { it.rank !in wild }
        return when (nat.size) {
            3 -> score(cards, a23Second)
            0 -> TPKind.TRAIL.ordinal * KIND_STEP + 14 // three wilds: A-A-A
            1 -> TPKind.TRAIL.ordinal * KIND_STEP + nat[0].rank // two wilds copy the natural card
            else -> {
                val (x, y) = nat
                val sameSuit = x.suit == y.suit
                var best = Long.MIN_VALUE
                for (r in 2..14) {
                    best = maxOf(best, scoreRanks(x.rank, y.rank, r, false, a23Second))
                    // a joker can take the suit of the other two, but never copy one of them exactly
                    if (sameSuit && r != x.rank && r != y.rank) best = maxOf(best, scoreRanks(x.rank, y.rank, r, true, a23Second))
                }
                best
            }
        }
    }

    /** [seat]'s hand score in this hand's variation. */
    fun score(s: TeenPattiState, seat: Int): Long = handScore(s.hands[seat], s.variant, s.setup.a23Second, s.joker)

    /** Kind of [seat]'s best hand (wilds used), null in 999 where kinds don't count. Muflis: the kind as dealt. */
    fun handKind(s: TeenPattiState, seat: Int): TPKind? = when (s.variant) {
        TPVariant.NINE99 -> null
        TPVariant.MUFLIS -> kind(s.hands[seat])
        else -> kindOf(score(s, seat))
    }

    /** Ids of the wild cards in [seat]'s hand. */
    fun wildIds(s: TeenPattiState, seat: Int): Set<Int> {
        val h = s.hands.getOrNull(seat) ?: return emptySet()
        val w = wildRanks(s.variant, h, s.joker)
        return h.filter { it.rank in w }.map { it.id }.toSet()
    }

    private val tables = HashMap<String, LongArray>()

    /**
     * Every one of the 22 100 possible hands' scores under [variant], sorted (built once per variation, A-2-3 rule and,
     * in JOKER, joker rank).
     */
    private fun table(a23Second: Boolean, variant: TPVariant = TPVariant.CLASSIC, jokerRank: Int = 0): LongArray {
        val v = if (variant == TPVariant.FOUR_X_BOOT || variant == TPVariant.MIXED) TPVariant.CLASSIC else variant
        val key = "$v/$a23Second/${if (v == TPVariant.JOKER) jokerRank else 0}"
        synchronized(tables) { tables[key]?.let { return it } }
        val d = deck()
        val joker = if (v == TPVariant.JOKER) TCard(-1, jokerRank, 0) else null
        val out = LongArray(22100)
        var k = 0
        for (i in 0 until 52) for (j in i + 1 until 52) for (l in j + 1 until 52) out[k++] = handScore(listOf(d[i], d[j], d[l]), v, a23Second, joker)
        out.sort()
        synchronized(tables) { tables[key] = out }
        return out
    }

    /** Share of all possible hands [seat]'s hand beats in this hand's variation (ties count half), 0..1. */
    fun strength(s: TeenPattiState, seat: Int): Double =
        percentileIn(table(s.setup.a23Second, s.variant, s.joker?.rank ?: 0), score(s, seat))

    /** Share of all possible hands that [score] beats (ties count half), 0..1. */
    fun percentile(score: Long, a23Second: Boolean): Double = percentileIn(table(a23Second), score)

    private fun percentileIn(t: LongArray, score: Long): Double {
        val below = lowerBound(t, score)
        val upTo = lowerBound(t, score + 1)
        return (below + (upTo - below) / 2.0) / t.size
    }

    private fun lowerBound(a: LongArray, v: Long): Int {
        var lo = 0
        var hi = a.size
        while (lo < hi) {
            val m = (lo + hi) ushr 1
            if (a[m] < v) lo = m + 1 else hi = m
        }
        return lo
    }

    /** Spades, hearts, clubs, diamonds; high to low. */
    fun sortHand(h: List<TCard>): List<TCard> = h.sortedWith(compareByDescending<TCard> { it.rank }.thenBy { it.suit })

    // ------------------------------------------------------------------ dealing

    fun newGame(setup0: TeenPattiSetup, seed: Long): List<TPStep> {
        val setup = normalize(setup0)
        val n = setup.players
        val dealer = Rng(seed).nextInt(n)
        val s0 = TeenPattiState(
            setup = setup, seed = seed, handNo = 0, dealer = (dealer - 1 + n) % n,
            chips = List(n) { START_CHIPS }, hands = List(n) { emptyList() },
            inHand = List(n) { false }, folded = List(n) { false }, seen = List(n) { false },
            put = List(n) { 0 }, pot = 0, stake = BOOT, turn = -1, blindChaals = List(n) { 0 },
            handsWon = List(n) { 0 }, phase = TPPhase.HandOver(emptyList(), 0),
        )
        return nextHand(s0)
    }

    /** Deals the next hand. Only after a hand is over (the first hand comes from [newGame]). */
    fun nextHand(prev: TeenPattiState): List<TPStep> {
        require(prev.phase is TPPhase.HandOver) { "The hand is still being played" }
        val pre = ArrayList<TPStep>()
        var s0 = prev
        if (s0.endless) {
            // anyone who can't pay the boot gets a fresh stack (a new bot sits down / the player is topped up)
            val broke = (0 until s0.players).filter { s0.chips[it] < BOOT }
            if (broke.isNotEmpty() && s0.handNo > 0) {
                s0 = s0.copy(chips = s0.chips.mapIndexed { i, c -> if (i in broke) START_CHIPS else c })
                pre += TPStep(TPEvent.Refilled(broke, START_CHIPS), s0)
            }
        }
        val n = s0.players
        val handNo = s0.handNo + 1
        val rng = Rng(s0.seed xor (handNo * -0x61c8864680b583ebL))
        var variant = if (s0.setup.variant == TPVariant.MIXED) PLAYABLE[rng.nextInt(PLAYABLE.size)] else s0.setup.variant
        // a 4× boot hand needs at least two players who can pay it
        if (variant == TPVariant.FOUR_X_BOOT && s0.chips.count { it >= BOOT * 4 } < 2) variant = TPVariant.CLASSIC
        val boot = if (variant == TPVariant.FOUR_X_BOOT) BOOT * 4 else BOOT
        val inHand = (0 until n).map { s0.chips[it] >= boot }
        val dealer = nextFrom(s0.dealer, n) { inHand[it] }
        val cards = deck().toMutableList()
        rng.shuffle(cards)
        var k = 0
        val hands = List(n) { seat -> if (inHand[seat]) sortHand(List(3) { cards[k++] }) else emptyList() }
        val joker = if (variant == TPVariant.JOKER) cards[k] else null
        val chips = s0.chips.mapIndexed { i, c -> if (inHand[i]) c - boot else c }
        val put = inHand.map { if (it) boot else 0 }
        val s = s0.copy(
            handNo = handNo, dealer = dealer, chips = chips, hands = hands, inHand = inHand,
            folded = List(n) { false }, seen = List(n) { false }, put = put, pot = put.sum(), stake = boot,
            turn = nextFrom(dealer, n) { inHand[it] }, blindChaals = List(n) { 0 }, actions = 0, shown = emptyList(),
            phase = TPPhase.Betting, variant = variant, joker = joker, boot = boot,
        )
        return pre + TPStep(TPEvent.Dealt(handNo, dealer, boot, variant, joker), s)
    }

    // ------------------------------------------------------------------ moves

    private fun nextFrom(from: Int, n: Int, ok: (Int) -> Boolean): Int {
        var x = (from + 1) % n
        repeat(n) {
            if (ok(x)) return x
            x = (x + 1) % n
        }
        return from
    }

    /** Next seat after [from] still in the hand. */
    fun nextActive(s: TeenPattiState, from: Int): Int = nextFrom(from, s.players) { s.inHand[it] && !s.folded[it] }

    /** The seat still in the hand just before [seat] (who played before it): the sideshow partner. */
    fun prevActive(s: TeenPattiState, seat: Int): Int {
        val n = s.players
        var x = (seat - 1 + n) % n
        repeat(n) {
            if (s.inHand[x] && !s.folded[x]) return x
            x = (x - 1 + n) % n
        }
        return seat
    }

    fun chaalCost(s: TeenPattiState, seat: Int, raise: Boolean): Int = s.stake * (if (s.seen[seat]) 2 else 1) * (if (raise) 2 else 1)

    fun options(s: TeenPattiState, seat: Int): TPOptions {
        if (s.handOver || seat !in 0 until s.players || !s.inHand[seat] || s.folded[seat]) return TPOptions()
        val canSee = !s.seen[seat]
        val ph = s.phase
        if (ph is TPPhase.Sideshow) return TPOptions(canSee = canSee, mustReply = ph.to == seat, myTurn = ph.to == seat)
        if (s.turn != seat) return TPOptions(canSee = canSee)
        val chips = s.chips[seat]
        val cost = chaalCost(s, seat, false)
        val raiseCost = chaalCost(s, seat, true)
        val afford = chips >= cost
        val active = s.active
        val prev = prevActive(s, seat)
        return TPOptions(
            canSee = canSee, myTurn = true, canPack = true,
            canChaal = afford, chaalCost = cost,
            canRaise = s.stake < MAX_STAKE && chips >= raiseCost, raiseCost = raiseCost,
            canShow = afford && active.size == 2,
            canSideshow = afford && s.setup.sideshow && active.size >= 3 && s.seen[seat] && prev != seat && s.seen[prev],
            canAllIn = !afford,
        )
    }

    /** null if [a] is legal, otherwise a short reason. */
    fun validate(s: TeenPattiState, a: TPAction): String? {
        if (s.handOver) return "The hand is over"
        val o = options(s, a.seat)
        return when (a) {
            is TPAction.See -> if (o.canSee) null else "You already saw your cards"
            is TPAction.SideshowReply -> if (o.mustReply) null else "Nobody asked you for a sideshow"
            else -> when {
                !o.myTurn || o.mustReply -> "Not your turn"
                a is TPAction.Pack -> null
                a is TPAction.Chaal && !a.raise -> if (o.canChaal) null else "Not enough chips"
                a is TPAction.Chaal -> if (o.canRaise) null else "You can't raise now"
                a is TPAction.Show -> if (o.canShow) null else "Show needs exactly two players"
                a is TPAction.AllIn -> if (o.canAllIn) null else "You can still afford a chaal"
                a is TPAction.Sideshow -> if (o.canSideshow) null else "Sideshow not allowed now"
                else -> "Unknown move"
            }
        }
    }

    fun apply(s0: TeenPattiState, a: TPAction): List<TPStep> {
        val err = validate(s0, a)
        require(err == null) { err ?: "" }
        val seat = a.seat
        val steps = ArrayList<TPStep>()
        var s = s0
        when (a) {
            is TPAction.See -> {
                s = s.copy(seen = s.seen.set(seat, true))
                steps += TPStep(TPEvent.Saw(seat), s)
            }
            is TPAction.Pack -> {
                s = s.copy(folded = s.folded.set(seat, true))
                val left = s.active
                if (left.size == 1) {
                    s = s.copy(turn = -1)
                    steps += TPStep(TPEvent.Packed(seat), s)
                    award(s, left, WinReason.LAST_STANDING, steps)
                } else {
                    s = s.copy(turn = nextActive(s, seat))
                    steps += TPStep(TPEvent.Packed(seat), s)
                }
            }
            is TPAction.Chaal -> {
                val cost = chaalCost(s, seat, a.raise)
                s = pay(s, seat, cost).copy(
                    stake = if (a.raise) (s.stake * 2).coerceAtMost(MAX_STAKE) else s.stake,
                    blindChaals = if (s.seen[seat]) s.blindChaals else s.blindChaals.set(seat, s.blindChaals[seat] + 1),
                    actions = s.actions + 1,
                )
                s = s.copy(turn = nextActive(s, seat))
                steps += TPStep(TPEvent.Chaal(seat, cost, blind = !s.seen[seat], raised = a.raise), s)
            }
            is TPAction.Show -> {
                val cost = chaalCost(s, seat, false)
                s = pay(s, seat, cost).copy(turn = -1, actions = s.actions + 1)
                steps += TPStep(TPEvent.ShowCalled(seat, cost), s)
                val other = s.active.first { it != seat }
                // a tie goes against whoever asked for the show
                val winner = if (score(s, seat) > score(s, other)) seat else other
                val both = listOf(seat, other).sortedBy { it }
                s = s.copy(shown = both)
                steps += TPStep(TPEvent.Showdown(both, listOf(winner)), s)
                award(s, listOf(winner), WinReason.SHOW, steps)
            }
            is TPAction.AllIn -> {
                val cost = s.chips[seat]
                s = pay(s, seat, cost).copy(turn = -1, actions = s.actions + 1)
                steps += TPStep(TPEvent.AllIn(seat, cost), s)
                val inn = s.active
                val best = inn.maxOf { score(s, it) }
                val winners = inn.filter { score(s, it) == best }
                s = s.copy(shown = inn)
                steps += TPStep(TPEvent.Showdown(inn, winners), s)
                award(s, winners, WinReason.ALL_IN, steps)
            }
            is TPAction.Sideshow -> {
                val cost = chaalCost(s, seat, false)
                val to = prevActive(s, seat)
                s = pay(s, seat, cost).copy(turn = to, phase = TPPhase.Sideshow(seat, to), actions = s.actions + 1)
                steps += TPStep(TPEvent.SideshowAsked(seat, to, cost), s)
            }
            is TPAction.SideshowReply -> {
                val ph = s.phase as TPPhase.Sideshow
                val loser = if (!a.accept) -1 else if (score(s, ph.from) > score(s, ph.to)) ph.to else ph.from
                if (loser >= 0) s = s.copy(folded = s.folded.set(loser, true))
                s = s.copy(phase = TPPhase.Betting)
                s = s.copy(turn = nextActive(s, ph.from))
                steps += TPStep(TPEvent.SideshowResult(ph.from, ph.to, a.accept, loser), s)
            }
        }
        return steps
    }

    private fun pay(s: TeenPattiState, seat: Int, amount: Int): TeenPattiState = s.copy(
        chips = s.chips.set(seat, s.chips[seat] - amount),
        put = s.put.set(seat, s.put[seat] + amount),
        pot = s.pot + amount,
    )

    /** Gives the pot to [winners] (split evenly; odd chips go to the first winner left of the dealer). */
    private fun award(s0: TeenPattiState, winners: List<Int>, reason: WinReason, steps: MutableList<TPStep>) {
        val n = s0.players
        val order = winners.sortedBy { (it - s0.dealer - 1 + n) % n }
        val share = s0.pot / order.size
        val amounts = order.mapIndexed { i, _ -> share + if (i == 0) s0.pot - share * order.size else 0 }
        val chips = s0.chips.toMutableList()
        val won = s0.handsWon.toMutableList()
        order.forEachIndexed { i, w ->
            chips[w] += amounts[i]
            won[w]++
        }
        var s = s0.copy(chips = chips, handsWon = won, turn = -1, phase = TPPhase.HandOver(order, s0.pot))
        steps += TPStep(TPEvent.PotWon(order, amounts, reason), s)
        val stillIn = chips.count { it >= BOOT }
        // an endless table never ends by itself: the player leaves when they like
        if (!s.endless && (s.handNo >= s.setup.hands || stillIn <= 1)) {
            s = s.copy(phase = TPPhase.MatchOver)
            steps += TPStep(TPEvent.MatchOver(s.ranking), s)
        }
    }

    private fun <T> List<T>.set(i: Int, v: T): List<T> = toMutableList().also { it[i] = v }

    fun suitSymbol(s: Int) = when (s) {
        0 -> "♠"
        1 -> "♥"
        2 -> "♦"
        else -> "♣"
    }
}
