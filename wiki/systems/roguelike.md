# Roguelike

> Linear 12-battle run with a 20-card deck, four difficulties that unlock in order, archetype opponents and gold / pack rewards.

## Content

### Run structure
- Deck: exactly 20 cards from the player's collection, rarity budget 20 points (legendary 4 · epic 2 · rare 1 · common 0). Built in the deck builder in roguelike mode (3 saved roguelike decks).
- 12 battles = 3 acts × 4, the 4th battle of each act is a boss (★). Castle HP carries over between battles (capped at max castle, start 30), walls reset to 15 each battle. A loss ends the run.
- After every win: a mandatory pick of 2 cards (3 after a boss) from 4 (5) rare+ offers from the player's collection; limited rerolls per run. After a boss also an optional bonus (+6 max castle, +6 walls, repair 15, +1 mine).
- Build-win target = `max(70, maxCastle + 15)` (prevents starting a battle above the target).
- Code: `RogueData.kt` (run state, config), `RogueEnemies.kt` (difficulty, archetypes, enemy generation – pure Kotlin, also used by the simulation), `GameViewModel` (run flow), `RogueScreens.kt` (reward, end, difficulty dialog), `RogueSaveManager` / `RogueBattleState` (persistence).

### Difficulties (`RogueDifficulty`)
Apprentice and Knight are open; completing Knight unlocks Commander (Vojevůdce), completing Commander unlocks Nightmare. Cleared difficulties are stored in `PlayerProfile.rogueCleared`. The choice is made in a dialog after tapping „Zahájit run"; the last one is remembered (`rogue_difficulty` pref).

| | Učeň | Rytíř | Vojevůdce | Noční můra |
|---|---|---|---|---|
| Enemy castle / walls | ×0.60 | ×0.80 | ×0.95 | ×1.10 |
| Heal after a win | +10 | +8 | +5 | +2 |
| Deck quality shift | −4 | −2 | +2 | +6 |
| Rerolls per run | 5 | 3 | 2 | 1 |
| Enemy start resources | – | – | +1 each | +2 each, +1 card |
| Gold (act bosses + end) | 135 | 270 | 405 | 540 |
| Packs on completion | 0 | 1 | 2 | 3 |
| First completion extra packs | 1 | 2 | 3 | 5 |
| XP on completion | 50 | 100 | 150 | 200 |

Gold: act 1 boss 40, act 2 boss 80, completion 150, × difficulty multiplier (0.5 / 1 / 1.5 / 2). Boss gold is credited immediately (kept even if the run is lost later); completion rewards and packs are credited when the last boss falls. Each battle also gives the normal offline result reward (win 25 XP + 10 gold). No entry fee.

### Opponents (`RogueEnemyGen`)
- **Archetype** (`RogueArchetype`): Attacker, Mage, Defender, Colossus, Saboteur (quality ≥ 4), Chaos (≥ 5) – each based on preset decks (`PRESET_DECKS`). Never the same archetype twice in a row. The name says the archetype and grows by act (e.g. Nájezdník → Válečník → Válečný vládce).
- **Quality** `q = battle index + (boss ? 1 : 0) + difficulty shift` (min 0). It sets rarity caps (rare 8 + q, epic (q − 3)·3/4, legendary 1 from q 9, 2 from 11, 3 from 15) and **coherence** – the share of the deck taken from the archetype preset (0.10 + 0.025·q; Knight 10 → 35 %, Nightmare up to 55 %); the rest is a random balanced deck.
- Cards over a cap, banned cards (`ENEMY_DECK_BANLIST`: morph / swap / bomb / X-cards) and copies over the rarity limit are replaced by a card with the same resource, the closest cost, the same kind (mine / card type) and the highest rarity that still fits. Always 30 cards.
- Stats: base castle 16 + 2·battle (+4 boss), walls 6 + battle (+2 boss), start magic / attack = act (+1 boss), extra attack mine from act 2, magic mine in act 3, stone mine for bosses; ×difficulty and bonuses above.

### Balance (simulation)
`EngineSimulation.rogueRuns` plays whole runs (AI as the player with a 20-card deck from a preset cut down to the budget, picking reward cards that fit its resources). AI plays worse than a human, so compare difficulties relatively. 2000 runs per difficulty (2026-11-05):

| | completed | battles won | reached boss 1 | boss 2 |
|---|---|---|---|---|
| Before difficulties (random decks) | 10.7 % | 5.0 | 53 % | 32 % |
| Učeň | 22.9 % | 5.5 | 55 % | 34 % |
| Rytíř | 9.2 % | 4.2 | 43 % | 23 % |
| Vojevůdce | 1.3 % | 3.4 | 36 % | 15 % |
| Noční můra | 0.0 % | 1.9 | 17 % | 1 % |

Findings: full presets (even downgraded to commons) were far stronger than the old random decks – coherence is the main lever, the castle/wall multiplier barely matters. Attacker and Mage are the strongest archetypes, Chaos and Defender the weakest.

## Related pages
- [[systems/ai]] — the AI that plays the opponents
- [[systems/roadmap-accounts-monetization]] — currency / packs the rewards feed into

## Changelog
- 2026-11-05: Page created – difficulties, rewards, archetype opponents, simulation results.
