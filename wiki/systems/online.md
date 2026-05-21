# Online Multiplayer

> Online games run over a Node.js WebSocket server. `GameSession.js` manages game state; the client (`OnlineLobbyViewModel.kt`) communicates via messages.

## Architecture

```
Android client ←→ WebSocket ←→ Node.js server (GameSession.js)
```

The client sends actions (play, discard, decision_response) and the server sends back the updated state.

## Key messages

### Client → Server
| Message | Description |
|---------|-------------|
| `play` | Play a card (cardId) |
| `discard` | Discard a card (cardId) |
| `decision_response` | Decision overlay pick (chosenId) |
| `mulligan` | Mulligan selection |

### Server → Client
| Message | Description |
|---------|-------------|
| `STATE` | Updated game state |
| `DECISION_REQUEST` | Show Decision overlay (effectType, options, timeoutMs) |
| `CARD_LOST` | Card loss animation (burn, steal) |
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
- [[architecture]] — technical architecture
- [[cards/decisions]] — Decision mechanic
- [[mechanics/win-conditions]] — win conditions online

## Changelog
- 2026-05-21: Page created
