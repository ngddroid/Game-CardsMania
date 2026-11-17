# Cards Mania — Phase 3 Plan: Spider fixes · Mindi · Game-style UI overhaul

> Status: **waiting for your approval**. Nothing in this document has been built yet.
> Everything below is delivered in **one phase**, in the order of §8.

---

## 0. What I checked before writing this

| Check | Result |
|---|---|
| **Spider, played on your Realme phone through real taps and drags** | A script read the game from the phone and made every move with real touches: drags, taps and deals. It **won a full 1-suit game in 110 moves**: all 8 runs were collected, and the win screen, coins and achievements worked. **The rules work.** The problems are in how it *feels* to play with fingers (§1). |
| **Spider, deal preparation speed** | Under 20 ms, so it's not the cause. |
| **UI audit** | I took screenshots of Home, Events, Collection, Profile, Settings, Game Setup, the table and Spider (§4). |
| **Mindi research** | Read pagat.com, gamerules.com and others, the reference app (DroidVeda "Mindi", 4.3★, 100K+), OEngines Mindi, Mindi Plus, Gamostar Mindi, and the Callbreak.com table design (§2). |
| **Spider app research** | MobilityWare Spider (4.5★, 50M+), Brainium Spider, and Microsoft Solitaire Collection (§1). |

