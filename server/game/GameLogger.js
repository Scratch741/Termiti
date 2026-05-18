'use strict';
/**
 * GameLogger – server-side strukturovaný log online her.
 *
 * Formát: JSON Lines (NDJSON) – jeden objekt na řádek.
 * Jeden soubor na hru: logs/YYYY-MM-DD_gameId.jsonl
 * Retence: soubory starší než MAX_AGE_DAYS se smažou při cleanup().
 *
 * Použití:
 *   const logger = new GameLogger(gameId);
 *   logger.logStart({ playerA, playerB, ... });
 *   logger.logAction({ turn, side, action, card, stateAfter });
 *   logger.logEnd({ winner, durationSec });
 *   logger.close();
 */

const fs   = require('fs');
const path = require('path');

const LOGS_DIR     = path.join(__dirname, '..', 'logs');
const MAX_AGE_DAYS = 14;
const MAX_AGE_MS   = MAX_AGE_DAYS * 24 * 60 * 60 * 1000;

// Ujisti se, že adresář existuje
if (!fs.existsSync(LOGS_DIR)) {
  fs.mkdirSync(LOGS_DIR, { recursive: true });
}

class GameLogger {
  /**
   * @param {string} gameId  – UUID hry
   */
  constructor(gameId) {
    this.gameId    = gameId;
    this.startedAt = Date.now();

    const date     = new Date().toISOString().slice(0, 10);   // YYYY-MM-DD
    const filename = `${date}_${gameId}.jsonl`;
    this._path     = path.join(LOGS_DIR, filename);
    this._stream   = fs.createWriteStream(this._path, { flags: 'a', encoding: 'utf8' });
    this._stream.on('error', err => {
      console.error(`[GameLogger ${gameId}] Chyba zápisu: ${err.message}`);
    });
  }

  // ── Veřejné metody ────────────────────────────────────────────────────────

  /**
   * Hra začala – základní metadata.
   * @param {{ nameA, nameB, deviceIdA, deviceIdB, abilitiesA, abilitiesB, mode, winTargetA, winTargetB }} p
   */
  logStart(p) {
    this._write({
      event: 'game_start',
      gameId: this.gameId,
      mode:   p.mode,
      playerA: { name: p.nameA, deviceId: p.deviceIdA || null, abilities: p.abilitiesA || [] },
      playerB: { name: p.nameB, deviceId: p.deviceIdB || null, abilities: p.abilitiesB || [] },
      winTarget: { A: p.winTargetA, B: p.winTargetB }
    });
  }

  /**
   * Mulligan odeslaný – jen kolik karet bylo vyměněno.
   * @param {'A'|'B'} side
   * @param {number}  swapped  – počet vrácených karet
   */
  logMulligan(side, swapped) {
    this._write({ event: 'mulligan', side, swapped });
  }

  /**
   * Akce zahraná/odhozená karta.
   * @param {{ turn, side, action, cardId, cardBaseId, cardName, costPaid, costType, isXCost }} p
   * @param {object} stateAfter  – výstup _compactState()
   */
  logAction(p, stateAfter) {
    this._write({
      event:      'action',
      turn:       p.turn,
      side:       p.side,
      action:     p.action,           // 'play' | 'discard' | 'end_turn' | 'skip_turn'
      cardId:     p.cardId    || null,
      cardBaseId: p.cardBaseId|| null,
      cardName:   p.cardName  || null,
      costPaid:   p.costPaid  ?? null,
      costType:   p.costType  || null,
      isXCost:    p.isXCost   || false,
      state:      stateAfter
    });
  }

  /**
   * Karta ztracena soupeřovým efektem (spálena / ukradena).
   * @param {'A'|'B'} causedBySide  – strana, která způsobila ztrátu
   * @param {string}  action        – 'BURNED' | 'STOLEN'
   * @param {string}  cardName
   */
  logCardLost(causedBySide, action, cardName) {
    this._write({
      event:  'card_lost',
      causedBy: causedBySide,
      action,
      cardName
    });
  }

  /**
   * Rozhodnutí – hráč si vybral kartu v Decision overlaji.
   * @param {'A'|'B'} side
   * @param {string}  effectType  – 'DecisionBurnOpponent' | ...
   * @param {string}  chosenId    – instance ID vybrané karty
   * @param {string}  chosenName
   */
  logDecision(side, effectType, chosenId, chosenName) {
    this._write({
      event:      'decision',
      side,
      effectType,
      chosenId:   chosenId   || null,
      chosenName: chosenName || null
    });
  }

  /**
   * Konec hry.
   * @param {'A'|'B'|'DRAW'} winner
   * @param {object} finalState  – výstup _compactState()
   */
  logEnd(winner, finalState) {
    const durationSec = Math.round((Date.now() - this.startedAt) / 1000);
    this._write({
      event:       'game_end',
      winner,
      durationSec,
      state:       finalState
    });
  }

  /** Zavře write stream (volat po logEnd). */
  close() {
    this._stream.end();
  }

  // ── Privátní ─────────────────────────────────────────────────────────────

  _write(obj) {
    try {
      const line = JSON.stringify({ ts: new Date().toISOString(), ...obj }) + '\n';
      this._stream.write(line);
    } catch (e) {
      console.error(`[GameLogger ${this.gameId}] Chyba serializace: ${e.message}`);
    }
  }
}

// ── Statický snapshot stavu (používá GameSession) ──────────────────────────

/**
 * Vrátí kompaktní snapshot stavu obou hráčů pro logování.
 * Nepřidává velké pole (ruka, balíček) – jen důležité hodnoty.
 *
 * @param {object} stateA  – PlayerState hráče A
 * @param {object} stateB  – PlayerState hráče B
 */
function compactState(stateA, stateB) {
  const snap = (s) => ({
    hp:      s.castleHP,
    wall:    s.wallHP,
    res:     { ...s.resources },
    mines:   { ...(s.mines || {}) },
    deck:    s.deck.length,
    discard: s.discardPile.length,
    // Full hand – id uses displayBaseId for shapeshifters, same as _serializeHand()
    hand:    s.hand.map(c => ({
      id:       c.displayBaseId || c.baseId || c.id,
      name:     c.name,
      cost:     c.cost,
      costType: c.costType || null,
      rarity:   c.rarity   || null
    }))
  });
  return { A: snap(stateA), B: snap(stateB) };
}

// ── Cleanup starých logů ───────────────────────────────────────────────────

/**
 * Smaže .jsonl soubory v logs/ starší než MAX_AGE_DAYS dní.
 * Bezpečné volat při startu serveru i periodicky.
 */
function cleanupOldLogs() {
  const now = Date.now();
  let deleted = 0;
  try {
    const files = fs.readdirSync(LOGS_DIR);
    for (const file of files) {
      if (!file.endsWith('.jsonl')) continue;
      const full = path.join(LOGS_DIR, file);
      try {
        const stat = fs.statSync(full);
        if (now - stat.mtimeMs > MAX_AGE_MS) {
          fs.unlinkSync(full);
          deleted++;
        }
      } catch (_) { /* soubor mezitím smazán – přeskoč */ }
    }
    if (deleted > 0) {
      console.log(`[GameLogger] Cleanup: smazáno ${deleted} log souborů starších než ${MAX_AGE_DAYS} dní.`);
    }
  } catch (e) {
    console.error('[GameLogger] Chyba při cleanup:', e.message);
  }
}

module.exports = { GameLogger, compactState, cleanupOldLogs };
