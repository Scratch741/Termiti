'use strict';

/**
 * RatingSystem – per-hráčský rating oddělený pro každý herní mód.
 *
 * Persistence: server/data/ratings.json  (JSON soubor, zapisuje se po každé hře)
 *
 * Schéma:
 *   ratings.json = {
 *     "<deviceId>": {
 *       name:     "...",
 *       lastSeen: "<ISO timestamp>",
 *       modes: {
 *         "<mode>": {
 *           rating: 1000,
 *           wins:   0,
 *           losses: 0,
 *           draws:  0,
 *           games:  0
 *         }
 *       }
 *     }
 *   }
 *
 * Delta ratingu:
 *   Výhra  +25
 *   Prohra −15
 *   Remíza  ±0
 *   Minimum ratingu: 0
 */

const fs   = require('fs');
const path = require('path');

const DATA_DIR  = path.join(__dirname, '..', 'data');
const DATA_FILE = path.join(DATA_DIR, 'ratings.json');
const BAK_FILE  = DATA_FILE + '.bak';   // předchozí uložená verze
const TMP_FILE  = DATA_FILE + '.tmp';

const BASE_RATING = 1000;
const WIN_DELTA   =   +25;
const LOSS_DELTA  =   -15;

// Hezké názvy módů pro log
const MODE_LABELS = {
  normal:       'Constructed',
  super_random: 'Super Náhodný',
};
function modeLabel(mode) {
  return MODE_LABELS[mode] || mode;
}

class RatingSystem {
  constructor() {
    /** @type {Record<string, { name:string, lastSeen:string, modes:Record<string,{rating,wins,losses,draws,games}> }>} */
    this._data = {};
    this._load();
  }

  // ── Persistence ────────────────────────────────────────────────────────────

  /**
   * Načte hodnocení. Poškozený ratings.json (výpadek proudu nebo plný disk při zápisu)
   * se NEPŘEPÍŠE prázdnými daty: odloží se stranou jako ratings.json.corrupt-<čas>
   * a načte se předchozí verze z ratings.json.bak. Dřív server s poškozeným souborem
   * začal s prázdnými daty a první dohraná hra je zapsala přes celý žebříček.
   */
  _load() {
    const read = file => JSON.parse(fs.readFileSync(file, 'utf8'));
    if (!fs.existsSync(DATA_FILE)) {
      if (fs.existsSync(BAK_FILE)) {
        try { this._data = read(BAK_FILE); console.warn('[Rating] ratings.json chybí – načtena záloha .bak'); }
        catch (e) { console.error('[Rating] Záloha .bak je také poškozená:', e.message); }
      }
      return;
    }
    try {
      this._data = read(DATA_FILE);
      console.log(`[Rating] Načteno ${Object.keys(this._data).length} hráčů z ${DATA_FILE}`);
    } catch (e) {
      const aside = `${DATA_FILE}.corrupt-${new Date().toISOString().replace(/[:.]/g, '-')}`;
      console.error(`[Rating] ratings.json je poškozený (${e.message}) – odkládám jako ${aside}`);
      try { fs.renameSync(DATA_FILE, aside); } catch (_) {}
      try {
        this._data = read(BAK_FILE);
        console.warn(`[Rating] Načtena předchozí verze z .bak (${Object.keys(this._data).length} hráčů)`);
      } catch (e2) {
        console.error('[Rating] Záloha .bak chybí nebo je poškozená – začínám s prázdnými daty, poškozený soubor zůstal odložený');
        this._data = {};
      }
    }
  }

  /**
   * Uloží hodnocení tak, aby soubor nikdy nezůstal napůl zapsaný: zápis do .tmp,
   * fsync na disk, předchozí verze do .bak a teprve pak přejmenování .tmp → ratings.json
   * (přejmenování je v rámci jednoho disku atomické – soubor je buď starý, nebo celý nový).
   */
  _save() {
    try {
      if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
      const fd = fs.openSync(TMP_FILE, 'w');
      try {
        fs.writeSync(fd, JSON.stringify(this._data, null, 2));
        fs.fsyncSync(fd);
      } finally {
        fs.closeSync(fd);
      }
      if (fs.existsSync(DATA_FILE)) fs.copyFileSync(DATA_FILE, BAK_FILE);
      fs.renameSync(TMP_FILE, DATA_FILE);
    } catch (e) {
      console.error('[Rating] Chyba ukládání:', e.message);
    }
  }

  // ── Interní getters ────────────────────────────────────────────────────────

  /** Vrátí (nebo vytvoří) záznam hráče. */
  _entry(deviceId, name) {
    if (!this._data[deviceId]) {
      this._data[deviceId] = { name, lastSeen: new Date().toISOString(), modes: {} };
    }
    // Aktualizuj jméno (může se změnit) a čas
    this._data[deviceId].name     = name;
    this._data[deviceId].lastSeen = new Date().toISOString();
    return this._data[deviceId];
  }

