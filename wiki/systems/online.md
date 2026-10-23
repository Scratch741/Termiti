# Online Multiplayer

> Online games run over a Node.js WebSocket server. `GameSession.js` manages game state; the client (`OnlineLobbyViewModel.kt`) communicates via messages.

## Architecture

```
Android client ←→ WebSocket ←→ Node.js server (GameSession.js)
```

The client sends actions (play, discard, decision_response) and the server sends back the updated state.

## Protocol version handshake

The client sends `protocolVersion` in its `JOIN` message. The server compares it to `PROTOCOL_VERSION` (`server/server.js`, currently `1`) which must match `PROTOCOL_VERSION` in `OnlineLobbyViewModel.kt`. On mismatch the server **rejects the JOIN** (does not close the socket, to avoid a reconnect loop) with:

```javascript
{ type: 'VERSION_MISMATCH', server: N, client: N, msg: 'Tvá verze hry je zastaralá. Aktualizuj aplikaci…' }
```

Bump `PROTOCOL_VERSION` (both sides) on any breaking change to the protocol or shared card data. Independent of the game's SemVer (see [[architecture]]).

## Heartbeat (zombie connection detection)

`server.js` runs a 30s ping/pong heartbeat (`wss.clients` → `ws.ping()`, `ws.isAlive` flag flipped by the `pong` handler, `ws.terminate()` if a client missed the previous ping). Without this, a connection that dies without a clean TCP FIN/RST (WiFi drop, OS suspending the app, app killed in background — all common on mobile) stays `readyState === OPEN` on the server **indefinitely** (until the OS-level TCP timeout, often hours) — the player becomes a permanent "ghost" in `players`/`queue`/`superQueue`: inflates/desyncs the online count shown to different clients (depends on when each one last received a `COUNT` broadcast) and can leave a real player stuck searching forever if the ghost occupies the matchmaking queue. `ws.terminate()` triggers the normal `'close'` handler, so ghost cleanup reuses the existing disconnect/reconnect-grace-period logic — no separate cleanup path needed. Fixed 2026-07-16.

## Key messages

### Client → Server
| Message | Description |
|---------|-------------|
| `JOIN` | Register; carries `protocolVersion` (handshake) |
| `play` | Play a card (cardId) |
| `discard` | Discard a card (cardId) |
| `decision_response` | Decision overlay pick (chosenId) |
| `mulligan` | Mulligan selection |

### Server → Client
| Message | Description |
|---------|-------------|
| `STATE` | Updated game state |
| `DECISION_REQUEST` | Show Decision overlay (effectType, options, timeoutMs; `resourceOptions` for DecisionChooseResource) |
| `CARD_LOST` | Card loss animation (burn, steal); `fromHand` marks losses taken out of a hand |
| `VERSION_MISMATCH` | Client protocol version incompatible → "update the app" |
| `GAME_OVER` | Game ended (result) |
| `GAME_ERROR` | Action error |

## Decision in online games

Server generates options via `_buildDecisionOptions()` and sends `DECISION_REQUEST`. Client shows overlay; after pick sends `decision_response` with `chosenId`.

**Timeout:** Remaining turn time + remaining timebank. On timeout the server auto-resolves to the first option.

**Timebank:** Each player has a timebank (seconds). Time spent on the Decision overlay is deducted from the timebank.

## Gotcha: `lastPlayedCard` masking CARD_LOST displays

