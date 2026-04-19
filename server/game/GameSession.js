'use strict';
/**
 * GameSession – server-side game instance.
 * Manages one complete game between two WebSocket clients.
 */
const {
  CARD_MAP, randomDeck, buildDeckFromIds, shuffle
} = require('./cards');
const {
  createPlayerState, generateResources, drawCards,
  applyEffects, deriveCardType, checkWin, resolveByHp
} = require('./engine');

const MULLIGAN_HAND_SIZE = 5;
const TURN_HAND_DRAW    = 1;
const TURN_SECONDS      = 15;
const TIMEBANK_SECONDS  = 120;

class GameSession {

  /**
   * @param {string}    gameId
   * @param {WebSocket} wsA
   * @param {string}    nameA
   * @param {WebSocket} wsB
   * @param {string}    nameB
   * @param {string[]|null} deckIdsA  – 30 base ID karet pro hráče A (null = náhodný)
   * @param {string[]|null} deckIdsB  – 30 base ID karet pro hráče B (null = náhodný)
   */
  constructor(gameId, wsA, nameA, wsB, nameB, deckIdsA = null, deckIdsB = null) {
    this.gameId = gameId;

    this.ws      = { A: wsA,      B: wsB      };
    this.name    = { A: nameA,    B: nameB    };
    this.deckIds = { A: deckIdsA, B: deckIdsB };

    // Game state
    this.state     = { A: null, B: null };
    this.phase     = 'mulligan';         // 'mulligan' | 'playing' | 'ended'
    this.activeSide = 'A';               // whose turn it is
    this.turnNumber = 0;

    // Mulligan tracking
    this.mulliganDone = { A: false, B: false };

    // Empty-deck skip tracking: hra skončí až oba hráči přeskočí tah s prázdnými balíčky
    this.skippedEmptyDeck = { A: false, B: false };

    // Last played/discarded card (sent to both clients so they can animate it)
    this.lastPlayedCard    = null;
    this.lastPlayedAction  = null;  // 'PLAYED' | 'DISCARDED' | 'BURNED' | 'STOLEN'
    this.lastPlayedBySide  = null;
    this.lastPlayedCardIdx = null;  // index v ruce před zahráním

    // Timer
    this.timebank          = { A: TIMEBANK_SECONDS, B: TIMEBANK_SECONDS };
    this.turnStartedAt     = 0;   // kdy začal aktuální tah (fáze 1)
    this.timebankStartedAt = null; // null = ve fázi tahu; timestamp = ve fázi timebanku
    this._turnTimer        = null;

    // Log of last actions (sent to both clients each state push)
    this.lastLog = [];
  }

  // ── Start ──────────────────────────────────────────────────────────────────

  start() {
    // Build decks – vlastní balíček pokud poslaný, jinak náhodný
    const deckA = this.deckIds.A ? buildDeckFromIds(this.deckIds.A) : randomDeck();
    const deckB = this.deckIds.B ? buildDeckFromIds(this.deckIds.B) : randomDeck();

    this.state.A = createPlayerState(deckA);
    this.state.B = createPlayerState(deckB);

    // Deal opening hands
    drawCards(this.state.A, MULLIGAN_HAND_SIZE);
    drawCards(this.state.B, MULLIGAN_HAND_SIZE);

    // Pick who goes first (already decided in matchmaking, side A = first player)
    this.activeSide = 'A';

    // Notify both clients
    this._send('A', { type: 'GAME_MULLIGAN', hand: this._serializeHand('A') });
    this._send('B', { type: 'GAME_MULLIGAN', hand: this._serializeHand('B') });
  }

  // ── Mulligan ───────────────────────────────────────────────────────────────

