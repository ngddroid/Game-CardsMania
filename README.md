# Cards Mania — Developer Guide

Cards Mania is an Android card-game collection written in Kotlin with Jetpack Compose. It contains **five games**:

| Game | Players | Code name | Rules code | Screen code |
|---|---|---|---|---|
| Classic Mania | 2–6, bots or Wi-Fi friends | `GameMode.CLASSIC` | `engine/` | `ui/game/` |
| Merciless Mania | 2–6 | `GameMode.CHAOS` | `engine/` | `ui/game/` |
| Flip Mania | 2–6 | `GameMode.ECLIPSE` | `engine/` | `ui/game/` |
| Spider Solitaire | 1 | — | `spider/` | `ui/spider/` |
| Mindi (team trick game) | 4, 6 or 8, bots or Wi-Fi friends | — | `mindi/` | `ui/mindi/` |

Everything works offline. Multiplayer uses only the local Wi-Fi network (no servers, no internet). All card art,
tables and icons are drawn in code; the only bundled assets are sounds, two fonts and two legal pages.

This guide explains how the code is organised, how one tap travels through the app, and how to change things safely.
Read §1–§5 first; after that, jump to the section for the part you're working on.

> Design history: [`docs/BUILD_PLAN.md`](docs/BUILD_PLAN.md) (original design, full Mania rules, naming and IP rules),
> [`docs/MULTIPLAYER_PLAN.md`](docs/MULTIPLAYER_PLAN.md) (Wi-Fi multiplayer) and
> [`docs/PHASE3_PLAN.md`](docs/PHASE3_PLAN.md) (Spider fixes, Mindi, game-style UI).

---

## Contents

