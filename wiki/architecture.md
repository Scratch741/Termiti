# Technical Architecture

> Termiti (app display name **Darkmage**) is an Android app (Kotlin/Compose) with an optional Node.js server for online multiplayer.

## Versioning

- **SemVer** `MAJOR.MINOR.PATCH` — game is in beta (`0.x`). MAJOR = breaking client+server change, MINOR = new content/cards/localization, PATCH = fixes. See `CHANGELOG.md`; releases git-tagged (`v0.1.0`).
- **`versionCode`** (Android, `app/build.gradle.kts`) increments by 1 on **every** released build, else Android refuses the update.
- **`PROTOCOL_VERSION`** — client (`OnlineLobbyViewModel.kt`) ↔ server (`server.js`) handshake; independent of game version. Bumped only on breaking protocol / shared-card-data changes. Mismatch → server rejects JOIN with `VERSION_MISMATCH`. See [[systems/online]].

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
- **`AiEngine.kt`** — heuristic card scoring for AI; returns `AiAction` (Play/Discard); combo-chain lethal lookahead
- **`CardRepository.kt`** — parses `cards.json` → `List<Card>`
- **`OnlineLobbyViewModel.kt`** — WebSocket client, online lobby, online Decision handling, `PROTOCOL_VERSION`
- **`DeckBuilderScreen.kt`** — deck builder UI
- **`ArenaDraftScreen.kt`** — arena draft UI

### Localization (see [[systems/localization]])
- **`Language.kt`** — pack identity (code, name, flag, author, version)
- **`AppStrings.kt`** — all UI strings as typed fields; `LocalStrings` CompositionLocal
- **`LanguagePack.kt`** — `LanguagePack.fromJson()`, `CardText`, Czech fallback chain
- **`LanguageManager.kt`** — loads `assets/lang/<code>.json`, persists selection
- UI strings **and** card texts localized; missing keys fall back to Czech

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

## Card sync: cards.js → cards.json

`server/game/cards.js` is the **single source of truth**. The Gradle task `syncCards` regenerates `app/src/main/assets/cards.json` from it on **every build** (`assembleDebug` logs `syncCards: cards.json updated`).

**Implication:** Never hand-edit `cards.json` — changes are overwritten at the next build. Always edit `cards.js`. (The build output / committed `cards.json` reflects the last sync, so it can be committed too.)

## Related pages
- [[overview]] — game overview
- [[systems/ai]] — AiEngine in depth
- [[systems/online]] — online multiplayer
- [[systems/localization]] — JSON language packs
- [[cards/effects]] — CardEffect sealed class

## Changelog
- 2026-05-21: Page created
- 2026-05-29: Added versioning/`PROTOCOL_VERSION`, localization files, Darkmage rename; `syncCards` now regenerates `cards.json` at build (no manual sync)
