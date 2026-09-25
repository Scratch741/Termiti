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
Each option is a `ResourceOption(type, amount)`. The overlay renders each option as a **placeholder card** (`resourcePlaceholderCard()` builds a synthetic `Card` carrying an `AddResource` effect + the matching resource art), so it reuses the normal card-picker UI rather than custom buttons. On pick, `resolveDecision` reads the placeholder's `AddResource` effect and grants the resource. Online sends `chosenId = resType.name`.

| Card | ID | Cost | Options |
|------|----|------|---------|
| Alchymistova volba | 124 | 2 MAGIC | 4 Magic / 4 Attack / 4 Stones |

Title/subtitle come from `decisionAlchemyTitle` / `decisionAlchemySubtitle` (localized).

## AI behavior for Decision cards

AI auto-picks the first option for most Decision effects. Exceptions:

- **`DecisionMine`** — AI picks the mine type it has the **least** of (minimizes `ai.mines[resType]`)
- **`DecisionChooseResource`** — AI picks the resource it has the **least** of (minimizes `ai.resources[type]`)

## Picking what to offer (`scoreCardForSituation`)

`Magický žolík` (`"118"`, `SmartJoker`) offers one card per type — the best **Magie / Útok / Stavba / Chaos** card for the situation — and the same scorer picks the AI's own Decision options (`GameViewModel:1946/1959`). It must therefore see what a card really does **right now**:

- **Conditional effects are resolved first.** `ConditionalEffect` is flattened against the current state via `checkCondition`: condition met → score the inner effect, not met → contribute nothing. (Before 2026-09-25 the whole `ConditionalEffect` fell through to `else -> 2.0`.)
- **Lethality is summed over the whole card, not per effect.** Castle damage from every effect is added up — `AttackCastle`, `StealCastle`, the X-scaled variants, and the part of `AttackPlayer` that gets past the wall (an `AttackWall` on the same card lowers that wall first) — and only the total is compared with the opponent's castle HP. The same applies to building: total `BuildCastle` + `ConvertWallToCastle` + X-scaled vs. the HP still missing to `winTarget`. A winning card scores 200.

Worked example (the bug that prompted this): opponent at 9 castle / 9 wall, player holding 7 attack. `Ostřelovač` (`"026"`, 5 castle damage + 5 more while attack > 5) deals 10 and wins, but per-effect scoring gave it 5×12/9 + 2.0 = **8.67** and offered `Přímý zásah` (8 damage, **10.67**) instead — a card that does not win. Now Ostřelovač returns 200; with only 4 attack its condition fails and it correctly drops to 6.67, below Přímý zásah.

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
- 2026-05-28: Added `DecisionChooseResource` (Alchymistova volba 124)
- 2026-05-29: `DecisionChooseResource` now renders options as placeholder cards (not buttons); localized titles
- 2026-09-25: Documented `scoreCardForSituation` after fixing it — conditional effects were scored as a flat 2.0 and lethality was judged per effect, so Magický žolík could withhold the card that wins the game.
