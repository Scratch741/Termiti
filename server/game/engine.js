'use strict';
/**
 * Herní engine – portováno z GameLogic.kt + PlayerState.kt + GameState.kt
 * Veškerá logika je čistá (bez side-effectů na síťovou vrstvu).
 */
const { ALL_CARDS, makeInstance, shuffle } = require('./cards');

const MAX_RESOURCE = 999;
const MAX_MINES    = 99;

// ── PlayerState ───────────────────────────────────────────────────────────────

function createPlayerState(deckCards) {
  return {
    castleHP: 30,
    wallHP:   10,
    resources: { MAGIC: 0, ATTACK: 0, STONES: 0, CHAOS: 0 },
    mines:     { MAGIC: 1, ATTACK: 1, STONES: 1 },
    /** Zbývající kola blokády produkce pro každý typ dolu. */
    mineBlockedTurns: { MAGIC: 0, ATTACK: 0, STONES: 0 },
    /** Odložené suroviny – aplikují se po N kolech. */
    pendingResources: [],
    deck:  [...deckCards],
    hand:  [],
    discardPile: [],
    lastPlayedType: null,
    /** Maximální velikost ruky – výchozí 7, extra_hand_card → 8. */
    maxHandSize: 7,
    /** null = neaktivní; '' = libovolný typ; 'Magie' = jen Magie. Resetuje se při přechodu na nový tah. */
    drawCardOnPlay: null,
    /** Seznam aktivních GainResourcePerCardPlayed efektů pro toto kolo. */
    gainResourcePerCardPlayed: [],
    /** Seznam aktivních GainCastlePerCardPlayed efektů pro toto kolo. */
    gainCastlePerCardPlayed: []
  };
}

/**
 * Odvodí kategoriový typ karty (odpovídá Card.type v Kotlinu).
 * Karty s efektem AddMine → "Důl"; jinak dle costType.
 */
function deriveCardType(card) {
  const hasMine = card.effects && card.effects.some(e => e.type === 'AddMine');
  if (hasMine) return 'Důl';
  switch (card.costType) {
    case 'ATTACK': return 'Útok';
    case 'STONES': return 'Stavba';
    case 'MAGIC':  return 'Magie';
    case 'CHAOS':  return 'Chaos';
    default:       return card.costType;
  }
}

/**
 * Volá se na začátku každého tahu hráče:
 * 1. Aplikuje odložené suroviny, které dozrály (turnsLeft → 0).
 * 2. Generuje produkci dolů (přeskočí zablokované a sníží čítač).
 */
function generateResources(state) {
  // 1. Odložené suroviny
  state.pendingResources = (state.pendingResources || []).filter(p => {
    p.turnsLeft--;
    if (p.turnsLeft <= 0) {
      state.resources[p.type] = Math.min(MAX_RESOURCE, (state.resources[p.type] || 0) + p.amount);
      return false; // odeber ze seznamu
    }
    return true; // nechej v seznamu
  });

  // 2. Produkce dolů (s kontrolou blokády)
  for (const [type, amount] of Object.entries(state.mines)) {
    const blocked = state.mineBlockedTurns[type] || 0;
    if (blocked > 0) {
      state.mineBlockedTurns[type] = blocked - 1;
    } else {
      state.resources[type] = Math.min(MAX_RESOURCE, (state.resources[type] || 0) + amount);
    }
  }
}

/**
 * Lízne count karet.
 * @returns {Array} Burned cards (ruka full → shoří)
 */
function drawCards(state, count, maxHand = 7) {
  const burned = [];
  for (let i = 0; i < count; i++) {
    if (state.deck.length > 0) {
      const card = state.deck.shift();
      if (state.hand.length < maxHand) {
        state.hand.push(card);
      } else {
        state.discardPile.push(card);
        burned.push(card);
      }
    }
  }
  return burned;
}

// ── checkCondition ────────────────────────────────────────────────────────────

