# Mine System

> Mines are permanent resource generators. Each player has 4 mine types; every turn they produce resources according to their level.

## Default state

```
mines[MAGIC]  = 1
mines[ATTACK] = 1
mines[STONES] = 1
mines[CHAOS]  = 0
```

## How mines work

At the start of each turn:
```kotlin
for (type in ResourceType.values()) {
    resources[type] = (resources[type] ?: 0) + (mines[type] ?: 0)
}
```

## Blocking mines

`BlockMine(type, turns)` — blocks opponent's mine production for N turns. A blocked mine does not produce, but its level is unchanged.

Cards: Sabotáž `C13` (MAGIC, 2 turns), Ničení kamenolomu `C14` (STONES), Zákeřnost `C15` (ATTACK), Velká sabotáž `C16` (MAGIC + STONES, 3 turns).

## Destroying mines

`DestroyMine(type, amount)` — reduces opponent's mine by `amount`. **Minimum is 1** — the last unit of a non-CHAOS mine cannot be destroyed.

## Mine cards by type

### Magic mines
| ID | Name | Cost | Effect |
|----|------|------|--------|
| 013 | Magický pramen | 3 MAGIC | am(MAGIC, 1) |
| 016 | Velký pramen | 5 MAGIC | am(MAGIC, 2) |
| 045 | Očarované doly | 7 MAGIC | am(MAGIC, 3) |
| 073 | Škola magie | 4 MAGIC | ar(MAGIC,2) + am(MAGIC,1) |
| 075 | Rozmach těžby | 6 MAGIC | am(MAGIC,2) + am(STONES,1) |

### Attack mines
| ID | Name | Cost | Effect |
|----|------|------|--------|
| 015 | Výcvikový tábor | 3 MAGIC | am(ATTACK, 1) |
| 043 | Výcvikové centrum | 5 MAGIC | am(ATTACK, 2) |
| 072 | Zbrojnice | 4 MAGIC | ar(ATTACK,2) + am(ATTACK,1) |
| 076 | Vojenská základna | 6 MAGIC | am(ATTACK,2) + am(STONES,1) |
| 104 | Válečný trénink | 4 ATTACK | ap(5) + am(ATTACK,1) |

### Stone mines
| ID | Name | Cost | Effect |
|----|------|------|--------|
| 014 | Kamenolom | 3 MAGIC | am(STONES, 1) |
| 042 | Velký kamenolom | 5 MAGIC | am(STONES, 2) |
| 063 | Věž strážní | 5 STONES | bw(11) + am(STONES,1) |
| 089 | Architekt | 4 STONES | bw(5) + am(STONES,1) |
| 090 | Rozšíření těžby | 7 STONES | am(STONES, 2) |

### Chaos mines
| ID | Name | Cost | Effect |
|----|------|------|--------|
| C03 | Chaotický důl | 4 MAGIC | am(CHAOS, 1) |
| C29 | Bouřlivá mysl | 5 MAGIC | ar(CHAOS,3) + am(CHAOS,1) |
| C30 | Chrám chaosu | 7 MAGIC | am(CHAOS, 2) |

### Multi / special
| ID | Name | Cost | Effect |
|----|------|------|--------|
| 044 | Trifekta dolů | 6 MAGIC | am(MAGIC,1) + am(ATTACK,1) + am(STONES,1) |
| 080 | Velkovýroba | 10 MAGIC | am(MAGIC,2) + am(ATTACK,2) + am(STONES,2) |
| C35 | Chaotická přeměna | 4 CHAOS | cvM(MAGIC→CHAOS) |

## Decision card for mines

**Průzkum dolů `117`** (1 MAGIC, Combo, EPIC) — `DecisionMine`: shows 4 options, 1 random mine of each type. Player picks one and adds it to hand.

## Related pages
- [[mechanics/resources]] — resource system
- [[cards/decisions]] — DecisionMine
- [[systems/ai]] — AI mine evaluation

## Changelog
- 2026-05-21: Page created
