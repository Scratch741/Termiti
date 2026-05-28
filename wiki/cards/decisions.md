# Decision Cards

> Decision cards interrupt the game flow — after playing, an overlay appears with card choices. The player picks one, the effect is applied, then the turn continues.

## How Decision cards work

1. Player plays a Decision card
2. `playCard()` detects a Decision effect → saves state to `decisionPlayer/decisionAi/decisionOld/decisionEffect`
3. UI shows `DecisionState` overlay (4 options)
4. Player picks → `resolveDecision(chosen)` → effect applied → turn continues

### Critical implementation note
If a Decision card also has `DrawPerCardPlayed` or other accumulated draws, they are saved to `decisionPendingDraws` and executed **after** the player's choice inside `resolveDecision()`.

## Decision effects and their cards

### `DecisionChooseType(cardType, picks=4, costReduction=0)`
Offers `picks` random cards of `cardType` from the full pool. Player adds the chosen one to hand with a `costReduction` discount.

| Card | ID | Cost | cardType | costReduction |
|------|----|------|----------|---------------|
| Rekrut | 109 | 3 ATTACK | "Útok" | 2 |
| Stavitel | 112 | 3 STONES | "Stavba" | 2 |
| Goblin šaman | 113 | 3 MAGIC | "Magie" | 2 |
| Chaotický mudrc | 114 | 3 CHAOS | "Chaos" | 2 |

### `DecisionBurnOpponent(picks=4)`
Shows `picks` random cards from opponent's **deck**. Player picks one to discard (moves to opponent's discard pile).

| Card | ID | Cost |
|------|----|------|
| Likvidace | 108 | 4 CHAOS |

### `DecisionFromDiscard(picks=4)`
Shows `picks` cards from own **discard pile** (excludes the just-played card). Player adds chosen one to hand.

| Card | ID | Cost |
|------|----|------|
| Vzpomínka | 110 | 2 MAGIC |

### `DecisionFromDeck(picks=4)`
Shows `picks` cards from own **deck**. A **copy** arrives in hand — original stays in deck.

| Card | ID | Cost |
|------|----|------|
| Intuice | 111 | 2 MAGIC |

### `DecisionMine`
Shows exactly **4 options**: 1 random mine of each type (MAGIC, ATTACK, STONES, CHAOS). Player adds the chosen mine to hand.

| Card | ID | Cost |
|------|----|------|
| Průzkum dolů | 117 | 1 MAGIC |

### `DecisionChooseResource(options)`
Special: the overlay shows **resource buttons** (not cards). Each option is a `ResourceOption(type, amount)`. The player picks one and immediately receives that amount of the resource. `DecisionState.resourceChoices` drives the UI branch in `DecisionOverlay`; resolved by `resolveResourceDecision(type, amount)` offline / `resolveOnlineResourceDecision` online (sends `chosenId = resType.name`).

| Card | ID | Cost | Options |
|------|----|------|---------|
| Alchymistova volba | 124 | 2 MAGIC | 4 Magic / 4 Attack / 4 Stones |

## AI behavior for Decision cards

AI auto-picks the first option for most Decision effects. Exceptions:

- **`DecisionMine`** — AI picks the mine type it has the **least** of (minimizes `ai.mines[resType]`)
- **`DecisionChooseResource`** — AI picks the resource it has the **least** of (minimizes `ai.resources[type]`)

## Online (GameSession.js)

Server handles Decision via:
- `_buildDecisionOptions(side, effect, playedCardId)` — generates options
- `DECISION_REQUEST` message → client shows overlay
- `_resolveDecision(side, chosenId)` — applies the choice
- Timeout = remaining turn time + remaining timebank; auto-resolves to first option

## Related pages
- [[cards/effects]] — all effects
- [[mechanics/combo]] — Combo + Decision interaction
- [[systems/ai]] — AI scoring of Decision cards
- [[systems/online]] — server implementation

## Changelog
- 2026-05-21: Page created; Průzkum dolů (117) added
- 2026-05-28: Added `DecisionChooseResource` (Alchymistova volba 124) — resource-button overlay variant
