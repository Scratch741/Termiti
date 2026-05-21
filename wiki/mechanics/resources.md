# Resource System

> Four resource types (MAGIC, ATTACK, STONES, CHAOS) are used to pay for cards. Mines produce them automatically each turn.

## Resource types

| Type | CZ name | Base production | Primary use |
|------|---------|-----------------|-------------|
| MAGIC | Magie | 1/turn | Paying helper cards, Chaos generators, Mines |
| ATTACK | Útok | 1/turn | Paying attack cards |
| STONES | Kámen | 1/turn | Paying build cards |
| CHAOS | Chaos | 0/turn | Paying strong Chaos cards |

## Resource production

At the start of each turn the active player receives:
```
resources[type] += mines[type]
```

Default state (game start): each player has 1 mine of each type (MAGIC, ATTACK, STONES) and 0 Chaos mines.

## Gaining Chaos

Chaos does not come from mines by default (starts at 0). Added via effects:
- `AddResource(CHAOS, n)` — instantly (Goblin `046`, Jed `049`, Chaotická jiskra `C01`…)
- `AddMine(CHAOS, n)` — permanently (Chaotický důl `C03`, Chrám chaosu `C30`…)

## Mines (AddMine)

See [[mechanics/mines]] for details.

## Resource modification effects

### Steal (`StealResource`)
Takes resources from opponent and gives them to self. Example: Lupič `065` steals 4 ATTACK.

### Drain (`DrainResource`)
Destroys opponent's resources with no gain for self. Example: Sabotér `067` drains 5 STONES.

### Convert (`ConvertMine`)
Chaotická přeměna `C35` — converts 1 MAGIC mine unit to CHAOS.

## X-cost cards

Cards with `isXCost=true` cost 0 and consume **all** available resources of the given costType:
- Náhlá smrt `101` (ATTACK) — X/2 direct castle hit
- Kamenný příval `102` (STONES) — X/2 castle repair
- Magické rozdělení `103` (MAGIC) — X/2 to both ATTACK and STONES

## Related pages
- [[mechanics/mines]] — mine system
- [[cards/types]] — costType of cards
- [[cards/effects]] — StealResource, DrainResource, AddResource

## Changelog
- 2026-05-21: Page created
