# CardEffect — All Effects

> Sealed class `CardEffect` in `CardEffect.kt` defines every possible card effect. `GameLogic.kt` applies them; Decision effects are no-ops in GameLogic and handled by ViewModel / GameSession.

## Basic effects

| Effect | Parameters | Description |
|--------|------------|-------------|
| `AddResource` | type, amount | Instantly adds resources |
| `AddResourceDelayed` | type, amount, turns | Adds resources after N turns |
| `AddMine` | type, amount=1 | Permanently increases mine production |
| `BuildWall` | amount | Adds (or removes) wall height |
| `BuildCastle` | amount | Adds (or removes) castle height |
| `AttackPlayer` | amount | Attacks player — hits wall first, overflow goes to castle |
| `AttackWall` | amount | Attacks wall ONLY, no overflow to castle |
| `AttackCastle` | amount | Direct castle hit — ignores wall |
| `StealResource` | type, amount | Steals resources from opponent and adds them to self |
| `DrainResource` | type, amount | Destroys opponent's resources (no gain for self) |
| `StealCastle` | amount | Steals HP from opponent's castle and adds to own |
| `DestroyMine` | type, amount=1 | Reduces opponent's mine (min 1) |
| `BlockMine` | type, turns | Blocks opponent's mine production for N turns |
| `StealCard` | count=1 | Steals random cards from opponent's hand |
| `BurnCard` | count=1 | Destroys random cards from opponent's hand |
| `AddCardsToDeck` | cardId, count | Adds copies of a card to own deck (shuffled in) |
| `DrawCard` | count=1 | Draws cards from own deck to hand |
| `DrawBoth` | count=1 | Both players draw cards |
| `ConvertMine` | from, to | Converts 1 unit of mine `from` → `to` |
| `SwapHands` | — | Swaps both players' entire hands |

## Conditional effects

| Effect | Parameters | Description |
|--------|------------|-------------|
| `ConditionalEffect` | condition, effect | Applies `effect` only if `condition` is true |

### Conditions
- `ResourceAbove(type, threshold)` — player has ≥ threshold of resource type
- `ResourceMoreThanOpponent(type)` — player has more of resource type than opponent
- `WallAbove(threshold)` / `WallBelow(threshold)` — wall height
- `CastleAbove(threshold)` / `CastleBelow(threshold)` — castle height

## Persistent effects (This Turn)

| Effect | Parameters | Description |
|--------|------------|-------------|
| `CloneNextPlayed` | count=2 | Next played card is cloned N times into deck |
| `DrawPerCardPlayed` | cardType=null | Draw 1 card for each subsequent card played (of given type) |
| `GainResourcePerCardPlayed` | type, amount, cardType=null | Gain `amount` resources per subsequent card played |
| `GainCastlePerCardPlayed` | amount, cardType=null | Gain `amount` castle per subsequent card played |
| `ShapeShift` | — | At start of each turn, card transforms into a random card |

## X-cost effects

Card costs 0 but consumes ALL available resources of the given type on play (X = total consumed).

| Effect | Divisor | Description |
|--------|---------|-------------|
| `XScaledAttackPlayer` | 2 | Attacks for X/divisor |
| `XScaledAttackCastle` | 2 | Direct castle hit for X/divisor |
| `XScaledBuildCastle` | 2 | Repairs castle for X/divisor |
| `XScaledDualResource` | typeA, typeB, 2 | Adds X/divisor to both resource types |

## Decision effects (see [[cards/decisions]])

| Effect | Description |
|--------|-------------|
| `DecisionBurnOpponent` | N cards from opponent's deck; player picks one to discard |
| `DecisionChooseType` | N random cards of given type; player adds one to hand (with optional discount) |
| `DecisionFromDiscard` | N cards from own discard pile; player adds one to hand |
| `DecisionFromDeck` | N cards from own deck; a copy arrives in hand (original stays) |
| `DecisionMine` | Exactly 4 options: 1 random mine of each type (MAGIC/ATTACK/STONES/CHAOS) |

## Related pages
- [[cards/types]] — card types
- [[cards/decisions]] — Decision mechanic in detail
- [[mechanics/combo]] — Combo + CloneNextPlayed
- [[architecture]] — where effects are implemented

## Changelog
- 2026-05-21: Page created