  /**
   * @param {'A'|'B'} side
   * @param {string[]} returnIds  – instance IDs the player wants to swap back
   */
  handleMulligan(side, returnIds) {
    if (this.phase !== 'mulligan') return;
    if (this.mulliganDone[side]) return;

    const ps = this.state[side];

    if (returnIds && returnIds.length > 0) {
      // Odděl vrácené a ponechané karty
      const kept     = [];
      const returned = [];
      for (const card of ps.hand) {
        if (returnIds.includes(card.id)) returned.push(card);
        else kept.push(card);
      }
      ps.hand = kept;

      // Lízni náhrady DŘÍV než vrácené karty dáš zpět do balíčku
      // → hráč nemůže dostat zpět přesně ty samé instance
      drawCards(ps, returned.length);

      // Teď vrácené karty zamíchej do balíčku
      for (const c of returned) ps.deck.push(c);
      shuffle(ps.deck);
    }

    this.mulliganDone[side] = true;

    // Acknowledge to the submitting player
    this._send(side, {
      type: 'MULLIGAN_OK',
      hand: this._serializeHand(side)
    });

    // If both done → start game
    if (this.mulliganDone.A && this.mulliganDone.B) {
      this._startGame();
    } else {
      // Tell the other side their opponent confirmed
      const other = side === 'A' ? 'B' : 'A';
      this._send(other, { type: 'OPPONENT_MULLIGAN_DONE' });
    }
  }

  // ── Game start ─────────────────────────────────────────────────────────────

  _startGame() {
    this.phase      = 'playing';
    this.turnNumber = 1;

    // First player gets resources but NO extra draw
    generateResources(this.state[this.activeSide]);

    this._log(`Hra začala. Na tahu: ${this.name[this.activeSide]}`);
    this._startTurnTimer();  // nejdřív nastav turnStartedAt, pak pošli stav
    this._sendStateBoth();
  }

  // ── Turn timer ─────────────────────────────────────────────────────────────
  //
  // Dvoufázové odpočítávání:
  //   Fáze 1 – kolo (TURN_SECONDS): hráč hraje zadarmo, timebank se nespotřebovává
  //   Fáze 2 – timebank hráče: pokud nevyprší → tah přeskočen
  //
  // Každý hráč má VLASTNÍ timebank (this.timebank[A/B]).
  // Při normálním zahrání karty / ukončení tahu se odečte jen čas strávený
  // v timebank fázi (elapsed - TURN_SECONDS).

  _startTurnTimer() {
    this._clearTurnTimer();
    const side             = this.activeSide;
    this.turnStartedAt     = Date.now();
    this.timebankStartedAt = null;   // začínáme ve fázi tahu

    // Fáze 1: timer kola (TURN_SECONDS)
    this._turnTimer = setTimeout(() => {
      if (this.activeSide !== side || this.phase !== 'playing') return;

      const bank = this.timebank[side];
      if (bank <= 0) {
        // Timebank prázdný → přeskoč okamžitě
        this._log(`${this.name[side]} vypršel čas – tah přeskočen.`);
        this._advanceTurn();
        return;
      }

      // Fáze 2: začíná timebank tohoto hráče
      this.timebankStartedAt = Date.now();

      this._turnTimer = setTimeout(() => {
        if (this.activeSide !== side || this.phase !== 'playing') return;
        this.timebank[side]    = 0;
        this.timebankStartedAt = null;
        this._log(`${this.name[side]} vypršel čas i timebank – tah přeskočen.`);
        this._advanceTurn();
      }, bank * 1000);

    }, TURN_SECONDS * 1000);
  }

  _clearTurnTimer() {
    if (this._turnTimer) { clearTimeout(this._turnTimer); this._turnTimer = null; }
    this.timebankStartedAt = null;
  }

  /**
   * Odečte spotřebovaný timebank aktivního hráče při jeho akci.
   * Voláno PŘED _clearTurnTimer().
   */
  _consumeTimebank(side) {
    if (this.timebankStartedAt === null) return;  // fáze tahu – timebank se nespotřebovává
    const bankUsed = Math.ceil((Date.now() - this.timebankStartedAt) / 1000);
    this.timebank[side] = Math.max(0, this.timebank[side] - bankUsed);
  }

  // ── Public: reconnect – pošli aktuální stav znovu ─────────────────────────

  resendStateTo(side, newWs) {
    this.ws[side] = newWs;
    if (this.phase === 'mulligan') {
      this._send(side, { type: 'GAME_MULLIGAN', hand: this._serializeHand(side) });
    } else if (this.phase === 'playing') {
      this._send(side, this._buildStateFor(side));
    }
  }

  // ── Action dispatcher ──────────────────────────────────────────────────────

