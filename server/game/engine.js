'use strict';
/**
 * Herní engine – portováno z GameLogic.kt + PlayerState.kt + GameState.kt
 * Veškerá logika je čistá (bez side-effectů na síťovou vrstvu).
 */
const { makeInstance, shuffle } = require('./cards');

// ── PlayerState ───────────────────────────────────────────────────────────────

function createPlayerState(deckCards) {
  return {
    castleHP: 30,
    wallHP:   10,
    resources: { MAGIC: 0, ATTACK: 0, STONES: 0, CHAOS: 0 },
    mines:     { MAGIC: 1, ATTACK: 1, STONES: 1 },
    deck:  [...deckCards],
    hand:  [],
    discardPile: []
  };
}

function generateResources(state) {
  for (const [type, amount] of Object.entries(state.mines)) {
    state.resources[type] = (state.resources[type] || 0) + amount;
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

function checkCondition(cond, player) {
  switch (cond.type) {
    case 'ResourceAbove': return (player.resources[cond.resType] || 0) > cond.threshold;
    case 'WallAbove':     return player.wallHP   > cond.threshold;
    case 'WallBelow':     return player.wallHP   < cond.threshold;
    case 'CastleAbove':   return player.castleHP > cond.threshold;
    default: return false;
  }
}

// ── applyEffects ──────────────────────────────────────────────────────────────

/**
 * @param {Array}    effects
 * @param {object}   self       – hráč, který hrál kartu
 * @param {object}   opponent   – soupeř
 * @param {Map}      cardMap    – CARD_MAP pro AddCardsToDeck
 * @param {Function} onOpponentLoss  – (card, action) => void, volá se, když soupeř přijde o kartu
 */
function applyEffects(effects, self, opponent, cardMap, onOpponentLoss) {
  for (const fx of effects) {
    switch (fx.type) {

      case 'AddResource':
        self.resources[fx.resType] = Math.max(0, (self.resources[fx.resType] || 0) + fx.amount);
        break;

      case 'AddMine':
        self.mines[fx.resType] = Math.max(0, (self.mines[fx.resType] || 0) + fx.amount);
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
        self.resources[fx.resType]     = (self.resources[fx.resType]     || 0) + taken;
        break;
      }

      case 'DrainResource': {
        const drained = Math.min(fx.amount, opponent.resources[fx.resType] || 0);
        opponent.resources[fx.resType] = (opponent.resources[fx.resType] || 0) - drained;
        break;
      }

      case 'ConditionalEffect':
        if (checkCondition(fx.condition, self))
          applyEffects([fx.effect], self, opponent, cardMap, onOpponentLoss);
        break;

      case 'DestroyMine': {
        const cur = opponent.mines[fx.resType] || 0;
        if (cur > 0) opponent.mines[fx.resType] = Math.max(0, cur - fx.amount);
        break;
      }

      case 'StealCard':
        for (let i = 0; i < fx.count; i++) {
          if (opponent.hand.length > 0) {
            const idx = Math.floor(Math.random() * opponent.hand.length);
            const stolen = opponent.hand.splice(idx, 1)[0];
            self.hand.push(stolen);
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
        drawCards(self, fx.count);
        break;

      case 'StealCastle': {
        const stolen = Math.min(fx.amount, Math.max(0, opponent.castleHP));
        opponent.castleHP -= stolen;
        self.castleHP = Math.min(100, self.castleHP + stolen);
        break;
      }
    }
  }
}

// ── Win condition ─────────────────────────────────────────────────────────────

/**
 * @returns {'A'|'B'|'DRAW'|null}  strana výherce, nebo null = pokračuj
 * @param {object} stateA
 * @param {object} stateB
 * @param {string} activeSide – kdo právě hrál (pro případ že obě podmínky nastanou zároveň)
 */
function checkWin(stateA, stateB) {
  const aDead  = stateA.castleHP <= 0;
  const bDead  = stateB.castleHP <= 0;
  const aBuilt = stateA.castleHP >= 60;
  const bBuilt = stateB.castleHP >= 60;

  if (aDead && bDead) return 'DRAW';
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

module.exports = {
  createPlayerState, generateResources, drawCards,
  checkCondition, applyEffects, checkWin, resolveByHp
};
