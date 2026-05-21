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

## Related pages
- [[overview]] — game overview
- [[mechanics/game-flow]] — turn flow
- [[systems/online]] — online resolution

## Changelog
- 2026-05-21: Page created; 99-round limit added
