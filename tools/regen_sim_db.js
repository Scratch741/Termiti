// Přegeneruje CARD_EFFECTS_DB v deckbuilder.html ze server/game/cards.js.
// Databáze simulátoru byla ručně udržovaná kopie a rozešla se s hrou
// (chybějící karty, nepřevedené rozhodovací efekty, špatné combo příznaky, jiné hodnoty).
const fs = require('fs');
const path = 'app/src/main/java/com/example/termiti/deckbuilder.html';

const mod = require(process.cwd()+'/server/game/cards.js');
const CARDS = Array.isArray(mod) ? mod : (mod.CARDS || mod.cards || Object.values(mod)[0]);

const COND = {
  ResourceAbove:              c => ({ t: 'RA',  r: c.resType, v: c.threshold }),
  WallAbove:                  c => ({ t: 'WA',  v: c.threshold }),
  WallBelow:                  c => ({ t: 'WB',  v: c.threshold }),
  CastleAbove:                c => ({ t: 'CA',  v: c.threshold }),
  CastleBelow:                c => ({ t: 'CB',  v: c.threshold }),
  LastPlayedType:             c => ({ t: 'LPT', r: c.cardType }),
  ResourceMoreThanOpponent:   c => ({ t: 'RMO', r: c.resType }),
};

const FX = {
  AttackPlayer:              e => ({ t: 'AP', a: e.amount }),
  AttackCastle:              e => ({ t: 'AC', a: e.amount }),
  AttackWall:                e => ({ t: 'AW', a: e.amount }),
  BuildCastle:               e => ({ t: 'BC', a: e.amount }),
  BuildWall:                 e => ({ t: 'BW', a: e.amount }),
  StealCastle:               e => ({ t: 'SC', a: e.amount }),
  AddResource:               e => ({ t: 'AR', r: e.resType, a: e.amount }),
  AddMine:                   e => ({ t: 'AM', r: e.resType, a: e.amount }),
  StealResource:             e => ({ t: 'SR', r: e.resType, a: e.amount }),
  DrainResource:             e => ({ t: 'DR', r: e.resType, a: e.amount }),
  DestroyMine:               e => ({ t: 'DM', r: e.resType, a: e.amount }),
  BlockMine:                 e => ({ t: 'BLK', r: e.resType, turns: e.turns }),
  DrawCard:                  e => ({ t: 'DC', a: e.count }),
  DrawBoth:                  e => ({ t: 'DB', a: e.count }),
  StealCard:                 e => ({ t: 'StealC', a: e.count }),
  BurnCard:                  e => ({ t: 'BurnC', a: e.count }),
  AddCardsToDeck:            e => ({ t: 'AddD', id: e.cardId, count: e.count }),
  AddToOpponentDeck:         e => ({ t: 'AddOpp', id: e.cardId, count: e.count }),
  TrapOnDraw:                e => ({ t: 'Trap', e: mapFx(e.effect) }),
  ConditionalEffect:         e => ({ t: 'CE', c: COND[e.condition.type](e.condition), e: mapFx(e.effect) }),
  ConvertMine:               e => ({ t: 'CM', from: e.from, to: e.to }),
  ConvertWallToCastle:       () => ({ t: 'CWC' }),
  SwapHands:                 () => ({ t: 'SW' }),
  RandomizeHands:            () => ({ t: 'RH' }),
  ShapeShift:                () => ({ t: 'SS' }),
  Mirror:                    () => ({ t: 'MIR' }),
  Clone:                     () => ({ t: 'CLN' }),
  PeekAndStealHand:          () => ({ t: 'PSH' }),
  SmartJoker:                () => ({ t: 'SJ' }),
  DecisionMine:              () => ({ t: 'DMINE' }),
  NextCardIsCombo:           () => ({ t: 'NCC' }),
  GiveRandomCard:            e => ({ t: 'GRC', ct: e.costType }),
  MomentumAttack:            e => ({ t: 'MMA', b: e.base, bon: e.bonusPerAttack }),
  ModifyHandCost:            e => ({ t: 'MHC', d: e.delta, opp: !!e.targetOpponent }),
  DiscountRandomCard:        e => ({ t: 'DRC', d: e.delta, ct: e.cardType, n: e.count }),
  CloneNextPlayed:           e => ({ t: 'CNP', n: e.count }),
  DrawPerCardPlayed:         e => ({ t: 'DPC', ct: e.cardType ?? null }),
  GainResourcePerCardPlayed: e => ({ t: 'GRP', r: e.resType, a: e.amount, ct: e.cardType ?? null }),
  GainCastlePerCardPlayed:   e => ({ t: 'GCP', a: e.amount, ct: e.cardType ?? null }),
  DecisionChooseType:        e => ({ t: 'DCT', ct: e.cardType, pr: e.picks, cr: e.costReduction || 0 }),
  DecisionBurnOpponent:      e => ({ t: 'DBO', pr: e.picks }),
  DecisionFromDiscard:       e => ({ t: 'DFD', pr: e.picks }),
  DecisionFromDeck:          e => ({ t: 'DFK', pr: e.picks }),
  DecisionDrawFromDeck:      e => ({ t: 'DFKD', pr: e.picks }),
  DecisionChooseResource:    e => ({ t: 'DCR', opts: e.options }),
  XScaledAttackPlayer:       e => ({ t: 'XAP', div: e.divisor }),
  XScaledAttackCastle:       e => ({ t: 'XAC', div: e.divisor }),
  XScaledBuildCastle:        e => ({ t: 'XBC', div: e.divisor }),
  XScaledDualResource:       e => ({ t: 'XDR', rA: e.typeA, rB: e.typeB, div: e.divisor }),
};

const unknown = new Set();
function mapFx(e) {
  const f = FX[e.type];
  if (!f) { unknown.add(e.type); return null; }
  return f(e);
}

const lines = [];
for (const c of CARDS) {
  const fx  = (c.effects || []).map(mapFx).filter(Boolean);
  const dfx = (c.discardEffects || []).map(mapFx).filter(Boolean);
  const parts = ['ct:"' + c.costType + '"', 'fx:' + JSON.stringify(fx)];
  if (dfx.length)  parts.push('dfx:' + JSON.stringify(dfx));
  if (c.isCombo)   parts.push('combo:true');
  if (c.isXCost)   parts.push('xCost:true');
  lines.push('  "' + c.id + '":{' + parts.join(',') + '},');
}
if (unknown.size) { console.error('NEZNÁMÉ EFEKTY: ' + [...unknown].join(', ')); process.exit(1); }

const head = 'let CARD_EFFECTS_DB = {\n';
const body = head +
  '  // GENEROVÁNO ze server/game/cards.js – needituj ručně.\n' +
  '  // Regenerace: node tools/regen_sim_db.js\n' +
  lines.join('\n') + '\n}';

const html = fs.readFileSync(path, 'utf8');
const start = html.indexOf('let CARD_EFFECTS_DB');
let depth = 0, k = html.indexOf('{', start);
while (true) { if (html[k] === '{') depth++; else if (html[k] === '}') depth--; k++; if (depth === 0) break; }
fs.writeFileSync(path, html.slice(0, start) + body + html.slice(k), 'utf8');
console.log('CARD_EFFECTS_DB přegenerováno: ' + CARDS.length + ' karet');
