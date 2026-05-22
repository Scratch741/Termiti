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
