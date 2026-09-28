# Localization

> UI strings **and** card texts are fully localizable via JSON packs in `assets/lang/<code>.json`; any missing key falls back to Czech so incomplete community translations never crash the app.

## Overview

As of `v0.1.0` the game ships complete Czech (`cs`) and English (`en`) packs covering all UI strings and all 176 cards (names + descriptions) plus passive-ability texts. Adding a new language = dropping a new `<code>.json` file into `assets/lang/` (see `lang/TRANSLATION_GUIDE.md`).

## File format (`assets/lang/<code>.json`)

```json
{
  "meta":    { "code": "en", "name": "English", "flag": "🇬🇧", "author": "...", "version": 1 },
  "strings": { "play": "PLAY", "settings": "SETTINGS", "decisionAlchemyTitle": "ALCHEMY", ... },
  "cards":   { "001": { "name": "Quick Attack", "desc": "Attack the enemy for 5. [Combo]" }, ... },
  "abilities": { "extra_castle": { "name": "...", "desc": "..." }, ... },
  "campaignLocations": { "loc_goblins": { "name": "Goblin Camp", "desc": "..." }, ... },
  "campaignOpponents":  { "gob_scout": { "name": "Goblin Scout", "title": "Cocky Brawler", "desc": "..." }, ... }
}
```

- **`meta`** — language identity (`Language` data class: code, name, flag, author, version).
- **`strings`** — UI strings, parsed into `AppStrings` (one field per key).
- **`cards`** — `id → { name, desc }` (`CardText`). Missing entry → card's built-in Czech text.
- **`abilities`** — passive-ability `id → { name, desc }`.
- **`campaignLocations`** — Campaign `CampaignLocation.id → { name, desc }` (`title` unused).
- **`campaignOpponents`** — Campaign `CampaignOpponent.id → { name, title, desc }`.

## Key Kotlin types

| Type | Role |
|------|------|
| `Language` | Pack identity (code, name, flag, author, version) |
| `AppStrings` | All UI strings as typed fields; `LocalStrings` CompositionLocal exposes the current one |
| `CardText` | `{ name, desc, title="" }` — shared record reused for cards, abilities (name=title), campaign locations (title unused) and campaign opponents (all three fields) |
| `LanguagePack` | `language + strings + cards + abilities + campaignLocations + campaignOpponents`; built by `LanguagePack.fromJson(root, fallbackPack)` |
| `LanguageManager` | Loads packs from assets, persists selection (SharedPreferences), exposes reactive current pack |

## Fallback chain

1. Requested key in the active pack →
2. else the same key in the **Czech fallback pack** (`fbVal`) →
3. else a hard-coded Czech default baked into `buildStrings`.

`LanguagePack.fallback()` is a hard-coded Czech pack used if JSON loading fails entirely. For card texts, a missing/empty `name`/`desc` falls back to the card's built-in Czech text in [[cards/effects]] / `CardPresentation`.

## Card text resolution

Card game data (cost, effects, rarity) stays in `cards.json`; only **display name + description** are localized. `CardRepository` / `CardPresentation` consult the active `LanguagePack.cards[id]` first, then fall back to the Czech `CardPres.description` / built-in name.

### Card text style (cs + en)

Both languages follow the same phrases (the Czech text is also the built-in fallback in `CardPresentation.kt` and must stay identical to `cs.json`):

| Effect | Czech | English |
|---|---|---|
| Attack (wall first) | `Zaútočí za N.` | `Attack for N.` |
| Castle damage | `Poškodí hrad −N.` | `Castle damage −N.` |
| Build | `Hrad +N.` / `Hradby +N.` | `Castle +N.` / `Walls +N.` |
| Resource gain | `Magie +N.` / `kámen +1` | `+N Magic.` / `+1 Stone` (never "refund") |
| Mine | `Důl magie +1.` / `Doly kamene +2.` | `+1 Magic mine.` / `+2 Stone mines.` (no "Permanently" — mines always are) |
| Several mines | `Důl magie, útoku i kamene +1.` | `+1 to each mine (Magic, Attack, Stone).` |
| Drain | `Soupeř ztratí N kamenů.` | `Opponent loses N Stone.` |
| Steal castle | `Ukradni N hradu.` | `Steal N castle HP.` |
| Condition | `Pokud máš <5 hradeb, …` | `If you have <5 walls, …` |
| Castle condition | `Pokud máš <40 hradu, hrad +10.` | `If you have <40 castle, castle +10.` |
| Steal resource | `Ukradni 4 útoku.` (no „od soupeře“) | `Steal 4 Attack.` (no "from the opponent") |
| Card type this turn | `za každou zahranou kartu **ÚTOK** útok +2` | `+2 Attack per **ATTACK** card played` |
| Discard effect | `**Zahození:** hradby −4.` | `**Discard:** walls −4.` |
| Choice of resources | `magie, útok, kámen nebo chaos +4` | `+4 Magic, Attack, Stone or Chaos` |

