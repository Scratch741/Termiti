'use strict';
/**
 * GameSession – server-side game instance.
 * Manages one complete game between two WebSocket clients.
 */
const {
  CARD_MAP, ALL_CARDS, makeInstance, balancedDeck, superBalancedDeck, buildDeckFromIds, shuffle
} = require('./cards');
const {
  MAX_RESOURCE,
  createPlayerState, generateResources, drawCards,
  applyEffects, deriveCardType, applyPassiveAbilities, checkWin, resolveByHp,
  transformShapeShifters
} = require('./engine');

const DECISION_TYPES = new Set(['DecisionBurnOpponent', 'DecisionChooseType', 'DecisionFromDiscard', 'DecisionFromDeck']);
const { ratingSystem } = require('./RatingSystem');

const MULLIGAN_HAND_SIZE    = 4;
const MULLIGAN_TIMEOUT_MS   = 30_000;   // 30 s na mulligan; po vypršení auto-skip
const TURN_HAND_DRAW        = 1;
const TURN_SECONDS          = 15;
const TIMEBANK_SECONDS      = 120;

class GameSession {

  /**
   * @param {string}    gameId
   * @param {WebSocket} wsA
   * @param {string}    nameA
   * @param {WebSocket} wsB
   * @param {string}    nameB
   * @param {string[]|null} deckIdsA  – 30 base ID karet pro hráče A (null = náhodný)
   * @param {string[]|null} deckIdsB  – 30 base ID karet pro hráče B (null = náhodný)
   * @param {Function|null} onEnd     – callback(gameId) volaný při ukončení hry
   */
  constructor(gameId, wsA, nameA, wsB, nameB, deckIdsA = null, deckIdsB = null, onEnd = null, mode = 'normal', abilitiesA = [], abilitiesB = [], deviceIdA = null, deviceIdB = null) {
    this.gameId = gameId;
    this.onEnd  = onEnd;
    this.mode   = mode;   // 'normal' | 'super_random'

    this.ws        = { A: wsA,       B: wsB       };
    this.name      = { A: nameA,     B: nameB     };
    this.deviceId  = { A: deviceIdA, B: deviceIdB };
    this.deckIds   = { A: deckIdsA,  B: deckIdsB  };
    this.abilities = { A: abilitiesA, B: abilitiesB };

    // Game state
    this.state     = { A: null, B: null };
    this.phase     = 'mulligan';         // 'mulligan' | 'playing' | 'ended'
    this.activeSide = 'A';               // whose turn it is
    this.turnNumber = 0;

    // Mulligan tracking
    this.mulliganDone   = { A: false, B: false };
    this._mulliganTimers = { A: null, B: null };

    // Quick-draw tracking (1 extra card on first turn, not in mulligan)
    this.quickDrawApplied = { A: false, B: false };

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

    // Pending Decision state (set while waiting for DECISION_RESPONSE from client)
    this.pendingDecision      = null;  // { side, effect, isCombo, options }
    this._decisionTimer       = null;
    this._decisionStartedAt   = null;  // timestamp kdy byl odeslán DECISION_REQUEST
    this._decisionTurnPhaseMs = null;  // zbývající ms ve fázi kola v tom okamžiku (bez timebanku)
  }

  // ── Start ──────────────────────────────────────────────────────────────────

