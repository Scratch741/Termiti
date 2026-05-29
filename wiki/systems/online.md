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
| `CARD_LOST` | Card loss animation (burn, steal) |
| `VERSION_MISMATCH` | Client protocol version incompatible → "update the app" |
| `GAME_OVER` | Game ended (result) |
| `GAME_ERROR` | Action error |

## Decision in online games

Server generates options via `_buildDecisionOptions()` and sends `DECISION_REQUEST`. Client shows overlay; after pick sends `decision_response` with `chosenId`.

**Timeout:** Remaining turn time + remaining timebank. On timeout the server auto-resolves to the first option.

**Timebank:** Each player has a timebank (seconds). Time spent on the Decision overlay is deducted from the timebank.

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

## Related pages
- [[architecture]] — technical architecture, versioning
- [[cards/decisions]] — Decision mechanic
- [[mechanics/win-conditions]] — win conditions online

## Changelog
- 2026-05-21: Page created
- 2026-05-29: Added `PROTOCOL_VERSION` handshake / `VERSION_MISMATCH`; `resourceOptions` in `DECISION_REQUEST`
