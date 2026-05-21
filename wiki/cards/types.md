# Card Types

> Every card belongs to one type determined by its `costType` and effects. The type affects how other effects interact with the card (Inspirace, Rekrut, Stavitel…).

## Types and costType

| Type | costType | Description |
|------|----------|-------------|
| **Útok** (Attack) | ATTACK | Damages the opponent (AttackPlayer, AttackWall, AttackCastle) |
| **Stavba** (Build) | STONES | Builds castle and walls (BuildCastle, BuildWall) |
| **Magie** (Magic) | MAGIC | Resources, mines, utility effects (paid in Magic) |
| **Chaos** | CHAOS | Strong effects, destruction, stealing; paid in Chaos or Magic |
| **Líznutí** (Draw) | various | Cards with DrawCard effect (Průzkumník, Bojová taktika…) |

## How card type is derived (`deriveCardType`)

Type is derived in `engine.js` (server) and analogously in Kotlin:

1. If the card has an `AddMine` effect → type **"Doly"** (special category)
2. Otherwise derived from `costType`:
   - `ATTACK` → **"Útok"**
   - `STONES` → **"Stavba"**
   - `MAGIC` → **"Magie"**
   - `CHAOS` → **"Chaos"**

## ID ranges overview

| Range | Type |
|-------|------|
| 001–018 | Basic Attack and Build |
| 019–036 | Extended Attack and Build |
| 037–045 | Resources and Mines |
| 046–097 | Arcomage-inspired |
| 098–117 | Special cards (X-cost, Decision, new) |
| C01–C35 | Chaos cards |
| D01–D09 | Draw cards |

## How type affects gameplay

- **`DrawPerCardPlayed`** (Inspirace `D09`) — triggers on cards of a given type: `dpc('Magie')` = draw for every Magic card played
- **`DecisionChooseType`** (Rekrut `109`, Stavitel `112`, Goblin šaman `113`, Chaotický mudrc `114`) — offers random cards of that type
- **`GainResourcePerCardPlayed`** — triggers on type (Válečné bubny: +2 ATTACK per Útok card)
- **`GainCastlePerCardPlayed`** — triggers on type (Cihla na cihlu: +3 castle per Stavba card)

## Related pages
- [[cards/effects]] — all effects in detail
- [[cards/decisions]] — Decision cards
- [[mechanics/resources]] — resource system

## Changelog
- 2026-05-21: Page created
