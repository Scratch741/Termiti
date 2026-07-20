# Discard Mechanic

> `Card.discardEffects` — a card can carry a second, alternative effect list that fires ONLY when the card is discarded (not played). When present, `effects` never runs for that card instance; discarding and playing become two mutually exclusive paths.

## Purpose

Introduced for high-risk/high-reward card designs, e.g. a future card "Zamořené krysy" (Infested Rats): played, it shuffles X rat cards into the opponent's deck (cost X resources, do nothing when drawn); if instead the player discards this card from hand, something bad happens to them. No such card exists yet — this page documents the underlying system, ready for future card authoring.

## Data model

```kotlin
// Card.kt
val effects: List<CardEffect>            // normal play effects
val discardEffects: List<CardEffect> = emptyList()   // NEW — alternative, fires on discard
```

- Default is an empty list → existing cards behave exactly as before.
- Reuses the existing `CardEffect` sealed class — no new effect types were added. Self-harm designs use negative amounts on self-targeting effects (`ar('CHAOS', -2)`, `bc(-5)`, `bw(-5)`); beneficial designs use positive amounts, the same way normal `effects` are authored.
- JSON schema: optional `"discardEffects": [...]` array, same shape as `"effects"`. Missing key = empty list (`CardRepository.parseCard` uses `optJSONArray(...) ?: JSONArray()`).
- `server/game/cards.js` RAW tuple gained an 11th (optional) positional element `discardEffects`; `ALL_CARDS` mapping defaults it to `[]`. Hand-instance objects (`makeInstance`) carry it automatically via object spread — no extra server-side parsing needed.

## Trigger points

| Path | File | Function |
|------|------|----------|
| Offline, human discard | `GameViewModel.kt` | `discardCard(card)` |
| Offline, AI discard | `GameViewModel.kt` | `finishTurn()` → `AiAction.Discard` branch |
| Online (server-authoritative) | `server/game/GameSession.js` | `_handleDiscardCard(side, { cardId })` |
| Deck-builder simulator | `deckbuilder.html` | `act.type==='discard'` branch in the sim game loop |

All four call `applyEffects(card.discardEffects, self=<discarder>, opponent=<other side>, ...)` **instead of** the normal play-effects call — `card.effects` is never touched by a discard. Card-loss callbacks (`onOpponentCardLost`/`onSelfCardLost`) are wired the same way as a normal `playCard()` call so stolen/burned cards still show up in the discard slot / CARD_LOST notifications. A win-condition check runs immediately after (a self-harm discard effect can theoretically end the game).

```kotlin
// GameViewModel.discardCard() — human
if (card.discardEffects.isNotEmpty()) {
    log.appendLog(ls.logDiscardEffectTriggered.format(card.displayName))
    applyEffects(card.discardEffects, player, ai, allCards, ...)
    s1.checkWinCondition()?.let { result -> scheduleGameEnd(result, s1); return }
}
```

Decision-type effects (`DecisionMine`, `SmartJoker`, etc.) and `Mirror`/`Clone` are **not supported** inside `discardEffects` — discard is a single atomic action, not a mini turn. Only "simple" immediate effects are expected here (stat changes, steal/drain, draw, burn/steal card).

## AI awareness

`AiEngine.bestDiscard()` picks the lowest-scoring hand card to discard. A dedicated `scoreDiscardEffect(fx)` function (separate from the play-effect `scoreEffect(fx)`, which ignores sign) scores `discardEffects` from the discarder's perspective — negative (self-harm) raises the card's overall score so the AI avoids discarding it; positive lowers it so the AI prefers discarding it:

```kotlin
val discardScore = card.discardEffects.sumOf { scoreDiscardEffect(it) }
effectScore * 2 - turnsToAfford * 2 + costBonus - discardScore
```

The deck-builder simulator mirrors this with `sde(fx)` inside `bd()`.

## UI

`GameCardView.kt` (`CardViewTextured`) shows a small skull-icon badge (border color `DiscardRed`, same slot as the combo/generated/condition badges, top-right corner) whenever `card.discardEffects.isNotEmpty()`, warning the player that discarding this card is not "free". No description auto-generation exists in the app (card text is hand-authored, same convention as `[Combo]`/`[X-kost]` suffixes) — an author should manually add a clause like `"| **Při zahození:** ..."` to the card's description string (`parseCardDesc` already supports `|` as a line break and `**bold**`). `deckbuilder.html`'s `generateCardDesc()` DOES auto-append a `"| **Při zahození:** ..."` clause from `discardEffects` for cards loaded from JSON, since that tool builds descriptions from data.

## Related pages
- [[cards/effects]] — the CardEffect types reused by discardEffects
- [[mechanics/game-flow]] — where discard sits in the turn flow (does not end the turn, 1×/round)
- [[systems/ai]] — AiEngine scoring, `bestDiscard()`

## Changelog
- 2026-07-20: Page created — initial "Discard" mechanic implementation (infrastructure only, no card uses it yet)
