# Proposals

> Game ideas that are agreed to be worth doing but not scheduled or implemented yet — with the open decisions that block them.

## Status legend

| Status | Meaning |
|---|---|
| idea | proposed, not designed yet |
| design | being designed, open questions below |
| pilot | a few cards/prototype in the game, being measured |
| done | implemented — move the details to the relevant page and keep a one-line entry here |

---

## Dual-colour cards (dvoubarevné karty)

**Status:** idea · proposed by the user 2026-09-28

### Rule

A card has two cost resources (e.g. Attack + Chaos). It is paid **entirely with the one the player has more of**. On a tie the choice is deterministic.

Why: fixes the stuck hand when one resource is short and another is overflowing; creates bridges between archetypes (Útok+Chaos, Magie+Kámen, …). Automatic payment keeps it one-tap on mobile and trivial for the AI.

### Open decisions

1. **Tie-break** — recommended: the card's **first (primary) resource**, printed first on the card, so the player sees it without memorising a global order (alternative: a fixed global order such as Magic > Attack > Stone > Chaos).
2. **Which card type it counts as** for type-matters effects ("Toto kolo: za každou zahranou kartu **ÚTOK**…" — War Drums, Momentum, War Zeal; type discounts — Recruit/Builder/Goblin Shaman/Chaos Sage; random card of a type; LastPlayedType conditions; `attackCardsThisTurn` counts `costType == ATTACK`):
   - both types — most synergy, risk of being too strong (recommended to start with, measured in the simulator),
   - primary type only — predictable,
   - the type actually paid with — logical but the card behaves differently depending on resources, hard to read.
3. **Balance** — at equal cost and effect a dual card is strictly better than a single-colour one (playable whenever either resource suffices). Expect +1 cost or a slightly weaker effect; measure in `deckbuilder.html`.
4. **Automatic payment can hurt** — e.g. Attack 6 / Chaos 5 with a pure-Attack 6 card also in hand: the dual card takes the Attack. Keep the simple rule but show in the cost badge which resource will be taken right now; the AI must account for it in its move scoring.
5. **Exclusions** — X-cost cards (spend everything) should not be dual.

### Implementation scope (measured 2026-09-28)

- `costType` is referenced in 102 places in 17 Kotlin files and 35 places in the server (`engine.js`, `GameSession.js`, `GameLogger.js`, `ReplayViewer.js`, `cards.js`).
- Card model: a second cost resource (`cards.js` format, `Card`, `CardRepository`), one shared "which resource pays" function used by player, AI and server; every affordability check (hand highlight, AI filter, `canPlay`).
- UI: dual frame art (from the user), split cost badge, log cost chip; deck builder filters, composition stats (count in both?) and mana curve.
- Simulator `deckbuilder.html`: payment rule in `simAI`/`simOneGame`, `CARD_EFFECTS_DB` generation.
- Online: new card field + payment rule on the server → **bump `PROTOCOL_VERSION`** (old clients could neither show nor pay such a card).

### Suggested path

Pilot with 4–6 cards (e.g. Útok+Chaos, Magie+Kámen), simulate against the presets first, then frame art, deck builder, online.

## New cards with new mechanics (5 + 2 backup)

**Status:** idea · proposed 2026-10-05, the user likes all seven; not scheduled. Numbers are starting guesses – measure each in the engine (`EngineSimulation`) before adding. All need engine work on client, server, simulator and AI.