  start() {
    // Build decks – vlastní balíček pokud poslaný, jinak náhodný
    console.log(`[GameSession] ${this.name.A} deckIds: ${this.deckIds.A ? `${this.deckIds.A.length} karet` : 'náhodný'}`);
    console.log(`[GameSession] ${this.name.B} deckIds: ${this.deckIds.B ? `${this.deckIds.B.length} karet` : 'náhodný'}`);
    // super_random: oba hráči sdílí stejný balíček, každý jen jinak zamíchaný
    const sharedSuperDeck = this.mode === 'super_random' ? superBalancedDeck() : null;
    const buildDeck = (ids) => {
      if (sharedSuperDeck) return shuffle([...sharedSuperDeck]); // kopie s vlastním zamícháním
      return ids ? buildDeckFromIds(ids) : balancedDeck();
    };
    const deckA = buildDeck(this.deckIds.A);
    const deckB = buildDeck(this.deckIds.B);

    this.state.A = createPlayerState(deckA);
    this.state.B = createPlayerState(deckB);

    // Aplikuj pasivní schopnosti na startovní stav (před rozdáním karet)
    applyPassiveAbilities(this.state.A, this.abilities.A);
    applyPassiveAbilities(this.state.B, this.abilities.B);

    // Vítězný cíl hradu:
    //   extra_castle  → vlastní cíl 75 (výměna za 5 HP navíc při startu)
    //   iron_bastion  → soupeřův cíl 75 (soupeř musí postavit více)
    // Obě schopnosti NESTACKUJÍ – max je vždy 75, ne 80.
    // (Obě jsou popsané jako "cíl 65", nikoli "+5")
    this.winTarget = {
      A: (this.abilities.A.includes('extra_castle') || this.abilities.B.includes('iron_bastion')) ? 75 : 70,
      B: (this.abilities.B.includes('extra_castle') || this.abilities.A.includes('iron_bastion')) ? 75 : 70
    };

    // Deal opening hands
    drawCards(this.state.A, MULLIGAN_HAND_SIZE);
    drawCards(this.state.B, MULLIGAN_HAND_SIZE);

    // Pick who goes first (already decided in matchmaking, side A = first player)
    this.activeSide = 'A';

    // Notify both clients
    this._send('A', { type: 'GAME_MULLIGAN', hand: this._serializeHand('A'), timeoutMs: MULLIGAN_TIMEOUT_MS });
    this._send('B', { type: 'GAME_MULLIGAN', hand: this._serializeHand('B'), timeoutMs: MULLIGAN_TIMEOUT_MS });

    // Auto-confirm after timeout if player hasn't responded
    for (const side of ['A', 'B']) {
      this._mulliganTimers[side] = setTimeout(() => {
        if (this.phase !== 'mulligan' || this.mulliganDone[side]) return;
        console.log(`[Mulligan ${this.gameId}] Timeout pro ${this.name[side]}(${side}) – auto-skip`);
        this.handleMulligan(side, []);
      }, MULLIGAN_TIMEOUT_MS);
    }
  }

  // ── Mulligan ───────────────────────────────────────────────────────────────

