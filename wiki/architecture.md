# Technical Architecture

> Termiti is an Android app (Kotlin/Compose) with an optional Node.js server for online multiplayer.

## Tech stack

| Layer | Technology |
|-------|------------|
| UI | Jetpack Compose (Android) |
| Game logic (offline) | Kotlin, ViewModel, Coroutines |
| Game logic (online) | Node.js, WebSocket (`ws`) |
| Card persistence | `cards.json` (assets) + `cards.js` (server) |
| Animations | Jetpack Compose animations (Lottie removed) |

## Key Kotlin files

### Game core
- **`CardEffect.kt`** — sealed class of all game effects (AddResource, AddMine, DecisionChooseType…)
- **`Card.kt`** — card data model (id, baseId, cost, costType, isCombo, effects, isGenerated…)
- **`GameState.kt`** — data class for game state (playerState, aiState, currentTurn, winTargets)
- **`PlayerState.kt`** — mutable class for player state (castleHP, wallHP, resources, mines, hand, deck…)
- **`GameLogic.kt`** — pure functions: `applyEffects()`, no-op stubs for Decision effects
- **`Gameviewmodel.kt`** — main ViewModel: `playCard()`, `resolveDecision()`, `finishTurn()`, AI turn

### Presentation layer
- **`CardPresentation.kt`** — effect texts and drawable art refs (map `id → CardPres`)
- **`GameCardView.kt`** — Compose component for rendering a card in hand
- **`GameBattlefield.kt`** — game screen (board, animations)
- **`GameOverlay.kt`** — overlay for Decision picker, game over, log

### Other systems
- **`AiEngine.kt`** — heuristic card scoring for AI; returns `AiAction` (Play/Discard)
- **`CardRepository.kt`** — parses `cards.json` → `List<Card>`
- **`OnlineLobbyViewModel.kt`** — WebSocket client, online lobby, online Decision handling
- **`DeckBuilderScreen.kt`** — deck builder UI
- **`ArenaDraftScreen.kt`** — arena draft UI

## Key server files (Node.js)

- **`server/game/cards.js`** — card database (RAW array + helpers `ar()`, `am()`, `dct()`…)
- **`server/game/GameSession.js`** — server game logic, `_buildDecisionOptions()`, `_resolveDecision()`
- **`server/game/engine.js`** — pure game logic (applyEffects, checkWin, deriveCardType)

## Critical: PlayerState vs. Compose

`PlayerState` is a **mutable class** (not a data class). Compose detects changes by reference comparison. If `PlayerState` is mutated in-place and the same reference is passed to `GameState.copy()`, Compose sees no change → UI does not recompose.

**Fix:** Always call `player.deepCopy()` before setting state in cases where the UI must react:

```kotlin
// WRONG — Compose sees no change
gameState.value = old.copy(playerState = player)

// CORRECT — new reference → Compose recomposes
gameState.value = old.copy(playerState = player.deepCopy())
```

Most critical after Combo cards and inside `resolveDecision()`.

## Card sync: cards.js ↔ cards.json

**Problem:** Gradle task `syncCards` invokes Node.js, but Node.js is not in PATH → task silently fails → `cards.json` stays stale.

**Workaround:** Manually update `app/src/main/assets/cards.json` on every change to `cards.js`.

## Related pages
- [[overview]] — game overview
- [[systems/ai]] — AiEngine in depth
- [[systems/online]] — online multiplayer
- [[cards/effects]] — CardEffect sealed class

## Changelog
- 2026-05-21: Page created