  /** Vrátí (nebo inicializuje) statistiky hráče pro daný mód. */
  _modeStats(deviceId, name, mode) {
    const entry = this._entry(deviceId, name);
    if (!entry.modes[mode]) {
      entry.modes[mode] = { rating: BASE_RATING, wins: 0, losses: 0, draws: 0, games: 0 };
    }
    return entry.modes[mode];
  }

  // ── Veřejné API ────────────────────────────────────────────────────────────

  /**
   * Vrátí aktuální rating hráče pro daný mód.
   * Pokud hráč nebo mód neexistuje, vrátí BASE_RATING.
   */
  getRating(deviceId, mode) {
    const entry = this._data[deviceId];
    if (!entry || !entry.modes[mode]) return BASE_RATING;
    return entry.modes[mode].rating;
  }

  /**
   * Vrátí kompletní statistiky hráče (všechny módy).
   * Vrátí null pokud hráč neexistuje.
   */
  getStats(deviceId) {
    return this._data[deviceId] ?? null;
  }

  /**
   * Vrátí žebříček hráčů seřazený podle ratingu pro daný mód.
   * @param {string} mode   - 'normal' | 'super_random'
   * @param {number} limit  - max počet hráčů
   * @returns {{ rank, name, rating, wins, losses, draws, games }[]}
   */
  getLeaderboard(mode, limit = 20) {
    return Object.values(this._data)
      .filter(d => d.modes && d.modes[mode])
      .map(d => ({
        name:   d.name,
        rating: d.modes[mode].rating,
        wins:   d.modes[mode].wins,
        losses: d.modes[mode].losses,
        draws:  d.modes[mode].draws,
        games:  d.modes[mode].games,
      }))
      .sort((a, b) => b.rating - a.rating)
      .slice(0, limit)
      .map((p, i) => ({ rank: i + 1, ...p }));
  }

  /**
   * Vrátí celkový počet unikátních hráčů ve všech módech.
   */
  getTotalPlayers() {
    return Object.keys(this._data).length;
  }

  /**
   * Zaloguje a uloží výsledek hry pro oba hráče.
   *
   * @param {string}          deviceIdA
   * @param {string}          nameA
   * @param {string}          deviceIdB
   * @param {string}          nameB
   * @param {'A'|'B'|'DRAW'}  winner
   * @param {string}          mode
   * @param {string}          gameId     – jen pro log
   *
   * @returns {{ deltaA:number, deltaB:number, newRatingA:number, newRatingB:number } | null}
   *   null pokud chybí deviceId (anonymní hráč)
   */
  recordResult(deviceIdA, nameA, deviceIdB, nameB, winner, mode, gameId = '') {
    if (!deviceIdA || !deviceIdB) {
      console.log(`[Rating] Přeskočeno (chybí deviceId) – hra ${gameId}`);
      return null;
    }

    const sA = this._modeStats(deviceIdA, nameA, mode);
    const sB = this._modeStats(deviceIdB, nameB, mode);

    let deltaA = 0, deltaB = 0;

    if (winner === 'DRAW' || winner === 'DRAW_BOTH_DEAD') {
      sA.draws++;
      sB.draws++;
    } else if (winner === 'A') {
      deltaA = WIN_DELTA;
      deltaB = LOSS_DELTA;
      sA.wins++;
      sB.losses++;
    } else {
      deltaA = LOSS_DELTA;
      deltaB = WIN_DELTA;
      sA.losses++;
      sB.wins++;
    }

    sA.games++;
    sB.games++;
    sA.rating = Math.max(0, sA.rating + deltaA);
    sB.rating = Math.max(0, sB.rating + deltaB);

    this._save();

    // ── Konzolový log ─────────────────────────────────────────────────────────
    const modeName = modeLabel(mode);
    const winStr   = (winner === 'DRAW' || winner === 'DRAW_BOTH_DEAD') ? 'REMÍZA' : `výhra ${winner === 'A' ? nameA : nameB}`;
    const dA = deltaA >= 0 ? `+${deltaA}` : `${deltaA}`;
    const dB = deltaB >= 0 ? `+${deltaB}` : `${deltaB}`;

    console.log(
      `[Rating][${modeName}] ${gameId ? `hra ${gameId} ` : ''}${winStr} | ` +
      `${nameA}: ${sA.rating - deltaA} → ${sA.rating} (${dA}) | ` +
      `${nameB}: ${sB.rating - deltaB} → ${sB.rating} (${dB})`
    );

    return { deltaA, deltaB, newRatingA: sA.rating, newRatingB: sB.rating };
  }
}

// Singleton – sdílí se napříč celou server session
const ratingSystem = new RatingSystem();

module.exports = { ratingSystem, BASE_RATING, WIN_DELTA, LOSS_DELTA };
