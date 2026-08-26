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
## [2026-07-16] ingest | AI: Inspirace (DrawPerCardPlayed cardType="Magie") played with no effect - AI's totoKoloPenalty/totoBuff gated on comboCardsInHand (any isCombo card in hand), but the real trigger (GameViewModel._playCard) checks card.type against the effect's cardType filter, unrelated to isCombo. AI overvalued Inspirace when holding unrelated combo cards (played it alone, nothing to trigger it) and undervalued/misordered it against non-combo Magie cards (played those first, Inspirace last = wasted). Fixed: matchingTypeCount()/typeMatches() replace the combo-count check in DrawPerCardPlayed/GainResourcePerCardPlayed/GainCastlePerCardPlayed scoring + totoKoloPenalty + totoBuff; added waitForSetupPenalty to discourage playing a matching payoff card before its still-unplayed setup card. Documented in systems/ai.md
## [2026-07-17] ingest | Balance: Spion (071) reworked to "Steal 1 of each resource. +2 Chaos." (added ar('CHAOS',2) on top of the existing 4x steal-1). Text cleanup: removed the redundant "vc. chaosu"/"incl. Chaos" clause from Spion, Chaoticka krize (C09), Anarchie (C12), Chaoticky vymenik (C31) descriptions - "kazdeho zdroje"/"each resource" already implies Chaos, no functional change for those three. Updated cards.js, cards.json, CardPresentation.kt, cs.json, en.json, deckbuilder.html.
## [2026-07-17] ingest | Balance: Drak (051) and Upiri drak (078) chaos bonus reduced from +4 to +3 (ar('CHAOS',4) -> ar('CHAOS',3)). Demon (052) chaos +4 left unchanged (not requested). Updated cards.js, cards.json, CardPresentation.kt, cs.json, en.json, deckbuilder.html.
## [2026-07-19] ingest | Fixed offline auto-pass glitch: when the player's hand+deck are both empty, finishTurn() recursively calls itself every round (only AI acting) with just delay(700L) between rounds - felt like a glitch, AI appearing to rapid-fire several identical cards in a row with no breathing room. Added delay(600L) before the recursive finishTurn() call in this branch only (normal turn transitions unaffected). Documented in mechanics/game-flow.md.
## [2026-07-19] ingest | Found the real cause of the "AI plays the same card 3x in a row" + spurious lose-sound-then-win glitch: the auto-pass branch (player hand+deck both empty) set gameState.value with activePlayer=PLAYER before its own delay/recursive finishTurn() call. endPlayerTurn()/waitTurn() guard only on activePlayer==PLAYER, so the player could click during that window and start a SECOND concurrent finishTurn() coroutine racing the queued one - two AI turns played out over separate deep-copied state (interleaved plays looked like duplicated cards) and could independently reach scheduleGameEnd() with different results (stale lose sound fires synchronously, then the correct win result overwrites the pending reveal). Fixed: auto-pass branch now locks activePlayer=AI immediately, matching the pattern already used elsewhere in finishTurn (documented in mechanics/game-flow.md).
## [2026-07-20] ingest | New "Discard" mechanic: Card.discardEffects (List<CardEffect>, default empty) - alternative effect list fired ONLY when a card is discarded instead of played; card.effects never runs for that instance. Reuses existing CardEffect types (no new effect kinds); self-harm designs use negative amounts on self-targeting effects. Wired into all four game paths: offline human discardCard(), offline AI (AiAction.Discard branch in finishTurn), online server _handleDiscardCard() (GameSession.js, with CARD_LOST notifications + win check), and the deckbuilder.html simulator. AiEngine.bestDiscard() gained scoreDiscardEffect() (separate from scoreEffect, correctly signed for self-harm) so the AI avoids discarding cards that would hurt it and prefers discarding ones that would help it. UI: GameCardView.kt shows a skull badge (DiscardRed border) on cards with discardEffects. New log string logDiscardEffectTriggered (cs/en). Infrastructure only - no card uses the mechanic yet (example discussed: "Zamořené krysy", not created). Documented in new page mechanics/discard.md.
## [2026-07-26] ingest | Campaign localization: CampaignLocation/CampaignOpponent had zero i18n (raw hardcoded Czech .name/.title/.description read directly by all 3 campaign screens). Added title field to CardText, campaignLocations/campaignOpponents maps to LanguagePack, matching LanguageManager lookups, and displayName/displayTitle/displayDescription getters (mirroring Card.displayName). Screens switched to display* properties. Full en+cs content for 4 locations + 40 opponents added to lang/*.json. Also backfilled missing en/cs translations for cards "133"-"139" (see below - added same day, translations were skipped). Documented in systems/localization.md.
## [2026-07-26] ingest | 6 new discard-mechanic cards ("133"-"139", skipping none) + new CardEffect ConvertWallToCastle (converts caster's entire current wall HP into castle HP, first used by Legendary "139" Pohlcení hradeb). Two earlier drafts of the STONES self-harm card ("Zazděný poklad", "Vázaný duch") were rejected by design review as strictly-worse duplicates of existing cards "009"/"013" and redesigned around fresh effect combos (DrainResource(STONES) primary, siphon patterns) before final approval as "138" Podkopané valy. New effect wired offline (CardEffect.kt, GameLogic.kt applyEffects, CardRepository.kt JSON parsing, AiEngine.kt scoreEffect, GameViewModel.kt scoreCardForSituation + sound detection, GameCardView.kt icon, DeckBuilderScreen.kt/ArenaDraftScreen.kt category) and online (engine.js applyEffects, GameSession.js smart-pick scoring, cards.js cwtc() helper) - exhaustive-when compiler errors used to find every offline call site. New placeholder card "134" Krysa (TrapOnDraw(AttackWall(3)), isPlaceholder, maxCopies:0) shuffled in by "133" Zamořené krysy, realizing the hypothetical example mechanics/discard.md originally used to motivate the whole mechanic. Not updated: deckbuilder.html SIMULÁTOR tab's separate hand-authored shorthand effect DB (DECK BUILDER tab unaffected, reads real cards.json). Server-side changes not runtime-tested (no Node.js in this environment). Documented in cards/effects.md and mechanics/discard.md.
## [2026-08-26] ingest | "134" redesigned per explicit user spec, replacing the draw-triggered trap with a real playable curse card. Was: Krysa, 0 CHAOS, effects:[TrapOnDraw(AttackWall(3))] (fired automatically on draw, no player choice). Now: Zamořená Krysa, 3 CHAOS, effects:[] (no play effect - playing it purely costs chaos and, being non-combo, ends the turn via the existing default turn-end-after-play rule, no new CardEffect needed), discardEffects:[BuildCastle(-4), BuildWall(-4)]. Net: whoever draws it must choose between burning a turn + 3 chaos for nothing or taking 4 castle + 4 wall via discard. isPlaceholder/maxCopies:0 unchanged - still only enters play via "133"'s AddToOpponentDeck, still excluded from all reward/decision/deck-builder pools. Updated cards.json, CardPresentation.kt, cards.js, cs.json, en.json. Documented in mechanics/discard.md.
## [2026-07-24] ingest | Wired real card art for the 8 discard-mechanic cards "132"-"139" (Zoufaly zold, Zamorene krysy, Zamorena krysa, Osudova mince, Posledni vypad, Zapomenuta poznamka, Podkopani hradeb for "138" Podkopane valy, Pohlceni hradeb), replacing the art_placeholder_* stand-ins in CardPresentation.kt. User supplied the PNGs directly into app/src/main/res/drawable/ (untracked, no "art_" prefix, distinct from the pre-existing "050" Podkopani hradeb's art_podkopani_hradeb.png). No effect/balance changes.
## [2026-08-26] ingest | Balance: "133" Zamorene krysy - removed its own discardEffects ([bw(-5)]) entirely, now [] (no effect if 133 itself is discarded unplayed). Only "134" Zamorena Krysa (the seeded curse card) still punishes on discard (-4 castle, -4 wall) - correctly hits whoever actually holds and discards it (the opponent, since only their deck receives copies via AddToOpponentDeck). "136" Posledni vypad: AttackPlayer amount 7->10, rarity EPIC->RARE (maxCopies unchanged at 2, both rarities map to the same value). Updated cards.js, cards.json, CardPresentation.kt, cs.json, en.json.
## [2026-08-26] ingest | Online: added server-side PLAY_CARD throttle (PLAY_CARD_MIN_INTERVAL_MS=1000) so two combo cards played back-to-back by the same side are processed at least 1s apart - previously a fast double-tap during a combo chain could get processed almost instantly, sending GAME_STATE updates too fast for the opponent's client to render/notice. Implemented as a queueing wrapper (_handlePlayCard reserves the next free per-side slot >= now, >= end of previous reservation, then calls the renamed _playCardNow via setTimeout if needed) rather than outright rejection, so 3+ rapid taps queue up 1s apart instead of colliding. Guards against the game ending/turn changing mid-delay (same activeSide/phase==='playing' check pattern as the existing _turnTimer callbacks). Server-side only (server/game/GameSession.js) - offline has no equivalent issue (single device, no opponent client to outrun).
## [2026-08-26] ingest | Balance/text: "080" Velkovyroba's description said "Kazdy dul +2" ("every mine +2") but the actual effect ([am('MAGIC',2), am('ATTACK',2), am('STONES',2)]) never included Chaos - misleading. Reworded to match "044" Trifekta dolu's pattern ("Dul magie, utoku i kamene +2"), no effect change. Updated CardPresentation.kt, cs.json, en.json.
## [2026-08-26] ingest | Reworked the PLAY_CARD pacing fix from earlier today: the previous approach delayed *processing* of PLAY_CARD itself (server-side queue before _playCardNow ran), which meant the acting player's own combo also felt sluggish/blocked, not just the opponent's view - not what was wanted. Replaced with _sendStateBothPaced(): the acting player's GAME_STATE always sends immediately (their own play stays instant), only the opponent's copy is routed through a new per-side FIFO delivery queue (_queueRevealFor/_pumpRevealQueue) spaced >=1s apart (REVEAL_MIN_INTERVAL_MS), so each combo card still gets its own reveal to the opponent instead of being skipped/coalesced. Added _clearRevealQueue(), called from the plain _sendStateBoth() before every authoritative broadcast (turn end, discard, decision, game end), to prevent a stale queued reveal from arriving after a later "urgent" message and flickering the opponent's UI out of order. Removed the old _nextAllowedPlayAt/PLAY_CARD_MIN_INTERVAL_MS processing-delay mechanism entirely. Documented in systems/online.md.
