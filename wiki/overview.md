# Termiti — Přehled hry

> Tahová karetní hra pro 2 hráče. Postav hrad do výšky 70 nebo zniž soupeřův hrad na 0.

---

## Co je Termiti

Android karetní hra inspirovaná **Arcomage**. Hráč vs. AI (offline) nebo hráč vs. hráč (online přes WebSocket server). Každý hráč buduje hrad, sbírá suroviny a hraje karty — útočí, staví, krade nebo sabotuje.

---

## Herní smyčka

```
Kolo začíná
  → Příjem surovin z dolů
  → Líz kartu
  → Hraj karty (Combo = pokračuješ, non-Combo = konec tahu)
  → AI hraje
  → Opakuj
```

---

## Zdroje a doly

| Surovina | Použití |
|----------|---------|
| 🔵 Magie | Helper karty, doly, chaos generátory |
| ⚔️ Útok | Útočné karty |
| 🪨 Kámen | Stavební karty |
| 🌀 Chaos | Silné Chaos karty |

Doly produkují suroviny každé kolo. Výchozí stav: 1 důl každého typu (Chaos = 0).

---

## Podmínky výhry

| Podmínka | Výsledek |
|----------|----------|
| Hrad ≥ 70 | Výhra stavbou |
| Soupeřův hrad ≤ 0 | Výhra zničením |
| Kolo 99+ | Vyhraje vyšší hrad |
| Remíza | Obě podmínky najednou |

---

## Typy karet

| Typ | Platí se | Příklady |
|-----|----------|---------|
| Útok | ATTACK | Rychlý útok, Ogr, Drak |
| Stavba | STONES | Kamenná zeď, Citadela |
| Magie | MAGIC | Mobilizace, Magický pramen, Lupič |
| Chaos | CHAOS/MAGIC | Chaotický výbuch, Anarchie |
| Rozhodnutí | různé | Rekrut, Stavitel, Průzkum dolů |

---

## Kde co najít (wiki)

| Oblast | Stránka |
|--------|---------|
| Technická architektura | [[architecture]] |
| Všechny efekty karet | [[cards/effects]] |
| Decision karty | [[cards/decisions]] |
| Průběh tahu | [[mechanics/game-flow]] |
| Systém dolů | [[mechanics/mines]] |
| AI engine | [[systems/ai]] |
| Online multiplayer | [[systems/online]] |

---

## Technologie

- **Android** — Kotlin, Jetpack Compose
- **Server** — Node.js + WebSocket
- **Karty** — `cards.js` (server) + `cards.json` (klient, manuální sync)
