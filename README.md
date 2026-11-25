# Mindi & Mania: Color Card Game — Developer Guide

Mindi & Mania: Color Card Game (formerly Cards Mania; app ID `com.ngdstudio.mindimania`, which must never change once uploaded to Play) is an Android card-game collection written in Kotlin with Jetpack Compose. It contains **eight games**:

| Game | Players | Code name | Rules code | Screen code |
|---|---|---|---|---|
| Classic Mania | 2–6, bots or Wi-Fi friends | `GameMode.CLASSIC` | `engine/` | `ui/game/` |
| Merciless Mania | 2–6 | `GameMode.CHAOS` | `engine/` | `ui/game/` |
| Flip Mania | 2–6 | `GameMode.ECLIPSE` | `engine/` | `ui/game/` |
| Spider Solitaire | 1 | — | `spider/` | `ui/spider/` |
| Mindi (team trick game) | 4, 6 or 8, bots or Wi-Fi friends | — | `mindi/` | `ui/mindi/` |
| Bhabhi (last one holding cards loses) | 3–8, bots or Wi-Fi friends | — | `bhabhi/` | `ui/bhabhi/` |
| Teen Patti (best three cards, table chips) | 3–6, bots | — | `teenpatti/` | `ui/teenpatti/` |
| Rummy (13-card Points Rummy, table chips) | 2–6, bots | — | `rummy/` | `ui/rummy/` |
 