function checkCondition(cond, player, opponent) {
  switch (cond.type) {
    case 'ResourceAbove': {
      // Podmínka "máš X surovin" se vyhodnocuje proti stavu PŘED zaplacením
      // ceny karty – jinak by karta, na kterou hráč právě měl, nemohla splnit
      // svou vlastní podmínku. _preCostResources nastavuje GameSession.playCard.
      const r = player._preCostResources || player.resources;
      return (r[cond.resType] || 0) > cond.threshold;
    }
    case 'WallAbove':     return player.wallHP   > cond.threshold;
    case 'WallBelow':     return player.wallHP   < cond.threshold;
    case 'CastleAbove':    return player.castleHP > cond.threshold;
    case 'CastleBelow':    return player.castleHP < cond.threshold;
    case 'LastPlayedType': return player.lastPlayedType === cond.cardType;
    case 'ResourceMoreThanOpponent': {
      const r = player._preCostResources || player.resources;
      const playerRes   = r[cond.resType] || 0;
      const opponentRes = (opponent && opponent.resources[cond.resType]) || 0;
      return playerRes > opponentRes;
    }
    default: return false;
  }
}

// ── applyEffects ──────────────────────────────────────────────────────────────

/**
 * @param {Array}    effects
 * @param {object}   self           – hráč, který hrál kartu
 * @param {object}   opponent       – soupeř
 * @param {Map}      cardMap        – CARD_MAP pro AddCardsToDeck
 * @param {Function} onOpponentLoss – (card, action) => void, volá se, když soupeř přijde o kartu
 * @param {number}   xValue         – hodnota X pro X-kost efekty (spotřebovaný zdroj)
 */
