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
