# Combo System

> Cards with `isCombo=true` do not end the turn — the player can play another card. A non-combo card or a discard ends the turn.

## How Combo works

```kotlin
if (card.isCombo) {
    isPlayerComboTurn.value = true
    // player continues
} else {
    finishTurn(old, player, ai)
}
```

The UI shows a "COMBO" indicator when `isPlayerComboTurn == true`.

## Combo + Decision interaction

If a card is both Combo and Decision (e.g. Chaotická replikace `116`, Průzkum dolů `117`):

1. Card is played → Decision overlay is shown
2. Player picks in `resolveDecision()`
3. After pick: `isPlayerComboTurn = true` → player continues turn

**Bug fixed in history:** After picking in the Decision overlay the selected card was not visible in hand. Cause: `PlayerState` mutable class → Compose did not detect change. Fix: `player.deepCopy()` in the Combo branch of `resolveDecision()`.

## CloneNextPlayed

`CloneNextPlayed(count=2)` — persistent "this turn" effect: the next played card is cloned N times into the deck.

**Implementation:**
- Sets flag `cloneNextPlayed = count` in `PlayerState`
- When next card is played: adds `count` copies to deck
- Resets on turn transition or after activation

**Card:** Chaotická replikace `116` (2 CHAOS, Combo, EPIC)

### AI and CloneNextPlayed

AI gets a −30 penalty if after playing Chaotická replikace it cannot afford any other card:

```kotlin
val canAffordAny = ai.hand.any { other ->
    other.id != card.id && (residualRes[other.costType] ?: 0) >= other.effectiveCost
}
val clonePenalty = if (!canAffordAny) -30 else 0
```

## Selected Combo cards

| ID | Name | Cost | Effect |
|----|------|------|--------|
| 001 | Rychlý útok | 2 ATTACK | ap(5) |
| 004 | Magie | 0 MAGIC | ar(MAGIC,2) |
| 012 | Mobilizace | 1 MAGIC | ar(ATTACK,3) |
| 013 | Magický pramen | 3 MAGIC | am(MAGIC,1) |
| 109 | Rekrut | 3 ATTACK | dct(Útok,4,2) |
| 112 | Stavitel | 3 STONES | dct(Stavba,4,2) |
| 113 | Goblin šaman | 3 MAGIC | dct(Magie,4,2) |
| 114 | Chaotický mudrc | 3 CHAOS | dct(Chaos,4,2) |
| 116 | Chaotická replikace | 2 CHAOS | cnp(2) |
| 117 | Průzkum dolů | 1 MAGIC | dmine() |

## Related pages
- [[mechanics/game-flow]] — turn flow
- [[cards/decisions]] — Combo + Decision interaction
- [[systems/ai]] — AI scoring of Combo cards

## Changelog
- 2026-05-21: Page created