`GAME_STATE`'s `lastPlayedCard` field always wins over a preceding `CARD_LOST` message on the client — the client's discard-slot / ghost display (`NewBattlefield`'s `lastCard`/`lastCardAction`) is a single value, not a queue, so whichever message the client processes *last* determines what the center slot shows. `this.lastPlayedCard` on the server is only reset in `_handleEndTurn`/`_handleSkipTurn`, **not** automatically after `_handlePlayCard`'s non-combo branch calls `_advanceTurn()` — so a stale "X played card Y" can survive into the *next* player's `GAME_STATE` and silently overwrite a same-tick `CARD_LOST` (e.g. an overdraw burn), even though the burn is still correctly logged (the log is an append-only list, unaffected).

**Fix pattern:** explicitly `this.lastPlayedCard = null; this.lastPlayedAction = null;` before the next `_sendStateBoth()`/`GAME_STATE` whenever a *more visually important* event (trap explosion, self-inflicted burn) needs to own the discard slot instead. Two applied instances (2026-07-16):
- `_advanceTurn()`: natural per-turn draw — reset on `traps.length > 0 || burned.length > 0` (previously traps-only, so plain overdraw at turn start was masked).
- `_handlePlayCard()`: reset when `selfLostCards.length > 0` (DrawCard/DrawBoth overdraw, RandomizeHands) and no Decision effect is pending (Decision overlay wants the "played" reveal behind it, and doesn't depend on `lastPlayedCard` anyway — it has its own `DECISION_REQUEST`).

Any new effect that can cause a self-inflicted card loss mid-play should route through the same `onSelfLoss` callback into `selfLostCards`, which is now automatically covered by this reset.

### Related: Decision effects that burn/steal from the OPPONENT (Likvidace, Zákeřný špeh)

`DecisionBurnOpponent` (Likvidace) and `PeekAndStealHand` (Zákeřný špeh) resolve via `_resolveDecision`, which sends a `causedByMe: true` `CARD_LOST` for the lost card — the client's `causedByMe` handler sets `lastPlayedCard.value` to that lost card (BURNED/STOLEN ring). Unlike a *direct* BurnCard/StealCard effect (where `this.lastPlayedCard` on the server still equals the played card when the next `GAME_STATE` fires, so the display self-corrects), `_resolveDecision` used to unconditionally null `this.lastPlayedCard` after every decision type — so for these two, nothing ever corrected the display back to "Likvidace was played"; the slot got stuck showing the burned/stolen card indefinitely.

**Fix (2026-07-16):** `_resolveDecision` skips the null-reset for `DecisionBurnOpponent`/`PeekAndStealHand`, so the *same* played-card gets resent in the next `GAME_STATE`, correcting the display. This would normally duplicate the `gameLog` entry (the client used to blindly append on every non-null `lastPlayedCard`) — fixed by adding client-side dedup keyed on the card's unique instance `id` (`lastLoggedPlayedCardId` in `OnlineLobbyViewModel`): the display (`lastPlayedCard`/`lastPlayedAction`/`lastPlayedByMe`) always updates, but the log only appends once per unique id.

## Paced combo reveal for the opponent (`_sendStateBothPaced`)

A combo card doesn't end the turn, so a fast player can play several combo cards back-to-back almost instantly. If every play broadcast `GAME_STATE` immediately to both sides (the normal `_sendStateBoth()`), the opponent's client could receive the *second* card's state before it had rendered/registered the first — the play would flash by unnoticed.

**The player's own experience must stay instant** — only the *opponent's* copy needs pacing. `_sendStateBothPaced(actingSide)` (used at both combo-continuation call sites — the non-Decision branch of `_handlePlayCard` and the combo branch of `_resolveDecision`) sends `actingSide`'s state directly, but routes the opponent's copy through a per-side FIFO queue (`_queueRevealFor` → `_pumpRevealQueue`) that delivers one message at a time, at least `REVEAL_MIN_INTERVAL_MS` (1000ms) apart. Each individual combo play still gets its own delivery to the opponent (never dropped or coalesced into just the latest state) — they just arrive spaced out instead of all at once.

**Ordering gotcha:** the plain `_sendStateBoth()` (turn end, discard, decision request, game end — anything authoritative that must land *now*) always wins over a still-draining paced queue. Without a flush, a stale queued reveal (e.g. from combo card 2) could arrive to the opponent *after* an unpaced "turn ended" message that was sent immediately following combo card 3 — an out-of-order flicker. `_sendStateBoth()` therefore calls `_clearRevealQueue()` for both sides before sending, discarding any not-yet-delivered paced reveals in favor of the fresh authoritative state.

Fixed 2026-08-26 (replacing an earlier, cruder fix that delayed *processing* PLAY_CARD itself — which also slowed down the acting player's own combo, not just the opponent's view of it).

## GameSession.js — key methods

| Method | Description |
|--------|-------------|
| `_handlePlayCard(side, data)` | Handles card play |
| `_buildDecisionOptions(side, effect, playedCardId)` | Generates Decision options |
| `_resolveDecision(side, chosenId)` | Applies Decision pick |
| `_advanceTurn()` | Turn transition (increment turnNumber, check limit) |
| `_endGame(winner)` | Ends the game |

## 99-round limit (online)

```javascript
if (this.turnNumber >= 99) {
    this._log('Round limit reached — resolved by castle height.');
    this._endGame(resolveByHp(this.state.A, this.state.B));
    return;
}
```

## Opponent hand-loss ghosts (`fromHand` in `CARD_LOST`)

When you burn or steal from the opponent's hand, one card back disappears from their strip; `NewBattlefield` marks it with a ghost effect showing the lost card's face. Offline this is driven by `GameViewModel.aiHandLossLog` — an ordered FIFO consumed one entry per removed slot. Online the queue was never filled, and the fallback (the singular `lastCard`/`lastCardAction`) never fired: `CARD_LOST` is sent *before* the `GAME_STATE` that shrinks `oppState.handSize`, so by the time `LaunchedEffect(aiHandSize)` runs, `GAME_STATE` has already overwritten `lastPlayedAction` back to `PLAYED` with the card the attacker played. Nothing was shown at all.

**Fix (2026-08-28):** `CARD_LOST` payloads carry `fromHand: true` when the card really came out of a hand — the direct `BurnCard`/`StealCard` path in `_playCardNow`, the same path fired by `discardEffects`, and `PeekAndStealHand` (Zákeřný špeh). `DecisionBurnOpponent` (Likvidace) deliberately does **not** set it: it burns from the opponent's *deck*, and an entry queued for it would never be consumed, shifting the cursor so every later loss showed the wrong card. The client (`OnlineLobbyViewModel.opponentHandLoss`) enqueues only `causedByMe` + `fromHand` events and passes the queue to `NewBattlefield(oppLossQueue = …)`, exactly as offline does. Ordering no longer matters — the queue survives until a slot is actually removed.

The field is additive: an old server simply omits it, the queue stays empty, and the client falls back to the previous behaviour, so `PROTOCOL_VERSION` was not bumped.

## Server hosting and capacity

**Hardware (2026-10-21):** Oracle Cloud VM `vnic-main`, IP `138.2.136.49`, port `8765` (`ws://…/lobby`, HTTP API on the same port). 2 vCPU, 954 MiB RAM (~510 MiB free with the game server running), **no swap**. Installed by `server/setup.sh` into `/opt/termiti-server`, systemd unit `termiti-lobby`. Single dependency: `ws` (`uuid` was replaced by `crypto.randomUUID()` on 2026-10-21).

**One lobby:** there is a single lobby with two queues (constructed and `super_random`). Players are paired into independent `GameSession` objects in memory; the number of simultaneous games is limited only by CPU and RAM.

### Load test (`server/loadtest.js`)

Starts its **own** server instance from its folder on port `8766` (refuses `8765`) and connects N bots that play in pairs: mulligan without swap, end their turn after 2–4 s. Reports join time, server CPU, message-handling delay (event-loop lag) and memory. The instance writes `data/` and `logs/` next to itself, so run it from a clone, never from `/opt/termiti-server`:

```bash
git clone https://github.com/Scratch741/Termiti.git /tmp/termiti-load
cd /tmp/termiti-load/server && npm install --omit=dev
node loadtest.js 1000 30 0     # bots, measured seconds, ramp-up seconds (0 = all at once)
```

### Results on the server (2026-10-21)

Production instance stopped during the test; the bots ran on the same VM and took part of the CPU, so join times are pessimistic. Bots end turns without playing cards, so real games cost slightly more per turn.

| Bots (games) | Join all at once | CPU while playing | Message delay p99 / max | Instance memory |
|---|---|---|---|---|
| 200 (100) | 5.5 s | 4 % of one core | 11 / 57 ms | 58 → 67 MB |
| 500 (250) | 15.5 s | 9 % | 13 / 58 ms | 60 → 89 MB |
| 1000 (500) | 27.5 s | 17 % | 15 / 222 ms | 60 → 99 MB |

No errors or disconnects at any size. Gameplay cost grows linearly and is cheap; by CPU the server could hold several thousand concurrent players. Caveats: the small cloud VM may throttle sustained CPU use (the test ran only 30 s), and with no swap an out-of-memory situation kills the process instead of slowing it down.

### Connection limits and anti-bot protection (`server.js`, 2026-10-21)

| Protection | Default | Env override | On violation |
|---|---|---|---|
| Max message size (`ws` `maxPayload`) | 16 kB | – | connection closed (1009) before the message is buffered; `ws` default was 100 MB |
| Players on the server (open connections) | 500 | `MAX_PLAYERS` | `ERROR` „Server je plný…" + close 1000 |
| Simultaneous connections per IP | 10 | `MAX_CONN_PER_IP` | `ERROR` + close 1000 |
| New connections per IP per minute | 30 | `MAX_NEW_CONN_PER_MIN` | `ERROR` + close 1000 |
| Connection without `JOIN` | 10 s | `JOIN_TIMEOUT_MS` | terminated |
| Messages per connection | 30 / s dropped, 90 / s | – | over 30 dropped, over 90 terminated |

Refusals close with code **1000** on purpose: the app then does not auto-reconnect and keeps the server's message on the error screen (any other code is replaced by the generic "connection lost" text). Per-IP limits are generous because mobile carriers put many customers behind one public IP (CGNAT). `loadtest.js` lifts the per-IP limits and sets `MAX_PLAYERS` above N for its own instance (all bots come from 127.0.0.1). Not covered: identity is the client-sent `deviceId` (can be spoofed; one person can farm rating with two connections) and volumetric DDoS (needs a proxy such as Cloudflare, i.e. a domain and `wss://`).

### Replays, admin pages and rating storage (2026-10-23)

- **Game logs** (`logs/*.jsonl`, `GameLogger`) are written live and contain both hands and both players' `deviceId`. `/replay` and `/replay-data` now serve a game only after it has a `game_end` event (in-progress → 404 „Hra ještě běží") and strip `deviceId` (`ReplayViewer.publicReplay`). Before, a player could open `/replay-data?id=<own gameId>` mid-game and read the opponent's hand, and `/replays` exposed every player's `deviceId` (the only identity – rating and reconnect takeover).
- **Admin-only:** `/replays`, `/crash-logs`, `/crash-log` and the recent-games section of `/`. Key = env `ADMIN_KEY`, else `data/admin_key.txt` (generated on first start, logged once, printed by `setup.sh`). Open `/?key=<key>` once; an HttpOnly cookie keeps it. The leaderboard on `/` stays public.
- **`ratings.json`** is saved via `.tmp` + fsync + rename, the previous version kept as `.bak`. A corrupt file is moved aside as `ratings.json.corrupt-<time>` and `.bak` is loaded – it is never overwritten with empty data (before, a corrupt file meant an empty leaderboard written over the old one on the next game end).
- `server/data/*` and `server/logs/` are git-ignored (admin key, ratings).

### Gotcha: `broadcastCount()` was quadratic

`COUNT` (players online / in queue) used to be sent to **every** player on every join, leave and queue change. A mass reconnect (e.g. after a server restart) cost O(N²): measured on a PC, 1000 bots joining at once waited 33 s for `WELCOME` at 100 % CPU. Since 2026-10-21 `COUNT` goes out at most once per `COUNT_INTERVAL_MS` (1 s) with current numbers; joins now cost linearly (~27 ms per player on the VM, bots included). `WELCOME` still carries the counts immediately to the joining player.

## Related pages
- [[architecture]] — technical architecture, versioning
- [[cards/decisions]] — Decision mechanic
- [[mechanics/win-conditions]] — win conditions online

## Changelog
- 2026-05-21: Page created
- 2026-05-29: Added `PROTOCOL_VERSION` handshake / `VERSION_MISMATCH`; `resourceOptions` in `DECISION_REQUEST`
- 2026-07-16: Documented `lastPlayedCard` masking gotcha + fix (self-inflicted overdraw burns weren't visible in the discard slot, only logged)
- 2026-07-16: Added WebSocket ping/pong heartbeat (`server.js`) — fixes ghost connections inflating/desyncing online count and blocking matchmaking
- 2026-08-26: Documented paced combo reveal (`_sendStateBothPaced`) — opponent-only delivery queue for rapid combo plays, with `_sendStateBoth()` flushing it to prevent out-of-order delivery
- 2026-08-28: Added `fromHand` to `CARD_LOST` + client-side `opponentHandLoss` queue — opponent hand-loss ghosts were invisible online
- 2026-10-21: Server hosting and capacity: hardware, one-lobby model, `loadtest.js`, load test results on the VM, `broadcastCount()` throttle
- 2026-10-21: Connection limits and anti-bot protection (maxPayload, MAX_PLAYERS, per-IP limits, JOIN timeout, flood disconnect)
- 2026-10-23: Replays only for finished games without deviceId, admin key for replay list and crash logs, atomic ratings.json with .bak
