# Cards Mania — Local Multiplayer (Same Wi-Fi) Plan

## Goal
Friends on the **same Wi-Fi** play one match together. One phone **hosts a room**, the others **join** it.
The host chooses the **mode** (Classic / Merciless / Flip), the number of **seats (2–6)**, how many seats are **bots**,
and the usual match options. No internet, no accounts, no server: everything stays on the local network.

## How it works

```
 Host phone (authoritative)                      Guest phones
 ┌─────────────────────────────┐   TCP (LAN)    ┌──────────────────────┐
 │ GameEngine  (real state)    │◀── actions ────│ ClientGameViewModel  │
 │ Bots (run on host)          │── steps ──────▶│ renders snapshots    │
 │ HostServer (ServerSocket)   │  (redacted     │ sends taps as Action │
 │ NSD advert "_cardsmania._tcp"│  per player)  │ NSD discovery / code │
 └─────────────────────────────┘                └──────────────────────┘
```

* **Host-authoritative.** Only the host runs the rules engine and the bots. Guests send *actions*; the host checks
  them with the same `validate()` used offline and broadcasts the resulting steps. A modified guest app can't cheat
  its way past the rules.
* **Hidden information stays hidden.** Each guest receives a *redacted* snapshot. Other players' hands and the draw
  pile have their face replaced, except the side that is public in Flip mode.
* **Discovery.**
  * Android **NSD (mDNS)** advertises the room; guests see it in a list (room name, mode, players x/y).
  * Some routers block mDNS, so every room also shows an **8-character room code** (it encodes the host's IP and
    port), and guests can type it in.
* **Transport.**
  * One TCP socket per guest, carrying newline-delimited JSON messages (kotlinx.serialization).
  * Heartbeat every 3 s; a guest is dropped after 15 s of silence.
  * The host listens on a port in 47820–47835.

## Messages (protocol v1)

| Guest → Host | Host → Guest |
|---|---|
| `Hello(name, avatar, token, protocol)` | `Welcome(seatToken)` / `Rejected(reason)` |
| `Act(action)` (seat id is forced by the host) | `Lobby(room)`: players, seats, bots, mode, rules |
| `Emote(text)` | `Start(yourId, config, speed)` |
| `Ping` | `Steps(steps)`: event plus redacted snapshot |
| `Leave` | `Emote(playerId, text)`, `ActionRejected`, `Ended(reason)`, `Pong` |

## Seats and rules
* Seat 0 is the host. Guests take seats in the order they join.
* **Open seats** = seats − 1 − bots. When there are no open seats the room is full, and new joins are rejected.
* The host can't reduce seats (or raise bots) below what joined guests need. The host can **kick** a guest in the lobby.
* Bot difficulty applies to all bots; their characters are picked automatically.
* Turn timer: **30 s** per turn for guests in multiplayer; on timeout the host plays a safe move for them.
* **Disconnects:**
  * If a guest leaves mid-game, a bot (Normal) takes their seat.
  * If they rejoin with the same device token, they get the seat back.
  * If the host leaves, the room ends for everyone, and guests see "Host ended the game".
* **Pausing:** pause doesn't stop a multiplayer game; the menu offers Leave instead.
* **Rounds:** only the host starts the next round or a rematch.
* **Rewards:** every player earns coins and XP on their own device as usual. Multiplayer games are not saved for "Continue".

## Screens
1. **Home**: a highlighted "Multiplayer · Same Wi-Fi" banner with a glowing *NEW* badge.
2. **Multiplayer hub**: Wi-Fi status, **Host a Room**, **Join a Room**, and a "same Wi-Fi" tip. It asks for the Android 17 *local network* permission when needed.
3. **Host room**:
   * room name and code
   * player list (host, guests, bots, open seats) with kick buttons
   * mode, seats 2–6, bots 0–N, bot difficulty, starting hand, deck, match length, house rules
   * **Start game** button
4. **Join room**: list of rooms found nearby (auto-refreshing), "Enter room code", then the guest lobby (read-only settings, "Waiting for host…", Leave).
5. **Game**: the same table for everyone; the local player always sits at the bottom. Remote players show a Wi-Fi badge, and a disconnected player shows "BOT".

## Code layout
```
net/NetProtocol.kt      messages, room code, state redaction
net/Connection.kt       JSON-lines socket wrapper (read loop + write queue)
net/HostServer.kt       ServerSocket, guest registry, broadcast
net/RoomDiscovery.kt    NSD advertise / discover / resolve + multicast lock
net/NetworkInfo.kt      Wi-Fi check and local IPv4
net/MultiplayerSession.kt  lobby state machine for host and guest (lives in AppContainer)
ui/game/GameController.kt  shared interface for GameScreen
ui/game/GameViewModel.kt   local + HOST mode (remote humans, broadcasting)
ui/game/ClientGameViewModel.kt  guest mode
ui/multiplayer/*           hub, host room, join, guest lobby
```

## Permissions and policies
* `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` and `CHANGE_WIFI_MULTICAST_STATE` are install-time
  permissions. `ACCESS_LOCAL_NETWORK` is requested at runtime on Android 17+.
* The app still never contacts the internet. The privacy policy now says that during multiplayer the player name and
  avatar are shared with other phones in the same room on the local network only. The Play Data Safety form stays
  "no data collected", because nothing reaches the developer or third parties.

## Testing
* JVM tests: protocol round-trip for every message, redaction (no hidden faces leak), and a host plus two guests
  over localhost sockets playing a full match.
* Device: the Samsung hosts, and the Android emulator joins by room code; then the reverse.
