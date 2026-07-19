# Turn Flow

> Each round consists of the player's turn and the AI's turn. Combo cards allow playing multiple cards in one turn.

## Round structure

```
Start of round (currentTurn++)
  ├── Apply mines (AddMine production → resources)
  ├── Draw card to hand
  └── Active player's turn
        ├── Player plays a card (playCard)
        │     ├── Deduct cost (resources -= cost)
        │     ├── Apply effects (GameLogic.applyEffects)
        │     ├── Decision effect? → show overlay → wait
        │     ├── Combo card? → player continues turn
        │     └── Non-combo? → finishTurn → switch to opponent
        └── Player discards a card (max 1× per turn)
              └── turn CONTINUES — player may still play cards / end turn
```

## Discard rule (1× per turn)

Discarding a card does **not** end the turn, but is allowed only **once per turn**. After using it, the drag-to-discard gesture is disabled for the rest of the turn (UI) and the server rejects further `DISCARD_CARD` (authoritative — `discardUsedThisTurn`, sent to the client as `myState.discardUsed`). The flag resets when the player's next turn starts.

- Offline: `GameViewModel.discardCard` + `playerDiscardUsed` flag (reset in `finishTurn` / game start)
- Online: `GameSession._handleDiscardCard` keeps the turn running (restarts the turn timer, like combo cards)
- AI: unchanged — the AI only discards when it cannot play anything with a full hand, so ending its turn right after is equivalent

## `playCard()` — key function (Gameviewmodel.kt)

```
1. Calculate cost (effectiveCost; X-cost cards consume everything)
2. Deduct resources from player
3. Move card from hand to discardPile
4. Call applyEffects() → mutates PlayerState
5. Handle persistent effects (DrawPerCardPlayed, GainResourcePerCardPlayed, CloneNextPlayed)
6. Handle DrawCard effects → draw with animation (delay 350ms)
7. Decision effect? → save state, show overlay, RETURN (turn paused)
8. Combo? → isPlayerComboTurn = true, continue
9. Non-combo? → finishTurn()
```

## `finishTurn()` — turn transition

```
1. Check win condition (checkWinCondition)
2. Switch activePlayer (PLAYER ↔ AI)
3. Add mine resources for new active player
4. Draw card for new active player
5. AI turn? → run aiPlayTurn()
```

## Animation timing

DrawCard animations are timed using `delay()` in a coroutine:
- After each drawn card → `delay(350L)` → then `finishTurn()`
- Reason: Compose batching — without a suspension point, state updates merge and the animation appears only at the start of the next turn

## Auto-pass (player has no hand and no deck)

At the end of `finishTurn()`, if the player's hand **and** deck are both empty (nothing left to draw or play), the game logs "Hráč přeskočil kolo" and calls `finishTurn()` again for them automatically — this is a recursive tail-call: `finishTurn` invoking itself once per stuck round, each call its own `viewModelScope.launch`.

### Bug: concurrent double `finishTurn()` (real root cause, not just pacing)

The auto-pass branch used to set `gameState.value = s3` with `activePlayer = ActivePlayer.PLAYER` (inherited from `s3`'s construction) and only *then* `delay(700L)` before recursing into the next `finishTurn()`. `endPlayerTurn()`/`waitTurn()`/`discardCard()`/`playCard()` all guard on nothing more than `activePlayer == PLAYER` — so during that ~0.7–1.3s window the player could tap Wait/End Turn (the guard passes, since `activePlayer` genuinely *was* `PLAYER`) and start a **second, fully independent `finishTurn()` coroutine** racing the one already queued by auto-pass. Two concurrent AI turns then play out over separate deep-copied state, interleaved on screen — looking like the AI replaying the same card several times — and each can independently reach `checkWinCondition()` → `scheduleGameEnd()` with a *different* result (AI attack RNG/scoring noise differs per run). `scheduleGameEnd()` plays the win/lose sound synchronously and only *later* (1750ms) commits `gameOver.value` — so the first (stale) call's sound plays immediately, then the second call's `gameEndJob?.cancel()` overwrites the pending reveal with the correct final result. Net effect: a "you lost" sound followed by a "you win" screen.

This matches the general pattern already guarded against elsewhere in `finishTurn` (see the AI-turn code comment: *"activePlayer = AI i v mezistavu, aby hráč nemohl kliknout v okně delay a nespustil druhou souběžnou finishTurn coroutinu"*) — the auto-pass branch just hadn't been brought in line with it.

**Fixed 2026-07-19:** the auto-pass branch now sets `activePlayer = ActivePlayer.AI` immediately (before any `delay`), closing the click window entirely, and adds a `delay(600L)` before the recursive `finishTurn()` call (on top of the existing `delay(700L)`) so consecutive AI-only rounds aren't back-to-back with no breathing room. Scoped to this branch only — normal turn transitions are unaffected.

## Round limit

After reaching **round 99** (`currentTurn >= 99`) the game ends: the player with the taller castle wins.
Implemented in `GameState.checkWinCondition()` and `GameSession.js` (server).

## Related pages
- [[mechanics/win-conditions]] — win conditions
- [[mechanics/combo]] — Combo cards
- [[cards/decisions]] — Decision turn interruption
- [[systems/ai]] — AI turn

## Changelog
- 2026-05-21: Page created
- 2026-07-12: Discard rule changed — discarding no longer ends the turn; allowed 1× per turn (offline + online server-authoritative)
- 2026-07-19: Documented auto-pass (empty hand + deck) recursive `finishTurn()` cascade; added extra `delay(600L)` to fix back-to-back AI-only rounds feeling like a glitch
- 2026-07-19: Found and fixed the real root cause: the auto-pass branch exposed `activePlayer = PLAYER` during its delay window, letting the player click Wait/End Turn and start a second concurrent `finishTurn()` — two AI turns raced over separate state, replaying cards and double-firing `scheduleGameEnd()` (stale "lose" sound overwritten by the correct "win" result). Now locks `activePlayer = AI` immediately in that branch.
