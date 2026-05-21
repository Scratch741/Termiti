# Termiti Wiki — LLM Agent Schema

This file defines how the LLM agent (Claude Code or any other) should maintain and extend the Termiti game wiki. Read it at the start of every session and follow it for all operations.

---

## What this wiki is

A structured knowledge base for the game **Termiti** — an Android card game (Kotlin + Jetpack Compose) with a Node.js server for online multiplayer. The wiki covers game mechanics, technical architecture, cards, systems, and key development decisions.

---

## Directory structure

```
wiki/
  CLAUDE.md              ← this file (schema)
  index.md               ← page catalog (update on every ingest)
  log.md                 ← append-only operation log
  overview.md            ← CZ: game overview for human readers
  architecture.md        ← technical stack, key files, gotchas
  cards/
    types.md             ← card types and costType
    effects.md           ← CardEffect sealed class — all effects
    decisions.md         ← Decision cards (Decision*) — special mechanic
    list.md              ← full card list with IDs, costs, effects
  mechanics/
    game-flow.md         ← turn flow, game states
    resources.md         ← resources (MAGIC, ATTACK, STONES, CHAOS)
    mines.md             ← mine system, AddMine, blocking
    combo.md             ← Combo system, isCombo, CloneNextPlayed
    win-conditions.md    ← win conditions, 99-round limit
  systems/
    ai.md                ← AiEngine — heuristics, scoring, penalties
    online.md            ← online multiplayer, GameSession.js, WebSocket
    deck-builder.md      ← deck builder, arena draft, grantStarterDeck
  raw/                   ← source files (immutable — LLM reads only)
```

---

## Page conventions

Every wiki page follows this structure:

```markdown
# Page title

> One-sentence summary.

## Content
...

## Related pages
- [[page1]] — reason for link
- [[page2]] — reason for link

## Changelog
- YYYY-MM-DD: What was added/changed
```

- Code always in fenced blocks with language tag (` ```kotlin `, ` ```javascript `)
- Card IDs always quoted: `"001"`, `"C03"`, `"D09"`
- Czech card names kept as-is (they are proper nouns)
- Cross-links written as `[[page-name]]`

---

## Operations

### Ingest (adding new knowledge)
When the user adds a source document or describes a game change:
1. Read the source
2. Identify affected wiki pages (typically 5–15)
3. Update affected pages
4. Update `index.md` if new pages were created
5. Append to `log.md`: `## [YYYY-MM-DD] ingest | Description`

### Query (answering questions)
When the user asks a question:
1. Read `index.md` to orient
2. Search relevant pages
3. Answer with citations (`[[page]]`)
4. If the answer is valuable (analysis, comparison, architectural decision) → save as a new page

### Lint (health check)
Periodically or on request:
- Find contradictions between pages
- Find orphan pages (no inbound links)
- Find stale info (code was changed)
- Suggest missing pages

---

## Key project files (for orientation)

| File | Description |
|------|-------------|
| `app/src/main/java/com/example/termiti/CardEffect.kt` | Sealed class of all effects |
| `app/src/main/java/com/example/termiti/Gameviewmodel.kt` | Offline game logic |
| `app/src/main/java/com/example/termiti/AiEngine.kt` | AI heuristics |
| `app/src/main/java/com/example/termiti/GameState.kt` | Game state, win conditions |
| `app/src/main/java/com/example/termiti/CardPresentation.kt` | Card texts and art references |
| `app/src/main/java/com/example/termiti/CardRepository.kt` | JSON → Card parsing |
| `server/game/cards.js` | Card database (server) |
| `server/game/GameSession.js` | Server-side game logic (online) |
| `app/src/main/assets/cards.json` | Client card copy (manual sync!) |

---

## Critical technical notes

- `PlayerState` is a **mutable class** (not a data class) → Compose does not auto-detect changes → always call `deepCopy()` before setting new state
- `cards.json` does **not sync automatically** (Node.js not in PATH) → when changing `cards.js`, always manually update `cards.json`
- `GameState` is a **data class** → structural equality → safe for Compose
- A card has `id` (instance, unique) and `baseId` (template, shared across copies)
