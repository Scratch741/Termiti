# Termiti Wiki — Index

> Page catalog. Update on every ingest or when a new page is created.

## General

| Page | Description | Updated |
|------|-------------|---------|
| [[overview]] | CZ: game overview, loop, platforms, localization | 2026-05-29 |
| [[architecture]] | Tech stack, key files, versioning, Compose gotchas | 2026-05-29 |

## Cards

| Page | Description | Updated |
|------|-------------|---------|
| [[cards/types]] | Card types (Útok/Stavba/Magie/Chaos), deriveCardType | 2026-05-21 |
| [[cards/effects]] | CardEffect sealed class — all effects | 2026-05-21 |
| [[cards/decisions]] | Decision cards + AI behavior | 2026-05-29 |

## Mechanics

| Page | Description | Updated |
|------|-------------|---------|
| [[mechanics/game-flow]] | Turn flow, playCard(), finishTurn(), animation timing | 2026-05-21 |
| [[mechanics/resources]] | MAGIC/ATTACK/STONES/CHAOS, steal, drain | 2026-05-21 |
| [[mechanics/mines]] | Mine system, blocking, destruction, card list | 2026-05-21 |
| [[mechanics/combo]] | Combo system, CloneNextPlayed, AI penalty | 2026-05-21 |
| [[mechanics/win-conditions]] | Win conditions, 99-round limit | 2026-05-21 |
| [[mechanics/discard]] | Discard mechanic — alternative effects fired on discard | 2026-07-20 |

## Systems

| Page | Description | Updated |
|------|-------------|---------|
| [[systems/ai]] | AiEngine scoring, penalties, lethal lookahead, Decision AI | 2026-05-29 |
| [[systems/online]] | Online multiplayer, GameSession.js, protocol handshake, timebank | 2026-05-29 |
| [[systems/localization]] | JSON language packs (cs/en), AppStrings, fallback chain | 2026-05-29 |

## Missing pages (TODO)

- `cards/list.md` — full list of all 176 cards with IDs and effects
- `systems/deck-builder.md` — deck builder, arena draft, starter deck
- `mechanics/passive-abilities.md` — passive abilities (extra_castle, extra_hand_card)
- `systems/rating.md` — online rating system
- `cards/history.md` — card change log (rarity changes, new cards)