  /**
   * Main entry point for client GAME_ACTION messages.
   * @param {'A'|'B'} side
   * @param {string}  action  – 'PLAY_CARD' | 'DISCARD_CARD' | 'END_TURN' | 'SKIP_TURN'
   * @param {object}  data    – action-specific payload
   */
  handleAction(side, action, data) {
    if (this.phase !== 'playing') {
      this._sendError(side, 'Hra není aktivní.');
      return;
    }
    if (side !== this.activeSide) {
      this._sendError(side, 'Nejsi na tahu.');
      return;
    }

    this._consumeTimebank(side);  // odečti spotřebovaný timebank (pokud byl použit)
    this._clearTurnTimer();       // hráč reagoval – zastav odpočet

    switch (action) {
      case 'PLAY_CARD':    this._handlePlayCard(side, data); break;
      case 'DISCARD_CARD': this._handleDiscardCard(side, data); break;
      case 'END_TURN':     this._handleEndTurn(side); break;
      case 'SKIP_TURN':    this._handleSkipTurn(side); break;
      default:
        this._sendError(side, `Neznámá akce: ${action}`);
    }
  }

  // ── Play card ──────────────────────────────────────────────────────────────

  _handlePlayCard(side, { cardId }) {
    const self = this.state[side];
    const opp  = this.state[side === 'A' ? 'B' : 'A'];

    // Find card in hand
    const cardIdx = self.hand.findIndex(c => c.id === cardId);
    if (cardIdx === -1) {
      this._sendError(side, 'Karta není v ruce.');
      return;
    }
    const card = self.hand[cardIdx];

    // Check resources – všechny typy (MAGIC, ATTACK, STONES, CHAOS) fungují stejně
    // X-kost karty spotřebují veškerý dostupný zdroj
    const res = card.costType;
    let xValue = 0;
    if (card.isXCost) {
      xValue = self.resources[res] || 0;
      self.resources[res] = 0;
    } else {
      const cost = card.cost || 0;
      if (res && cost > 0) {
        if ((self.resources[res] || 0) < cost) {
          this._sendError(side, 'Nedostatek zdrojů.');
          return;
        }
        self.resources[res] -= cost;
      }
    }

    // Remove from hand → discard
    self.hand.splice(cardIdx, 1);
    self.discardPile.push(card);

    // Zapamatuj si zahranou kartu + index v ruce (před splice) pro zobrazení soupeři
    this.lastPlayedCard    = { id: card.id, baseId: card.baseId, name: card.name,
                                cost: card.cost, costType: card.costType, rarity: card.rarity };
    this.lastPlayedAction  = 'PLAYED';
    this.lastPlayedBySide  = side;
    this.lastPlayedCardIdx = cardIdx;

    // Handle combo cards: apply effects only if isCombo check passes (always for now)
    // Nastav typ právě hrané karty před applyEffects – podmínka LastPlayedType to přečte
    self.lastPlayedType = deriveCardType(card);

    const lostCards = [];
    applyEffects(
      card.effects,
      self,
      opp,
      CARD_MAP,
      (c, action) => lostCards.push({ card: c, action }),
      xValue
    );

    this._log(`${this.name[side]} zahrál ${card.name}`);

    // Notify opponent about stolen/burned cards
    for (const { card: lc, action } of lostCards) {
      this._send(side === 'A' ? 'B' : 'A', {
        type:   'CARD_LOST',
        cardId: lc.id,
        action  // 'STOLEN' | 'BURNED'
      });
    }

    // Win check
    const winner = checkWin(this.state.A, this.state.B);
    if (winner !== null) {
      this._endGame(winner);
      return;
    }

    // Non-combo karta → automaticky ukončí tah (jako offline hra)
    if (!card.isCombo) {
      this._advanceTurn();
    } else {
      // Combo karta → hráč pokračuje v tahu, jen pošleme nový stav
      this._sendStateBoth();
    }
  }

  // ── Discard card ───────────────────────────────────────────────────────────

