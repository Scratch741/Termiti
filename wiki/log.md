# Termiti Wiki — Log

> Append-only log of all operations. Add entries as `## [YYYY-MM-DD] type | description`.
> Quick scan of recent entries: every entry starts with `## [`.

---

## [2026-05-21] init | Wiki created

Initial wiki structure created for Termiti game following the Karpathy LLM Wiki pattern.

**Pages created:**
- `CLAUDE.md` — LLM agent schema
- `index.md` — page catalog
- `overview.md` — CZ game overview
- `architecture.md` — technical architecture
- `cards/types.md` — card types
- `cards/effects.md` — CardEffect sealed class
- `cards/decisions.md` — Decision cards
- `mechanics/game-flow.md` — turn flow
- `mechanics/resources.md` — resource system
- `mechanics/mines.md` — mine system
- `mechanics/combo.md` — Combo system
- `mechanics/win-conditions.md` — win conditions
- `systems/ai.md` — AiEngine
- `systems/online.md` — online multiplayer

**Knowledge base built from:**
- Full development conversation (Lottie removal, Decision bugs, cards 113–117, rarity changes, starter deck, 99-round limit)
- Source files: CardEffect.kt, Gameviewmodel.kt, GameSession.js, cards.js, AiEngine.kt

## [2026-05-21] ingest | Card 117 — Průzkum dolů

New card `117 Průzkum dolů` added (1 MAGIC, Combo, EPIC) with `DecisionMine` effect.
Updated pages: `cards/decisions.md`, `mechanics/mines.md`, `systems/ai.md`, `cards/effects.md`.

## [2026-05-26] ingest | Cards 119–121, C39 + new effects

New cards added (4 ATTACK Combo EPIC `119 Válečný pokřik`, 2 MAGIC EPIC `120 Hromadná sleva`, 3 CHAOS RARE `121 Kletba cen`, C39 CHAOS EPIC `Velký zmatek`).

New effects introduced: `GiveRandomCard(costType)`, `ModifyHandCost(delta, targetOpponent)`, `RandomizeHands`.

Updated pages: `cards/effects.md`.

## [2026-05-26] ingest | Card 122 Stavební pokřik + win-condition fix

New card `122 Stavební pokřik` (4 STONES, Combo, EPIC): `bc(7)` + `grc('STONES')` — direct building mirror of `119 Válečný pokřik`.

Win condition fix in `GameState.kt`: `currentTurn > 99` (was `>= 99`); both-decks-empty path now always uses `resolveByHp()` even when turn limit fires; castle/death conditions evaluated before turn limit.

Top bar texture replaced: `bg_top_bar.png` (1920×160 px) replaces `bg_top_panel.png`.

Updated pages: `cards/effects.md`, `log.md`.

## [2026-05-28] ingest | Cards 124–128 (new + cost-6 magic)

New cards added:
- `124 Alchymistova volba` (2 MAGIC, Combo, RARE): `DecisionChooseResource` — pick 4 Magic / 4 Attack / 4 Stones.
- `125 Válečný zápal` (2 ATTACK, Combo, RARE): `ap(4)` + `grp(CHAOS,1,'Útok')`.
- `126 Magický proud` (3 MAGIC, Combo, EPIC): `grp(CHAOS,2,'Magie')`.
- `127 Pyroblast` (6 MAGIC, EPIC): `ac(10)` + `dr(MAGIC,2)` — direct castle hit + magic drain.
- `128 Archmág` (6 MAGIC, Combo, LEGENDARY): `dpc()` + `grp(MAGIC,1)` — this turn, each extra card played draws 1 and gains 1 Magic (dual engine).

New effect introduced: `DecisionChooseResource(options)` — decision overlay shows resource buttons instead of cards; resolved via `resolveResourceDecision` (offline) / `resolveOnlineResourceDecision` (online). Server: added to `DECISION_TYPES`, `_buildDecisionOptions`, `_resolveDecision`; `DECISION_REQUEST` carries `resourceOptions`.

127/128 use only existing effects (`AttackCastle`, `DrainResource`, `DrawPerCardPlayed`, `GainResourcePerCardPlayed`).

Updated pages: `cards/effects.md`, `cards/decisions.md`, `log.md`.

## [2026-05-29] ingest | v0.1.0 — localization, versioning, Temný mág, AI lethal lookahead

Large upstream pull (`30baa61..123c54a`, 44 files). Wiki synced to reflect:

- **Localization system landed** (was only "prepared"): UI strings + all 176 card texts + passive abilities localizable via `assets/lang/<code>.json`; complete `cs` + `en`; Czech fallback chain. New types `LanguagePack`, `CardText`, `AppStrings`, `LanguageManager`. → **new page `systems/localization.md`**.
- **Versioning introduced**: SemVer + `CHANGELOG.md` + git tag `v0.1.0`, `versionCode`/`versionName`, and `PROTOCOL_VERSION` client↔server handshake (`VERSION_MISMATCH` on mismatch). → `architecture.md`, `systems/online.md`.
- **App display name** renamed Termiti → **Darkmage**. → `overview.md`, `architecture.md`.
- **New card `L01` Temný mág** (13 CHAOS, LEGENDARY): `sr` MAGIC/ATTACK/STONES 3 each + `ac(18)` + `sca(5)` — iconic namesake legendary. Card total now **176**.
- **`DecisionChooseResource` reworked**: options now render as **placeholder cards** (`resourcePlaceholderCard()`) reusing the card-picker UI, not custom buttons; localized titles. → `cards/decisions.md`, `cards/effects.md`.
- **AI**: documented `isLethal` + combo-chain lethal lookahead (`comboSetupForLethal`, ported to simulator), smarter discard, DecisionChooseResource picks least-held resource. → `systems/ai.md`.
- **`syncCards` now regenerates `cards.json` at build** (no more manual sync) — corrected the stale "Node.js not in PATH" workaround. → `architecture.md`.
- **Card balance**: `125 Válečný zápal` chaos trigger now counts **every** card (`grp('CHAOS',1)`, no type filter); Temný mág cost 8→13.

Updated pages: `overview.md`, `architecture.md`, `systems/online.md`, `systems/ai.md`, `cards/decisions.md`, `cards/effects.md`, `index.md` + new `systems/localization.md`.

## [2026-05-21] maintenance | Translated wiki to English

## [2026-05-21] ingest | Localization infrastructure

Added language switching (Czech / English) preparation:
- `Language.kt` — enum with CZECH / ENGLISH + label + flag
- `LanguageManager.kt` — singleton, SharedPreferences persistence, `currentState: MutableState<Language>` for reactive Compose updates
- `AppStrings.kt` — `AppStrings` data class with all UI strings + `CzStrings` + `EnStrings` + `LocalStrings` CompositionLocal + `currentStrings` shortcut
- `CardPresentation.kt` — `CardPres.descriptionEn` field added; `localizedDescription(language)` helper
- `SettingsScreen.kt` — language toggle (CZ 🇨🇿 / EN 🇬🇧) + strings via `LocalStrings`
- `MainActivity.kt` — `LanguageManager.init()` + `CompositionLocalProvider(LocalStrings provides ...)`

Migration status: SettingsScreen strings migrated. All other screens still use hardcoded Czech strings — migrate by replacing literals with `LocalStrings.current.*`.

All pages rewritten in English for token efficiency. `overview.md` kept in Czech as a human-readable overview.
## [2026-07-12] ingest | AI discard fix: discards only with a full hand + non-empty deck (previously discarded even with an empty deck = pure card loss); Discard rules section added to systems/ai.md
## [2026-07-12] ingest | Mirror/Clone cost parity: online server charges Klon as source effectiveCost+1 in source costType (was: own cost 2 + modifier); ModifyHandCost costModifier preserved on morph cards (online client no longer hardcodes it, offline updateCloneCards keeps curse idempotently); Morph cards section added to cards/effects.md
## [2026-07-12] ingest | Online empty-deck fix: SKIP_TURN from a player who played/discarded this turn (combo) is treated as normal end-turn via actedThisTurn tracking; game no longer ends prematurely. Documented in mechanics/win-conditions.md
## [2026-07-12] ingest | Discard rule change: discarding a card no longer ends the turn, allowed 1x per turn (offline GameViewModel.playerDiscardUsed + online GameSession.discardUsedThisTurn, server-authoritative with myState.discardUsed for the client). Documented in mechanics/game-flow.md
## [2026-07-12] ingest | AI endgame last-chance fallback fix: force-played zero-effect conditional cards (e.g. Zasobnik with unmet condition) when losing with empty decks. realizedAttackOrBuild() now recurses into ConditionalEffect + checkCondition; absolute fallback now requires effectScore > 0. Documented in systems/ai.md
## [2026-07-16] ingest | Online: self-inflicted overdraw burns (natural turn-draw with full hand, DrawCard/DrawBoth cards like Studna vedomosti, RandomizeHands) were logged but not shown in the discard slot - GAME_STATE's stale lastPlayedCard (only reset in _handleEndTurn/_handleSkipTurn, not after _advanceTurn's non-combo path) overwrote the CARD_LOST-driven display. Fixed in _advanceTurn (burned.length gate, was traps-only) and _handlePlayCard (selfLostCards gate). Documented in systems/online.md
## [2026-07-16] ingest | Online: Likvidace (DecisionBurnOpponent) / Zakerny spion (PeekAndStealHand) left the discard slot permanently stuck showing the lost opponent card instead of reverting to 'played [card]', because _resolveDecision unconditionally nulled lastPlayedCard, preventing the natural self-correction that direct BurnCard/StealCard plays get for free. Fixed: skip the null for these two decision types + client-side gameLog dedup by card instance id (lastLoggedPlayedCardId) to avoid duplicate log entries from the resend. Documented in systems/online.md
## [2026-07-16] ingest | server.js had NO WebSocket heartbeat at all - a connection that dies without a clean TCP close (WiFi drop, OS-suspended app) stays readyState=OPEN on the server indefinitely, becoming a permanent ghost in players/queue/superQueue. Matches reported bug: two devices in lobby saw different online counts, and both searching super_random never matched each other. Added standard ws-library ping/pong heartbeat (30s interval, ws.terminate() on missed pong, reuses existing 'close' cleanup path). Documented in systems/online.md
