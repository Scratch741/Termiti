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

## Lethal detection

`isLethal(card, xVal)` simulates a card's damage (wall → castle overflow / direct castle hit) and self-castle gain; if it would drop the opponent's castle to ≤0 or push AI's castle to its win target this turn, the card gets an override score (`1000 ± noise`) so the AI always takes the kill.

### Combo-chain lethal (1-step lookahead)
`comboSetupForLethal()` catches kills that need a setup card first. For each affordable **combo** card that generates resources (`AddResource`), it recomputes AI resources after paying the cost + adding the gains, then checks whether a currently-**unaffordable** hand card becomes affordable **and** lethal. If so, the AI plays the setup combo first; the game loop then plays the finisher and wins.

> Example: `Vojenský rozkaz` (+6 ATTACK) → `Démon` becomes affordable and lethal. Without this lookahead the AI only saw single-step lethals and missed the win.

## AI and Combo vs. Non-combo

Combo cards receive a bonus score (AI prefers to chain Combo sequences). A non-combo card ends the AI's turn.

> The standalone game **simulator** mirrors this logic (combo-chain lethal ported) so balance sims match real AI behavior.

## Related pages
- [[cards/effects]] — effects and their values
- [[cards/decisions]] — AI with Decision cards
- [[mechanics/combo]] — Combo + AI interaction

## Changelog
- 2026-05-21: Page created; DecisionMine AI logic added
- 2026-05-29: Documented lethal detection + combo-chain lethal lookahead (`comboSetupForLethal`); DecisionChooseResource AI picks least-held resource; smarter discard (keep strong cards)