  _handleDiscardCard(side, { cardId }) {
    const self = this.state[side];

    const cardIdx = self.hand.findIndex(c => c.id === cardId);
    if (cardIdx === -1) {
      this._sendError(side, 'Karta není v ruce.');
      return;
    }

    const card = self.hand.splice(cardIdx, 1)[0];
    self.discardPile.push(card);

    this.lastPlayedCard   = { id: card.id, baseId: card.baseId, name: card.name,
                               cost: card.cost, costType: card.costType, rarity: card.rarity };
    this.lastPlayedAction  = 'DISCARDED';
    this.lastPlayedBySide  = side;
    this.lastPlayedCardIdx = cardIdx;

    this._log(`${this.name[side]} odhodil ${card.name}`);
    this._advanceTurn();
  }

  // ── End turn ───────────────────────────────────────────────────────────────

  _handleEndTurn(side) {
    this._advanceTurn();
  }

  // ── Skip turn (empty deck) ─────────────────────────────────────────────────

  _handleSkipTurn(side) {
    const self = this.state[side];
    const opp  = this.state[side === 'A' ? 'B' : 'A'];

    // Both decks empty → zaznamenej, že tento hráč přeskočil
    // Hra skončí teprve až OBA hráči přeskočí tah s prázdnými balíčky
    if (self.deck.length === 0 && opp.deck.length === 0) {
      this.skippedEmptyDeck[side] = true;
      if (this.skippedEmptyDeck.A && this.skippedEmptyDeck.B) {
        // Oba přeskočili → rozhodne hrad
        const winner = resolveByHp(this.state.A, this.state.B);
        this._log('Oba hráči přeskočili s prázdnými balíčky – konec hry!');
        this._endGame(winner);
        return;
      }
      // Jeden z hráčů teprve přeskočil – předej tah druhému
      this._advanceTurn();
      return;
    }

    // Balíčky ještě nejsou oba prázdné – resetuj příznaky a pokračuj normálně
    this.skippedEmptyDeck = { A: false, B: false };
    this._advanceTurn();
  }

  // ── Advance turn ───────────────────────────────────────────────────────────

  _advanceTurn() {
    // Switch active side
    this.activeSide = this.activeSide === 'A' ? 'B' : 'A';
    // Increment round counter only when A's turn starts (= one full round completed)
    if (this.activeSide === 'A') {
      this.turnNumber++;
    }

    const next = this.state[this.activeSide];
    generateResources(next);
    const burned = drawCards(next, TURN_HAND_DRAW);

    if (burned.length > 0) {
      this._log(`${this.name[this.activeSide]} spálil kartu (plná ruka).`);
    }

    this._log(`Tah ${this.turnNumber}: ${this.name[this.activeSide]}`);

    // Win check (shouldn't happen mid-turn but be safe)
    const winner = checkWin(this.state.A, this.state.B);
    if (winner !== null) {
      this._endGame(winner);
      return;
    }

    this._startTurnTimer();  // nejdřív nastav turnStartedAt, pak pošli stav
    this._sendStateBoth();
  }

  // ── Game over ──────────────────────────────────────────────────────────────