| Card | Type / cost / rarity | Text (CZ) | Hook | Counterplay | New mechanic |
|---|---|---|---|---|---|
| **Proroctví** | Magie 3, EPIC, combo | „Vyber typ karty (Útok / Stavba / Magie / Chaos). Pokud soupeřova příští zahraná karta bude toho typu, ukradni 8 hradu a lízni 1." | Mind game: the opponent sees a prophecy is active but not the chosen type – bluff, delay, play around it. | Play a different type, even a worse move. | Hidden choice + condition waiting for the opponent's next play (online: hidden state). AI guesses from the opponent's deck composition. Largest effort. |
| **Prokletý amulet** | Chaos 3, RARE | „Vloží Prokletý amulet do soupeřovy ruky. Kdo ho má v ruce, ztratí na začátku svého tahu 3 hradu. Zahrát ho stojí 2 chaosu (combo) a vrátí ho do ruky soupeře, kde škodí o 1 víc." | Hot potato that escalates each pass; only chaos can bounce it, so chaos decks play with it and others suffer. | Save chaos, or spend the once-per-turn discard on it. | Card into the opponent's HAND (today only into a deck), "while in hand" effect, power counter on the card. |
| **Semínko zloby** | Útok 2, RARE | „Poškodí hrad −2. Za každé kolo, kdy je v tvé ruce, poškození +2." | Hold or fire? A visible growing number; if stolen (Telekineze, Krádež osudu) the opponent throws your grown seed at you, Spálená knihovna turns it to ash. | Hand steal / burn finally have a juicy target. | Counter on a card in hand growing each round (same base as the amulet). |
| **Hradní zahrada** | Kámen 5, EPIC | „Trvalé: dokud máš aspoň 10 hradeb, na začátku tvého tahu hrad +3." | First card that makes walls valuable (sims show walls decide little today); a slow unstoppable engine for builders. | Knock walls under 10 – wall-damage cards (Beranidlo, Zápalné šípy) gain a purpose. | Persistent aura while a condition holds + an icon on the battlefield. |
| **Váhy osudu** | Magie 7, LEGENDARY | „Hrady obou hráčů se vyrovnají na jejich průměr." | Direct answer to the "run over in two turns" problem: 90 vs 30 becomes 60 / 60. Dead when ahead – a pure comeback tool. | Do not leave the opponent low for long, or finish before they save 7 magic. | One simple effect (smallest effort). AI should play it only when clearly behind. |
| *Obětní oltář* (backup) | Chaos ~4, EPIC | „Zahodí celou tvou ruku. Za každou zahozenou kartu hrad soupeře −4. Efekty zahození se spustí." | Big combo turn with the discard cards (Zoufalý žold, Osudová mince, Zapomenutá poznámka). | Burn/steal the hand before the combo. | Discard-hand effect that triggers each discard effect. |
| *Fénix* (backup) | Magie ~4, EPIC | „Poškodí hrad −6. Pak se zamíchá zpět do tvého balíčku a příště poškodí o 4 víc." | Recurring, growing threat late in the game. | Likvidace (burn from deck). | Self-reshuffle with a per-instance power counter. |

Suggested order: Váhy osudu (one effect) → Hradní zahrada (aura + icon) → Semínko zloby + Prokletý amulet (shared counter-on-card base, amulet adds card-to-opponent-hand) → Proroctví (hidden choice, online sync).

Earlier lists from 2026-10-02 (not recorded in detail here): 11 cards to reach 200 (Vrhací sekera, Obléhací věž, Generál, Magický šíp, Kontrašpionáž, Ohnivý déšť, Prastará runa, Poslední kámen, Divoký blesk, Hrobník, Rozštěpená realita) and the wall cards Pád hradeb (opponent's walls to 0, ATTACK 3 combo – measured as an average card) / Zemětřesení (both walls to 0, needs AI scoring by the actual own-wall loss) – see wiki/log.md 2026-10-02.

---

## Related pages
- [[cards/types]] — card types and `costType`, `deriveCardType`
- [[mechanics/resources]] — resources and payment
- [[mechanics/combo]] — type-matters "this turn" effects
- [[systems/ai]] — affordability and move scoring
- [[systems/online]] — protocol version

## Changelog
- 2026-09-28: Page created; first proposal: dual-colour cards.
- 2026-10-05: Seven new-mechanic card ideas (Proroctví, Prokletý amulet, Semínko zloby, Hradní zahrada, Váhy osudu; backup Obětní oltář, Fénix).