> If you saw a **specific** Spider problem (a crash, a card that won't move, a wrong rule), tell me what happened and I'll
> add it. The list in §1 is what I found by testing myself.

---

## 1. Spider Solitaire: what's wrong and the fix

### 1.1 Problems found

1. **Touch targets are far too small.**
   * Each card is 32 dp wide (10 columns).
   * Only **13 dp** of a face-up card and **5 dp** of a face-down card are visible in a column; a fingertip is about 45 dp.
   * Result: you often grab the card above or below the one you meant, or nothing at all. This is almost certainly the
     "not working" feeling.
2. **Most of the screen goes unused.** Cards use fixed spacing, so the bottom ~60% of the screen is empty while the
   cards stay cramped at the top.
3. **Tap-to-move picks the destination for you, with no way to choose another.** Tapping a card that is already on a
   valid card can send it to an unexpected column.
4. **Dragging gives no guidance.** Columns where the cards could go aren't highlighted, and an invalid drop just snaps
   back with no explanation.
5. **The top bar can be unreadable.** Gold text on the light "Candy Pop" background (your current choice) is hard to
   read. Light backgrounds also lower card contrast.
6. **Missing things players expect** (from the reference apps):
   * auto-complete
   * an idle hint pulse
   * daily trophies (bronze, silver, gold)
   * a "winnable deal" badge
   * left-handed layout
   * statistics from the toolbar
   * a clear "can't deal: fill empty columns" cue on the stock

### 1.2 Fixes

| # | Fix |
|---|---|
| S1 | **Smart spacing:** spread face-up cards over the full height (up to 45% of a card visible, compressing only when a column gets long), and use bigger cards (side margins and gaps cut to 2 dp, about +6% width). Face-up targets become **about 22–26 dp** tall instead of 13 dp. |
| S2 | **Forgiving touch:** column hit areas span the gaps; tapping empty space below a column means its bottom card; a tap on a card that isn't in the movable run snaps to the nearest valid run start; drag pick-up uses the finger's position, not the exact card edge. |
| S3 | **Two ways to move, your choice in Theme settings:** *Smart tap* (current behavior, but tapping again cycles through the other valid destinations) and *Tap, then tap target* (the selected run lifts and glows, and valid columns light up). Drag works in both. |
| S4 | **Drag guidance:** while you drag, valid columns glow green and the others dim slightly. Drops snap magnetically within 1.3 card widths. An invalid drop shakes and shows a short reason ("A 7 goes on an 8", "Only same-suit runs move together"). |
| S5 | **Readable HUD:** the top bar and stats sit on a dark glass plate with an outline on every background. A light background gets an automatic soft dark overlay behind the columns. Candy Pop is toned down. |
| S6 | **Auto-complete:** once every card is face-up and the stock is empty, an "Auto Finish" button appears and plays the game out with animation. |
| S7 | **Idle hint:** after 25 seconds without a move, the best move pulses softly. This can be switched off. |
| S8 | **Daily trophies:** the calendar gets crown tiers, with bronze at 10 days solved in a month, silver at 20, and gold for every day, plus a monthly trophy shelf. |
| S9 | **Stock feedback:** the stock shows deals left, and when dealing is blocked it shakes, outlines the empty columns and shows a message. |
| S10 | **Left-handed layout** (stock on the left) and **Statistics** in the New Game dialog and Profile. |
| S11 | **Polish:** a card "lift" with a bigger shadow on pick-up, a soft click on drop, and a gold sweep and sparkle trail when a run collects. |
| S12 | **Device test:** the same automated finger-play script plays 1, 2 and 4-suit games on your phone after the changes, and I check screenshots at every stage. |

---

## 2. Mindi: full design

**Mindi** (also called Mendikot or Dehla Pakad) is a traditional Indian trick-taking game. The game itself is
public-domain and the name is generic, so we can use "Mindi". As with the other games, we will **not** copy any
app's name styling, art, avatars or screens.

### 2.1 Table setups

| Setup | Teams | Deck | Cards each | Tricks |
|---|---|---|---|---|
| **4 players (default)** | 2 vs 2, partners opposite | 52 | 13 | 13 |
| 6 players | 3 vs 3, alternating seats | 48 (the four 2s removed) | 8 | 8 |
| 8 players | 4 vs 4, alternating seats | 48 (the four 2s removed) | 6 | 6 |
| Double deck (6 or 8 players) | as above | 104 (8 tens) | 17 / 13 | 17 / 13 |

You can play against bots offline, or **multiplayer on the same Wi-Fi** using the existing room system. In
multiplayer, the host picks the teams, or shuffles them.

### 2.2 Rules

* **Card order:** A K Q J 10 9 8 7 6 5 4 3 2.
* **Deal:** 5 cards each, then 4, then 4. The player after the dealer leads first.
* **Following suit:** you must follow suit if you can, otherwise play any card. The highest trump wins the trick; if
  no trump was played, the highest card of the suit led wins. The winner leads the next trick.
* **Only the four 10s ("mindis") score:**

| Result | When |
|---|---|
| **Hand win** | 3 or 4 tens (5+ with a double deck) |
| **Tie-break** | Equal tens: most tricks wins; if tricks are also equal (6/8 players), the team that won the last trick |
| **Mendikot** | All four 10s (all 8 with a double deck) |
| **Whitewash ("52-card mendikot")** | All tricks |

### 2.3 Trump modes (chosen in setup)

| Mode | How it works |
|---|---|
| **Hide trump (Band Hukum), default** | The player after the dealer secretly picks one card from their hand; its suit is trump. The first time anyone **can't follow suit**, the card is revealed with a big flip ("TRUMP: ♥") and goes back to its owner. **From that moment** that suit is trump. The player who triggered the reveal must play a trump if they have one (switchable). Before the reveal there is no trump. The hidden card can't be played while hidden; if it's still hidden at the last trick, it is revealed automatically. |
| **Cut trump (Katte)** | No trump at the start. The first card played by someone who can't follow suit sets trump for the rest of the hand. That card already counts as a trump in that trick and can be over-trumped. |
| **Open trump** | A random card is shown before the deal; its suit is trump. Easiest mode, good for beginners. |

### 2.4 Scoring and match

* **Match length:** first team to **5 points** (you can choose 3, 5, 7, 10 or a single hand). A normal win is
  **1 point**, a Mendikot **2**, and a whitewash **3**.
* **Dealer rotation ("the losers deal"):**

| Situation | Who deals next |
|---|---|
| The dealer's team loses | The same dealer deals again |
| The dealer's team loses a whitewash | The dealer's partner |
| The dealer's team wins | The deal passes to the next player |

  A **kot (Mendikot) counter** and a **winning-streak counter** are shown on the scoreboard.

### 2.5 Bots (Easy, Normal, Hard, Expert)

* **Bots only know what a real player at that seat would know.** They remember the cards played, which suits each
  player has shown they're out of, and whether trump is revealed.
* **They play as a team.** The most common complaint about other Mindi apps is a partner AI that plays against you.
  Our bots will:
  * never trump a trick their partner is already winning
  * **give a 10 to their partner** when the partner is sure to win the trick
  * hold their own 10s until they're safe
  * lead suits the partner can win
  * in Hide mode, decide whether to reveal and play trump, and choose a smart hidden card
  * in Cut mode, cut with a low trump when it wins a 10
* **Expert** uses the same simulation search as our Genius bot: it guesses the hidden hands from what it has seen,
  then plays out many possible endings to pick the best card.
* **Hint button:** suggests a card for you.

### 2.6 Table design (portrait, like the rest of the app)

* **Seats:**
  * You at the bottom (avatar inside your card fan).
  * **Partner at the top**, opponents on the left and right; 6 or 8 seats spread around an oval.
  * Team colors: **Blue** for your team, **Red** for opponents, shown as rings on every avatar.
* **Trick area:**
  * Cards land in the center, nudged toward the player who played them.
  * The winning card glows; then all cards **sweep into the winner's team pile** with a whoosh.
* **Trump indicator, always visible:**
  * Hide mode: a face-down card in a gold frame at the top. It flips with a banner and sound when revealed.
  * The trump suit is also highlighted on the cards in your hand.
* **Tens tracker, visible at all times:** shows **which 10s each team has captured** (top requests in reviews of other
  apps), plus each team's trick count. Capturing a 10 flies the card to the tracker with sparkles and a "MINDI!" pop.
* **Your hand:** fan sorted with **alternating red and black suits** (another top review request), with trump suit
  first. Cards you can't legally play are dimmed. Tap twice or swipe up to play.
* **Turn timer:** a ring around the active player's avatar (default 20 s, can be turned off). It is always on in
  multiplayer.
* **Last trick:** tap the team pile to see the previous trick.
* **End of hand:**
  * A podium screen with the winning team crowned, their captured 10s displayed, and a big **"MENDIKOT!"** or
    **"WHITEWASH!"** celebration when it applies.
  * Then a **scoreboard** listing each hand (tens, tricks, points), with the next dealer marked.
* **Other features:**
  * emotes
  * how-to-play with animated examples
  * a **Mindi Daily Challenge** (fixed deal against Hard bots, with 3 star goals)
  * achievements, stats and missions

### 2.7 Multiplayer support

The same-Wi-Fi room system gains a **game picker**: Classic, Merciless, Flip or **Mindi**. For Mindi, the host
chooses:
* seats: 4, 6 or 8
* bots to fill the empty seats
* trump mode
* match length
* teams (drag players between Blue and Red, or Shuffle)

As in the other games, the host phone runs the rules and every phone sees only its own hand. The protocol version is
raised so older app versions are refused politely.

---

## 3. Adding both games everywhere

| Place | Change |
|---|---|
| **Home / lobby** | New game grid (§4.3) with big tiles for Spider Solitaire and Mindi |
| **Onboarding** | Page 2 becomes "5 games in one": the three Mania modes, Spider and Mindi, animated. Page 3 mentions team play and Wi-Fi multiplayer. |
| **Splash** | Card fan shows a Mania card, a Spider ace and a Mindi 10 |
| **How to Play** | Tabs for all 5 games, including full Spider and Mindi rules with animated examples |
| **Privacy policy and Terms** | List the games; Mindi multiplayer uses the same local Wi-Fi rules; no new data |
| **Events** | Daily challenges for **Mania, Spider and Mindi**; weekly missions include Spider and Mindi |
| **Profile** | Stats tabs per game (Mania, Spider, Mindi) and a trophy cabinet |
| **Collection** | New tabs: Spider backgrounds and Mindi table felts |
| **Achievements** | About 8 new Mindi achievements (first win, Mendikot, Whitewash, comeback, Expert win, …) |
| **Multiplayer** | Mindi rooms (§2.7) |
| **Developer guide (README.md)** | New Mindi and UI-system sections |

---

## 4. Making the whole app look like a game

### 4.1 Audit: why it feels like an app, not a game

1. **Same purple glass card everywhere.** Home, Events, Collection, Profile and Settings all look like the same list
   of rounded rectangles.
2. **Home is a long scrolling list of banners.** Spider, Multiplayer, Continue, Daily and Gift are stacked vertically.
   Game lobbies use a **grid of big illustrated game tiles** with a HUD on top.
3. **System font.** Titles are the phone's own font with an outline. Every polished casual game uses a chunky display
   font.
4. **Standard Android icons** in the bottom bar and toolbars look generic.
5. **Little feedback when you earn something:** coins don't fly to the coin counter, there's no level-up popup, and
   reward dialogs have no light rays or chest-opening moment.
6. **Weak previews:** Collection shows locked squares, and Profile is a plain statistics sheet.
7. **Spider and the Mania table look like different apps**, with no shared frame or HUD style.

### 4.2 New design system

* **Fonts:**
  * Two free fonts (SIL Open Font License, free for commercial use) bundled into the app: **Lilita One** for titles,
    numbers and buttons, and **Nunito** for body text.
  * About 400 KB from the official Google Fonts GitHub repository.
  * **Approving this plan approves that download.** Otherwise I'll keep the system font.
* **Custom icons:** a hand-drawn icon set (in code) replaces the stock Android icons:
  * lobby, trophy, chest, avatar, settings, help
  * hint bulb, undo, deal, emote
  * coin "+", and others
* **New building blocks:**
  * ribbon section headers
  * beveled "jewel" panels with an inner glow and corner studs
  * chunky 3D bottom bar where the selected tab rises up
  * currency capsule with a "+" (opens the daily gift)
  * avatar with a level ring
  * buttons with a moving shine sweep
  * reward popups with spinning light rays
  * **coins that fly to the counter**
  * level-up and achievement toasts
* **Backgrounds:** a lobby "spotlight table" scene with depth (soft spotlight, drifting bokeh, sparkles). Each game
  tile has its own artwork and colors.
* **Transitions:** a card-shuffle wipe when entering any game.

### 4.3 Screen by screen

| Screen | Redesign |
|---|---|
| **Home → Game Lobby** | **Top HUD:** avatar, level ring, coins with "+", settings. **Game grid:** large tiles for Classic, Merciless, Flip, Spider Solitaire and Mindi, each with artwork, a title in the display font, a tag ("2–6 players", "Solo", "4–8 · Teams") and a PLAY ribbon. **Big Multiplayer tile:** full width, animated. **Side buttons:** Daily Gift chest with countdown, Daily Challenges, Continue. |
| Game setup screens | A hero header with game art; the options become visual cards (players shown as seats around a mini table, and so on) |
| Mania table | New HUD plate, custom icons, and a tidier action bar; the cards stay as they are |
| Spider | The §1 fixes plus the new HUD and toolbar style |
| Mindi | Built in the new style from the start |
| Events | Game tabs; calendars with crowns; missions as reward cards with chests; an achievement trophy wall |
| Collection | Real mini-table previews; locked items show the level with a padlock badge; buying plays a chest-opening animation |
| Profile | A large player card with a frame, per-game stat tabs, a trophy cabinet, and match history with game icons |
| Settings, How to Play, Multiplayer, dialogs | Use the new panels, ribbons, icons and fonts |
| Onboarding and splash | Updated for 5 games with the new style |

**Accessibility stays:** color-blind symbols, large cards, reduce motion, left-handed play.

---

## 5. IP and Play Store safety

* Mindi, Mendikot, Dehla Pakad and Spider Solitaire are traditional, generic game names, so they're safe to use.
  We use **our own** art, layouts, avatars, rule text and sounds.
* We never copy the look of the reference apps (DroidVeda, OEngines, MobilityWare, Brainium, Microsoft) and never
  mention them in the app or store listing.
* There are still no ads, no internet and no data collection, so the Data Safety form stays "No data collected".

---

## 6. What I need from you

1. **Approve this plan.** Approval includes downloading the two free fonts in §4.2.
2. *(Optional)* Describe the Spider problem you saw, if it isn't covered by §1.
3. **A USB cable would help.** Testing on your phone over Wi-Fi debugging keeps disconnecting (it dropped about 10
   times today). A cable makes the device tests much faster and more reliable; Wi-Fi still works if needed.

---

## 7. Testing (I play and test everything myself)

* **Unit tests:**
  * **Mindi:** deal sizes (4/6/8 players, double deck), follow-suit, all 3 trump modes (reveal timing, must-trump,
    hidden card restrictions, cut over-trumping), trick winner, 10s and tie-breaks, Mendikot and whitewash points, the
    dealer rotation table, match end.
  * **Spider:** the new hit-test and spacing math, auto-complete.
* **Simulation:**
  * 3,000+ bot-only Mindi hands across every setup: no lost or duplicated cards, every hand ends, no illegal plays.
  * Bot strength: Expert > Hard > Easy.
  * Teamwork check: bots never over-trump a partner who is already winning the trick.
* **Multiplayer:** a host and 3 guests over localhost play full Mindi matches, and hidden hands and the hidden trump
  never leak to another player.
* **On your phone:**
  * Finger-play scripts like today's play full Spider games (1/2/4 suits) and Mindi matches (each trump mode) through
    real taps.
  * Screenshots of every screen, checked against the audit list.
  * Frame-rate check.
* All existing tests (38) keep passing.

---

## 8. Build order (single phase)

| # | Step |
|---|---|
| 1 | Design system v2: download and bundle fonts, typography, custom icon set, new components (ribbons, jewel panels, HUD, reward popups, coin fly, transitions) |
| 2 | Spider fixes S1–S11 and new Spider tests |
| 3 | Mindi rules engine, deck setups, trump modes, scoring and dealer rotation, plus unit tests |
| 4 | Mindi bots (Easy to Expert) with teamwork rules, simulation tests |
| 5 | Mindi table: layout, card art reuse, trick animations, trump indicator, tens tracker, timer, last-trick view, end-of-hand podium, scoreboard |
| 6 | Mindi setup screen, daily challenge, achievements, stats, missions, saved-game resume |
| 7 | Mindi in Wi-Fi multiplayer: game picker, teams editor, protocol version bump, localhost tests |
| 8 | New Game Lobby (Home) with tiles for all 5 games and the Multiplayer tile |
| 9 | Restyle Events, Collection, Profile, Settings, How to Play, Multiplayer screens, dialogs, Mania table HUD |
| 10 | Onboarding, splash, How to Play (Spider and Mindi), privacy policy and terms updates |
| 11 | README developer guide update |
| 12 | Full test run, finger-play scripts on your phone, screenshot review, fix what turns up |

**Expected size:** about 35–45 new or changed Kotlin files, 6,000–8,000 lines.