function applyEffects(effects, self, opponent, cardMap, onOpponentLoss, xValue = 0) {
  for (const fx of effects) {
    switch (fx.type) {

      case 'AddResource':
        self.resources[fx.resType] = Math.min(MAX_RESOURCE, Math.max(0, (self.resources[fx.resType] || 0) + fx.amount));
        break;

      case 'AddResourceDelayed':
        // Přidá do fronty odložených surovin
        self.pendingResources = self.pendingResources || [];
        self.pendingResources.push({ type: fx.resType, amount: fx.amount, turnsLeft: fx.turns || 1 });
        break;

      case 'AddMine':
        self.mines[fx.resType] = Math.min(MAX_MINES, Math.max(0, (self.mines[fx.resType] || 0) + fx.amount));
        break;

      case 'BuildWall':
        self.wallHP = Math.min(100, Math.max(0, self.wallHP + fx.amount));
        break;

      case 'BuildCastle':
        self.castleHP = Math.min(100, self.castleHP + fx.amount);
        break;

      case 'AttackPlayer': {
        const wallDmg = Math.min(fx.amount, opponent.wallHP);
        opponent.wallHP -= wallDmg;
        const overflow = fx.amount - wallDmg;
        if (overflow > 0) opponent.castleHP -= overflow;
        break;
      }

      case 'AttackWall':
        opponent.wallHP = Math.max(0, opponent.wallHP - fx.amount);
        break;

      case 'AttackCastle':
        opponent.castleHP -= fx.amount;
        break;

      case 'StealResource': {
        const taken = Math.min(fx.amount, opponent.resources[fx.resType] || 0);
        opponent.resources[fx.resType] = (opponent.resources[fx.resType] || 0) - taken;
        self.resources[fx.resType]     = Math.min(MAX_RESOURCE, (self.resources[fx.resType] || 0) + taken);
        break;
      }

      case 'DrainResource': {
        const drained = Math.min(fx.amount, opponent.resources[fx.resType] || 0);
        opponent.resources[fx.resType] = (opponent.resources[fx.resType] || 0) - drained;
        break;
      }

      case 'ConditionalEffect':
        if (checkCondition(fx.condition, self, opponent))
          applyEffects([fx.effect], self, opponent, cardMap, onOpponentLoss, xValue);
        break;

      case 'DestroyMine': {
        // Minimum 1 pro MAGIC/ATTACK/STONES, pro CHAOS minimum 0
        const cur = opponent.mines[fx.resType] || 0;
        const min = fx.resType === 'CHAOS' ? 0 : 1;
        if (cur > min) opponent.mines[fx.resType] = Math.max(min, cur - fx.amount);
        break;
      }

      case 'ConvertMine': {
        // Chaos důl se vždy zvýší; zdrojový důl se sníží jen pokud je nad floorem.
        // Non-CHAOS dol nejde pod 1 (nelze si zničit poslední magický důl).
        const floor   = fx.from === 'CHAOS' ? 0 : 1;
        const curFrom = self.mines[fx.from] || 0;
        self.mines[fx.to] = Math.min(MAX_MINES, (self.mines[fx.to] || 0) + 1);
        if (curFrom > floor) self.mines[fx.from] = curFrom - 1;
        break;
      }

      case 'BlockMine': {
        // Zablokuj produkci dolu na N kol (stackuje se, max 5)
        opponent.mineBlockedTurns = opponent.mineBlockedTurns || {};
        const current = opponent.mineBlockedTurns[fx.resType] || 0;
        opponent.mineBlockedTurns[fx.resType] = Math.min(5, current + fx.turns);
        break;
      }

      case 'StealCard':
        for (let i = 0; i < fx.count; i++) {
          if (opponent.hand.length > 0) {
            const idx = Math.floor(Math.random() * opponent.hand.length);
            const stolen = opponent.hand.splice(idx, 1)[0];
            if (self.hand.length < 7) {
              self.hand.push(stolen);
            } else {
              self.discardPile.push(stolen);  // ruka plná → ukradená karta shoří
            }
            onOpponentLoss && onOpponentLoss(stolen, 'STOLEN');
          }
        }
        break;

      case 'BurnCard':
        for (let i = 0; i < fx.count; i++) {
          if (opponent.hand.length > 0) {
            const idx = Math.floor(Math.random() * opponent.hand.length);
            const burned = opponent.hand.splice(idx, 1)[0];
            opponent.discardPile.push(burned);
            onOpponentLoss && onOpponentLoss(burned, 'BURNED');
          }
        }
        break;

      case 'AddCardsToDeck': {
        const tmpl = cardMap && cardMap.get(fx.cardId);
        if (tmpl) {
          for (let i = 0; i < fx.count; i++) {
            self.deck.push(makeInstance(tmpl));
          }
          shuffle(self.deck);
        }
        break;
      }

      case 'DrawCard':
        drawCards(self, fx.count, self.maxHandSize || 7);
        break;

      case 'StealCastle': {
        const stolen = Math.min(fx.amount, Math.max(0, opponent.castleHP));
        opponent.castleHP -= stolen;
        self.castleHP = Math.min(100, self.castleHP + stolen);
        break;
      }

      case 'DrawPerCardPlayed':
        self.drawCardOnPlay = fx.cardType || '';
        break;

      case 'GainResourcePerCardPlayed':
        self.gainResourcePerCardPlayed.push(fx);
        break;

      case 'GainCastlePerCardPlayed':
        self.gainCastlePerCardPlayed.push(fx);
        break;

      // Pasivní příznak – transformace probíhá v transformShapeShifters() při startu tahu
      case 'ShapeShift': break;

      case 'SwapHands': {
        const selfOldHand     = [...self.hand];
        const opponentOldHand = [...opponent.hand];
        self.hand.length     = 0;
        opponent.hand.length = 0;
        self.hand.push(...opponentOldHand);
        opponent.hand.push(...selfOldHand);
        // Zaloguj každou kartu z původní soupeřovy ruky jako ukradenou
        for (const card of selfOldHand) {
          onOpponentLoss && onOpponentLoss(card, 'STOLEN');
        }
        break;
      }

      // ── X-kost efekty ────────────────────────────────────────────────────────
      case 'XScaledAttackPlayer': {
        const dmg     = Math.floor(xValue / (fx.divisor || 2));
        const wallDmg = Math.min(dmg, opponent.wallHP);
        opponent.wallHP -= wallDmg;
        const overflow = dmg - wallDmg;
        if (overflow > 0) opponent.castleHP -= overflow;
        break;
      }

      case 'XScaledAttackCastle':
        opponent.castleHP -= Math.floor(xValue / (fx.divisor || 2));
        break;

      case 'XScaledBuildCastle': {
        const amount = Math.floor(xValue / (fx.divisor || 2));
        self.castleHP = Math.min(100, self.castleHP + amount);
        break;
      }

      case 'XScaledDualResource': {
        const amount = Math.floor(xValue / (fx.divisor || 2));
        self.resources[fx.typeA] = Math.min(MAX_RESOURCE, (self.resources[fx.typeA] || 0) + amount);
        self.resources[fx.typeB] = Math.min(MAX_RESOURCE, (self.resources[fx.typeB] || 0) + amount);
        break;
      }
    }
  }
}

// ── Passive abilities ─────────────────────────────────────────────────────────

/**
 * Aplikuje pasivní schopnosti na startovní stav hráče.
 * Voláno jednou hned po createPlayerState(), před rozdáním karet.
 * @param {object}   state     – výsledek createPlayerState()
 * @param {string[]} abilities – seznam ID aktivních schopností hráče
 */
