# Discard Mechanic

> `Card.discardEffects` — a card can carry a second, alternative effect list that fires ONLY when the card is discarded (not played). When present, `effects` never runs for that card instance; discarding and playing become two mutually exclusive paths.

## Purpose

Introduced for high-risk/high-reward card designs. Realized by "Zamořené krysy" (Infested Rats, `"133"`): played, it shuffles 3 copies of a placeholder curse card into the opponent's deck; that placeholder has no play effect of its own (just a chaos cost + forced turn-end), so the opponent who draws it is stuck choosing between wasting a turn to play it or discarding it for a direct HP penalty. See changelog for the full mechanic history.

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
- 2026-07-26: First real content batch — 6 cards now use `discardEffects` beyond the original test card `"132"` (Zoufalý žold):
  - `"133"` Zamořené krysy (3 CHAOS, RARE) — play: `AddToOpponentDeck("134", 3)`, shuffles the placeholder `"134"` into the opponent's deck; discard: `BuildWall(-5)` self-harm. Realizes the hypothetical example this page originally used to motivate the whole mechanic (§ Purpose). **`"134"` was redesigned on 2026-08-26 — see below.**
  - `"135"` Osudová mince (2 CHAOS, EPIC) — play: `StealResource(CHAOS,3)`; discard: `AddResource(CHAOS,2)` (both positive — a "flex" card).
  - `"136"` Poslední výpad (3 ATTACK, EPIC) — play: `AttackPlayer(7)`; discard: `BuildWall(-4)` self-harm.
  - `"137"` Zapomenutá poznámka (1 MAGIC, COMMON) — play: `AddResource(MAGIC,2)`; discard: `DrawCard(1)` (never a dead draw).
  - `"138"` Podkopané valy (3 STONES, RARE) — play: `BuildWall(7)` + `DrainResource(STONES,3)`; discard: `BuildWall(-5)` self-harm.
  - `"139"` Pohlcení hradeb (8 STONES, LEGENDARY) — play: new effect `ConvertWallToCastle` (see [[cards/effects]]); discard: `BuildCastle(-8)` self-harm.
  - Design rule learned this round: a punishing-discard card's PLAY side must be a genuinely new effect/combo, not a reprint of an existing card's stat-line with a discard clause bolted on — two earlier drafts ("Zazděný poklad", "Vázaný duch") were rejected for being strictly-worse duplicates of "Pevné základy" (`"009"`) and "Magický pramen" (`"013"`) respectively.
  - All 6 added to `cards.json`, `CardPresentation.kt`, and mirrored server-side in `cards.js`/`engine.js`/`GameSession.js` for online parity (server-side `ConvertWallToCastle` execution + smart-pick scoring not runtime-tested — no Node.js in this environment, see repo `CLAUDE.md`).
  - Not updated: `deckbuilder.html`'s SIMULÁTOR tab has its own hand-authored per-card shorthand effect DB (separate from the DECK BUILDER tab, which reads real `cards.json` fine) — cards `"133"`–`"139"` and `ConvertWallToCastle` are not yet in it.
- 2026-08-26: `"134"` redesigned from a draw-triggered trap into a real playable curse card, per user spec. Was: `Krysa`, 0 CHAOS, `effects:[TrapOnDraw(AttackWall(3))]` (fired automatically on draw, never sat in hand as a real choice), no `discardEffects`. Now: `Zamořená Krysa`, 3 CHAOS, `effects:[]` (no play effect at all — playing it purely costs chaos and, being a non-combo card, ends the turn via the normal turn-end-after-play rule, no new `CardEffect` needed), `discardEffects:[BuildCastle(-4), BuildWall(-4)]`. Net effect: the opponent who draws it must choose between burning a turn + 3 chaos for nothing, or taking 4 castle + 4 wall damage via discard — a genuine dilemma instead of an unavoidable draw-time ping. `isPlaceholder:true`/`maxCopies:0` unchanged (still excluded from all reward/decision/deck-builder pools, still only enters play via `"133"`'s `AddToOpponentDeck`). Updated in `cards.json`, `CardPresentation.kt`, `cards.js` (server), and both `cs.json`/`en.json` localization files.
