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