function applyPassiveAbilities(state, abilities) {
  for (const id of (abilities || [])) {
    switch (id) {
      case 'extra_castle':  state.castleHP          += 5; break; // 30 → 35
      case 'extra_wall':    state.wallHP             += 5; break; // 10 → 15
      case 'extra_magic':   state.resources.MAGIC   += 1; break;
      case 'extra_attack':  state.resources.ATTACK  += 1; break;
      case 'extra_stones':  state.resources.STONES  += 1; break;
      case 'extra_chaos':      state.resources.CHAOS += 1; break;
      case 'extra_hand_card':  state.maxHandSize      = 8; break; // max ruka 7 → 8
      // ── Posily balíčku ───────────────────────────────────────────────────────
      case 'boost_attack':  _addBoostCards(state, t => t === 'Útok',  2); break;
      case 'boost_build':   _addBoostCards(state, t => t === 'Stavba',2); break;
      case 'boost_magic':   _addBoostCards(state, t => t === 'Magie', 2); break;
      case 'boost_chaos':   _addBoostCards(state, t => t === 'Chaos', 2); break;
      case 'boost_random':  _addBoostCards(state, t => t !== 'Důl',   3); break;
      // quick_draw se řeší v GameSession.start() při rozdávání karet
    }
  }
}

/** Přidá [count] náhodných karet filtrovaných podle typu do balíčku hráče. */
function _addBoostCards(state, typeFilter, count) {
  const pool = ALL_CARDS.filter(c => typeFilter(deriveCardType(c)));
  if (pool.length === 0) return;
  const picked = shuffle([...pool]).slice(0, count);
  for (const tmpl of picked) {
    state.deck.push(makeInstance(tmpl));
  }
  shuffle(state.deck);
}

// ── Win condition ─────────────────────────────────────────────────────────────

/**
 * @returns {'A'|'B'|'DRAW'|null}  strana výherce, nebo null = pokračuj
 * @param {object} stateA
 * @param {object} stateB
 * @param {number} winTargetA – hrad A musí dosáhnout ≥ teto hodnoty (default 60, s extra_castle 65)
 * @param {number} winTargetB – hrad B musí dosáhnout ≥ teto hodnoty
 */
function checkWin(stateA, stateB, winTargetA = 60, winTargetB = 60) {
  const aDead  = stateA.castleHP <= 0;
  const bDead  = stateB.castleHP <= 0;
  const aBuilt = stateA.castleHP >= winTargetA;
  const bBuilt = stateB.castleHP >= winTargetB;

  if (aDead && bDead) return 'DRAW_BOTH_DEAD';
  if (aDead)   return 'B';
  if (bDead)   return 'A';
  if (aBuilt && bBuilt) return 'DRAW';
  if (aBuilt)  return 'A';
  if (bBuilt)  return 'B';
  return null;
}

/** Porovná hrady (při oboustranném přeskočení s prázdnými balíčky). */
function resolveByHp(stateA, stateB) {
  if (stateA.castleHP > stateB.castleHP) return 'A';
  if (stateB.castleHP > stateA.castleHP) return 'B';
  return 'DRAW';
}

/**
 * Transformuje všechny ShapeShift karty v ruce v náhodné karty z pool.
 * Volá se na začátku každého tahu (po lízu) v GameSession._advanceTurn().
 * Původní instance ID je zachováno, aby klientský stav nerekrekoval slot.
 */
/** true = karta je (nebo byla) Shapeshifter – trackovano přes baseId i efekty */
function isShapeShifterInstance(card) {
  return card.baseId === 'C34' ||
         (card.effects && card.effects.some(fx => fx.type === 'ShapeShift'));
}

function transformShapeShifters(hand, cardPool) {
  const validPool = cardPool.filter(c => !c.effects.some(fx => fx.type === 'ShapeShift'));
  if (validPool.length === 0) return;
  for (let i = 0; i < hand.length; i++) {
    if (isShapeShifterInstance(hand[i])) {
      const tmpl = validPool[Math.floor(Math.random() * validPool.length)];
      // baseId = 'C34' zachováme → příští kolo se znovu transformuje
      // displayBaseId = ID šablony → klient zobrazí správnou kartu, ne původní Shapeshifter
      hand[i] = { ...tmpl, id: hand[i].id, baseId: 'C34', displayBaseId: tmpl.id };
    }
  }
}

module.exports = {
  MAX_RESOURCE,
  createPlayerState, generateResources, drawCards,
  checkCondition, deriveCardType, applyEffects,
  applyPassiveAbilities, checkWin, resolveByHp,
  transformShapeShifters
};
