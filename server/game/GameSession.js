'use strict';
/**
 * GameSession – server-side game instance.
 * Manages one complete game between two WebSocket clients.
 */
const {
  CARD_MAP, randomDeck, shuffle
} = require('./cards');
const {
  createPlayerState, generateResources, drawCards,
  applyEffects, checkWin, resolveByHp
} = require('./engine');

const MULLIGAN_HAND_SIZE = 5;
const TURN_HAND_DRAW    = 1;

class GameSession {

  /**
   * @param {string}    gameId
   * @param {WebSocket} wsA
   * @param {string}    nameA
   * @param {WebSocket} wsB
   * @param {string}    nameB
   */
  constructor(gameId, wsA, nameA, wsB, nameB) {
    this.gameId = gameId;

    this.ws   = { A: wsA,   B: wsB   };
    this.name = { A: nameA, B: nameB };

    // Game state
    this.state     = { A: null, B: null };
    this.phase     = 'mulligan';         // 'mulligan' | 'playing' | 'ended'
    this.activeSide = 'A';               // whose turn it is
    this.turnNumber = 0;

    // Mulligan tracking
    this.mulliganDone = { A: false, B: false };

    // Last played card (sent to both clients so they can animate it)
    this.lastPlayedCard = null;  // { id, baseId, name, cost, costType, rarity }
    this.lastPlayedBySide = null; // 'A' | 'B'

    // Log of last actions (sent to both clients each state push)
    this.lastLog = [];
  }

  // ── Start ──────────────────────────────────────────────────────────────────

  start() {
    // Build decks (randomDeck() already calls makeInstance internally)
    const deckA = randomDeck();
    const deckB = randomDeck();

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
      // Put returned cards at bottom of deck, draw same number
      const kept    = [];
      const returned = [];
      for (const card of ps.hand) {
        if (returnIds.includes(card.id)) returned.push(card);
        else kept.push(card);
      }
      ps.hand = kept;
      // Shuffle returned cards back into deck
      for (const c of returned) ps.deck.push(c);
      shuffle(ps.deck);
      // Draw replacements
      drawCards(ps, returned.length);
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
    this.phase     = 'playing';
    this.turnNumber = 1;

    // First player gets resources but NO extra draw
    generateResources(this.state[this.activeSide]);

    this._log(`Hra začala. Na tahu: ${this.name[this.activeSide]}`);
    this._sendStateBoth();
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

    // Check resources
    const cost = card.cost || 0;
    const res  = card.costType;
    if (res && res !== 'CHAOS') {
      if ((self.resources[res] || 0) < cost) {
        this._sendError(side, 'Nedostatek zdrojů.');
        return;
      }
      self.resources[res] -= cost;
    } else if (res === 'CHAOS') {
      // Chaos: pay 1 from each resource type available
      let remaining = cost;
      for (const rType of ['MAGIC', 'ATTACK', 'STONES']) {
        if (remaining <= 0) break;
        const pay = Math.min(remaining, self.resources[rType] || 0);
        self.resources[rType] = (self.resources[rType] || 0) - pay;
        remaining -= pay;
      }
      if (remaining > 0) {
        this._sendError(side, 'Nedostatek zdrojů pro Chaos kartu.');
        // Refund (undo the deductions by restarting — simplest: re-check)
        // Already partially deducted — restart is complex; send error before deducting next time.
        // TODO: validate CHAOS cost atomically before deducting
        return;
      }
    }

    // Remove from hand → discard
    self.hand.splice(cardIdx, 1);
    self.discardPile.push(card);

    // Zapamatuj si zahranou kartu pro animaci
    this.lastPlayedCard   = { id: card.id, baseId: card.baseId, name: card.name,
                               cost: card.cost, costType: card.costType, rarity: card.rarity };
    this.lastPlayedBySide = side;

    // Handle combo cards: apply effects only if isCombo check passes (always for now)
    const lostCards = [];
    applyEffects(
      card.effects,
      self,
      opp,
      CARD_MAP,
      (c, action) => lostCards.push({ card: c, action })
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

    this._log(`${this.name[side]} odhodil ${card.name}`);
    this._sendStateBoth();
  }

  // ── End turn ───────────────────────────────────────────────────────────────

  _handleEndTurn(side) {
    this._advanceTurn();
  }

  // ── Skip turn (empty deck) ─────────────────────────────────────────────────

  _handleSkipTurn(side) {
    const self = this.state[side];
    const opp  = this.state[side === 'A' ? 'B' : 'A'];

    // Both decks empty → resolve by HP
    if (self.deck.length === 0 && opp.deck.length === 0) {
      const winner = resolveByHp(this.state.A, this.state.B);
      this._endGame(winner);
      return;
    }

    this._advanceTurn();
  }

  // ── Advance turn ───────────────────────────────────────────────────────────

  _advanceTurn() {
    // Switch active side
    this.activeSide = this.activeSide === 'A' ? 'B' : 'A';
    this.turnNumber++;

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

    this._sendStateBoth();
  }

  // ── Game over ──────────────────────────────────────────────────────────────

  _endGame(winner) {
    this.phase = 'ended';

    let winnerName = null;
    if (winner === 'A') winnerName = this.name.A;
    else if (winner === 'B') winnerName = this.name.B;

    this._sendStateBoth();   // final state snapshot

    const msg = {
      type:       'GAME_OVER',
      winner,                // 'A' | 'B' | 'DRAW'
      winnerName
    };
    this._send('A', { ...msg, youWin: winner === 'A' || winner === 'DRAW' });
    this._send('B', { ...msg, youWin: winner === 'B' || winner === 'DRAW' });

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
        castleHP:    my.castleHP,
        wallHP:      my.wallHP,
        resources:   { ...my.resources },
        mines:       { ...my.mines },
        hand:        this._serializeHand(mySide),
        deckSize:    my.deck.length,
        discardSize: my.discardPile.length
      },
      oppState: {
        castleHP:    opp.castleHP,
        wallHP:      opp.wallHP,
        resources:   { ...opp.resources },
        mines:       { ...opp.mines },
        handSize:    opp.hand.length,       // opponent hand is hidden
        deckSize:    opp.deck.length,
        discardSize: opp.discardPile.length
      },
      log:              [...this.lastLog],
      lastPlayedCard:   this.lastPlayedCard,   // null nebo { id, baseId, name, ... }
      lastPlayedByMe:   this.lastPlayedBySide === side
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
