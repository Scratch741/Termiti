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

---

## Related pages
- [[cards/types]] — card types and `costType`, `deriveCardType`
- [[mechanics/resources]] — resources and payment
- [[mechanics/combo]] — type-matters "this turn" effects
- [[systems/ai]] — affordability and move scoring
- [[systems/online]] — protocol version

## Changelog
- 2026-09-28: Page created; first proposal: dual-colour cards.