Every game works offline. Multiplayer uses only the local Wi-Fi network (no game servers). The only online parts are Firebase (analytics, crash reports, push) and Play in-app updates, see "Firebase, notifications and updates". All card art,
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
10. [Bhabhi](#10-bhabhi)
    * [Teen Patti](#10a-teen-patti)
    * [Rummy](#10b-rummy)
11. [Wi-Fi multiplayer](#11-wi-fi-multiplayer)
12. [Saved data, profile and rewards](#12-saved-data-profile-and-rewards)
13. [UI system: theme, fonts, design kit, full screen, back button](#13-ui-system-theme-fonts-design-kit-full-screen-back-button)
14. [Audio, icons and other assets](#14-audio-icons-and-other-assets)
15. [Tests](#15-tests)
16. [How to make common changes](#16-how-to-make-common-changes)
17. [Gotchas we already ran into](#17-gotchas-we-already-ran-into)
18. [Known gaps / TODO before release](#18-known-gaps--todo-before-release)

---

## 1. Quick start

| Item | Value |
|---|---|
| applicationId (Play Store ID) | `com.ngdstudio.mindimania` (permanent once uploaded) |
| Code package / namespace | `com.ngdstudio.mindimania` (internal only; players never see it) |
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

* Test reports: `app/build/test-results/testDebugUnitTest/*.xml` (141 tests in 17 suites, §15).
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

Bhabhi has the same shape: `ui/bhabhi/` (`BhabhiScreen` → Canvas → `BhabhiWorld`, `BhabhiViewModel`) on top of
`bhabhi/` (`BhabhiEngine`, `BhabhiBot`, `BhabhiSearch`), where one `BhabhiAction` returns a `List<BStep>`.

Five principles hold everywhere:

1. **Rules live in pure Kotlin.** `engine/`, `spider/`, `mindi/`, `bhabhi/` (and most of `net/`) have no Android imports, so they
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
   (`Immersive.apply`, §13.4) and shows `AppNavHost` inside `CardsManiaTheme`.
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
| `studio` | `ui/splash/StudioIntroScreen` | app start: NG Studio intro (~3.9 s, tap skips after 1.2 s), same as Balls Adventure; layers `res/drawable-nodpi/ngi_*.png` |
| `splash` | `ui/splash/SplashScreen` | after the studio intro |
| `language?first=` | `ui/language/LanguageScreen` (English / हिन्दी / ગુજરાતી) | first launch, right after the splash (`first=true`, no back); Settings → Language |
| `onboarding` | `ui/onboarding/OnboardingScreen` (4 pages, no skip button) | first run |
| `profile_setup` | `ui/onboarding/ProfileSetupScreen` | after onboarding |
| `main` | `ui/home/MainScreen` with `HomeTab`, `EventsTab`, `CollectionTab`, `ProfileTab` | lobby |
| `setup?mode=` | `ui/setup/GameSetupScreen` (Mania options) | Mania tiles |
| `game` | `ui/game/GameScreen` (Mania table) | setup START, Quick Play, Continue, Daily, multiplayer |
| `spider?daily=` | `ui/spider/SpiderScreen` | Spider tile; Events → Spider daily (passes today's date) |
| `mindi_setup` | `ui/mindi/MindiSetupScreen` | Mindi tile |
| `mindi` | `ui/mindi/MindiScreen` | Mindi setup, Events → Mindi daily, multiplayer |
| `bhabhi_setup` | `ui/bhabhi/BhabhiSetupScreen` | Bhabhi tile |
| `bhabhi` | `ui/bhabhi/BhabhiScreen` | Bhabhi setup (new game or Continue) |
| `mp_hub`, `mp_host`, `mp_join`, `mp_lobby` | `ui/multiplayer/*` | Multiplayer tile |
| `settings`, `legal/{doc}`, `howto?mode=` | `ui/settings`, `ui/legal`, `ui/howto` | lobby top bar, pause menus |

### 4.3 How a screen knows which game to start

Game configs are too big for route arguments, so they are handed over through `AppContainer`:

* **Mania:** set `app.pendingLaunch = GameLaunch(config, resume, daily, net)`, then navigate to `game`.
  `GameScreen` reads it once and clears it. `config == null` with `resume = true` loads the saved game.
* **Mindi:** set `app.pendingMindi = MindiLaunch(setup, resume, daily, net)`, then navigate to `mindi`.
* **Bhabhi:** set `app.pendingBhabhi = BhabhiLaunch(setup, resume)`, then navigate to `bhabhi`.
* **Spider:** needs only the optional daily date, which travels in the route (`spider?daily=2026-11-09`).

**Play Again / Rematch** never reuses the old ViewModel. `GameScreen` bumps a `gameKey`, and the `mindi` and `bhabhi`
routes bump `mindiKey` / `bhabhiKey`; the new key gives a new `viewModel(key = …)` and a fresh table (see §17, gotchas 7 and 8).

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

| Piece | Mania | Spider | Mindi | Bhabhi |
|---|---|---|---|---|
| Rules | `engine/GameEngine` (mutable state, `copyDeep()` snapshots) | `spider/SpiderEngine` (immutable `SpiderState`) | `mindi/MindiEngine` (immutable `MindiState`) | `bhabhi/BhabhiEngine` (immutable `BhabhiState`) |
| One move returns | `List<Step>` (`GameEvent` + `GameState`) | `SpiderResult` (new state + `SpiderEvent`s) | `List<MStep>` (`MindiEvent` + `MindiState`) | `List<BStep>` (`BhabhiEvent` + `BhabhiState`) |
| Controller | `ui/game/GameViewModel` | `ui/spider/SpiderViewModel` | `ui/mindi/MindiViewModel` | `ui/bhabhi/BhabhiViewModel` |
| What the canvas receives | `Frame` via `frame` / `fx` flows | `SpiderFrame(state, events, kind)` | `MStep` via the `steps` flow | `BStep` via the `steps` flow |
| Sprite world | `SpriteWorld` + `TableLayout` + `Fx` | `SpiderWorld` + `SpiderLayout` | `MindiWorld` + `MindiLayout` (reuses `Fx`) | `BhabhiWorld` + `BhabhiLayout` (reuses `Fx`) |
| Bots | `engine/ai/BotAI`, `MonteCarlo` | — (hints: `SpiderEngine.hints`) | `mindi/MindiBot`, `MindiSearch` | `bhabhi/BhabhiBot`, `BhabhiSearch` |
| Saved game | `filesDir/saved_game.json` | `filesDir/spider_save.json` | `filesDir/mindi_save.json` | `filesDir/bhabhi_save.json` |

**How the sprite worlds animate:** every visible card is a sprite with a current and a target position. When a new
state arrives, the layout object computes where each card *should* be. The world starts a tween (a curved path with
an ease-out, optional spin, delay and 3D flip via `android.graphics.Camera`) from where it is to where it should be.
The canvas draws cached bitmaps, so even a full table is cheap, and it **only redraws while something moves**
(see "Performance rules" below).
**Timing rule:** the ViewModel's per-event wait must be at least as long as the world's tween for that event, or the
next step starts mid-animation.

**Performance rules (keep them when you add a game or an effect):**
* **Tables sleep when still.** Each world implements `AnimatedWorld` (`ui/game/FrameDriver.kt`): `isBusy(now)` is true
  while a card moves (or waits on a delay), a drag eases, a flip runs or `fx.isActive(now)`; `idleFrameMs(now)` asks for
  a slow pace (about 30 fps) only while a time-based decoration is visible (pulsing glows, the Mania direction ring).
  The screen uses `rememberFrameTick(driver, world)` and calls `driver.wake()` after every change it makes to the world
  (frames, cues, selection, settings); `Modifier.wakeOnTouch(driver)` covers touches. A forgotten wake is caught by a
  500 ms safety net, so it shows up as a short lag before an animation, not a freeze. **Anything new that animates
  with `now` must be counted in `isBusy` or `idleFrameMs`**, or it will freeze between frames.
* **Nothing allocated per frame** in a world's `draw`/`update`: no `map {}`/`toHashSet()`/`Pair`/`Path()`/gradients/
  strings; cache them by size or value. Card bitmaps are looked up without building key strings
  (`CardArt` by `Face`/`CardBack`, `PlayingCardArt` by array index).
* **Animated values are read in the draw phase.** `val pulse by rememberPulse()` (or any `animateFloat`) and read it
  inside `graphicsLayer {}`, `drawBehind {}`/`drawWithCache {}` or a Canvas block, not in `Modifier.scale(..)`/`color = ..`,
  which recompose the whole composable every frame.
* **60 Hz.** `FrameRate.capAt60` (in `MainActivity.kt`) asks 90/120 Hz screens for their 60 Hz mode.
* **Baseline profile.** `app/src/main/baseline-prof.txt` (wildcard rules for the app and kotlinx.serialization) is
  compiled ahead of time at install, installed by `androidx.profileinstaller`.

**Bots never cheat.** They only read what a human in that seat could know: their own hand, played cards, hand sizes,
suits a player has shown they're out of (`voidMask` / `voids`), and in Flip mode the visible side of other players'
cards. The search bots (Genius / Expert) re-deal the hidden cards at random before each simulation.

---

## 6. Folder map

All Kotlin paths are under `app/src/main/java/com/ngdstudio/mindimania/`.

### App shell

| Path | What it does |
|---|---|
| `CardsManiaApp.kt` | `Application`, `AppContainer` (services + `pendingLaunch` / `pendingMindi`), `GameLaunch`, `LocalApp` |
| `i18n/Language.kt` | `AppLanguage` (EN, HI, GU), `LocaleManager` (saved choice + locale wrapping), `Strings` (translated text outside Compose) |
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
| `bhabhi/BhabhiEngine.kt` | Bhabhi rules: `BCard`, `BhabhiState`, `BhabhiPhase`, `BhabhiEvent`, `BhabhiAction`, `BhabhiEngine` |
| `bhabhi/BhabhiBot.kt` | Bhabhi bots (`BhabhiBot`: cut-risk heuristics) and Expert search (`BhabhiSearch`) |
| `bhabhi/BhabhiData.kt` | `BhabhiSetup`, `BhabhiStats`, `BhabhiProfile`, `BhabhiSeat` |

### Data, audio, network

| Path | What it does |
|---|---|
| `data/Repositories.kt` | `Settings` + `SettingsRepository`, `Profile` + `ProfileRepository` (JSON in DataStore), `SavedGameStore`, `Leveling`, `AppJson` |
| `data/Catalog.kt` | Bot characters, avatars, `TableTheme`, `AvatarFrame`, `ModeInfo`, `DifficultyInfo`, all achievements, weekly missions |
| `data/Progression.kt` | `GameFactory` (Mania configs, dates, daily), `DailyChallenge`, `MatchSummary`, `Progression` (rewards, gift chest, missions) |
| `audio/Audio.kt` | `Sfx` ↔ `res/raw`, `SoundManager` (SoundPool), `MusicPlayer` (menu/day/night cross-fade), `Haptics` |
| `net/NetProtocol.kt` | `Net` constants, `NetMsg`, `RoomState`, `RoomGame`, `Redact` (Mania), `MindiRedact`, `BhabhiRedact`, `RoomCode` |
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
| `ui/design/` | Game UI kit v2 (§13.3): `GameIcons`, `JewelPanel`, `RibbonHeader`, `StrokeText`, `TagPill`, `HudBar`, `GameDock`/`GameTabBar`, `GameBackdrop`, `shineSweep`, `ShuffleWipe`, `GameOverlayHost`, `RewardPopup` |
| `ui/components/Components.kt` | `GameButton`, `RoundIconButton`, `OutlinedTitle`, `GlassPanel`, `RoyalBackground`, `AvatarBadge`, `CoinChip`, `LevelChip`, `ChoiceChip`, `GemSwitch`, `ScreenTitleBar` |
| `ui/kit/Kit.kt` | `rememberProfile` / `rememberSettings` (cached), `staggerIn`, `pressable`, `FaceImage` / `BackImage`, `GemDialog`, `GemSlider`, `TreasureChest`, `CoinBurst`, `StarRow`, `Pill`, … |
| `ui/render/CardArt.kt` | Mania card faces and backs drawn into cached bitmaps; `CardBack` (collectible backs) |
| `ui/render/CardIcons.kt` | Mania action icons and `SuitSymbols` (color-blind symbols) |
| `ui/game/` | Mania table: `GameScreen`, `GameViewModel`, `TableCanvas`, `SpriteWorld`, `TableLayout`, `TableBackground`, `Fx`, `GameOverlays`, `MatchTracker`, `Timing` |
| `ui/spider/` | Spider: `SpiderScreen`, `SpiderViewModel`, `SpiderWorld`, `SpiderLayout`, `SpiderDialogs`, `PlayingCardArt` (52-card faces, also used by Mindi and the lobby), `SpiderBackgrounds` |
| `ui/mindi/` | Mindi: `MindiSetupScreen`, `MindiScreen`, `MindiViewModel`, `MindiWorld`, `MindiLayout`, `MindiOverlays` |
| `ui/bhabhi/` | Bhabhi: `BhabhiSetupScreen`, `BhabhiScreen` (table, HUD, result, pause), `BhabhiViewModel`, `BhabhiWorld`, `BhabhiLayout` |
| `ui/home/` | `MainScreen` (lobby shell, bottom dock, exit dialog), `HomeTab` (game sections, see below), `EventsTab`, `CollectionTab`, `ProfileTab` |
| `ui/setup/GameSetupScreen.kt` | Mania setup; exports `ModeTile`, `HouseRulesPanel`, `RuleRow`, `Label` (reused by multiplayer) |
| `ui/multiplayer/` | `MultiplayerHubScreen`, `HostRoomScreen`, `JoinRoomScreen`, `GuestLobbyScreen`, `MultiplayerCommon` |
| `ui/splash`, `ui/onboarding`, `ui/settings`, `ui/legal`, `ui/howto` | Splash, onboarding + profile setup, settings, Terms/Privacy WebView, How to Play (all 6 games) |

**The lobby (`HomeTab`)** groups the games into two tabs (`LobbySection`), switched with `SectionTabs`:
**Indian Card** first, which is where the lobby opens (Mindi, Rummy, Teen Patti, Bhabhi and Spider Solitaire), then **Card Mania**
(Classic, Merciless and Flip Mania + Quick Play). The Multiplayer tile sits below both tabs under "Play with friends". The chosen tab is kept in
`AppContainer.lobbySection` while the app runs. `WideGameTile` grows with its text, so large font settings never cut
off the PLAY ribbon.

### Outside the Kotlin tree

| Path | What it is |
|---|---|
| `app/src/main/res/font/` | `lilita_one.ttf`, `nunito.ttf` (SIL Open Font License; texts in `assets/licenses/`) |
| `app/src/main/res/values*/strings_*.xml` | All on-screen text: English in `values/`, Hindi in `values-hi/`, Gujarati in `values-gu/` (§13.6) |
| `app/src/main/res/raw/` | 29 sound effects and 3 music loops (FLAC; 3 tiny clips WAV), generated by `tools/gen_audio.py` |
| `app/src/main/res/mipmap-*/` | App icon: adaptive layers (`ic_launcher_foreground/background/monochrome.png`), Android 7 icons (`ic_launcher.png`, `ic_launcher_round.png`), all made by `tools/make_icon.py`; `drawable/splash_icon.xml` stacks the same layers for the launch screen |
| `app/src/main/assets/legal/` | Privacy Policy (`privacy_policy.html`), Terms & Conditions (`terms_conditions.html`), website home (`index.html`), `legal.css`, logos and the Lilita One font. Same layout as Balls Adventure; shown in a WebView (JavaScript off) and uploaded as-is as the website. Company NGENDEV TECHNOLABS LLP (NG Studio), store.ngdtechnolab@gmail.com, Indian law / Rajkot courts. |
| `app/src/main/AndroidManifest.xml` | Portrait activity, splash theme, network + vibrate permissions |
| `app/src/test/java/...` | All unit, simulation and multiplayer tests (§15) |
| `tools/` | `gen_audio.py`, `make_icon.py`, `icon_source.png` (the icon artwork, 512 × 512), `play_store_icon_512.png` |
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
* You can deal whenever the stock has cards. Empty columns don't block the deal: each one gets a face-up card like
  every other column (`canDeal` only checks the stock).
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
| `hints`, `nextHint`, `stuck` | Hint system: scored moves → deal → none (stuck = no useful move and an empty stock) |
| `SpiderSolver.greedyWins`, `winnableSeed` | Find deals the hint system can win (all 1-suit games and every daily) |
| `SpiderSolver.finishMoves` | Search for a full solution once every card is face up (Auto Finish) |
| `SpiderDealSolver.solve` | Full-knowledge solver (sees face-down cards and the stock) used only to grade deals; `effort` = positions looked at before the win |
| `SpiderDeals.SUITS_2`, `SUITS_4` | Generated graded deals (200 / 150 seeds): every one can be won, none is easy |

**Deals.** 1 suit: `SpiderSolver.winnableSeed` (the hint system can win it). 2 and 4 suits: a random seed from
`SpiderDeals` that is not in `SpiderProfile.recentDeals` (the last `SpiderRules.RECENT_DEALS` = 60 graded deals played).
`SpiderDeals.kt` is generated, not hand-written: `SpiderDealGenerator` (a test that only runs with `SPIDER_GENERATE=1`)
solves candidate seeds with `SpiderDealSolver` (staged search: look for moves that turn a card up, empty a column or
finish a run, try the best few depth first, deal onto the best arrangements when stuck; a breadth-first pass, then a
deeper best-first pass) until it has twice the wanted number of winnable deals, and keeps the hardest half by solver
effort. Rebuild it with
`SPIDER_GENERATE=1 ./gradlew :app:testDebugUnitTest --rerun --tests '*SpiderDealGenerator*'` (tens of minutes).
`SpiderGradedDealsTest` re-solves a sample, so a change to the deal shuffle in `SpiderEngine.newGame` (which would
change what every seed deals) fails the tests instead of silently shipping unchecked deals.

### 8.2 Screen (`ui/spider/`)

* **`SpiderLayout`** is the single source of geometry for both drawing and touch, and is unit-tested. It spreads
  face-up cards as far as the tallest column allows (up to 45% of a card visible), and touch is forgiving: column hit
  areas include the gaps, tapping below a column means its bottom card, and touching above the movable run snaps to the
  run's first card. `leftHanded` mirrors the stock and completed piles.
* **`SpiderViewModel`** handles moves, deals, unlimited undo, hints (only from the Hint button), Auto Finish, the timer
  (paused in the background or while a dialog is open), saving, stats and rewards.
* **Two control styles** (Theme dialog, `SpiderProfile.selectMode`):
  * *Smart tap*: tapping a run sends it to the best spot; tapping the same run again tries the next spot.
  * *Tap, then target*: the run lifts and glows gold; tap a column to move there.
  * Dragging works in both; drops snap to a valid column within ~1.3 card widths.
  * **No hints on a card:** picking up or dragging a run never shows where it can go (no green columns, nothing
    dimmed), and there is no automatic idle hint. Hints appear only when the player presses Hint.
    (`SpiderProfile.idleHint` is unused and kept only so saved profiles still load.)
* **Animation:** `SpiderFrame(state, events, kind)` with `FrameKind` NEW_GAME, RESTORE, MOVE, DEAL, COLLECT, UNDO. A
  finished run is shown landing first, then flies to the completed pile.
* **Dialogs** (`SpiderDialog`): NEW_GAME (1/2/4 suits, replay), DAILY (calendar with bronze/silver/gold month tiers),
  LOOK (backgrounds, four-color suits, controls, timer, left-handed), RULES, STUCK, STATS, plus the win
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
* **`MindiViewModel`** has three roles: LOCAL (you + bots), HOST and GUEST (§11.4). It pumps `MStep`s, runs bots,
  the turn timer, hints, save/resume (local only) and rewards (coins, XP, stats, achievements, daily stars).
* **Daily Challenge:** one hand against Hard bots in Hide mode (`MindiViewModel.dailySetup()`), seed from the date;
  stars: ★ win, ★★ 3+ tens, ★★★ 9+ tricks or a Mendikot.

---

## 10. Bhabhi

A traditional Indian shedding game (also called Thulla or Get Away). You against 2–7 bots, or friends on the same Wi-Fi (§11).

### 10.1 Rules (`bhabhi/BhabhiEngine.kt`)

* One 52-card deck (Ace high), dealt out completely to 3–8 players (`normalize` clamps the count). Seats play in order
  `seat + 1`; whoever holds the **A♠** opens the first trick with it.
* You must follow the suit led if you can.
  * **Everyone follows:** the trick is thrown away (`BhabhiEvent.Discarded`, cards go to `state.discarded`) and the
    highest card of the led suit leads next.
  * **Someone can't follow:** any other card is a **thulla** (`Played(thulla = true)`). The trick stops at once and
    the highest card of the led suit picks up every card of that trick (`PickedUp`), then leads.
* Emptying your hand gets you out (`Out(seat, place)`, `state.out` keeps the order). The last player holding cards is
  the **Bhabhi** (`BhabhiPhase.GameOver(loser)`). If everyone left runs out on the same trick, whoever played its
  highest card is the Bhabhi. If the next leader is already out, the next seat with cards leads (`nextActive`).
* **Wrong thulla** (`isWrongThulla`): the game never blocks another suit (`playableCards` = the whole hand, except the
  A♠ on the opening lead; `legalCards` = the correct moves, used by bots). Playing another suit while you still hold the
  led suit does not cut the trick: the card is just thrown in and play goes on. You are fined `PENALTY_CARDS` (5) cards:
  `fine` goes round from your left, each other player holding 2+ cards hands over one random card (nobody gives their
  last), so fewer than 5 when nobody can spare one. Event `Penalty(seat, given)`; the cards are public (`known`), the
  fined seat's `voids` lose those suits, and a wrong thulla never adds a void. Applies on the safe first trick too.
  Bots never play one.
* House rule `BhabhiSetup.safeFirstTrick` (on by default): no thulla on the opening spade trick; off-suit cards are
  just thrown away.
* Public information the bots may use lives in the state: `voids` (suits a seat showed it lacks), `known` (cards
  everyone saw a seat pick up), `discarded`. `unseen(state, seat)` lists what a seat can't see.
* Like the other engines, the last step of `apply` carries the final state (who leads next); `turn` is −1 while a
  finished trick resolves and once the game is over.

### 10.2 Bots (`bhabhi/BhabhiBot.kt`)

* The danger is holding the highest card of a trick that gets cut. `cutRisk` estimates the chance that someone still
  to play can't follow: certain if they showed a void; otherwise, for Hard, from the unseen cards (Normal assumes a
  small fixed risk).
* Following: dump the biggest card when the trick looks safe (or when playing last); when a cut looks likely, stay under
  the current high card, or play the lowest card above it so someone later may still overtake.
* Leading: the safest suit, preferring the shortest (running out of a suit enables your own thullas). Thulla/discard:
  throw the highest card, preferring a short suit.
* **Expert** (`Difficulty.GENIUS`) uses `BhabhiSearch`: deals the unseen cards at random (respecting voids and known
  cards; `sampleWorld` never falls back to the real hands), plays each candidate card to the end of the game with the
  Hard heuristic for everyone, and keeps the best average result (budget `budgetMs` = 160).
* Measured over thousands of 4-player games (how often the seat ends up the Bhabhi; fair share 25%): Easy vs Easy 24%,
  Normal vs Easy 7.5%, Hard vs Normal 11%, Expert (40 ms) vs Hard 11%.

### 10.3 Screen (`ui/bhabhi/`)

* **`BhabhiSetupScreen`:** hero, Continue game, players 3–8, the safe-first-round rule, bot level, turn timer. Saves
  the setup in `Profile.bhabhi.setup`. Reuses `ToggleRow` from the Mindi setup.
* **`BhabhiScreen`:** top bar (trick number, cards thrown away), a strip with the finishing order so far, seats with
  card counts (medals once out, a red BHABHI tag at the end), status pill (whose turn; **no hints**: it never names
  the suit to play or says you can thulla), an emote button (there is no Hint button), and a **FINISH** button once you are out (`fastForward`: the bots finish at 3× speed; offline only, since in a
  Wi-Fi game friends are still playing). Result screen lists
  the full finishing order; back = pause menu (or home after the game).
* **`BhabhiLayout`** is Mindi's geometry plus room for a second hand row: hands grow after pickups, so `handSlots` uses
  up to 16 cards per row and stacks rows.
* No card in your hand glows, lifts or dims to show what follows suit; only the card you picked is raised.
* **`BhabhiWorld`** animates the deal, plays, thrown-away tricks (gather, turn over, fade), pickups (fly to the taker,
  or into your hand), wrong-thulla fines (WRONG THULLA! +5, cards fly from each giver), getting out (medal burst) and the Bhabhi reveal.
* **`BhabhiViewModel`** (local, or HOST/GUEST in a Wi-Fi room like Mindi, §11.4): pumps `BStep`s, bots, turn timer (a timed-out turn is played correctly for you), save/resume
  (`bhabhi_save.json`), rewards: coins by finishing place × bot multiplier (20 for the Bhabhi), XP, stats
  (`BhabhiStats`), achievements `bhabhi_escape`, `bhabhi_first`, `bhabhi_thulla`, `bhabhi_clean`, `bhabhi_big`,
  `bhabhi_expert`.

---

## 10a. Teen Patti

Three-card Indian game for 3–6 players against bots (no Wi-Fi play yet). **Table chips only:** everyone starts a match
with `START_CHIPS` (2000); chips never leave the match, are never bought or sold, and only decide the finishing
order. Coins and XP come from the finishing place, like Bhabhi. Keep it that way: selling chips or turning them into
coins would make this real gambling and change the store rating.

### 10a.1 Rules (`teenpatti/TeenPattiEngine.kt`)

* **Endless table (the game's mode):** `TeenPattiSetup.hands = 0` (`state.endless`). Hand after hand until the player
  quits (pause → Quit; Continue resumes the same table). Before each deal, anyone below the boot gets a fresh
  `START_CHIPS` stack (`TPEvent.Refilled`): a broke bot leaves and a new character sits down, a broke player is topped
  up. There is no match end and no result screen; the ViewModel settles every hand instead (`settleHand`: coins for a
  hand won, scaled by bot level, XP, `TeenPattiStats`, achievements). A fixed `hands` count still works (tests, old saves).

* A fixed-length match (`hands` > 0, not offered in the UI any more) ends after that many hands, or early when one
  player or fewer can still pay the boot; most chips wins (`state.ranking`).
* Each hand (`nextHand`): every seat with at least `BOOT` (20) pays it and gets 3 cards. The dealer moves to the next
  seat still in; play starts on its left.
* Blind vs seen: `TPAction.See` is allowed at any time (not only on your turn) and does not end the turn.
  `chaalCost` = stake (blind) or 2 × stake (seen); a raise pays double and doubles the stake, capped at `MAX_STAKE`.
* Show (exactly 2 left): pay a chaal, compare; a tie goes against the asker. Sideshow (setup switch; asker seen,
  3+ left, previous active seat seen): pay a chaal, `phase = Sideshow(from, to)`, `to` must send `SideshowReply`;
  the lower hand packs, a tie packs the asker.
* All in: only when you can't afford a chaal. Puts in your last chips and shows every hand still in at once; equal
  best hands split the pot. This keeps the engine free of side pots.
* Ranking (`score`): kind × 100 000 + tiebreak; suits never break ties. A-K-Q is the top run; A-2-3 is second
  (`a23Second`, default) or lowest. `percentile` ranks a hand among all 22 100 (table built once).
* `options(state, seat)` returns what a seat may do and the cost of each move; the buttons and the bots both use it.
* Plain classic Teen Patti only: no variations (no Muflis, jokers or wild cards, 999 or bigger boots). Older saves
  that still carry a `variant` or `joker` field load fine (the JSON reader ignores unknown keys).
* `strength(state, seat)` = percentile among all 22 100 hands (one table per A-2-3 rule), used by the bots.

### 10a.2 Bots (`teenpatti/TeenPattiBot.kt`)

* Blind for a few rounds while it's cheap, then `See`. Seen: win chance ≈ percentile ^ (opponents + pressure), where
  pressure grows with seen opponents who keep paying. Weighed against pot odds and the bot's own stack; long hands
  make weak hands give up. Easy misreads its hand and bluffs more; Expert reads the betting hardest.
* Measured, 4 players, 10 hands, seat 0 vs three Normal bots (start 2000): Easy ≈ 1885 chips, Normal ≈ 1981,
  Hard ≈ 2082, Expert ≈ 2171 (`TeenPattiBotTest`). The game is mostly luck, so the gaps are small by design.

### 10a.3 Screen (`ui/teenpatti/`)

* **`TeenPattiSetupScreen`:** hero, Continue, players 3–6, hands, Sideshow and A-2-3 switches, bot level, turn
  timer, hand-rankings sheet (ⓘ). Saves the setup in `Profile.teenPatti.setup`.
* **Landscape:** the `teenpatti` route calls `LandscapeWhileShown()` (`nav/AppNavHost.kt`), which sets
  `SCREEN_ORIENTATION_SENSOR_LANDSCAPE` while the table is on screen and portrait again when it leaves (setup, How to
  Play and the lobby stay portrait). `MainActivity` handles orientation in `configChanges`, so turning never recreates
  it; the table just gets a new size, `TeenPattiLayout` switches to its landscape geometry (`landscape = width > height`:
  title top-left, opponents on an arc, my cards bottom-centre, buttons bottom-right, side insets for a camera cutout)
  and a new `TeenPattiWorld` redraws the table as it is via a `Restored` step. Dialogs scroll and stay ≤ 480 dp wide.
* **The dealer:** the middle of the table holds only the dealer and the pot total under her. She is drawn in code
  (`DealerLady.kt`, `drawDealer`): long dark hair, a red dress with a gold necklace, hands on the rail around the deck.
  She's painted on the table canvas between the felt (`TeenPattiWorld.drawBase`) and the cards (`drawTop`), so dealt
  cards and pops fly over her; cards are dealt from her hands (`layout.dealerHands`). She blinks off the clock
  (`DealerClock`), so the idle table only wakes for a blink. The status line ("Your turn", "… won this round") lives in
  the button area.
* **My seat (landscape):** straight under the dealer, like sitting across from her. Centre column: dealer → pot total
  → my avatar → my three cards. One row at the avatar's height on each side: left `[hand name] [BLIND/SEEN or WINNER]`,
  right `[name] [chips]` (`MySeatLandscape`, `layout.mySeat` / `myLabelRight` / `myInfoLeft`). The emote button moves
  to the top-right next to ⓘ. Opponents' opened rows are clamped above my area, and side seats stay above the button area. Checked at 420 and 480 dpi (≈411 / 360 dp tall).
* **Pace:** bots think 1.5–2.5 s per move, and the table no longer speeds up after the player packs.
* **`TeenPattiScreen`:** seats with chips, BLIND/SEEN/PACKED/OUT tags and a dealer badge; pot in the middle; my three
  big cards (tap them or SEE to look; the hand's name shows once seen); buttons SEE · SHOW or SIDESHOW · PACK · CHAAL ·
  RAISE (or ALL IN); a sideshow prompt when a bot asks me; result screen ranked by chips. The top bar reads
  "Teen Patti · HAND 4" with the boot and stake under it. Face-up cards (mine, and hands shown at
  the end) lie flat side by side (`spread` = 1.08 card widths apart) so every rank and suit is fully visible;
  opponents' face-down cards stay a compact fan.
  SEE is a green badge on my face-down cards (not in the button bar). **The reveal** (show / all-in): the cards turn
  up, the winner gets a WINNER ribbon, every opened seat keeps its hand name (TRAIL, PAIR…) under its chips and the
  status pill says "Momo won this round". The table waits 2.3 s after a showdown before dealing.
* **`TeenPattiWorld`** animates the deal, boot and bet chips flying to the pot, packs, the private sideshow reveal
  (only when I'm in it), showdown flips with hand names, and the pot flying to the winner. `popAt` keeps seat pops
  on screen.
* **`TeenPattiViewModel`:** same pump/bot/timer shape as Bhabhi. Seeing my cards while a bot thinks is applied
  directly (the bot's move stays valid). After a hand it deals the next one; a timed-out turn packs (or declines a
  sideshow). Save file `teenpatti_save.json`; achievements `tp_win`, `tp_trail`, `tp_pure`, `tp_blind`, `tp_bluff`,
  `tp_expert`; stats in `TeenPattiStats`.

---

## 10b. Rummy

13-card **Points Rummy** for 2–6 players against bots, landscape (`LandscapeWhileShown`), an endless table like Teen
Patti. Table chips only (`START_CHIPS` 4000; each point lost costs `POINT_VALUE` 5 chips; broke players are refilled).

### 10b.1 Rules (`rummy/RummyEngine.kt`, `rummy/RummyMelds.kt`)

* Two decks + 2 printed jokers = 106 cards (`RCard`: rank 1..13, Ace = 1; printed joker rank 0). 13 cards each; one card is
  cut face up (`state.cut`) and its rank is wild (`state.wild`; a cut printed joker makes Aces wild); one card starts
  the open pile.
* Turn: `PickClosed` / `PickOpen` (phase `Pick`) then `Discard` or `Declare` (phase `Discard`). `Drop` before picking:
  20 points before your first pick, 40 later. An empty closed pile is rebuilt from the open pile (`Reshuffled`).
* Declaring is two steps for people: `StartDeclare(card)` puts the card in the finish slot (phase
  `Declaring(seat, card)`, event `DeclareStarted`; others see it face down) and the player gets 30 s
  (`RummyViewModel.ARRANGE_MS`) on the "Arrange your cards and declare" screen; then `Declare(card, groups)`. When the
  time runs out the cards are declared as arranged. Bots (and a bot standing in for a player who left) declare in one go.
* `Declare(card, groups)`: the groups (card ids) must cover the other 13 cards. Valid (`RummyMelds.evaluate`) =
  every group valid, ≥ 2 sequences, ≥ 1 pure → phase `Showdown(declarer, pending)`: everyone still in sends
  `Arrange(groups)` and loses that arrangement's points. Invalid → `WrongShow` (80 points, out of the hand, play goes on).
  Last player standing wins too. `settle` pays points × `POINT_VALUE` (capped at what a loser has) to the winner.
* Melds: pure sequence (no joker standing in; a wild-rank card in its own place is fine; A-2-3 and Q-K-A, no wrap),
  impure sequence, set (3–4 cards, distinct suits), joker-only group. Points: A/K/Q/J 10, numbers face value, jokers 0,
  cap 80; no pure → all cards count; a pure but < 2 sequences → all but the pure count; else only invalid groups count.
* `RummyMelds.best(hand, wild)` searches the lowest-points arrangement: run windows and sets as templates, DFS over the
  natural cards (each joins a meld or is left over), jokers fill gaps (printed first), leftovers split by suit.
  `budget` caps the search; a 14-throw scan takes well under a second (`RummyEngineTest.bestIsFastEnoughForBots`).

### 10b.2 Bots (`rummy/RummyBot.kt`)

* Take the open card only when keeping it (and throwing something else) lowers their points by a margin; jokers always.
* Throw the card whose removal leaves the fewest points, then the fewest neighbours, then the highest value; never the
  card just taken; declare as soon as a throw leaves a valid hand. Hard/Expert drop hopeless openers; Expert won't
  feed the next player's suit. Measured: hands end in ≈ 15 turns; decisions ≈ 10 ms on average.

### 10b.3 Screen (`ui/rummy/`)

* **Table** (`RummyScreen`, `RummyLayout`): felt + oval table; opponents round the rim (avatar, name plate, fanned
  backs, DROPPED / WRONG SHOW / DECLARED / WINNER tags); open pile (left) and closed pile with the cut joker sideways
  under it (tap to pick; they glow on my pick); the dashed **finish slot** (tap with one card selected = declare);
  my plate on the table's edge (name, live `Points`, chips, turn timer); status line under the piles.
* **My hand**: groups along the bottom with a bar under each — 1st Life / 2nd Life (green), Set (blue), Joker,
  Invalid (red). Tap cards to select; GROUP makes a new group; tapping a bar moves the selected cards there; Sort
  groups by suit; Grouping ON keeps the best arrangement automatically (any manual change turns it off). A suit mark
  under each rank keeps overlapped cards readable (`RummyCardView(index = true)`); printed jokers are drawn in code;
  wild-rank cards carry a gold J. DROP / DISCARD / DECLARE appear when they apply; an invalid declare asks first.
* **Arrange screen** (someone declared): "ARRANGE YOUR CARDS AND DECLARE", Points Loss, Time Left (30 s), CLICK TO
  DECLARE; my cards stay live. **Result**: every player's groups labelled pure / impure / set / invalid N, points,
  Win/Lose chips, Chips Left; the next hand deals after 10 s (NEXT ROUND skips the wait; Home leaves).
* `RummyViewModel`: step pump with flying cards (`RFly`), bots (0.9–1.6 s), turn timer (time's up: draw blind and
  throw it back), save `rummy_save.json` (state, seats, my groups), rewards per hand, `RummyStats`, achievements
  `rm_win`, `rm_fast`, `rm_clean`, `rm_expert`.

---

## 11. Wi-Fi multiplayer

Design notes: [`docs/MULTIPLAYER_PLAN.md`](docs/MULTIPLAYER_PLAN.md). Supports Mania (2–6 seats), Mindi
(4, 6 or 8 seats), Bhabhi (3–8 seats), Teen Patti (3–6 seats, endless table, any variation) and Rummy (2–6
seats); empty seats are filled by bots. Teen Patti and Rummy rooms need 6 or fewer people (the tiles are disabled
otherwise). Spider is single-device only.

> **Teen Patti and Rummy rooms are switched off for now** (`Net.TEEN_PATTI_RUMMY_ROOMS = false`): the host room
> screen doesn't show their tiles, so they are played against bots only. All their Wi-Fi code below (messages,
> redaction, host/guest view models, tests) is kept and still tested; set the flag to `true` to offer them again.

### 11.1 How rooms work

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
| Guest → host | `Hello`, `Probe`, `Act` (Mania action), `MindiAct` (Mindi action), `BhabhiAct` (Bhabhi action), `TeenPattiAct`, `RummyAct`, `Emote`, `Leave` |
| Host → guest | `Welcome`, `Rejected`, `Lobby(RoomState)`, `Start` / `Sync` / `Steps` (Mania), `MindiStart` / `MindiSync` / `MindiSteps` (Mindi), `BhabhiStart` / `BhabhiSync` / `BhabhiSteps` (Bhabhi), `TeenPattiStart` / `TeenPattiSync` / `TeenPattiSteps`, `RummyStart` / `RummySync` / `RummySteps`, `SeatsChanged` (bots that replaced broke players), `ActionRejected`, `Emote`, `Ended`, `Kicked` |
| Both | `Ping` (heartbeat) |

* **Settings** (`Net`): `PROTOCOL = 6` (v3 added Bhabhi rooms, v4 the Bhabhi wrong-thulla fine, v5 Teen Patti and Rummy rooms, v6 the Rummy arrange-before-declare step), ports 47820–47835, heartbeat 3 s, socket timeout 15 s, remote turn limit 30 s
  (then the host plays a safe move for that player). Mindi rooms can set their own turn timer.

### 11.2 The host decides everything

* Only the host runs the engine and the bots.
* Guests send their move (`Act` / `MindiAct`). `RoomHost` **overwrites the seat** in it with the guest's real seat
  (`withPlayer` / `withSeat`), so nobody can play for someone else. The host's ViewModel validates and applies it, or
  replies `ActionRejected`.

### 11.3 Each phone sees only what it should

* Every step is redacted separately for each guest before sending:
  * **Mania** (`Redact`): other hands and the draw pile become placeholder faces (`Redact.HIDDEN`); in Flip mode the
    publicly visible side is kept.
  * **Mindi** (`MindiRedact`): other players' cards become placeholders (counts kept), the hidden trump is hidden
    unless the viewer owns it, and the random seed is zeroed.
  * **Bhabhi** (`BhabhiRedact`): other players' cards become placeholders (counts kept) and the seed is zeroed. The
    rest of the state is public (played, thrown-away and picked-up cards, voids), so hints work on a guest.
  * **Teen Patti** (`TeenPattiRedact`): other hands are placeholders until they are opened (`state.shown`: a show or
    the end-of-hand reveal). After an accepted sideshow, only the two players involved see each other's cards. Seed zeroed.
  * **Rummy** (`RummyRedact`): other hands and the closed pile are placeholders (counts kept); a card someone else
    draws blind stays hidden in `PickedClosed`. The open pile is public. All hands open on the result screen.
* Tests assert that nothing secret ever reaches a guest (§15).

### 11.4 Host and guest inside the ViewModels

* **HOST:** plays normally, but `enqueue()` also broadcasts each step (`broadcastSteps` / `broadcastMindi`). Remote
  seats' moves arrive through `RoomHost.actions` / `mindiActions` / `bhabhiActions` / `tpActions` / `rummyActions`. A disconnected remote seat is played by a bot (BOT
  badge).
* **GUEST:** receives `Steps` / `MindiSteps` and plays them through the same pump and animations. It keeps a local copy
  of the latest state only to highlight playable cards; `submit()` sends the move to the host instead of applying it.
  Next Hand / Rematch / Room are host-only; guests see "Waiting for the host…".
* **Disconnects:** a dropped guest's seat is marked disconnected; the same device token can rejoin and receives a
  `Sync` / `MindiSync` snapshot. If the host leaves, everyone gets `Ended` ("The host left the game.").
* **Teen Patti / Rummy:** a remote player who times out packs (Teen Patti; a sideshow request is declined) or draws
  blind and throws that card back (Rummy). After a Rummy declaration each remote player gets the arrange time plus 2 s,
  then the host submits their best grouping. Broke players are replaced by bots only; humans stay seated.
* Multiplayer games are never saved for Continue; rewards are still given on each phone at the end.

### 11.5 Compatibility and permissions

* If you change `NetMsg`, `RoomState`, any engine event/action, or `GameState` / `MindiState` in a way old versions
  can't read, **increase `Net.PROTOCOL`**. Phones on different versions then get a polite "please update" instead of a
  broken game.
* Manifest: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`,
  `ACCESS_LOCAL_NETWORK`. On Android 17+, `ACCESS_LOCAL_NETWORK` is requested at runtime in `MultiplayerHubScreen`
  (`LocalNetworkPermission`).
* The app uses Firebase (analytics, crash reports, push) and Play updates. If you add other internet traffic (ads, new SDKs), update `assets/legal/privacy_policy.html` and the
  Play Data Safety form.

---

## 12. Saved data, profile and rewards

| What | Where | Code |
|---|---|---|
| Settings (volumes, haptics, speed, accessibility, sort, timer, onboarding done) | DataStore file `cardsmania`, one key each | `SettingsRepository` |
| Profile: name, avatar, coins, XP, unlocks, equipped cosmetics, Mania stats, achievements, gift, missions, daily stars, history, last Mania setup | DataStore key `profile_json` (one JSON string) | `ProfileRepository.update { }` |
| Spider progress and preferences | inside the profile: `Profile.spider` (`SpiderProfile`) | |
| Mindi progress and last setup | inside the profile: `Profile.mindi` (`MindiProfile`) | |
| Bhabhi stats and last setup | inside the profile: `Profile.bhabhi` (`BhabhiProfile`) | |
| Mania game in progress | `filesDir/saved_game.json` | `SavedGameStore` |
| Spider game in progress (incl. undo history) | `filesDir/spider_save.json` | `SpiderViewModel` |
| Mindi match in progress | `filesDir/mindi_save.json` | `MindiViewModel` |
| Bhabhi game in progress | `filesDir/bhabhi_save.json` | `BhabhiViewModel` |
| Multiplayer device token | `filesDir/device_token` | `MultiplayerSession` |

* **All game save files go through `data/SaveFiles.kt`:** writes, deletes and reads run one at a time in order (an older
  position can't overwrite a newer one; a delete after a win can't be undone by a waiting write), and each write goes to
  `<name>.tmp` and is renamed over the save, so a crash mid-write keeps the previous save.

* **Adding fields:** every field in `Profile`, `SpiderProfile`, `MindiProfile`, `BhabhiProfile` and `Settings` needs a default value.
  `AppJson` uses `ignoreUnknownKeys = true`, so old saves keep loading.
* **Rewards:**
  * Mania: `Progression.apply(profile, summary)` is the only place that changes coins, XP, stats, achievements, missions
    and daily stars after a match (`MatchTracker` builds the `MatchSummary`).
  * Spider, Mindi and Bhabhi apply their own rewards when a game ends (`SpiderViewModel.onWon`,
    `MindiViewModel.finishMatch`, `BhabhiViewModel.finishGame`).
* **Level:** `Leveling.levelFor(xp)`; each level needs 200 + 100 × (level − 1) XP.
* **Daily gift:** `Progression.giftReady` / `claimGift` (24 h cooldown, streak bonus).
* **Reset progress** (Settings) clears the whole DataStore, including settings, and deletes the Mania saved game.

---

## 13. UI system: theme, fonts, design kit, full screen, back button

### 13.1 Theme and fonts

* `ui/theme/Color.kt`: `CM` (royal purple + gold palette) and `Gems` (each suit's base/light/dark colors and display
  name).
* `ui/theme/Type.kt`: `DisplayFamily` = **Lilita One** (titles, numbers, buttons; it has one weight, so always use
  `FontWeight.Normal`), `BodyFamily` = **Nunito** variable font (weights 400–900).

### 13.2 Building blocks

* `ui/components/Components.kt`: the original components (`GameButton` with press lip and shine, `GlassPanel`,
  `OutlinedTitle`, `ChoiceChip`, `GemSwitch`, `ScreenTitleBar`, …). Most screens use these.
* `ui/kit/Kit.kt`: helpers (`rememberProfile`, `staggerIn`, `pressable`) and widgets (`GemDialog`, `GemSlider`,
  `TreasureChest`, `StarRow`, …).

### 13.3 Design kit v2 (`ui/design/`)

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

### 13.4 Full screen

`MainActivity` hides the system navigation bar with `Immersive.apply(window)` in `onCreate`, `onResume` and
`onWindowFocusChanged`. `GemDialog` applies it to its own dialog window too. Swiping up from the bottom edge shows the
bar briefly, and the back gesture keeps working. Screens still read `WindowInsets` for the status bar.

### 13.5 Back button

| Screen | Back does |
|---|---|
| Lobby (`MainScreen`) | Other tab → Lobby tab; Lobby tab → "Leave Cards Mania?" dialog |
| Onboarding | Previous page (the first page leaves the app) |
| Settings, Setup, How to Play, Terms, Multiplayer hub/join, Mindi setup | Previous screen (navigation default) |
| Mania / Mindi / Bhabhi table | Closes an open popup (emotes, last trick) → opens/closes the pause menu; after the match → home |
| Spider | Closes an open dialog, otherwise leaves (game saved); the win dialog's back goes home |
| Host room / guest lobby | Asks before closing / leaving the room |
| `GemDialog` | Dismisses it; non-dismissible dialogs can pass `onBack` |

---

### 13.6 Languages (English, Hindi, Gujarati)

* **No hard-coded text.** Every string the player sees lives in `res/values/strings_<area>.xml` with the same keys in
  `values-hi/` and `values-gu/`. Areas and key prefixes: `lang_` (language screen), `shell_` (lobby, menus, onboarding,
  settings), `data_` (achievements, modes, cards, colors, themes), `mania_` (Mania table, setup, How to Play),
  `spider_`, `mindi_`, `bhabhi_`, `mp_` (multiplayer).
* **In a composable:** `stringResource(R.string.x, args…)` / `pluralStringResource(R.plurals.x, n, n)`.
  **Anywhere else** (ViewModels, catalogs, canvas pop-ups, toasts): `Strings.get(R.string.x, args…)` /
  `Strings.plural(…)`. Display names on models are getters, e.g. `Achievement.title`, `Face.title`, `Gems.name()`,
  `ModeInfo.title()`, `MindiTrumpMode.title`, so callers stay simple.
* **Rule engines stay English.** `GameEngine`, `SpiderEngine`, `MindiEngine`, `BhabhiEngine` keep their English
  validation messages (the tests check them); the UI builds the translated message instead (e.g.
  `spiderMoveErrorText`, `bhabhiSuitName`).
* **How the language is applied:** `LocaleManager` keeps the choice in SharedPreferences (`locale_prefs`, read
  synchronously). `MainActivity` and `CardsManiaApp` wrap their base context with it; changing it on the Language
  screen calls `LocaleManager.apply` and `activity.recreate()`. Before any choice the phone's language is used if it's
  Hindi or Gujarati, otherwise English.
* **Adding text:** add the key to all three files of the area, then run `python3 tools/check_strings.py` (same keys,
  same `%1$s` placeholders, escaped apostrophes). Kept in English on purpose: the name "Cards Mania", bot names,
  "MANIA!", "XP", room codes, card rank letters, and messages that travel over the network are sent in the sender's
  language. the legal pages in `assets/legal/` are English only.
* Fonts: Lilita One and Nunito have no Devanagari or Gujarati letters, so Android draws those with its built-in
  fallback fonts.

## 14. Audio, icons and other assets

* **Sounds:** the `Sfx` enum maps 1:1 to `res/raw/sfx_*` and references `R.raw` directly, so code shrinking keeps
  the files. They are lossless FLAC (half the size of WAV, gapless loops on every Android version); three clips
  shorter than one FLAC block (`sfx_tap`, `sfx_toggle`, `sfx_stack_tick`) stay WAV. Convert new ones on a Mac with
  `afconvert -f flac -d flac in.wav out.flac` and keep only one file per name. Play with `app.sound.play(Sfx.X)`; `sound.snap()` picks a random card-snap.
* **Music:** `MusicPlayer.play(Track.MENU | DAY | NIGHT)`. Day and Night stay in step and cross-fade when a Flip game
  changes sides; only the one you can hear is playing, the other joins at the same position for the fade.
* **Regenerating:** `python3 -I tools/gen_audio.py` (standard library only) rewrites the sounds as WAV; convert them
  to FLAC as above and delete each converted `.wav` (two files with one name break the build);
  `python3 tools/make_icon.py` rebuilds every app icon (launcher sizes, themed icon, splash, Play Store 512 PNG,
  website logo) from `tools/icon_source.png`; to change the icon, replace that picture and run it. Check
  `tools/build/icon_preview.png` (circle, squircle, rounded square and themed). Licensed files with the same names can
  replace the sounds.
* **Fonts:** `res/font/lilita_one.ttf` and `res/font/nunito.ttf`, SIL Open Font License; the license texts ship in
  `assets/licenses/` and are credited in `assets/legal/terms_conditions.html`.

---

## 15. Tests

87 tests in 10 suites, all on the JVM (`./gradlew :app:testDebugUnitTest`).

| Test class | Covers |
|---|---|
| `engine/EngineRulesTest` | Mania deck counts, matching, Skip/Reverse (2 players), draws, stacking, Reverse Draw Four, knockout and last-standing, 7-swap / 0-rotate, Discard All, Skip Everyone, Color Storm, Flip, challenges, MANIA! catch, scoring |
| `engine/SimulationTest` | 2,700 bot-vs-bot Mania games with random house rules (cards conserved, no illegal moves, every game ends); Hard beats Easy; Genius plays legally |
| `data/ProgressionTest` | Rewards, achievements, streaks, daily always the same for a date, gift cooldown, saving/loading a Mania game in every mode |
| `spider/SpiderEngineTest` | 104-card decks, deal layout, move and group rules, run collection, scoring, dealing also fills empty columns, hints, 450 auto-played deals, daily deals always winnable |
| `spider/SpiderLayoutTest` | Spider spacing, forgiving hit-testing, auto-finish |
| `spider/SpiderGradedDealsTest` | Enough distinct graded deals, sampled deals still winnable, none won by the hint system alone, recent deals skipped |
| `spider/SpiderRulesChecklistTest` | One test per Spider rule (setup, single and group moves, empty column, flip, dealing, completed runs, winning, 1/2/4 suits, score), plus 120 whole games re-checking every rule after each move |
| `mindi/MindiEngineTest` | Deck sizes for every table, follow suit, trumps, Hide reveal + must-trump, Cut trump, revealed-suit follows aren't trumps, scoring + dealer rotation, partner teamwork, 1,000+ bot matches, Hard beats Easy, Expert plays legally |
| `bhabhi/BhabhiEngineTest` | Deal for 3–8 players, A♠ opens, follow suit, thrown-away tricks, thulla pickup (later players don't play), safe first round on/off, getting out and the Bhabhi, everyone out on the same trick, next leader when the high card's owner is out, save/load, 1,080 bot games with every rule re-checked, Hard beats Easy, Expert only guesses hidden cards |
| `net/MultiplayerTest` | Room codes, message round-trips, Mania redaction, host + 2 guests over localhost playing full Mania matches, full room rejects a third guest |
| `net/MindiMultiplayerTest` | Mindi message round-trips, redaction for every table/mode/seat, host + 3 guests playing full matches in each trump mode (guests that fake another seat are corrected), rejoin snapshot, seat limits |

**Testing multiplayer on devices:** install the APK on two devices (a phone plus an emulator on the same computer's
network works). Host on one (Lobby → Multiplayer → Host a Room), join on the other (Join a Room → from the list or
with the code).

---

## 16. How to make common changes

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

**Add a whole new game** (follow Bhabhi, the newest and simplest, or Spider/Mindi as templates)
1. Rules package with an immutable state, an `apply`/move function returning events + states, and JVM tests.
   Measure the bots against each other (as in §10.2) so every level really plays better than the one below.
2. `ui/<game>/`: a layout class (pure geometry), a world class (sprites on one Canvas), a ViewModel (pump events, run
   bots, save/resume, rewards) and a screen.
3. A route in `AppNavHost`, a `pending…` launch slot in `AppContainer`, a tile in the right `HomeTab` section
   (`LobbySection`), a How to Play tab, stats in
   `ProfileTab`, achievements in `Catalog`, a line in `OnboardingScreen` and in `assets/legal/privacy_policy.html` / `terms_conditions.html`.

**Add an achievement or mission:** entry in `Achievements.all` or `Missions` (`data/Catalog.kt`), then its condition in
`Progression.apply` (Mania), `SpiderViewModel.onWon` or `MindiViewModel.finishMatch`.

**Add a cosmetic:** entry in `TableTheme` / `AvatarFrame` (`Catalog.kt`), `CardBack` (`CardArt.kt`) or
`SpiderBackground` (`SpiderBackgrounds.kt`), with price and level. `CollectionTab` lists them automatically.

**Add a sound:** put `sfx_name.flac` (or `.wav`) in `res/raw`, add an `Sfx` entry, play it with `app.sound.play(Sfx.X)`.

**Add a screen:** a route in `Routes` + `composable(...)` in `AppNavHost`; build it from `GameBackdrop` /
`RoyalBackground`, `ScreenTitleBar`, `JewelPanel` / `GlassPanel` and `GameButton`; handle the status-bar inset; make
sure back does something sensible (§13.5).

---

## 17. Gotchas we already ran into

0. **Never hard-code on-screen text.** Use a string resource in all three languages (§13.6); a check that compares
   against English words (as the room screen once did with "Bot") breaks in Hindi and Gujarati.
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
10. **A dragged card must fly back when the drop is refused.** While dragging, `SpiderWorld` moves the sprite's `x`/`y`
    but keeps its old target `tx`/`ty`. `retarget()` must decide "already there" from the current `x`/`y`; comparing
    only `tx`/`ty` once left refused cards stuck where they were dropped, and the next drag then missed its column.
11. **Screen edges belong to the system Back swipe.** With swipe navigation, a drag that starts on the first or last
    column is right at the screen edge and Android treats it as Back (Spider used to exit mid-drag). `SpiderTable` marks
    those two column strips with `Modifier.systemGestureExclusion`. Android normally honours only 200dp of exclusion per
    edge, but not while the navigation bar is hidden, which it always is here (§13.4). Check the result with
    `adb shell dumpsys window | grep mSystemGestureExclusion`.
12. **Tables that redraw forever drain the battery.** Every game screen once redrew 120 times a second while idle
    (1.8 CPU cores on a Galaxy A07, on the lobby too). Measure with
    `adb shell dumpsys gfxinfo com.ngdstudio.mindimania reset`, wait 10 s, then read "Total frames rendered".

---

## 18. Known gaps / TODO before release

* Upload `app/src/main/assets/legal/` as the website (e.g. Firebase Hosting) and put its `privacy_policy.html` link in Play Console.
* Add release signing and run `./gradlew lint` (not run yet).
* Coin-fly and toasts only work inside the lobby (`GameOverlayHost`); game screens use their own reward overlays.
  `ShuffleWipe` is built but not wired into navigation.
* **Not yet tested on a real device:**
  * the Android 17 `ACCESS_LOCAL_NETWORK` permission prompt (test devices run older Android versions)
  * two physical phones on a home router (device tests used a phone plus the emulator)
  * Mindi with 6 or 8 players and the Cut / Open trump modes (covered by automated tests only)
* A guest who loses the connection mid-game has to rejoin from Join a Room (no automatic reconnect).
* Teen Patti is bots only (no Wi-Fi rooms, Daily Challenge or Events entry yet).
* Bhabhi has no Daily Challenge or Events entry yet, and the optional "take a hand" house rule
  is not implemented.
* After **Reset progress**, the default profile appears only after the app restarts.
* `SpiderProfile.tapToMove` is stored but no longer used (replaced by `selectMode`).
* Store listing: never mention or compare to other card-game brands; content rating target 13+ / Everyone.

### Firebase, notifications and updates

| Feature | Code | Notes |
|---|---|---|
| Analytics | `analytics/Analytics.kt` | `Analytics.event(name, "key" to value)`; screen views come from the nav listener in `AppNavHost`. Events: `game_start` (every game: game, net, resume), `game_end` (game, won, players/place, net, daily; Teen Patti and Rummy send one per hand, Teen Patti with its variant), `daily_gift`, `mp_host`, `mp_join`, `notif_permission`, `app_update_offer`, `app_update_flow`. User property `app_language`. |
| Crashlytics | same object | Crash keys `screen`, `app_language`; `Analytics.recordError(t)` for caught problems. The Gradle plugin uploads the R8 mapping on release builds. |
| Privacy switch | `Settings.shareData` | Settings → Legal → "Share usage & crash data" turns Analytics and Crashlytics collection off (Firebase remembers it). |
| Daily reminder (local) | `notify/Reminders.kt` | WorkManager, every 24 h around 19:00; skipped if the app was opened in the last 8 h (`Settings.lastOpenAt`). Text: gift ready → today's Spider challenge → "the table is ready". Switch: Settings → Notifications → Daily reminder. Test it now (debug builds only, `src/debug`): `adb shell am broadcast -n com.ngdstudio.mindimania/.notify.DebugReminderReceiver`. Other debug-only commands: `.notify.DebugTokenReceiver` (prints the FCM token, `adb logcat -s FCMToken`) and `.debug.DebugUnlockAllReceiver` (unlocks every Collection item). |
| Push (FCM) | `notify/PushService.kt` | Send from Firebase console → Messaging, to topic `all`, or `lang_en` / `lang_hi` / `lang_gu`. Channel `news`. Switch off = leave topics + delete token. |
| Notification permission | `MainActivity.askForNotificationsOnce` | Android 13+: asked once after onboarding; turning a switch on in Settings asks again or opens the system page. |
| In-app updates | `update/InAppUpdates.kt` | Play update priority 4–5 → immediate, otherwise flexible + "Restart" banner. Only for Play installs. |

`app/google-services.json` must be for package `com.ngdstudio.mindimania`. Without Firebase every `Analytics` / `Push`
call does nothing (checked with `FirebaseApp.getApps`). Library versions: Firebase BoM 35.1.0 (needs Kotlin ≥ 2.3,
hence `kotlin = "2.3.21"`), google-services 4.5.0, Crashlytics plugin 3.0.8, WorkManager 2.12.0, app-update-ktx 2.1.0.
Play Console Data safety must declare: app activity (app interactions), app info and performance (crash logs,
diagnostics), device or other IDs; collected, not shared, optional (the player can turn it off).
* App name: Play title and `app_name` = "Mindi & Mania: Color Card Game" (30 characters, the Play limit); the
  home-screen label is `app_name_short` = "Mindi & Mania" (only ~12 characters fit under the icon). In-app sentences use
  "Mindi & Mania". Never put another brand (or a look-alike spelling of one) in the name or listing.