Resource names are capitalized (Magic, Attack, Stone, Chaos), castle/walls are not mid-sentence. **Length:** the card shows at most 4 lines (7 sp); keep a description around 70 visible characters or less, fewer when it has bold upper-case keywords (they are wider). The longest Czech text (77) is the ceiling. A line break goes only where the Czech has one (before `[Combo]`, the second sentence of Daň z chaosu) — cards wrap on their own, no manual breaks mid-sentence.

## Adding a UI string

Every user-visible string goes through `AppStrings` — never a literal in a composable. Four places, all required (`AppStrings` delegates its properties to a map — **do not** turn it back into a constructor parameter per string: ART rejects calls with more than 255 argument registers, which crashed the app at startup with a VerifyError):

1. `AppStrings.kt` — `val myKey: String by values`
2. `LanguagePack.kt` `buildStrings` — `"myKey" to str("myKey", "Czech default"),`
3. `assets/lang/cs.json` and `assets/lang/en.json` — `"myKey": "..."` under `strings`

Read it with `LocalStrings.current.myKey` inside a composable, or `LanguageManager.currentStrings.myKey` outside one (ViewModels, coroutines inside `LaunchedEffect`, enum getters). Parameters use `%d`/`%s` + `.format(...)`.

Keep **internal keys** Czech when code compares them (deck-builder/arena effect categories `"Útok"`, `"Obrana"`, …; card `type`; `LogEntry` actor `"Hráč"`) and translate only at the display point — e.g. `arenaCategoryLabel(cat)`, `Card.displayType`, `Rarity.displayLabel`, `localizedDeckName(name)` (which also maps the stored Czech starter/template deck names).

**Known gap:** the online server (`server/game/GameSession.js`, `server/server.js`) still sends Czech text — the online game-log lines (`_log`/`_logFor`) and `ERROR`/`GAME_ERROR` messages — so those stay Czech for English players. Fixing it needs the server to send codes + params instead of prose. `VERSION_MISMATCH` is already handled client-side (the server text is ignored).

## Related pages
- [[architecture]] — where localization fits in the stack
- [[cards/decisions]] — decision overlay strings (alchemy, peek, etc.)
- [[overview]] — feature summary

## Changelog
- 2026-05-29: Page created — JSON localization system landed in `v0.1.0` (cs + en, UI + all cards)
- 2026-07-26: Campaign content (`CampaignLocation`/`CampaignOpponent`, previously 100% hardcoded Czech with no lookup at all) now localizable — added `title` field to `CardText`, `campaignLocations`/`campaignOpponents` maps to `LanguagePack`, `campaignLocationName/Desc`/`campaignOpponentName/Title/Desc` lookups to `LanguageManager`, and `displayName`/`displayTitle`/`displayDescription` getters to `CampaignLocation`/`CampaignOpponent` (mirroring `Card.displayName`). `CampaignMapScreen.kt`/`CampaignLocationScreen.kt`/`CampaignResultScreen.kt` switched from raw `.name`/`.title`/`.description` to the new `display*` properties. Full cs+en content added for all 4 locations + 40 opponents. Also backfilled `en`/`cs` translations for cards `"133"`-`"139"` (missed when those cards were added).
- 2026-09-21: Large sweep of hardcoded Czech UI text (~180 new keys): multiplayer lobby + login + connection errors, shop, leaderboard, online/offline in-game dialogs and overlays, arena, campaign map/location labels, profile setup, deck builder, roguelike (incl. act titles, enemy names and battle label), rarity names and built-in deck/template names. Added the "Adding a UI string" section and documented the remaining server-side gap.
- 2026-09-27: English card descriptions unified (63 cards): "refund 1 Stone" -> "+1 Stone", mines without "Permanently", one wording for castle damage (was "hit the castle for", "Direct castle hit for", "Damage the castle by", "Deal 2 damage"), "+N Magic" everywhere (was also "Magic +2"), "opponent loses" (was "sabotages 3 stone", "destroy 2 of the opponent's Magic"), 17 manual mid-sentence line breaks removed, and two inaccurate texts fixed (Strategic Construction: you choose 1 of the top 4; sabotage cards block only that mine). Style table added above.
- 2026-09-27: Czech card descriptions unified to the same rules (61 cards, fallback in CardPresentation.kt kept identical) with matching English tweaks (27 cards); style table extended.
