# Pasivní schopnosti – design dokument

## Pravidla systému
- Hráč může mít aktivní **max 2 pasivní schopnosti** najednou.
- Každou schopnost lze **vypnout** (slot zůstane prázdný).
- Schopnosti se **odemykají levelem** a **kupují za zlato**.
- Jednou koupená schopnost je hráčova navždy.

## Efekty pasivních schopností
Schopnosti upravují **počáteční stav hráče** při zahájení každé hry.

| ID | Název | Efekt | Level | Cena |
|----|-------|-------|-------|------|
| `extra_castle` | Pevný hrad | +5 startovní hrad, ale vítězný cíl se zvýší z 60 na 65 | 2 | 150 🪙 |
| `extra_wall` | Silné hradby | +5 startovní hradba | 3 | 100 🪙 |
| `extra_magic` | Magický talent | +1 magie na začátku hry | 4 | 120 🪙 |
| `extra_attack` | Bojový výcvik | +1 útok na začátku hry | 4 | 120 🪙 |
| `extra_stones` | Kamenný základ | +1 kameny na začátku hry | 5 | 120 🪙 |
| `extra_chaos` | Chaotická mysl | +1 chaos na začátku hry | 6 | 150 🪙 |

## Technická implementace
- `PassiveAbility.kt` – enum s definicemi všech schopností
- `PlayerProfile.unlockedAbilities` – Set<String> koupených schopností
- `PlayerProfile.activeAbilities` – List<String> (max 2 aktivních)
- `PlayerProfileManager.buyAbility()` / `setActiveAbilities()`
- `GameViewModel.createInitialState()` – čte `activeAbilities` z profilu a aplikuje buff na `PlayerState`
- `GameState.winTarget` – dynamický win target (default 60, +5 s `extra_castle`)

## Budoucí rozšíření
- Více schopností (větší balík, extra líznutí, levnější karty…)
- Schopnosti odemykané jinak než levelem (achievementy, aréna…)
- Schopnosti s vizuálním efektem v UI
