# Win Conditions

> The game ends when a win condition is met or after round 99.

## Win conditions (GameState.checkWinCondition)

| Condition | Result (GameResult) |
|-----------|---------------------|
| Round ≥ 99 | Resolved by castle height (resolveByHp) |
| Both castles ≤ 0 (simultaneous) | DRAW_BOTH_DEAD |
| Both castles ≥ winTarget (simultaneous) | DRAW |
| Player's castle ≤ 0 | PLAYER_CASTLE_DESTROYED |
| AI castle ≤ 0 | AI_CASTLE_DESTROYED |
| Player's castle ≥ playerWinTarget | PLAYER_CASTLE_BUILT |
| AI castle ≥ aiWinTarget | AI_CASTLE_BUILT |
| Higher castle after round 99 (player) | PLAYER_HP_WINS |
| Higher castle after round 99 (AI) | AI_HP_WINS |
| Equal castles after round 99 | DRAW |

## Win target (default: 70)

```kotlin
val playerWinTarget: Int = 70
val aiWinTarget: Int = 70
```

A passive ability `extra_castle` (if implemented) could raise the target to 75.

## 99-round limit

Implemented on both sides:

**Kotlin (GameState.kt):**
```kotlin
fun checkWinCondition(): GameResult? {
    if (currentTurn >= 99) return resolveByHp()
    // ... other conditions
}
```

**Node.js (GameSession.js):**
```javascript
if (this.turnNumber >= 99) {
    this._endGame(resolveByHp(this.state.A, this.state.B));
    return;
}
```

## Empty-deck skip rule (online)

With **both decks empty**, the game ends by `resolveByHp` only after **both players passively skip** their turn in a row (`skippedEmptyDeck.A && skippedEmptyDeck.B` in `GameSession`).

A skip counts as *passive* only when the player did nothing that turn. The client sends `SKIP_TURN` whenever both decks are empty — even after the player played combo cards — so the server tracks `actedThisTurn` (set by `PLAY_CARD`/`DISCARD_CARD`, reset when the player's turn starts): a `SKIP_TURN` from a player who acted is treated as a normal end-turn and clears their skip flag. Any play/discard also clears the player's own flag; the offline ENDGAME AI branch never discards for the same reason (would prevent the draw condition from ever triggering).

## Related pages
- [[overview]] — game overview
- [[mechanics/game-flow]] — turn flow
- [[systems/online]] — online resolution

## Changelog
- 2026-05-21: Page created; 99-round limit added
- 2026-07-12: Empty-deck skip rule documented + fix: SKIP_TURN after playing cards (combo) no longer counts as a passive skip — game ended prematurely instead of starting the next turn
