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

## Win target and starting castle

| Mode | Start castle | Win target (build) | Start wall | Wall cap |
|------|--------------|--------------------|------------|----------|
| Constructed – own deck offline, online `normal` | 50 (`CONSTRUCTED_START_CASTLE`) | 100 (`CONSTRUCTED_WIN_TARGET`) | 15 | 40 (`CONSTRUCTED_MAX_WALL`) |
| Random balanced, super-random (offline and online `super_random`) | 30 | 70 | 15 | 50 (`MAX_WALL`) |
| Campaign, roguelike, arena | per opponent / run | per opponent / run | | |

The constructed wall cap went from 50 to 40 later on 2026-10-02 (35 was tried first; measurements in wiki/log.md).

Constructed went from 35 / 70 to 50 / 100 on 2026-10-02 so that a weak draw does not lose the game in two turns: both routes now need 50 (kill 50, build +50). Measured before the change (engine, `EngineSimulation.pacing`): games over by round 12 21.9 % -> 6.3 %, the side behind by >= 15 after round 8 wins 33.0 % -> 39.3 %, average 17.7 -> 21.5 rounds; cost: games decided on castle height 9 % -> 25 %, build presets without big finishers (Kartář, Obránce2) much weaker.

Passive abilities add **+5** to a target: `extra_castle` to its owner (and +5 start castle), `iron_bastion` to the opponent. Offline the two can stack for the AI (+10); online they do not (max +5). The castle cap is `MAX_CASTLE` = 150 (client `PlayerState.kt`, server `engine.js`) – it must stay above every target, otherwise a 105 target would be unreachable.

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