  /**
   * @param {'A'|'B'} side
   * @param {string[]} returnIds  – instance IDs the player wants to swap back
   */
  handleMulligan(side, returnIds) {
    console.log(`[Mulligan ${this.gameId}] ${this.name[side]}(${side}) odeslal – phase=${this.phase} doneA=${this.mulliganDone.A} doneB=${this.mulliganDone.B}`);
    if (this.phase !== 'mulligan') { console.log(`[Mulligan ${this.gameId}] BLOKOVÁNO – phase není mulligan`); return; }
    if (this.mulliganDone[side])   { console.log(`[Mulligan ${this.gameId}] BLOKOVÁNO – ${side} už odeslal`);  return; }

    // Zruš mulligan timer pro tuto stranu
    if (this._mulliganTimers[side]) { clearTimeout(this._mulliganTimers[side]); this._mulliganTimers[side] = null; }

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
      console.log(`[Mulligan ${this.gameId}] Oba hráči hotovi → startGame`);
      this._startGame();
    } else {
      // Tell the other side their opponent confirmed
      const other = side === 'A' ? 'B' : 'A';
      console.log(`[Mulligan ${this.gameId}] Čekám na ${other} → OPPONENT_MULLIGAN_DONE → ${this.name[other]}`);
      this._send(other, { type: 'OPPONENT_MULLIGAN_DONE' });
    }
  }

  // ── Game start ─────────────────────────────────────────────────────────────

  _startGame() {
    this.phase      = 'playing';
    this.turnNumber = 1;

    // First player gets resources but NO extra draw
    generateResources(this.state[this.activeSide]);

    // quick_draw: hráč A (jde první) dostane 1 kartu navíc na začátku svého 1. tahu
    if (this.abilities[this.activeSide].includes('quick_draw') && !this.quickDrawApplied[this.activeSide]) {
      this.quickDrawApplied[this.activeSide] = true;
      drawCards(this.state[this.activeSide], 1);
    }

    // Transformuj shapeshiftery pro oba hráče před prvním tahem
    // (_advanceTurn to dělá automaticky od 2. tahu, ale první tah hráče A by bez toho byl netransformovaný)
    transformShapeShifters(this.state.A.hand, ALL_CARDS);
    transformShapeShifters(this.state.B.hand, ALL_CARDS);

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

  _clearMulliganTimers() {
    for (const side of ['A', 'B']) {
      if (this._mulliganTimers[side]) { clearTimeout(this._mulliganTimers[side]); this._mulliganTimers[side] = null; }
    }
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
    console.log(`[Reconnect ${this.gameId}] resendStateTo(${side}) phase=${this.phase} doneA=${this.mulliganDone.A} doneB=${this.mulliganDone.B}`);
    if (this.phase === 'mulligan') {
      // Resetuj mulliganDone pro reconnectujícího hráče – klient dostane GAME_MULLIGAN
      // znovu a musí mít možnost znovu odeslat svůj výběr.
      this.mulliganDone[side] = false;
      this._send(side, { type: 'GAME_MULLIGAN', hand: this._serializeHand(side) });
      // Pokud soupeř už svůj mulligan dokončil, informuj reconnectujícího hráče,
      // aby věděl, že stačí jen potvrdit svůj vlastní výběr.
      const other = side === 'A' ? 'B' : 'A';
      if (this.mulliganDone[other]) {
        console.log(`[Reconnect ${this.gameId}] ${other} už dokončil mulligan → posílám OPPONENT_MULLIGAN_DONE → ${side}`);
        this._send(side, { type: 'OPPONENT_MULLIGAN_DONE' });
      }
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

    // ── Čekáme na výběr Rozhodnutí ──────────────────────────────────────────
    if (this.pendingDecision) {
      if (action === 'DECISION_RESPONSE' && side === this.pendingDecision.side) {
        this._handleDecisionResponse(side, data);
      } else if (side === this.pendingDecision.side) {
        this._sendError(side, 'Nejdříve vyber kartu (Rozhodnutí).');
      }
      return;
    }

    if (side !== this.activeSide) {
      this._sendError(side, 'Nejsi na tahu.');
      return;
    }

    // Zachyť zbývající čas PŘED vymazáním timeru – použijeme ho jako základ pro timeout Rozhodnutí.
    // Timeout = zbývající turn čas + zbývající timebank (resp. jen timebank pokud jsme v timebank fázi).
    const _now = Date.now();
    if (this.timebankStartedAt !== null) {
      // Jsme ve fázi timebanku → turn čas je pryč; zbývající čas bude jen z timebanku
      this._remainingTurnMsAtAction = 0;
    } else {
      // Jsme ve fázi kola → zbývající ms v tomto kole (bez Min5000 – přičteme timebank)
      this._remainingTurnMsAtAction = Math.max(0, TURN_SECONDS * 1000 - (_now - this.turnStartedAt));
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
    // Hráč zahrál kartu → resetuj příznak prázdného přeskočení (není to skip)
    this.skippedEmptyDeck[side] = false;

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
    } else {
      const cost = card.cost || 0;
      if (res && cost > 0 && (self.resources[res] || 0) < cost) {
        this._sendError(side, 'Nedostatek zdrojů.');
        return;
      }
    }
    // Snapshot zdrojů PŘED zaplacením – ConditionalEffect (ResourceAbove) se
    // vyhodnocuje proti tomuto stavu, aby karta mohla splnit vlastní podmínku.
    self._preCostResources = { ...self.resources };
    // Zaplatit až teď (X-kost = vynuluj, jinak odečti cenu)
    if (card.isXCost) {
      self.resources[res] = 0;
    } else {
      const cost = card.cost || 0;
      if (res && cost > 0) self.resources[res] -= cost;
    }

    // Remove from hand → discard
    self.hand.splice(cardIdx, 1);
    self.discardPile.push(card);

    // Zapamatuj si zahranou kartu + index v ruce (před splice) pro zobrazení soupeři
    // Shapeshifter: použij displayBaseId → klient zobrazí transformovanou kartu, ne C34
    this.lastPlayedCard    = { id: card.id, baseId: card.displayBaseId || card.baseId, name: card.name,
                                cost: card.cost, costType: card.costType, rarity: card.rarity };
    this.lastPlayedAction  = 'PLAYED';
    this.lastPlayedBySide  = side;
    this.lastPlayedCardIdx = cardIdx;

    // Handle combo cards: apply effects only if isCombo check passes (always for now)
    // Nastav typ právě hrané karty před applyEffects – podmínka LastPlayedType to přečte
    self.lastPlayedType = deriveCardType(card);

    // DrawPerCardPlayed: flag nastaven předchozí kartou → líz 1 kartu (s volitelným filtrem typu)
    const cardType = deriveCardType(card);
    const drawFilter = self.drawCardOnPlay;
    if (drawFilter !== null && drawFilter !== undefined && (drawFilter === '' || drawFilter === cardType)) {
      const drawBurned = drawCards(self, 1, self.maxHandSize || 7);
      for (const bc of drawBurned) this._log(`🔥 ${this.name[side]} spálil ${bc.name} (plná ruka).`);
    }
    // GainResourcePerCardPlayed: přidej zdroje nastavené předchozí kartou (s filtrem typu)
    for (const grp of (self.gainResourcePerCardPlayed || [])) {
      if (!grp.cardType || grp.cardType === cardType) {
        self.resources[grp.resType] = Math.min(MAX_RESOURCE, (self.resources[grp.resType] || 0) + grp.amount);
      }
    }
    // GainCastlePerCardPlayed: přidej HP hradu nastavené předchozí kartou (s filtrem typu)
    for (const gcpp of (self.gainCastlePerCardPlayed || [])) {
      if (!gcpp.cardType || gcpp.cardType === cardType) {
        self.castleHP = Math.min(100, self.castleHP + gcpp.amount);
      }
    }

    // ── Rozhodnutí: detekuj Decision efekt PŘED applyEffects ───────────────
    // Decision efekty jsou no-ops v engine.applyEffects; zpracováváme je zde.
    const decisionFx = card.effects.find(fx => DECISION_TYPES.has(fx.type));

    const lostCards = [];
    applyEffects(
      card.effects,
      self,
      opp,
      CARD_MAP,
      (c, action) => lostCards.push({ card: c, action }),
      xValue
    );
    // Snapshot už není potřeba – vyčistit, aby neovlivnil další vyhodnocení
    delete self._preCostResources;

    this._log(`${this.name[side]} zahrál ${card.name}`);

    // Notify opponent about stolen/burned cards + log for both players
    const oppSideName = this.name[side === 'A' ? 'B' : 'A'];
    for (const { card: lc, action } of lostCards) {
      this._send(side === 'A' ? 'B' : 'A', {
        type:   'CARD_LOST',
        cardId: lc.id,
        action  // 'STOLEN' | 'BURNED'
      });
      if (action === 'BURNED') {
        this._log(`🔥 ${oppSideName} přišel o ${lc.name} (spálena).`);
      } else if (action === 'STOLEN') {
        this._log(`🗡️ ${this.name[side]} ukradl ${lc.name} od ${oppSideName}.`);
      }
    }

    // ── Decision: přeruš tah, pošli výběr hráči ─────────────────────────────
    if (decisionFx) {
      // Předej ID právě zahrané karty – DecisionFromDiscard ji vyloučí z nabídky
      // (karta je už v discardu, ale efekt by měl proběhnout "před" zahozením)
      const options = this._buildDecisionOptions(side, decisionFx, card.id);
      this.pendingDecision = { side, effect: decisionFx, isCombo: card.isCombo, options };

      // Pošli aktuální stav oběma (suroviny odečteny, karta v discardu)
      this._sendStateBoth();

      // Timeout = zbývající turn čas + zbývající timebank hráče
      // (po _consumeTimebank je this.timebank[side] již aktuální)
      const decisionTurnPhaseMs = this._remainingTurnMsAtAction ?? 0;
      const decisionTimebankMs  = this.timebank[side] * 1000;
      const decisionTimeoutMs   = Math.max(5000, decisionTurnPhaseMs + decisionTimebankMs);

      // Zapamatuj si kdy začalo rozhodnutí – při _resolveDecision odečteme spotřebovaný timebank
      this._decisionStartedAt   = Date.now();
      this._decisionTurnPhaseMs = decisionTurnPhaseMs;

      // Pošli nabídku aktivnímu hráči
      this._send(side, {
        type:       'DECISION_REQUEST',
        effectType: decisionFx.type,
        cardType:   decisionFx.cardType || null,
        picks:      decisionFx.picks   || 3,
        options:    options.map(c => ({
          id:       c.id,
          baseId:   c.baseId || c.id,
          name:     c.name,
          cost:     c.cost,
          costType: c.costType,
          rarity:   c.rarity
        })),
        timeoutMs: decisionTimeoutMs
      });

      // Auto-resolve po timeoutu – timebank byl spotřebován celý
      this._decisionTimer = setTimeout(() => {
        if (!this.pendingDecision || this.pendingDecision.side !== side) return;
        console.log(`[Decision ${this.gameId}] Timeout – auto-resolve`);
        // Nastav start na null, aby _resolveDecision neodečítal znovu (timeout = celý timebank pryč)
        this.timebank[side]         = 0;
        this._decisionStartedAt     = null;
        this._decisionTurnPhaseMs   = null;
        this._resolveDecision(side, options.length > 0 ? options[0].id : null);
      }, decisionTimeoutMs);

      return;
    }

    // ── Normální průběh ────────────────────────────────────────────────────
    const winner = checkWin(this.state.A, this.state.B, this.winTarget.A, this.winTarget.B);
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

  // ── Decision: sestav nabídku karet ─────────────────────────────────────────

  _buildDecisionOptions(side, effect, playedCardId = null) {
    const self = this.state[side];
    const opp  = this.state[side === 'A' ? 'B' : 'A'];
    const n    = effect.picks || 3;

    switch (effect.type) {
      case 'DecisionBurnOpponent': {
        // N náhodných karet ze soupeřova balíčku (hráč si vybere, která shoří)
        return [...opp.deck].sort(() => Math.random() - 0.5).slice(0, n);
      }
      case 'DecisionChooseType': {
        // N náhodných karet daného typu z celého poolu (šablony)
        const pool = ALL_CARDS.filter(c => deriveCardType(c) === effect.cardType);
        return [...pool].sort(() => Math.random() - 0.5).slice(0, n)
          .map(c => ({ ...c, id: c.id, baseId: c.id }));
      }
      case 'DecisionFromDiscard': {
        // N náhodných karet z vlastního odhazovacího balíčku
        // Vyloučí právě zahranou kartu – ta sice fyzicky leží v discardu, ale efekt
        // Vzpomínky by měl proběhnout "před" zahozením (hráč si nemůže vzít sám sebe)
        return [...self.discardPile]
          .filter(c => c.id !== playedCardId)
          .sort(() => Math.random() - 0.5)
          .slice(0, n);
      }
      case 'DecisionFromDeck': {
        // N náhodných karet z vlastního balíčku
        return [...self.deck].sort(() => Math.random() - 0.5).slice(0, n);
      }
      default: return [];
    }
  }

  // ── Decision: zpracuj odpověď hráče ────────────────────────────────────────

  _handleDecisionResponse(side, { chosenId }) {
    if (!this.pendingDecision || this.pendingDecision.side !== side) {
      this._sendError(side, 'Žádné čekající rozhodnutí.');
      return;
    }
    this._resolveDecision(side, chosenId);
  }

  _resolveDecision(side, chosenId) {
    if (this._decisionTimer) { clearTimeout(this._decisionTimer); this._decisionTimer = null; }

    const { effect, isCombo } = this.pendingDecision;
    this.pendingDecision = null;

    // Spotřebuj timebank hráče za dobu strávenou výběrem (pokud hráč odpověděl sám, ne timeout)
    if (this._decisionStartedAt !== null) {
      const decisionElapsed  = Date.now() - this._decisionStartedAt;
      const turnPhaseMs      = this._decisionTurnPhaseMs || 0;
      const timebankUsedMs   = Math.max(0, decisionElapsed - turnPhaseMs);
      const timebankUsedSec  = Math.ceil(timebankUsedMs / 1000);
      this.timebank[side]    = Math.max(0, this.timebank[side] - timebankUsedSec);
      this._decisionStartedAt   = null;
      this._decisionTurnPhaseMs = null;
    }

    const self = this.state[side];
    const opp  = this.state[side === 'A' ? 'B' : 'A'];
    const maxH = self.maxHandSize || 7;

    switch (effect.type) {
      case 'DecisionBurnOpponent': {
        if (chosenId) {
          const idx = opp.deck.findIndex(c => c.id === chosenId);
          if (idx !== -1) {
            const [burned] = opp.deck.splice(idx, 1);
            opp.discardPile.push(burned);
            this._log(`${this.name[side]} zahodil kartu ze soupeřova balíčku (${burned.name}).`);
          }
        }
        break;
      }
      case 'DecisionChooseType': {
        if (chosenId) {
          const tmpl = CARD_MAP.get(chosenId);
          if (tmpl && self.hand.length < maxH) {
            self.hand.push({ ...makeInstance(tmpl), isGenerated: true });
            this._log(`${this.name[side]} přidal ${tmpl.name} do ruky.`);
          }
        }
        break;
      }
      case 'DecisionFromDiscard': {
        if (chosenId) {
          const idx = self.discardPile.findIndex(c => c.id === chosenId);
          if (idx !== -1) {
            const [card] = self.discardPile.splice(idx, 1);
            if (self.hand.length < maxH) {
              self.hand.push({ ...card, isGenerated: true });
              this._log(`${this.name[side]} vrátil ${card.name} z odhazovacího balíčku.`);
            }
          }
        }
        break;
      }
      case 'DecisionFromDeck': {
        if (chosenId) {
          const orig = self.deck.find(c => c.id === chosenId);
          if (orig) {
            // Karta zůstane v balíčku – do ruky přijde kopie s novým ID
            const copy = { ...orig, id: `${orig.baseId || orig.id}_copy_${Date.now()}`, isGenerated: true };
            if (self.hand.length < maxH) {
              self.hand.push(copy);
              this._log(`${this.name[side]} zkopíroval z balíčku: ${orig.name}.`);
            }
          }
        }
        break;
      }
    }

    // lastPlayedCard už byl zalogován při prvním state pushu → vymaž, aby se nezalogoval znovu
    this.lastPlayedCard   = null;
    this.lastPlayedAction = null;

    const winner = checkWin(this.state.A, this.state.B, this.winTarget.A, this.winTarget.B);
    if (winner !== null) { this._endGame(winner); return; }

    if (!isCombo) {
      this._advanceTurn();
    } else {
      // Combo: hráč pokračuje v tahu – restart timeru, pošli stav
      this._startTurnTimer();
      this._sendStateBoth();
    }
  }

  // ── Discard card ───────────────────────────────────────────────────────────

  _handleDiscardCard(side, { cardId }) {
    // Hráč odhodil kartu → resetuj příznak prázdného přeskočení
    this.skippedEmptyDeck[side] = false;

    const self = this.state[side];

    const cardIdx = self.hand.findIndex(c => c.id === cardId);
    if (cardIdx === -1) {
      this._sendError(side, 'Karta není v ruce.');
      return;
    }

    const card = self.hand.splice(cardIdx, 1)[0];
    self.discardPile.push(card);

    this.lastPlayedCard   = { id: card.id, baseId: card.displayBaseId || card.baseId, name: card.name,
                               cost: card.cost, costType: card.costType, rarity: card.rarity };
    this.lastPlayedAction  = 'DISCARDED';
    this.lastPlayedBySide  = side;
    this.lastPlayedCardIdx = cardIdx;

    this._log(`${this.name[side]} odhodil ${card.name}`);
    this._advanceTurn();
  }

  // ── End turn ───────────────────────────────────────────────────────────────

  _handleEndTurn(side) {
    // Hráč aktivně ukončil tah → resetuj příznak prázdného přeskočení
    this.skippedEmptyDeck[side] = false;
    // Vymaž lastPlayedCard, aby se karta nezalogovala podruhé v GAME_STATE po změně tahu
    // (platí zejména pro combo karty, kde _sendStateBoth bylo voláno i při zahraní karty)
    this.lastPlayedCard   = null;
    this.lastPlayedAction = null;
    this._advanceTurn();
  }

  // ── Skip turn (empty deck) ─────────────────────────────────────────────────

  _handleSkipTurn(side) {
    // Stejně jako END_TURN: vymaž lastPlayedCard, aby nedošlo k duplicitě v logu
    this.lastPlayedCard   = null;
    this.lastPlayedAction = null;

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
    // Reset per-card-played efektů pro hráče, který právě skončil tah
    const prevState = this.state[this.activeSide];
    prevState.drawCardOnPlay = null;
    prevState.gainResourcePerCardPlayed = [];
    prevState.gainCastlePerCardPlayed = [];

    // Switch active side
    this.activeSide = this.activeSide === 'A' ? 'B' : 'A';
    // Increment round counter only when A's turn starts (= one full round completed)
    if (this.activeSide === 'A') {
      this.turnNumber++;
    }

    const next = this.state[this.activeSide];
    generateResources(next);

    // quick_draw: hráč B (jde druhý) dostane 1 kartu navíc na začátku svého 1. tahu
    let extraDraw = 0;
    if (this.abilities[this.activeSide].includes('quick_draw') && !this.quickDrawApplied[this.activeSide]) {
      this.quickDrawApplied[this.activeSide] = true;
      extraDraw = 1;
    }
    const burned = drawCards(next, TURN_HAND_DRAW + extraDraw, next.maxHandSize || 7);
    transformShapeShifters(next.hand, ALL_CARDS);

    for (const bc of burned) {
      this._log(`🔥 ${this.name[this.activeSide]} spálil ${bc.name} (plná ruka).`);
    }

    this._log(`Tah ${this.turnNumber}: ${this.name[this.activeSide]}`);

    // Win check (shouldn't happen mid-turn but be safe)
    const winner = checkWin(this.state.A, this.state.B, this.winTarget.A, this.winTarget.B);
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
    this._clearMulliganTimers();
    if (this._decisionTimer) { clearTimeout(this._decisionTimer); this._decisionTimer = null; }
    this.pendingDecision = null;

    let winnerName = null;
    if (winner === 'A') winnerName = this.name.A;
    else if (winner === 'B') winnerName = this.name.B;

    // ── Rating update ────────────────────────────────────────────────────────
    const ratingResult = ratingSystem.recordResult(
      this.deviceId.A, this.name.A,
      this.deviceId.B, this.name.B,
      winner,
      this.mode,
      this.gameId
    );
    // ratingResult = { deltaA, deltaB, newRatingA, newRatingB } | null

    // Finální stav – odhal soupeřovu ruku pro review mód
    this._send('A', this._buildStateFor('A', true));
    this._send('B', this._buildStateFor('B', true));

    const base = {
      type:      'GAME_OVER',
      winner,                // 'A' | 'B' | 'DRAW'
      winnerName,
      mode:      this.mode,
    };

    // Přidej rating info pokud existuje (deviceId byl k dispozici)
    this._send('A', {
      ...base,
      youWin:       winner === 'A',
      ratingChange: ratingResult ? ratingResult.deltaA       : null,
      newRating:    ratingResult ? ratingResult.newRatingA   : null,
    });
    this._send('B', {
      ...base,
      youWin:       winner === 'B',
      ratingChange: ratingResult ? ratingResult.deltaB       : null,
      newRating:    ratingResult ? ratingResult.newRatingB   : null,
    });

    this._log(`Konec hry. Vítěz: ${winnerName || 'REMÍZA'}`);
    console.log(`[Game ${this.gameId}] ended – winner: ${winner}`);

    // Uvolni hráče – aby mohli znovu vstoupit do fronty
    if (this.onEnd) this.onEnd(this.gameId);
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  /** Serialize a player's hand for transmission (full card data for owner). */
  _serializeHand(side) {
    return this.state[side].hand.map(c => ({
      id:          c.id,
      // Transformovaný Shapeshifter: pošli displayBaseId (skutečná šablona) nikoli 'C34',
      // aby klient zobrazil správnou kartu. Server sleduje Shapeshifter pomocí baseId: 'C34'.
      baseId:      c.displayBaseId || c.baseId,
      name:        c.name,
      cost:        c.cost,
      costType:    c.costType,
      rarity:      c.rarity,
      isGenerated: c.isGenerated || false
    }));
  }

  /**
   * Build the state payload to send to one player.
   * @param {boolean} revealOppHand – true = odhal soupeřovu ruku (posílá se jen při konci hry)
   */
  _buildStateFor(side, revealOppHand = false) {
    const mySide  = side;
    const oppSide = side === 'A' ? 'B' : 'A';
    const my  = this.state[mySide];
    const opp = this.state[oppSide];

    const oppStatePayload = {
      castleHP:        opp.castleHP,
      wallHP:          opp.wallHP,
      resources:       { ...opp.resources },
      mines:           { ...opp.mines },
      mineBlockedTurns:{ ...opp.mineBlockedTurns },
      handSize:        opp.hand.length,
      deckSize:        opp.deck.length,
      discardSize:     opp.discardPile.length,
      lastPlayedIdx:   this.lastPlayedBySide === oppSide ? this.lastPlayedCardIdx : null
    };

    // Při konci hry přidej skutečné karty soupeře – klient je zobrazí v review módu
    if (revealOppHand) {
      oppStatePayload.hand = this._serializeHand(oppSide);
    }

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
        discardSize:     my.discardPile.length,
        maxHandSize:     my.maxHandSize || 7
      },
      oppState:     oppStatePayload,
      myWinTarget:  this.winTarget[mySide],
      oppWinTarget: this.winTarget[oppSide],
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
