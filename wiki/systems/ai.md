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
score = effectScore - costForScore - chaosBlock + totoKoloPenalty + clonePenalty + totoBuff + waitForSetupPenalty + noise
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

### totoKoloPenalty / totoBuff / waitForSetupPenalty — TOTO KOLO effects (Inspirace etc.)

`DrawPerCardPlayed`, `GainResourcePerCardPlayed`, `GainCastlePerCardPlayed` all take an optional
`cardType` filter (e.g. `"Magie"`). The trigger (`GameViewModel._playCard`) checks the type of the
*next played card* against this filter — **not** `card.isCombo`. A card can trigger the buff without
being combo itself (it just ends the turn right after triggering it).

**Fixed 2026-07-16** — the AI used to gate all three of these on `comboCardsInHand` (count of any
`isCombo` card in hand, ignoring type). This meant Inspirace (`DrawPerCardPlayed(cardType="Magie")`)
was scored as valuable whenever the AI held *any* unrelated combo card, and scored as worthless when
it held only non-combo Magie cards — both wrong, since the trigger only cares about `card.type`. The
AI would play Inspirace with nothing to follow it, or waste it after already playing its Magie cards.

Fix: `matchingTypeCount(cardType, excludeId)` counts hand cards whose `.type` matches the effect's
filter (`null`/`""` = any type), independent of `isCombo`. Three places now use it:

- **`totoKoloPenalty`** — if, after paying for this TOTO KOLO card, no hand card of the matching type
  is still affordable, penalty `-25` (the buff would fire on nothing).
- **`totoBuff`** — while a buff from a previously-played card is active this turn, a candidate card
  gets a bonus (`+10` draw / `+6` resource / `+6` castle) **only if its own type matches** the active
  buff's filter — not just for being combo.
- **`waitForSetupPenalty`** *(new)* — the reverse case: if a not-yet-played TOTO KOLO card in hand
  would match the type of the card currently being scored, and is affordable, apply `-8` to discourage
  playing the payoff card *before* its setup card (which would waste the buff). This is what stops the
  AI from playing its Magie cards first and Inspirace last with nothing left to trigger.

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

## Endgame last-chance fallback (both decks empty, AI losing)

In the `bothDecksEmpty` branch, if no card scores > 0 and the AI is losing, it tries a "last chance" play rather than passively waiting (waiting when losing risks the empty-deck draw resolution locking in a loss). Two safeguards, both fixed 2026-07-12:

1. **`realizedAttackOrBuild()`** — the candidate search recurses into `ConditionalEffect` and calls `checkCondition` before counting a card as "attacks/builds now". A shallow top-level check would miss cards like `Zásobník` (`ConditionalEffect(CastleBelow(40), BuildCastle(10))` — top-level effect is `ConditionalEffect`, not `BuildCastle`).
2. **Absolute fallback gate** — if no card matches (1), the AI plays the highest-scored card ONLY if `card.effects.sumOf { scoreEffect(it) } > 0` (it does *something* right now). Without this, a conditional card with an unmet condition (effectScore = 0) still had a mildly-negative-but-not-catastrophic total score (`0 − cost + noise`, e.g. ≈ −3) — well above the `≤ −50` no-op threshold reserved for Mirror/Clone-without-source (`-100`) — so it slipped through and got force-played for **zero effect**, just wasting the cost.

## Discard rules

Discard follows the **same rule as the player's** (`GameViewModel.discardCard`): at most **once per turn**, and it does **not** end the turn. The AI loop tracks `aiDiscardUsed` and passes `canDiscard = !aiDiscardUsed` to `aiChooseAction`; every `AiAction.Discard` return is guarded by it, so the loop cannot spin. After a discard the loop shows the intermediate state, waits 1 s, and asks the AI again — it can then play normally (including a card it just drew).

*History: until 2026-09-21 an AI discard set `aiContinues = false`, i.e. it ended the AI's turn while the player's discard did not — the AI played under stricter rules than the player.*

**Deliberate discard (cards with `discardEffects`).** Because the discard does not cost the turn's play, the question is only whether the card is worth more discarded than kept. `discardValueNow(card)` scores the discard effects on the same scale as `score()`: `DrawCard` = 5 per useful card (7 when ≤ 2 cards remain in hand after the discard; 0 with an empty deck, capped by free slots and deck size), positive `AddResource` = `3 + amount/2` (as in `scoreEffect`), everything else via `scoreDiscardEffect` so self-harm stays negative. `keepValue(card)` = the card's own play value without noise (`effectScore − cost`). `bestDeliberateDiscard()` picks the card with the largest positive `discardValueNow − keepValue`. It runs **before** choosing a play (the drawn card may be the better play) unless a lethal play exists (score ≥ 1000), and also when nothing is affordable. Never in the both-decks-empty ENDGAME branch. Resulting behaviour: Zapomenutá poznámka (`"137"`, keep = 4 − 1 = 3, discard = draw ≈ 5–7) is discarded and the AI then continues its turn; Osudová mince (`"135"`) is discarded for +2 chaos only when the opponent has no chaos to steal; Zoufalý žold (`"132"`) is kept and played; self-harm cards (`"134"`, `"136"`, `"138"`, `"139"`) are never discarded on purpose.

**Forced discard.** Otherwise (and only while `canDiscard`) the AI discards when the hand is full (7) **and** its deck still has cards — freeing a slot for the next draw (a draw into a full hand would burn the card). In every other "stuck" situation it waits:

- hand not full → waiting costs nothing, next turn draws a new card
- own deck empty → discarding is a pure card loss (nothing to draw into the freed slot); resources grow every round, so unaffordable cards become affordable over time
- both decks empty → separate ENDGAME branch: never discard, otherwise the "both waiting with empty decks" draw condition (`resolveByHp`) could never trigger

`bestDiscard()` picks the card with the lowest hand value: `effectScore × 2 − turnsToAfford × 2 + costBonus` (strong/expensive cards are kept even if currently unaffordable; the affordability penalty is capped at 4 turns).

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
- 2026-07-12: Discard rules documented + fix: AI no longer discards with an empty deck (was a pure card loss — e.g. threw away Nedobytná pevnost instead of waiting); discard now only with a full hand + non-empty deck
- 2026-07-12: Endgame last-chance fallback fix: AI no longer force-plays a zero-effect conditional card (e.g. Zásobník with unmet CastleBelow condition) as a "last chance" when losing with empty decks — `realizedAttackOrBuild()` now condition-aware, absolute fallback requires effectScore > 0
- 2026-07-16: Fixed Inspirace/TOTO KOLO scoring: `totoKoloPenalty`/`totoBuff` used to key off `comboCardsInHand` (any `isCombo` card), but the actual trigger checks `card.type` against the effect's `cardType` filter — unrelated. Replaced with `matchingTypeCount()`; added new `waitForSetupPenalty` to stop the AI playing its Magie payoff cards before the Inspirace-style setup card
- 2026-09-21: Deliberate discard — the AI now weighs a card's `discardEffects` against its best play (`discardValueNow`/`bestDeliberateDiscard`); previously discard was only a forced fallback, so Zapomenutá poznámka was always played even with a 2-card hand and plenty of magic.
- 2026-09-21: AI discard now matches the player's rule — once per turn, does not end the turn (`canDiscard`/`aiDiscardUsed`); deliberate discard compares against the card's keep value instead of the best play.
