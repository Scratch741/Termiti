# AiEngine

> `AiEngine.kt` is a pure function (no Android dependencies) that returns an `AiAction` — either `Play(card)` or `Discard(card)` — for each AI turn.

## Architecture

```kotlin
sealed class AiAction {
    data class Play(val card: Card)    : AiAction()
    data class Discard(val card: Card) : AiAction()
}
```

AI iterates over the hand, scores each card, picks the highest.

## Scoring formula

```
score = effectScore - costForScore - chaosBlock + totoKoloPenalty + clonePenalty + totoBuff + noise
```

### effectScore — effect value
AI sums the value of all card effects:

| Effect | Value |
|--------|-------|
| AttackPlayer(n) | n |
| AttackCastle(n) | n × 1.3 (direct hit is more valuable) |
| AttackWall(n) | n × 0.7 |
| BuildCastle(n) | n × 1.2 |
| BuildWall(n) | n × 0.5 |
| AddResource(t, n) | n × 0.8 |
| AddMine(t, n) | n × 8 (mines are highly valued) |
| StealResource(t, n) | n × 1.5 |
| DrawCard(n) | n × 3 |
| DecisionChooseType | 5 |
| DecisionMine | 6 |
| CloneNextPlayed | 6 (before penalty) |
| ShapeShift | 5 |

### costForScore — cost penalty
AI prefers cheaper cards at equal effect:
```kotlin
val costForScore = card.effectiveCost * 0.5
```

### chaosBlock — penalty when AI has no Chaos
If the card costs CHAOS and AI has 0 Chaos resources:
```kotlin
val chaosBlock = if (card.costType == ResourceType.CHAOS && ai.resources[CHAOS] == 0) 50 else 0
```

### totoKoloPenalty — persistent effect penalty
If AI plays `DrawPerCardPlayed` or `GainResourcePerCardPlayed` but has no Combo follow-up → effect expires unused.

### clonePenalty — CloneNextPlayed penalty
If AI plays Chaotická replikace but cannot afford any other card → penalty −30:
```kotlin
val canAffordAny = ai.hand.any { other ->
    other.id != card.id && (residualRes[other.costType] ?: 0) >= other.effectiveCost
}
val clonePenalty = if (hasCloneNextPlayed && !canAffordAny) -30 else 0
```

### noise — randomness
Small random value for AI move variety.

## AI and Decision cards

AI auto-picks the first option for most Decision effects. Exception:

**`DecisionMine`** — AI picks the mine type it currently has the least of:
```kotlin
val best = opts.minByOrNull { card ->
    val mineEffect = card.effects.filterIsInstance<CardEffect.AddMine>().firstOrNull()
    ai.mines[mineEffect?.type] ?: 0
}
```

## AI and Combo vs. Non-combo

Combo cards receive a bonus score (AI prefers to chain Combo sequences). A non-combo card ends the AI's turn.

## Related pages
- [[cards/effects]] — effects and their values
- [[cards/decisions]] — AI with Decision cards
- [[mechanics/combo]] — Combo + AI interaction

## Changelog
- 2026-05-21: Page created; DecisionMine AI logic added
