# Turn Flow

> Each round consists of the player's turn and the AI's turn. Combo cards allow playing multiple cards in one turn.

## Round structure

```
Start of round (currentTurn++)
  ├── Apply mines (AddMine production → resources)
  ├── Draw card to hand
  └── Active player's turn
        ├── Player plays a card (playCard)
        │     ├── Deduct cost (resources -= cost)
        │     ├── Apply effects (GameLogic.applyEffects)
        │     ├── Decision effect? → show overlay → wait
        │     ├── Combo card? → player continues turn
        │     └── Non-combo? → finishTurn → switch to opponent
        └── Player discards a card
              └── finishTurn → switch to opponent
```

## `playCard()` — key function (Gameviewmodel.kt)

```
1. Calculate cost (effectiveCost; X-cost cards consume everything)
2. Deduct resources from player
3. Move card from hand to discardPile
4. Call applyEffects() → mutates PlayerState
5. Handle persistent effects (DrawPerCardPlayed, GainResourcePerCardPlayed, CloneNextPlayed)
6. Handle DrawCard effects → draw with animation (delay 350ms)
7. Decision effect? → save state, show overlay, RETURN (turn paused)
8. Combo? → isPlayerComboTurn = true, continue
9. Non-combo? → finishTurn()
```

## `finishTurn()` — turn transition

```
1. Check win condition (checkWinCondition)
2. Switch activePlayer (PLAYER ↔ AI)
3. Add mine resources for new active player
4. Draw card for new active player
5. AI turn? → run aiPlayTurn()
```

## Animation timing

DrawCard animations are timed using `delay()` in a coroutine:
- After each drawn card → `delay(350L)` → then `finishTurn()`
- Reason: Compose batching — without a suspension point, state updates merge and the animation appears only at the start of the next turn

## Round limit

After reaching **round 99** (`currentTurn >= 99`) the game ends: the player with the taller castle wins.
Implemented in `GameState.checkWinCondition()` and `GameSession.js` (server).

## Related pages
- [[mechanics/win-conditions]] — win conditions
- [[mechanics/combo]] — Combo cards
- [[cards/decisions]] — Decision turn interruption
- [[systems/ai]] — AI turn

## Changelog
- 2026-05-21: Page created