  _endGame(winner) {
    this.phase = 'ended';
    this._clearTurnTimer();

    let winnerName = null;
    if (winner === 'A') winnerName = this.name.A;
    else if (winner === 'B') winnerName = this.name.B;

    this._sendStateBoth();   // final state snapshot

    const msg = {
      type:       'GAME_OVER',
      winner,                // 'A' | 'B' | 'DRAW'
      winnerName
    };
    // DRAW = prohra pro oba (vzájemná zkáza apod.) – nikdo nevyhrává
    this._send('A', { ...msg, youWin: winner === 'A' });
    this._send('B', { ...msg, youWin: winner === 'B' });

    this._log(`Konec hry. Vítěz: ${winnerName || 'REMÍZA'}`);
    console.log(`[Game ${this.gameId}] ended – winner: ${winner}`);
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  /** Serialize a player's hand for transmission (full card data for owner). */
  _serializeHand(side) {
    return this.state[side].hand.map(c => ({
      id:       c.id,
      baseId:   c.baseId,
      name:     c.name,
      cost:     c.cost,
      costType: c.costType,
      rarity:   c.rarity
    }));
  }

  /** Build the state payload to send to one player. */
  _buildStateFor(side) {
    const mySide  = side;
    const oppSide = side === 'A' ? 'B' : 'A';
    const my  = this.state[mySide];
    const opp = this.state[oppSide];

    return {
      type: 'GAME_STATE',
      activeSide: this.activeSide,
      isMyTurn:   this.activeSide === side,
      turnNumber: this.turnNumber,
      myState: {
        castleHP:        my.castleHP,
        wallHP:          my.wallHP,
        resources:       { ...my.resources },
        mines:           { ...my.mines },
        mineBlockedTurns:{ ...my.mineBlockedTurns },
        pendingResources: (my.pendingResources || []).map(p => ({ ...p })),
        hand:            this._serializeHand(mySide),
        deckSize:        my.deck.length,
        discardSize:     my.discardPile.length
      },
      oppState: {
        castleHP:        opp.castleHP,
        wallHP:          opp.wallHP,
        resources:       { ...opp.resources },
        mines:           { ...opp.mines },
        mineBlockedTurns:{ ...opp.mineBlockedTurns },
        handSize:        opp.hand.length,       // opponent hand is hidden
        deckSize:        opp.deck.length,
        discardSize:     opp.discardPile.length,
        // Index zahrané karty v ruce (před zahráním) – null pokud soupeř nezahrál
        lastPlayedIdx:   this.lastPlayedBySide === oppSide ? this.lastPlayedCardIdx : null
      },
      log:              [...this.lastLog],
      lastPlayedCard:   this.lastPlayedCard,
      lastPlayedAction: this.lastPlayedAction,
      lastPlayedByMe:   this.lastPlayedBySide === side,

      // ── Timer (relativní – eliminuje desynchronizaci hodin mezi zařízeními) ──
      // turnRemainingMs  = zbývající ms ve fázi tahu (0 pokud jsme ve fázi timebanku)
      // timebankMeMs     = zbývající ms v mém timebanku  (klesá jen ve fázi timebanku)
      // timebankOppMs    = zbývající ms v timebankuoponenta (statické, dokud není jeho tah)
      ...this._buildTimerFor(side)
    };
  }

  /**
   * Vrátí časové údaje relativní k okamžiku odeslání zprávy.
   * Klient si uloží čas přijetí a odpočítává od toho – bez závislosti
   * na synchronizaci hodin mezi zařízeními.
   *
   * turnRemainingMs  – zbývající ms ve fázi tahu (0 pokud jsme ve fázi timebanku)
   * timebankMeMs     – zbývající ms v mém timebanku
   * timebankOppMs    – zbývající ms v timebankuoponenta
   */
  _buildTimerFor(side) {
    const now      = Date.now();
    const actSide  = this.activeSide;
    const oppSide  = actSide === 'A' ? 'B' : 'A';

    let turnRemainingMs;
    let activeBankMs;

    if (this.timebankStartedAt !== null) {
      // Jsme ve fázi timebanku aktivního hráče
      turnRemainingMs = 0;
      activeBankMs    = Math.max(0, this.timebank[actSide] * 1000 - (now - this.timebankStartedAt));
    } else {
      // Jsme ve fázi tahu
      turnRemainingMs = Math.max(0, TURN_SECONDS * 1000 - (now - this.turnStartedAt));
      activeBankMs    = this.timebank[actSide] * 1000;
    }

    const oppBankMs = this.timebank[oppSide] * 1000;

    // Přeložit do perspektivy "side" (Me = já, Opp = soupeř)
    const isActivePlayer = side === actSide;
    return {
      turnRemainingMs,
      timebankMeMs:  isActivePlayer ? activeBankMs : oppBankMs,
      timebankOppMs: isActivePlayer ? oppBankMs    : activeBankMs
    };
  }

  _sendStateBoth() {
    this._send('A', this._buildStateFor('A'));
    this._send('B', this._buildStateFor('B'));
    this.lastLog = [];
    // lastPlayedCard se NEresetuje – zůstane viditelný, dokud ho nenahradí nová karta
  }

  _log(msg) {
    this.lastLog.push(msg);
  }

  _send(side, obj) {
    const ws = this.ws[side];
    if (ws && ws.readyState === 1 /* OPEN */) {
      ws.send(JSON.stringify(obj));
    }
  }

  _sendError(side, msg) {
    this._send(side, { type: 'GAME_ERROR', msg });
  }
}

module.exports = { GameSession };