1. [Quick start](#1-quick-start)
2. [Names in code vs. names on screen](#2-names-in-code-vs-names-on-screen-read-this-first)
3. [The big picture](#3-the-big-picture)
4. [How the app starts and moves between screens](#4-how-the-app-starts-and-moves-between-screens)
5. [The pattern every game follows (one tap, end to end)](#5-the-pattern-every-game-follows-one-tap-end-to-end)
6. [Folder map](#6-folder-map)
7. [Mania games (Classic / Merciless / Flip)](#7-mania-games-classic--merciless--flip)
8. [Spider Solitaire](#8-spider-solitaire)
9. [Mindi](#9-mindi)
10. [Wi-Fi multiplayer](#10-wi-fi-multiplayer)
11. [Saved data, profile and rewards](#11-saved-data-profile-and-rewards)
12. [UI system: theme, fonts, design kit, full screen, back button](#12-ui-system-theme-fonts-design-kit-full-screen-back-button)
13. [Audio, icons and other assets](#13-audio-icons-and-other-assets)
14. [Tests](#14-tests)
15. [How to make common changes](#15-how-to-make-common-changes)
16. [Gotchas we already ran into](#16-gotchas-we-already-ran-into)
17. [Known gaps / TODO before release](#17-known-gaps--todo-before-release)

---

## 1. Quick start

| Item | Value |
|---|---|
| Package / applicationId | `com.puzzel.lab.cardsmania` |
| Language / UI | Kotlin 2.2.10, Jetpack Compose (BOM 2026.02.01), Material 3, Navigation Compose |
| Build | AGP 9.4.1 (built-in Kotlin, no `kotlin-android` plugin), Gradle 9.6, Kotlin serialization plugin |
| SDK | `minSdk 24`, `compileSdk 37`, `targetSdk 37` |
| JDK | Gradle daemon toolchain 25 (`gradle/gradle-daemon-jvm.properties`); Android Studio's bundled JBR also works |
| Orientation | Portrait only |
| Libraries | DataStore (settings/profile), kotlinx.serialization (saves + network), coroutines, core-splashscreen, lifecycle |

Build, test and install:

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

* Test reports: `app/build/test-results/testDebugUnitTest/*.xml` (62 tests in 8 suites, §14).
* With several devices connected, pick one with `adb -s <serial>` (`adb devices` lists them).
* Release signing is **not** configured yet; add a `signingConfig` before publishing.

---

## 2. Names in code vs. names on screen (read this first)

The Mania games were renamed after play-testing, but the **internal enum names were kept** because they are written
into saved games and multiplayer messages. Renaming them would break saved games and stop phones on different app
versions from playing together. Always show text through the label helpers (`ModeInfo`, `Gems.name()`, `Face.title`);
**never show `enum.name` to players.**

| Code | Shown to players | Label comes from |
|---|---|---|
| `GameMode.CLASSIC` / `CHAOS` / `ECLIPSE` | Classic Mania / **Merciless** Mania / **Flip** Mania | `data/Catalog.kt` → `ModeInfo` |
| `Side.SUN` / `Side.MOON` | Light side / Dark side | UI strings |
| `Suit.RUBY, SAPPHIRE, EMERALD, AMBER` | Red, Blue, Green, Yellow | `ui/theme/Color.kt` → `Gems.name()` |
| `Suit.ROSE, AQUA, TANGERINE, VIOLET` | Pink, Teal, Orange, Purple (Dark side) | same |
| `Suit.WILD` | Wild | same |
| `Kind.BLOCK` / `BLOCK_ALL` | Skip / Skip Everyone | `engine/model/Cards.kt` → `Face.title` |
| `Kind.DRAW` | Draw One / Two / Four / Five | same |
| `Kind.PURGE` | Discard All | same |
| `Kind.ECLIPSE` | Flip | same |
| `Kind.PRISM` | Wild | same |
| `Kind.PRISM_DRAW` | Wild Draw Two / Four / Six / Ten | same |
| `Kind.PRISM_REBOUND` | Wild Reverse Draw Four | same |
| `Kind.COLOR_STORM` | Wild Color Storm | same |
| `Kind.PRISM_HUNT` | Wild Draw Color | same |
| "MANIA!" call | the one-card call | `Action.CallMania` |
| Overload (25 cards) | Knockout rule / "OVERLOAD!" | `GameEngine.checkOverload` |
| `Difficulty.GENIUS` | Genius (Mania) / **Expert** (Mindi) | `DifficultyInfo`, Mindi setup screen |

Spider and Mindi use standard playing cards and their real, generic names.

**IP rule:** never use "UNO", "UNO Flip", "No Mercy", "Mattel", or the name, logo or art of any other card-game app
anywhere: code, assets, store listing or screenshots. See `docs/BUILD_PLAN.md` §1.

---

## 3. The big picture

```
┌──────────────────────────────── UI layer (Jetpack Compose) ──────────────────────────────────┐
│ MainActivity (immersive) → nav/AppNavHost → screens                                           │
│                                                                                               │
│  Mania table             Spider table              Mindi table          Menus & lobby          │
│  ui/game/                ui/spider/                ui/mindi/            ui/home, ui/setup,     │
│  GameScreen              SpiderScreen              MindiScreen          ui/multiplayer, …      │
│   ├ TableCanvas           ├ Canvas                  ├ Canvas                                   │
│   │  └ SpriteWorld        │  └ SpiderWorld          │  └ MindiWorld                            │
│   └ GameViewModel         └ SpiderViewModel         └ MindiViewModel                           │
└──────┬───────────────────────────┬──────────────────────────┬─────────────────────────────────┘
       │ Action → List<Step>       │ move → SpiderResult      │ MindiAction → List<MStep>
┌──────▼───────────┐     ┌─────────▼──────────┐     ┌─────────▼───────────┐
│ engine/          │     │ spider/            │     │ mindi/              │   pure Kotlin:
│ GameEngine, bots │     │ SpiderEngine,      │     │ MindiEngine,        │   no Android code,
│ (BotAI,          │     │ SpiderSolver       │     │ MindiBot,           │   unit-tested on
│  MonteCarlo)     │     │                    │     │ MindiSearch         │   the JVM
└──────────────────┘     └────────────────────┘     └─────────────────────┘
┌──────────── net/ ───────────────┐  ┌──────────── data/ ────────────┐  ┌──── audio/ ─────┐
│ RoomHost · RoomClient · Redact  │  │ Settings + Profile (DataStore) │  │ SoundManager    │
│ RoomDiscovery (NSD) · Session   │  │ saved games, progression       │  │ MusicPlayer     │
│ JSON lines over TCP, same Wi-Fi │  │ catalog (bots, achievements…)  │  │ Haptics         │
└─────────────────────────────────┘  └────────────────────────────────┘  └─────────────────┘
          all shared services live in AppContainer (CardsManiaApp.kt)
```

Five principles hold everywhere:

1. **Rules live in pure Kotlin.** `engine/`, `spider/`, `mindi/` (and most of `net/`) have no Android imports, so they
   run and are tested on a plain JVM. The UI never decides what is legal; it asks the engine.
2. **Engines report *what happened*, not just the result.** Every move returns a list of events, each paired with the
   full state right after it. The ViewModel plays these one by one as animations. This is also what gets sent to
   other phones in multiplayer.
3. **One ViewModel per table drives the pace.** It queues engine steps, waits for each animation, then decides who acts
   next (you, a bot, or a remote player).
4. **The table is one `Canvas`.** Cards are sprites drawn from cached bitmaps by a "world" object (`SpriteWorld`,
   `SpiderWorld`, `MindiWorld`); buttons, avatars and dialogs are normal Compose on top.
5. **App-wide services sit in `AppContainer`** (`CardsManiaApp.kt`): repositories, audio, haptics, the multiplayer
   session and the "what to launch next" hand-off fields. Compose reads it with `LocalApp.current`; ViewModels with
   `(app as CardsManiaApp).container`.

---

## 4. How the app starts and moves between screens

### 4.1 Startup

1. `CardsManiaApp.onCreate` builds the `AppContainer`. It starts listening to settings (volumes, haptics) and pauses
   music when the app goes to the background.
2. `MainActivity.onCreate` installs the system splash, enables edge-to-edge drawing, **hides the navigation bar**
   (`Immersive.apply`, §12.4) and shows `AppNavHost` inside `CardsManiaTheme`.
3. `SplashScreen` plays its animation, pre-renders card bitmaps in the background and starts the menu music. Then it
   goes to **Onboarding** (first run), **Profile setup** (no profile yet) or the **Lobby**.

### 4.2 Navigation map (`nav/AppNavHost.kt`)

```
splash ─┬─> onboarding ─> profile_setup ─┐
        └─────────────────────────────────┴─> main  (Lobby · Events · Collection · Profile tabs)
                                                 │
   ┌───────────────┬───────────────┬─────────────┼──────────────┬────────────────┬──────────────┐
setup?mode=…    spider?daily=…   mindi_setup    mp_hub         settings        howto?mode=…
   │                               │             ├─> mp_host ──┐   └─> legal/{terms|privacy}
   └─> game                        └─> mindi     └─> mp_join ─> mp_lobby
         ▲                               ▲                     │
         └──── host/guest start a Mania or Mindi match ────────┘
```

| Route | Screen | Opened from |
|---|---|---|
| `splash` | `ui/splash/SplashScreen` | app start |
| `onboarding` | `ui/onboarding/OnboardingScreen` (4 pages, no skip button) | first run |
| `profile_setup` | `ui/onboarding/ProfileSetupScreen` | after onboarding |
| `main` | `ui/home/MainScreen` with `HomeTab`, `EventsTab`, `CollectionTab`, `ProfileTab` | lobby |
| `setup?mode=` | `ui/setup/GameSetupScreen` (Mania options) | Mania tiles |
| `game` | `ui/game/GameScreen` (Mania table) | setup START, Quick Play, Continue, Daily, multiplayer |
| `spider?daily=` | `ui/spider/SpiderScreen` | Spider tile; Events → Spider daily (passes today's date) |
| `mindi_setup` | `ui/mindi/MindiSetupScreen` | Mindi tile |
| `mindi` | `ui/mindi/MindiScreen` | Mindi setup, Events → Mindi daily, multiplayer |
| `mp_hub`, `mp_host`, `mp_join`, `mp_lobby` | `ui/multiplayer/*` | Multiplayer tile |
| `settings`, `legal/{doc}`, `howto?mode=` | `ui/settings`, `ui/legal`, `ui/howto` | lobby top bar, pause menus |

### 4.3 How a screen knows which game to start

Game configs are too big for route arguments, so they are handed over through `AppContainer`:

* **Mania:** set `app.pendingLaunch = GameLaunch(config, resume, daily, net)`, then navigate to `game`.
  `GameScreen` reads it once and clears it. `config == null` with `resume = true` loads the saved game.
* **Mindi:** set `app.pendingMindi = MindiLaunch(setup, resume, daily, net)`, then navigate to `mindi`.
* **Spider:** needs only the optional daily date, which travels in the route (`spider?daily=2026-11-09`).

**Play Again / Rematch** never reuses the old ViewModel. `GameScreen` bumps a `gameKey`, and the `mindi` route bumps
`mindiKey`; the new key gives a new `viewModel(key = …)` and a fresh table (see §16, gotchas 7 and 8).

---

## 5. The pattern every game follows (one tap, end to end)

All three tables work the same way. Here is what happens when you play a card in a Mania game:

```
finger lifts on a card
  └─ TableCanvas.pointerInput        hit-test: SpriteWorld.hitHand(x, y) → card id
      └─ GameViewModel.tapCard(id)   first tap selects, second tap plays (or swipe up → dragPlay)
          └─ submit(Action.Play(…))
              ├─ engine.validate()   null = legal, otherwise the move is refused (error sound)
              └─ engine.apply()      returns List<Step> = [(CardPlayed, state), (TurnChanged, state), …]
                  └─ enqueue(steps)  (host: also broadcast to guests)
                      └─ pump()      for each step:
                           ├─ frame = Frame(state, event)   → SpriteWorld.applyFrame: cards tween to new spots
                           ├─ fx.emit(frame)                → particles / text pops for that event
                           ├─ onEventFeedback()             → sound + vibration
                           └─ delay(Timing.of(event))       → wait for the animation
                      └─ afterIdle() whose turn now?
                           ├─ you          → enable input, start your turn timer
                           ├─ a bot        → scheduleBot(): think on Dispatchers.Default, then submit()
                           └─ remote seat  → wait for their action (30 s limit)
```

| Piece | Mania | Spider | Mindi |
|---|---|---|---|
| Rules | `engine/GameEngine` (mutable state, `copyDeep()` snapshots) | `spider/SpiderEngine` (immutable `SpiderState`) | `mindi/MindiEngine` (immutable `MindiState`) |
| One move returns | `List<Step>` (`GameEvent` + `GameState`) | `SpiderResult` (new state + `SpiderEvent`s) | `List<MStep>` (`MindiEvent` + `MindiState`) |
| Controller | `ui/game/GameViewModel` | `ui/spider/SpiderViewModel` | `ui/mindi/MindiViewModel` |
| What the canvas receives | `Frame` via `frame` / `fx` flows | `SpiderFrame(state, events, kind)` | `MStep` via the `steps` flow |
| Sprite world | `SpriteWorld` + `TableLayout` + `Fx` | `SpiderWorld` + `SpiderLayout` | `MindiWorld` + `MindiLayout` (reuses `Fx`) |
| Bots | `engine/ai/BotAI`, `MonteCarlo` | — (hints: `SpiderEngine.hints`) | `mindi/MindiBot`, `MindiSearch` |
| Saved game | `filesDir/saved_game.json` | `filesDir/spider_save.json` | `filesDir/mindi_save.json` |

**How the sprite worlds animate:** every visible card is a sprite with a current and a target position. When a new
state arrives, the layout object computes where each card *should* be. The world starts a tween (a curved path with
an ease-out, optional spin, delay and 3D flip via `android.graphics.Camera`) from where it is to where it should be.
The canvas redraws every frame (`withFrameMillis`) by drawing cached bitmaps, so even a full table is cheap.
**Timing rule:** the ViewModel's per-event wait must be at least as long as the world's tween for that event, or the
next step starts mid-animation.

**Bots never cheat.** They only read what a human in that seat could know: their own hand, played cards, hand sizes,
suits a player has shown they're out of (`voidMask` / `voids`), and in Flip mode the visible side of other players'
cards. The search bots (Genius / Expert) re-deal the hidden cards at random before each simulation.

---

## 6. Folder map

All Kotlin paths are under `app/src/main/java/com/puzzel/lab/cardsmania/`.

### App shell

| Path | What it does |
|---|---|
| `CardsManiaApp.kt` | `Application`, `AppContainer` (services + `pendingLaunch` / `pendingMindi`), `GameLaunch`, `LocalApp` |
| `MainActivity.kt` | System splash, edge-to-edge, `Immersive` (hides the navigation bar), hosts `AppNavHost` |
| `nav/AppNavHost.kt` | `Routes`, transitions, every screen's wiring and launch hand-off |

### Rules (pure Kotlin)

| Path | What it does |
|---|---|
| `engine/model/Cards.kt` | `GameMode`, `Side`, `Suit`, `Kind`, `Face` (points, titles, descriptions), `Card` (two faces in Flip) |
| `engine/model/DeckFactory.kt` | Deck lists: Classic 108, Merciless 168, Flip 112 (Light/Dark faces randomly paired each match) |
| `engine/model/GameState.kt` | `HouseRules`, `PlayerSetup`, `GameConfig` (effective rules per mode), `PlayerState`, `Phase`, `GameState` |
| `engine/Actions.kt` | `Action` (input), `GameEvent` (what happened), `DrawReason`, `Step`; all `@Serializable` |
| `engine/GameEngine.kt` | Mania rules: `newGame`, `validate`, `apply`, `canPlay`, `playableCards`, `jumpInCards` |
| `engine/Rng.kt` | Deterministic SplitMix64; its whole state is one `Long` (stored in the game state, so saves resume exactly) |
| `engine/ai/BotAI.kt` | Mania Easy/Normal/Hard bots, color choice, challenges, jump-in, catch timing, hint |
| `engine/ai/MonteCarlo.kt` | Mania Genius bot (simulation search, time budget `budgetMs`) |
| `spider/SpiderEngine.kt` | Spider rules, hints, error messages, `SpiderSolver` (winnable seeds, auto-finish) |
| `spider/SpiderData.kt` | `SpiderProfile`, `SpiderModeStats`, `SpiderSave`, `SpiderRules` (rewards, daily seed, time format) |
| `mindi/MindiEngine.kt` | Mindi rules: `MCard`, `MindiState`, `MindiPhase`, `MindiEvent`, `MindiAction`, `MindiEngine` |
| `mindi/MindiBot.kt` | Mindi bots (`MindiBot`, partner-aware) and Expert search (`MindiSearch`) |
| `mindi/MindiData.kt` | `MindiTrumpMode`, `MindiSetup`, `MindiStats`, `MindiProfile`, `MindiSeat` |

### Data, audio, network

| Path | What it does |
|---|---|
| `data/Repositories.kt` | `Settings` + `SettingsRepository`, `Profile` + `ProfileRepository` (JSON in DataStore), `SavedGameStore`, `Leveling`, `AppJson` |
| `data/Catalog.kt` | Bot characters, avatars, `TableTheme`, `AvatarFrame`, `ModeInfo`, `DifficultyInfo`, all achievements, weekly missions |
| `data/Progression.kt` | `GameFactory` (Mania configs, dates, daily), `DailyChallenge`, `MatchSummary`, `Progression` (rewards, gift chest, missions) |
| `audio/Audio.kt` | `Sfx` ↔ `res/raw`, `SoundManager` (SoundPool), `MusicPlayer` (menu/day/night cross-fade), `Haptics` |
| `net/NetProtocol.kt` | `Net` constants, `NetMsg`, `RoomState`, `RoomGame`, `Redact` (Mania), `MindiRedact`, `RoomCode` |
| `net/Connection.kt` | One TCP socket, one JSON message per line, write queue, heartbeat |
| `net/RoomHost.kt` | Host server: guests, lobby, seats, kick, start (Mania/Mindi), per-guest redacted broadcasts |
| `net/RoomClient.kt` | Guest: join, `room` / `status` flows, `game` channel, `starts` flow, `probe` |
| `net/RoomDiscovery.kt` | Android NSD (mDNS) advertise / discover / resolve, multicast lock |
| `net/NetworkInfo.kt` | Wi-Fi / hotspot detection and local IPv4 address |
| `net/MultiplayerSession.kt` | The current room (`host` or `client`), `NetRole`, device token, `startHostedGame` / `startHostedMindi` |

### UI

| Path | What it does |
|---|---|
| `ui/theme/` | `CM` palette, `Gems` (suit colors + names), fonts (`DisplayFamily` Lilita One, `BodyFamily` Nunito), `CardsManiaTheme` |
| `ui/design/` | Game UI kit v2 (§12.3): `GameIcons`, `JewelPanel`, `RibbonHeader`, `StrokeText`, `TagPill`, `HudBar`, `GameDock`/`GameTabBar`, `GameBackdrop`, `shineSweep`, `ShuffleWipe`, `GameOverlayHost`, `RewardPopup` |
| `ui/components/Components.kt` | `GameButton`, `RoundIconButton`, `OutlinedTitle`, `GlassPanel`, `RoyalBackground`, `AvatarBadge`, `CoinChip`, `LevelChip`, `ChoiceChip`, `GemSwitch`, `ScreenTitleBar` |
| `ui/kit/Kit.kt` | `rememberProfile` / `rememberSettings` (cached), `staggerIn`, `pressable`, `FaceImage` / `BackImage`, `GemDialog`, `GemSlider`, `TreasureChest`, `CoinBurst`, `StarRow`, `Pill`, … |
| `ui/render/CardArt.kt` | Mania card faces and backs drawn into cached bitmaps; `CardBack` (collectible backs) |
| `ui/render/CardIcons.kt` | Mania action icons and `SuitSymbols` (color-blind symbols) |
| `ui/game/` | Mania table: `GameScreen`, `GameViewModel`, `TableCanvas`, `SpriteWorld`, `TableLayout`, `TableBackground`, `Fx`, `GameOverlays`, `MatchTracker`, `Timing` |
| `ui/spider/` | Spider: `SpiderScreen`, `SpiderViewModel`, `SpiderWorld`, `SpiderLayout`, `SpiderDialogs`, `PlayingCardArt` (52-card faces, also used by Mindi and the lobby), `SpiderBackgrounds` |
| `ui/mindi/` | Mindi: `MindiSetupScreen`, `MindiScreen`, `MindiViewModel`, `MindiWorld`, `MindiLayout`, `MindiOverlays` |
| `ui/home/` | `MainScreen` (lobby shell, bottom dock, exit dialog), `HomeTab` (game tiles), `EventsTab`, `CollectionTab`, `ProfileTab` |
| `ui/setup/GameSetupScreen.kt` | Mania setup; exports `ModeTile`, `HouseRulesPanel`, `RuleRow`, `Label` (reused by multiplayer) |
| `ui/multiplayer/` | `MultiplayerHubScreen`, `HostRoomScreen`, `JoinRoomScreen`, `GuestLobbyScreen`, `MultiplayerCommon` |
| `ui/splash`, `ui/onboarding`, `ui/settings`, `ui/legal`, `ui/howto` | Splash, onboarding + profile setup, settings, Terms/Privacy WebView, How to Play (all 5 games) |

### Outside the Kotlin tree

| Path | What it is |
|---|---|
| `app/src/main/res/font/` | `lilita_one.ttf`, `nunito.ttf` (SIL Open Font License; texts in `assets/licenses/`) |
| `app/src/main/res/raw/` | 29 sound effects and 3 music loops (WAV), generated by `tools/gen_audio.py` |
| `app/src/main/res/drawable/` | Vector app icon layers and splash icon (generated by `tools/gen_icon.py`) |
| `app/src/main/assets/terms.html`, `privacy.html` | Legal pages (WebView, JavaScript off). **Contain placeholders** (`[Your Company]`, `[contact@yourcompany.com]`, `[Your Jurisdiction]`) |
| `app/src/main/AndroidManifest.xml` | Portrait activity, splash theme, network + vibrate permissions |
| `app/src/test/java/...` | All unit, simulation and multiplayer tests (§14) |
| `tools/` | `gen_audio.py`, `gen_icon.py`, `icon.svg`, `play_store_icon_512.png` |
| `docs/` | Design documents |

---

## 7. Mania games (Classic / Merciless / Flip)

### 7.1 The rules engine (`engine/GameEngine.kt`)

```kotlin
val (engine, steps) = GameEngine.newGame(config)   // deals, flips the starter, applies first-card rules
engine.validate(action)                              // null = legal, otherwise a reason string
val steps = engine.apply(action)                     // mutates engine.state, returns the events
```

* **`GameState` is mutable.** `copyDeep()` makes an independent copy for snapshots, bots and saving.
* **`Phase` says what input the game is waiting for**, and `state.actingPlayer` says who must give it:

| Phase | Waiting for |
|---|---|
| `Play` | the current player to play or draw |
| `ChooseColor(reason)` | a color (`reason`: START, WILD, FLIP or STORM) |
| `ChooseSwap` | the player who played a 7 to pick someone to swap hands with |
| `Challenge` | the victim of a Wild Draw to challenge or accept |
| `DrawnCard` | the player to play or keep the card they just drew |
| `RoundOver` / `MatchOver` | the next round / end of the match |

* **Rule switches are computed in `GameConfig`** (e.g. stacking is always on in Merciless; challenge turns off when
  stacking is on). Read `config.stacking`, `config.sevenZero`, … never `config.rules.*` directly.
* **Every visible change calls `record(event)`**, which saves the event plus a snapshot. At the end, `finish()`
  re-snapshots the **last** step because the phase or turn often changes after the last event. Guests rely on it.
* **MANIA! call:** dropping to one card without calling sets `state.maniaVulnerable`; `Action.Catch` makes that player
  draw 2; the vulnerability clears when any other player acts.
* **Safety guards:** `idlePasses` ends a round nobody can continue; `MAX_TURNS` (3000) prevents endless games;
  eliminated players' cards go back into the draw pile, so the card total never changes.

### 7.2 The table (`ui/game/`)

```
GameScreen ── key(gameKey) ── GameContent
   ├─ TableCanvas      one Canvas: felt, every card, effects; taps and drags
   │    └─ SpriteWorld (Sprite objects, Fx particles, TableBackground bitmaps)
   │          └─ TableLayout (seats, hand fan, piles; pixel values; seats rotate so *you* are at the bottom)
   └─ Compose overlays (GameOverlays.kt): seats, top bar, status pill, action bar (sort, hint, emote, MANIA!),
      color picker, challenge dialog, pause menu, round/match results
```

* `GameViewModel` is the controller described in §5. `version` increases on every change so an older bot or timer job
  never acts on a stale state.
* `MatchTracker` counts what the human did (cards played, catches, biggest stack…) for rewards and achievements.
* `CardArt.configure(widthPx)` sets the card bitmap size and **clears the cache**. The splash and the table use the same
  size (`max(0.22 × screen width, hand card width)`) so nothing is redrawn.
* **Daily Challenge:** `GameFactory.daily(date)` builds the same deal for everyone that day (the date is the seed).

---

## 8. Spider Solitaire

### 8.1 Rules (`spider/SpiderEngine.kt`)

* 104 cards (8 runs of 13) in 10 columns, 6/6/6/6/5/5/5/5/5/5 with the top card face up; 50 cards stay in the stock and
  are dealt 10 at a time.
* Every column must hold a card before dealing, unless fewer than 10 cards are left on the table.
* Any face-up card goes on a card one rank higher (any suit). Groups move only as same-suit runs. A full K→A run of
  one suit leaves the table automatically.
* Score = 500 − moves + 100 × finished runs (deals and undos count as moves).
* **The state is immutable**, so undo is a stack of earlier states (an undo keeps `moves + 1`).

| Function | Purpose |
|---|---|
| `newGame(suits, seed)`, `move(state, m)`, `deal(state)` | Play; `move`/`deal` return `null` if illegal |
| `runStart`, `canPickUp`, `canMove`, `canDeal` | Legality questions |
| `destinations(state, from, index)` | All valid targets, best first (same suit → any valid card → empty column) |
| `moveError`, `pickError` | Short player-facing reasons ("A 7 goes on an 8", …) |
| `hints`, `nextHint`, `fillMove`, `stuck` | Hint system: scored moves → deal → fill an empty column → none |
| `SpiderSolver.greedyWins`, `winnableSeed` | Find deals the hint system can win (all 1-suit games and every daily) |
| `SpiderSolver.finishMoves` | Search for a full solution once every card is face up (Auto Finish) |

### 8.2 Screen (`ui/spider/`)

* **`SpiderLayout`** is the single source of geometry for both drawing and touch, and is unit-tested. It spreads
  face-up cards as far as the tallest column allows (up to 45% of a card visible), and touch is forgiving: column hit
  areas include the gaps, tapping below a column means its bottom card, and touching above the movable run snaps to the
  run's first card. `leftHanded` mirrors the stock and completed piles.
* **`SpiderViewModel`** handles moves, deals, unlimited undo, hints, the 25-second idle hint, Auto Finish, the timer
  (paused in the background or while a dialog is open), saving, stats and rewards.
* **Two control styles** (Theme dialog, `SpiderProfile.selectMode`):
  * *Smart tap*: tapping a run sends it to the best spot; tapping the same run again tries the next spot.
  * *Tap, then target*: the run lifts and valid columns glow; tap a column to move there.
  * Dragging works in both. While dragging, valid columns glow and drops snap within ~1.3 card widths.
* **Animation:** `SpiderFrame(state, events, kind)` with `FrameKind` NEW_GAME, RESTORE, MOVE, DEAL, COLLECT, UNDO. A
  finished run is shown landing first, then flies to the completed pile.
* **Dialogs** (`SpiderDialog`): NEW_GAME (1/2/4 suits, replay), DAILY (calendar with bronze/silver/gold month tiers),
  LOOK (backgrounds, four-color suits, controls, idle hint, timer, left-handed), RULES, STUCK, STATS, plus the win
  dialog.
* **Art:** `PlayingCardArt.face(rank, suit, fourColor)` draws standard cards (rank 1 = Ace, suits 0–3 = ♠ ♥ ♦ ♣) and
  is also used by Mindi. `SpiderBackgrounds.render(bg, w, h)` draws the 8 table backgrounds.

---

## 9. Mindi

### 9.1 Rules (`mindi/MindiEngine.kt`)

* **Tables:** 4 players (2 vs 2, 52 cards, 13 each); 6 or 8 players (the four 2s removed: 8 or 6 each); or a double
  deck for 6/8 players (8 tens; 5 tens win the hand). `normalize(setup)` fixes invalid combinations.
* **Seats:** play goes `seat + 1`; `team = seat % 2`, so partners sit opposite.
* **Tricks:** follow suit if you can. The highest trump wins, otherwise the highest card of the suit led.
* **Only the 10s score:** most 10s wins the hand; equal 10s → most tricks; still equal → winner of the last trick.
* **Points:** win 1, Mendikot (all 10s) 2, Whitewash (all tricks) 3. First team to `setup.target` wins the match.
* **Who deals next ("the losers deal")** (`nextDealer`): dealer's team lost → same dealer; lost a whitewash → dealer's
  partner; won → next player.
* **Trump modes** (`MindiTrumpMode`):
  * **HIDE:** the player after the dealer hides a card (`MindiPhase.ChooseHidden`). The first time anyone can't follow
    suit it is revealed (`TrumpRevealed`) and goes back to its owner. Trump-suit cards played earlier in that same
    trick don't count as trumps. Optional `mustTrumpAfterReveal`.
  * **CUT:** the first card played off-suit sets trump (`TrumpCut`) and already counts as a trump.
  * **OPEN:** a random card is shown before the deal (`TrumpShown`).
* **Phases:** `ChooseHidden(seat)`, `Play`, `HandOver(result)`, `MatchOver(result, winnerTeam)`.
* **Events:** `Dealt`, `TrumpShown`, `HiddenChosen`, `TrumpRevealed`, `TrumpCut`, `Played`, `TrickWon`, `HandEnded`,
  `MatchEnded`, and the UI-only `Restored` (redraw after loading a save).
* Useful queries: `legalCards(state, seat)`, `validate`, `trickWinner`, `revealPending`, `unseen(state, seat)`.

### 9.2 Bots (`mindi/MindiBot.kt`)

* `MindiBot.decide(state, seat, difficulty, rnd)` returns a `MindiAction` for whoever must act.
* **Team play:** never trump a trick the partner is surely winning; give 10s to a partner who is winning; keep your own
  10s until they're safe; lead boss cards and safe 10s.
* **Expert** (`Difficulty.GENIUS`) uses `MindiSearch`: re-deals the unseen cards consistently with known voids, plays
  each candidate card to the end of the hand with the Hard heuristic, and picks the best average (budget `budgetMs`).
* `MindiBot.hint(state, me)` powers the Hint button.

### 9.3 Screen (`ui/mindi/`)

* **`MindiSetupScreen`:** hero, Continue match, Daily Challenge, players (4/6/8, double deck), trump mode, points to
  win, bot level, turn timer. Saves the chosen setup in `Profile.mindi.setup`.
* **`MindiScreen`:** top bar, **team tracker** (both teams' captured 10s and tricks), the always-visible trump
  indicator, seats with team colors (Blue = your team, Red = rivals), dealer badge, timer ring, hint / last trick /
  emote buttons, and overlays from `MindiOverlays` (last trick, hand podium + scoreboard, match result, pause).
* **`MindiLayout`** places you at the bottom (`me`), other seats around an oval, trick cards nudged toward whoever
  played them, and your hand as a fan that shrinks cards for long hands so every rank corner stays visible.
* **`MindiViewModel`** has three roles: LOCAL (you + bots), HOST and GUEST (§10.4). It pumps `MStep`s, runs bots,
  the turn timer, hints, save/resume (local only) and rewards (coins, XP, stats, achievements, daily stars).
* **Daily Challenge:** one hand against Hard bots in Hide mode (`MindiViewModel.dailySetup()`), seed from the date;
  stars: ★ win, ★★ 3+ tens, ★★★ 9+ tricks or a Mendikot.

---

## 10. Wi-Fi multiplayer

Design notes: [`docs/MULTIPLAYER_PLAN.md`](docs/MULTIPLAYER_PLAN.md). Supports Mania (2–6 seats) and Mindi
(4, 6 or 8 seats); empty seats are filled by bots.

### 10.1 How rooms work

```
Host phone                                           Guest phones
MultiplayerSession.hostRoom()                        MultiplayerSession.join(ip, port)
 ├─ RoomHost.start()  ServerSocket on 47820–47835     ├─ RoomClient  (Hello → Welcome → Lobby…)
 ├─ RoomDiscovery.advertise()  NSD "_cardsmania._tcp" └─ RoomDiscovery.startDiscovery() → room list
 └─ RoomState (game, mode/rules or mindi, seats, bots, guests)  ── Lobby messages ──► guest lobby screen
```

* **Finding a room:** nearby rooms appear in Join a Room through NSD (mDNS); `RoomClient.probe` fetches each room's
  details. If the router blocks mDNS, guests type the **room code** (`RoomCode`: the host's IPv4 address and port
  offset in 8 characters, e.g. `24L7-RJ32`).
* **Messages** are `NetMsg` objects encoded as JSON, one per line (`Connection`):

| Direction | Messages |
|---|---|
| Guest → host | `Hello`, `Probe`, `Act` (Mania action), `MindiAct` (Mindi action), `Emote`, `Leave` |
| Host → guest | `Welcome`, `Rejected`, `Lobby(RoomState)`, `Start` / `Sync` / `Steps` (Mania), `MindiStart` / `MindiSync` / `MindiSteps` (Mindi), `ActionRejected`, `Emote`, `Ended`, `Kicked` |
| Both | `Ping` (heartbeat) |

* **Settings** (`Net`): `PROTOCOL = 2`, ports 47820–47835, heartbeat 3 s, socket timeout 15 s, remote turn limit 30 s
  (then the host plays a safe move for that player). Mindi rooms can set their own turn timer.

### 10.2 The host decides everything

* Only the host runs the engine and the bots.
* Guests send their move (`Act` / `MindiAct`). `RoomHost` **overwrites the seat** in it with the guest's real seat
  (`withPlayer` / `withSeat`), so nobody can play for someone else. The host's ViewModel validates and applies it, or
  replies `ActionRejected`.

### 10.3 Each phone sees only what it should

* Every step is redacted separately for each guest before sending:
  * **Mania** (`Redact`): other hands and the draw pile become placeholder faces (`Redact.HIDDEN`); in Flip mode the
    publicly visible side is kept.
  * **Mindi** (`MindiRedact`): other players' cards become placeholders (counts kept), the hidden trump is hidden
    unless the viewer owns it, and the random seed is zeroed.
* Tests assert that nothing secret ever reaches a guest (§14).

### 10.4 Host and guest inside the ViewModels

* **HOST:** plays normally, but `enqueue()` also broadcasts each step (`broadcastSteps` / `broadcastMindi`). Remote
  seats' moves arrive through `RoomHost.actions` / `mindiActions`. A disconnected remote seat is played by a bot (BOT
  badge).
* **GUEST:** receives `Steps` / `MindiSteps` and plays them through the same pump and animations. It keeps a local copy
  of the latest state only to highlight playable cards; `submit()` sends the move to the host instead of applying it.
  Next Hand / Rematch / Room are host-only; guests see "Waiting for the host…".
* **Disconnects:** a dropped guest's seat is marked disconnected; the same device token can rejoin and receives a
  `Sync` / `MindiSync` snapshot. If the host leaves, everyone gets `Ended` ("The host left the game.").
* Multiplayer games are never saved for Continue; rewards are still given on each phone at the end.

### 10.5 Compatibility and permissions

* If you change `NetMsg`, `RoomState`, any engine event/action, or `GameState` / `MindiState` in a way old versions
  can't read, **increase `Net.PROTOCOL`**. Phones on different versions then get a polite "please update" instead of a
  broken game.
* Manifest: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`,
  `ACCESS_LOCAL_NETWORK`. On Android 17+, `ACCESS_LOCAL_NETWORK` is requested at runtime in `MultiplayerHubScreen`
  (`LocalNetworkPermission`).
* The app never contacts the internet. If you ever add internet traffic (ads, analytics), update `privacy.html` and the
  Play Data Safety form.

---

## 11. Saved data, profile and rewards

| What | Where | Code |
|---|---|---|
| Settings (volumes, haptics, speed, accessibility, sort, timer, onboarding done) | DataStore file `cardsmania`, one key each | `SettingsRepository` |
| Profile: name, avatar, coins, XP, unlocks, equipped cosmetics, Mania stats, achievements, gift, missions, daily stars, history, last Mania setup | DataStore key `profile_json` (one JSON string) | `ProfileRepository.update { }` |
| Spider progress and preferences | inside the profile: `Profile.spider` (`SpiderProfile`) | |
| Mindi progress and last setup | inside the profile: `Profile.mindi` (`MindiProfile`) | |
| Mania game in progress | `filesDir/saved_game.json` | `SavedGameStore` |
| Spider game in progress (incl. undo history) | `filesDir/spider_save.json` | `SpiderViewModel` |
| Mindi match in progress | `filesDir/mindi_save.json` | `MindiViewModel` |
| Multiplayer device token | `filesDir/device_token` | `MultiplayerSession` |

* **Adding fields:** every field in `Profile`, `SpiderProfile`, `MindiProfile` and `Settings` needs a default value.
  `AppJson` uses `ignoreUnknownKeys = true`, so old saves keep loading.
* **Rewards:**
  * Mania: `Progression.apply(profile, summary)` is the only place that changes coins, XP, stats, achievements, missions
    and daily stars after a match (`MatchTracker` builds the `MatchSummary`).
  * Spider and Mindi apply their own rewards when a game ends (`SpiderViewModel.onWon`, `MindiViewModel.finishMatch`).
* **Level:** `Leveling.levelFor(xp)`; each level needs 200 + 100 × (level − 1) XP.
* **Daily gift:** `Progression.giftReady` / `claimGift` (24 h cooldown, streak bonus).
* **Reset progress** (Settings) clears the whole DataStore, including settings, and deletes the Mania saved game.

---

## 12. UI system: theme, fonts, design kit, full screen, back button

### 12.1 Theme and fonts

* `ui/theme/Color.kt`: `CM` (royal purple + gold palette) and `Gems` (each suit's base/light/dark colors and display
  name).
* `ui/theme/Type.kt`: `DisplayFamily` = **Lilita One** (titles, numbers, buttons; it has one weight, so always use
  `FontWeight.Normal`), `BodyFamily` = **Nunito** variable font (weights 400–900).

### 12.2 Building blocks

* `ui/components/Components.kt`: the original components (`GameButton` with press lip and shine, `GlassPanel`,
  `OutlinedTitle`, `ChoiceChip`, `GemSwitch`, `ScreenTitleBar`, …). Most screens use these.
* `ui/kit/Kit.kt`: helpers (`rememberProfile`, `staggerIn`, `pressable`) and widgets (`GemDialog`, `GemSlider`,
  `TreasureChest`, `StarRow`, …).

### 12.3 Design kit v2 (`ui/design/`)

| API | Use it for |
|---|---|
| `GameIcons.*` | Custom vector icons (Lobby, Trophy, Chest, Profile, Settings, Hint, Undo, Coin, Crown, suits, …) |
| `JewelPanel(rim, fill, title)` | Beveled panel with a metal rim and studs (`Rim`, `PanelFill` enums) |
| `RibbonHeader`, `StrokeText`, `TagPill` | Section ribbons, outlined game text, small tags |
| `HudBar(profile, …)`, `LevelGem` | Lobby top bar: avatar with level ring, coins with "+" |
| `GameDock` (through `GameTabBar`) | Bottom navigation: the selected tab rises as a colored gem tile (Lobby amber, Events ruby, Collection emerald, Profile sapphire) |
| `GameBackdrop(style)`, `backdropFor(theme)` | Lobby background scene (spotlight, bokeh, sparkles) |
| `Modifier.shineSweep()` | Moving shine on primary buttons |
| `GameOverlayHost`, `LocalCoinFly`, `Modifier.coinFlyTarget()`, `LocalGameToasts` | Coins flying to the counter, achievement / level-up toasts (currently hosted in the lobby only) |
| `RewardPopup` | Reward screen with light rays and Collect |
| `ShuffleWipe` | Card-shuffle transition (built, not yet wired into navigation) |

**Reduce motion** (Settings) is respected by the animated pieces; read it with `rememberSettings().value.reduceMotion`.

### 12.4 Full screen

`MainActivity` hides the system navigation bar with `Immersive.apply(window)` in `onCreate`, `onResume` and
`onWindowFocusChanged`. `GemDialog` applies it to its own dialog window too. Swiping up from the bottom edge shows the
bar briefly, and the back gesture keeps working. Screens still read `WindowInsets` for the status bar.

### 12.5 Back button

| Screen | Back does |
|---|---|
| Lobby (`MainScreen`) | Other tab → Lobby tab; Lobby tab → "Leave Cards Mania?" dialog |
| Onboarding | Previous page (the first page leaves the app) |
| Settings, Setup, How to Play, Terms, Multiplayer hub/join, Mindi setup | Previous screen (navigation default) |
| Mania / Mindi table | Closes an open popup (emotes, last trick) → opens/closes the pause menu; after the match → home |
| Spider | Closes an open dialog, otherwise leaves (game saved); the win dialog's back goes home |
| Host room / guest lobby | Asks before closing / leaving the room |
| `GemDialog` | Dismisses it; non-dismissible dialogs can pass `onBack` |

---

## 13. Audio, icons and other assets

* **Sounds:** the `Sfx` enum maps 1:1 to `res/raw/sfx_*.wav` and references `R.raw` directly, so code shrinking keeps
  the files. Play with `app.sound.play(Sfx.X)`; `sound.snap()` picks a random card-snap.
* **Music:** `MusicPlayer.play(Track.MENU | DAY | NIGHT)`. Day and Night run in sync and cross-fade when a Flip game
  changes sides.
* **Regenerating:** `python3 -I tools/gen_audio.py` (standard library only) rewrites the sounds;
  `tools/gen_icon.py` rebuilds the icon from `tools/icon.svg`. Licensed files with the same names can replace them.
* **Fonts:** `res/font/lilita_one.ttf` and `res/font/nunito.ttf`, SIL Open Font License; the license texts ship in
  `assets/licenses/` and are credited in `privacy.html` / `terms.html`.

---

## 14. Tests

62 tests in 8 suites, all on the JVM (`./gradlew :app:testDebugUnitTest`).

| Test class | Covers |
|---|---|
| `engine/EngineRulesTest` | Mania deck counts, matching, Skip/Reverse (2 players), draws, stacking, Reverse Draw Four, knockout and last-standing, 7-swap / 0-rotate, Discard All, Skip Everyone, Color Storm, Flip, challenges, MANIA! catch, scoring |
| `engine/SimulationTest` | 2,700 bot-vs-bot Mania games with random house rules (cards conserved, no illegal moves, every game ends); Hard beats Easy; Genius plays legally |
| `data/ProgressionTest` | Rewards, achievements, streaks, daily always the same for a date, gift cooldown, saving/loading a Mania game in every mode |
| `spider/SpiderEngineTest` | 104-card decks, deal layout, move and group rules, run collection, scoring, deal needs filled columns, hints, 450 auto-played deals, daily deals always winnable |
| `spider/SpiderLayoutTest` | Spider spacing, forgiving hit-testing, auto-finish |
| `mindi/MindiEngineTest` | Deck sizes for every table, follow suit, trumps, Hide reveal + must-trump, Cut trump, revealed-suit follows aren't trumps, scoring + dealer rotation, partner teamwork, 1,000+ bot matches, Hard beats Easy, Expert plays legally |
| `net/MultiplayerTest` | Room codes, message round-trips, Mania redaction, host + 2 guests over localhost playing full Mania matches, full room rejects a third guest |
| `net/MindiMultiplayerTest` | Mindi message round-trips, redaction for every table/mode/seat, host + 3 guests playing full matches in each trump mode (guests that fake another seat are corrected), rejoin snapshot, seat limits |

**Testing multiplayer on devices:** install the APK on two devices (a phone plus an emulator on the same computer's
network works). Host on one (Lobby → Multiplayer → Host a Room), join on the other (Join a Room → from the list or
with the code).

---

## 15. How to make common changes

**Add a Mania card type**
1. Add a `Kind` in `engine/model/Cards.kt` (points, title, description, `shortLabel`, `drawAmount` if it draws).
2. Add it to `DeckFactory`; implement it in `GameEngine.resolve()` (and `doChooseColor` for Wilds).
3. Draw it: `CardIcons` + `CardArt.drawCenter` / `drawCorner`. Add a pop-up in `SpriteWorld.spawnEffects` and a sound
   in `GameViewModel.onEventFeedback`. Teach `BotAI.scoreCard`.
4. Test in `EngineRulesTest`, run `SimulationTest`, increase `Net.PROTOCOL`.

**Add a Mania house rule**
1. Field in `HouseRules` (with a default) → computed property on `GameConfig` if mode-dependent → use in `GameEngine`.
2. Row in `HouseRulesPanel` (`GameSetupScreen.kt`; the host room reuses it) and `rulesSummary()` in
   `ui/multiplayer/MultiplayerCommon.kt`. Increase `Net.PROTOCOL`.

**Change a Mindi rule or add a Mindi option**
1. Add the option to `MindiSetup` (with a default) and use it in `MindiEngine`; add a test to `MindiEngineTest`.
2. Show it in `MindiSetupScreen` and in the host room's Mindi options (`HostRoomScreen`).
3. Teach `MindiBot` if it changes strategy. Increase `Net.PROTOCOL`.

**Add a Spider preference**
1. Field in `SpiderProfile` (with a default), a setter in `SpiderViewModel` (`updatePrefs`), a toggle in the LOOK dialog
   (`SpiderDialogs.kt`).

**Add a whole new game** (follow Spider/Mindi as templates)
1. Rules package with an immutable state, an `apply`/move function returning events + states, and JVM tests.
2. `ui/<game>/`: a layout class (pure geometry), a world class (sprites on one Canvas), a ViewModel (pump events, run
   bots, save/resume, rewards) and a screen.
3. A route in `AppNavHost`, a tile in `HomeTab`, a How to Play tab, stats in `ProfileTab`, achievements in `Catalog`,
   and a line in `privacy.html` / `terms.html`.

**Add an achievement or mission:** entry in `Achievements.all` or `Missions` (`data/Catalog.kt`), then its condition in
`Progression.apply` (Mania), `SpiderViewModel.onWon` or `MindiViewModel.finishMatch`.

**Add a cosmetic:** entry in `TableTheme` / `AvatarFrame` (`Catalog.kt`), `CardBack` (`CardArt.kt`) or
`SpiderBackground` (`SpiderBackgrounds.kt`), with price and level. `CollectionTab` lists them automatically.

**Add a sound:** put `sfx_name.wav` in `res/raw`, add an `Sfx` entry, play it with `app.sound.play(Sfx.X)`.

**Add a screen:** a route in `Routes` + `composable(...)` in `AppNavHost`; build it from `GameBackdrop` /
`RoyalBackground`, `ScreenTitleBar`, `JewelPanel` / `GlassPanel` and `GameButton`; handle the status-bar inset; make
sure back does something sensible (§12.5).

---

## 16. Gotchas we already ran into

1. **Tap handlers that keep an old action.** Inside `Modifier.pointerInput(key) { … }` a callback keeps its first
   value until the key changes. Wrap callbacks with `rememberUpdatedState` (see `GameButton`). This once made
   "Let's Play!" run the old "Next" action.
2. **Faded colors with gradients.** If a `Paint` that had a semi-transparent `color` gets a `shader`, Android multiplies
   the gradient by that alpha. Set `p.color = 0xFF000000.toInt()` before `p.shader = …` (done in `CardArt`,
   `SpriteWorld`, `TableBackground`, `PlayingCardArt`). This made every card look pale.
3. **The last step's snapshot** must reflect the final phase and turn (`GameEngine.finish()`); guests froze without it.
4. **`minSdk 24`:** use `java.util.Calendar` / `SimpleDateFormat`, not `java.time`, unless you enable desugaring.
5. **Room code field:** the dash is inserted automatically, so the field must reposition the cursor itself
   (`JoinRoomScreen`).
6. **Never display `enum.name`.** Use the label helpers (§2).
7. **A new game must get a fresh table.** "Play Again" creates a new `GameViewModel`; everything that draws or takes
   input is keyed on it (`key(gameKey)`, `remember(layout, vm)`, effects and `pointerInput` keyed on `vm`). Before this
   fix the table stayed attached to the finished game and looked stuck.
8. **ViewModels need the default factory.** A custom `ViewModelStoreOwner` can't create `AndroidViewModel`s (crash:
   `NoSuchMethodException <init> []`). For a fresh controller per match, pass a new key to `viewModel(key = …)` (see
   the `mindi` route).
9. **Hiding the navigation bar changes geometry.** Bottom insets became 0, so anything that assumed a bottom bar height
   moved. Always derive positions from `WindowInsets` and the layout classes, never from fixed pixel numbers.

---

## 17. Known gaps / TODO before release

* Replace the placeholders in `terms.html` and `privacy.html` (company name, contact email, jurisdiction).
* Add release signing and run `./gradlew lint` (not run yet).
* Coin-fly and toasts only work inside the lobby (`GameOverlayHost`); game screens use their own reward overlays.
  `ShuffleWipe` is built but not wired into navigation.
* **Not yet tested on a real device:**
  * the Android 17 `ACCESS_LOCAL_NETWORK` permission prompt (test devices run older Android versions)
  * two physical phones on a home router (device tests used a phone plus the emulator)
  * Mindi with 6 or 8 players and the Cut / Open trump modes (covered by automated tests only)
* A guest who loses the connection mid-game has to rejoin from Join a Room (no automatic reconnect).
* After **Reset progress**, the default profile appears only after the app restarts.
* `SpiderProfile.tapToMove` is stored but no longer used (replaced by `selectMode`).
* Store listing: never mention or compare to other card-game brands; content rating target 13+ / Everyone.
