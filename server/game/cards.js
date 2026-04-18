'use strict';
/**
 * Databáze karet – portováno z Kotlin allCards v Gameviewmodel.kt
 * Formát řádku: [id, name, cost, costType, isCombo, effects[], rarity]
 */

// ── Effect helpers ────────────────────────────────────────────────────────────
const ar = (t,n)    => ({ type:'AddResource',     resType:t, amount:n });
const am = (t,n=1)  => ({ type:'AddMine',         resType:t, amount:n });
const bw = (n)      => ({ type:'BuildWall',        amount:n });
const bc = (n)      => ({ type:'BuildCastle',      amount:n });
const ap = (n)      => ({ type:'AttackPlayer',     amount:n });
const aw = (n)      => ({ type:'AttackWall',       amount:n });
const ac = (n)      => ({ type:'AttackCastle',     amount:n });
const sr = (t,n)    => ({ type:'StealResource',    resType:t, amount:n });
const dr = (t,n)    => ({ type:'DrainResource',    resType:t, amount:n });
const cd = (c,e)    => ({ type:'ConditionalEffect',condition:c, effect:e });
const dm = (t,n=1)  => ({ type:'DestroyMine',     resType:t, amount:n });
const bm = (t,turns)=> ({ type:'BlockMine',        resType:t, turns });
const sc = (n=1)    => ({ type:'StealCard',        count:n });
const bn = (n=1)    => ({ type:'BurnCard',         count:n });
const ad = (id,n)   => ({ type:'AddCardsToDeck',   cardId:id, count:n });
const dc = (n=1)    => ({ type:'DrawCard',         count:n });
const sca= (n)      => ({ type:'StealCastle',      amount:n });

// Conditions
const rA = (t,v) => ({ type:'ResourceAbove', resType:t, threshold:v });
const wA = (v)   => ({ type:'WallAbove',     threshold:v });
const wB = (v)   => ({ type:'WallBelow',     threshold:v });
const cA = (v)   => ({ type:'CastleAbove',   threshold:v });

// ── Rarity max copies ─────────────────────────────────────────────────────────
const MAX_COPIES = { COMMON:4, RARE:3, EPIC:2, LEGENDARY:1 };

// ── Raw card data ─────────────────────────────────────────────────────────────
// [id, name, cost, costType, isCombo, effects, rarity]
const RAW = [
  // ── Útok ──────────────────────────────────────────────────────────────────
  ['001','Základní útok',  2,'ATTACK',0,[ap(5)],               'COMMON'],
  ['008','Šípy',           1,'ATTACK',0,[ac(3)],               'COMMON'],
  ['003','Ohnivá koule',   3,'MAGIC', 0,[ac(8)],               'COMMON'],
  ['007','Katapult',       4,'ATTACK',0,[ap(11)],              'RARE'],
  ['006','Podmíněný útok', 3,'ATTACK',0,[cd(rA('ATTACK',5), ac(10))],  'RARE'],
  ['017','Válečný sekyrník',4,'ATTACK',0,[ap(8),sr('ATTACK',2)],       'COMMON'],
  ['019','Zápalné šípy',   1,'ATTACK',0,[aw(5)],               'COMMON'],
  ['020','Beranidlo',      3,'ATTACK',0,[aw(11)],              'RARE'],
  ['021','Dělostřelectvo', 6,'ATTACK',0,[ap(15)],              'EPIC'],
  ['022','Přímý zásah',    3,'ATTACK',0,[ac(8)],               'RARE'],
  ['023','Dvojitý úder',   5,'ATTACK',0,[ap(12)],              'EPIC'],
  ['024','Berserk',        4,'ATTACK',0,[cd(wB(5), ac(13))],   'RARE'],
  ['025','Protiútok',      3,'ATTACK',0,[cd(wB(10), ac(10))],  'RARE'],
  ['026','Ostřelovač',     3,'ATTACK',0,[ac(5), cd(rA('ATTACK',5), ac(5))], 'EPIC'],
  ['027','Válečné bubny',  2,'ATTACK',1,[ap(4), ar('ATTACK',2)],       'COMMON'],
  ['046','Goblin',         1,'ATTACK',0,[ap(2), sr('MAGIC',1), ar('CHAOS',1)], 'COMMON'],
  ['047','Ogr',            3,'ATTACK',0,[ap(9)],               'RARE'],
  ['048','Upír',           4,'ATTACK',1,[ap(6), ar('MAGIC',3)],        'RARE'],
  ['049','Jed',            3,'ATTACK',0,[ap(3), dr('MAGIC',3), ar('CHAOS',1)], 'RARE'],
  ['050','Kobylky',        4,'ATTACK',0,[ap(8), dr('STONES',4)],       'RARE'],
  ['051','Drak',          11,'ATTACK',0,[ap(14), ac(8), ar('CHAOS',2)],'LEGENDARY'],
  ['052','Démon',         14,'ATTACK',0,[ac(16), ar('CHAOS',2)],       'LEGENDARY'],
  ['053','Plamenomet',     3,'ATTACK',0,[aw(10), ar('ATTACK',2)],      'RARE'],
  ['054','Válečný pochod', 5,'ATTACK',0,[ap(13), ar('ATTACK',2)],      'EPIC'],
  ['055','Mravenci',       2,'ATTACK',0,[ap(3), ar('ATTACK',2), cd(wB(5), ac(8))], 'EPIC'],
  ['056','Nájezdník',      3,'ATTACK',0,[sr('ATTACK',3), ap(4)],       'RARE'],
  ['078','Upíří drak',     9,'ATTACK',0,[ac(10), sr('MAGIC',4), sr('ATTACK',4), ar('CHAOS',2)], 'LEGENDARY'],
  ['079','Obléhání',       5,'ATTACK',0,[ap(12), dr('MAGIC',3), ar('CHAOS',2)], 'EPIC'],
  ['098','Hod cihlou',     3,'ATTACK',0,[bw(-4), ap(11)],      'RARE'],
  ['100','Obléhací sání',  6,'ATTACK',0,[ap(5), sca(6)],       'RARE'],

  // ── Stavba ────────────────────────────────────────────────────────────────
  ['002','Kamenná zeď',    3,'STONES',0,[bw(9)],               'COMMON'],
  ['010','Palisáda',       2,'STONES',0,[bw(5), ar('STONES',1)],'COMMON'],
  ['005','Posila hradu',   2,'STONES',0,[bc(4)],               'COMMON'],
  ['009','Pevné základy',  4,'STONES',0,[bc(8)],               'COMMON'],
  ['018','Mohutná věž',    5,'STONES',0,[bw(15)],              'RARE'],
  ['028','Záplata',        1,'STONES',0,[bc(3)],               'COMMON'],
  ['029','Opevnění',       2,'STONES',1,[bw(6)],               'COMMON'],
  ['030','Kamenný val',    4,'STONES',0,[bw(14)],              'RARE'],
  ['031','Renovace',       3,'STONES',0,[bc(6)],               'COMMON'],
  ['032','Citadela',       6,'STONES',0,[bc(13)],              'EPIC'],
  ['033','Zemní val',      2,'STONES',0,[cd(wB(8), bw(12))],   'RARE'],
  ['034','Opravář',        3,'STONES',0,[cd(wA(15), bc(8))],   'RARE'],
  ['035','Základní kámen', 3,'STONES',0,[bw(5), bc(3)],        'COMMON'],
  ['036','Hradní příkop',  3,'STONES',0,[bw(7), cd(cA(35), bw(5))], 'EPIC'],
  ['057','Bašta',          3,'STONES',0,[bw(7), ar('STONES',1)],'RARE'],
  ['058','Obranný val',    0,'STONES',1,[bw(4)],               'EPIC'],
  ['059','Pevnostní hrad', 4,'STONES',0,[bw(4), bc(6)],        'RARE'],
  ['060','Chrám',         10,'STONES',0,[bc(18)],              'EPIC'],
  ['061','Tunely',         3,'STONES',0,[cd(cA(40), bw(12))],  'EPIC'],
  ['062','Obranná aliance',5,'STONES',0,[bw(7), bc(4), ar('STONES',2)], 'EPIC'],
  ['063','Věž strážní',    5,'STONES',0,[bw(14), am('STONES',1)],      'EPIC'],
  ['064','Zásobník',       3,'STONES',0,[cd(cA(40), bc(10))],  'EPIC'],
  ['081','Rychlá hradba',  2,'STONES',0,[bw(7)],               'COMMON'],
  ['082','Masivní zeď',    6,'STONES',0,[bw(16)],              'RARE'],
  ['083','Nouzové opevnění',3,'STONES',0,[cd(wB(10), bw(16))], 'RARE'],
  ['084','Velká oprava',   4,'STONES',0,[bw(3), bc(7)],        'COMMON'],
  ['085','Královská obnova',7,'STONES',0,[bc(15)],             'EPIC'],
  ['086','Zednická rota',  5,'STONES',0,[bw(8), bc(6)],        'RARE'],
  ['087','Pevnost',        6,'STONES',0,[bw(10), bc(8)],       'EPIC'],
  ['088','Zesílené hradby',4,'STONES',0,[bw(10), cd(cA(30), bw(6))], 'RARE'],
  ['089','Architekt',      4,'STONES',0,[bw(5), am('STONES',1)],'RARE'],
  ['090','Velkostavba',    7,'STONES',0,[am('STONES',2)],      'EPIC'],
  ['091','Barikády',       3,'STONES',0,[bw(9), dr('ATTACK',2)],'RARE'],
  ['092','Strategická výstavba',4,'STONES',0,[cd(wA(20), bw(15))], 'EPIC'],
  ['094','Sklad materiálu',3,'STONES',1,[bw(6), ar('STONES',2)],'COMMON'],
  ['095','Obchod s kamenem',2,'STONES',1,[ar('STONES',5)],     'COMMON'],
  ['096','Nedobytná pevnost',10,'STONES',0,[bw(25)],           'LEGENDARY'],
  ['097','Obnova království',13,'STONES',0,[bc(25)],           'LEGENDARY'],

  // ── Zdroje / Kouzla ───────────────────────────────────────────────────────
  ['004','Magie',          0,'MAGIC', 1,[ar('MAGIC',2)],       'COMMON'],
  ['011','Zásoby kamene',  1,'MAGIC', 1,[ar('STONES',4)],      'COMMON'],
  ['012','Mobilizace',     1,'MAGIC', 1,[ar('ATTACK',3)],      'COMMON'],
  ['037','Rychlá magie',   1,'MAGIC', 1,[ar('MAGIC',4)],       'COMMON'],
  ['038','Vojenský rozkaz',2,'MAGIC', 1,[ar('ATTACK',6)],      'RARE'],
  ['039','Stavební boom',  2,'MAGIC', 1,[ar('STONES',6)],      'RARE'],
  ['040','Alchymie',       2,'MAGIC', 0,[cd(rA('MAGIC',4), ar('ATTACK',8))], 'EPIC'],
  ['041','Magické trio',   2,'MAGIC', 1,[ar('MAGIC',2), ar('ATTACK',2), ar('STONES',2)], 'RARE'],
  ['074','Tržiště',        3,'MAGIC', 1,[ar('MAGIC',3), ar('ATTACK',3), ar('STONES',3)], 'COMMON'],
  ['077','Přeměna magie',  3,'MAGIC', 0,[cd(rA('MAGIC',8), ar('ATTACK',10))], 'RARE'],

  // ── Doly ──────────────────────────────────────────────────────────────────
  ['013','Magický pramen', 3,'MAGIC', 0,[am('MAGIC',1)],       'RARE'],
  ['014','Kamenolom',      3,'MAGIC', 0,[am('STONES',1)],      'RARE'],
  ['015','Výcvikový tábor',3,'MAGIC', 0,[am('ATTACK',1)],      'RARE'],
  ['016','Velký pramen',   5,'MAGIC', 0,[am('MAGIC',2)],       'EPIC'],
  ['042','Velký kamenolom',4,'MAGIC', 0,[am('STONES',2)],      'EPIC'],
  ['043','Výcvikové centrum',4,'MAGIC',0,[am('ATTACK',2)],     'EPIC'],
  ['044','Trifekta dolů',  6,'MAGIC', 0,[am('MAGIC',1), am('ATTACK',1), am('STONES',1)], 'LEGENDARY'],
  ['045','Očarované doly', 7,'MAGIC', 0,[am('MAGIC',3)],       'LEGENDARY'],
  ['072','Zbrojnice',      4,'MAGIC', 0,[ar('ATTACK',2), am('ATTACK',1)], 'EPIC'],
  ['073','Škola magie',    4,'MAGIC', 0,[ar('MAGIC',2), am('MAGIC',1)],   'EPIC'],
  ['075','Zlaté doly',     6,'MAGIC', 0,[am('MAGIC',2), am('STONES',1)],  'EPIC'],
  ['076','Vojenská základna',6,'MAGIC',0,[am('ATTACK',2), am('STONES',1)],'EPIC'],
  ['080','Velkovýroba',    9,'MAGIC', 0,[am('MAGIC',2), am('ATTACK',2), am('STONES',2), ar('MAGIC',3)], 'LEGENDARY'],

  // ── Sabotáž ───────────────────────────────────────────────────────────────
  ['065','Lupič',          2,'MAGIC', 0,[sr('ATTACK',3)],      'COMMON'],
  ['066','Zlatokop',       2,'MAGIC', 0,[sr('STONES',4)],      'COMMON'],
  ['067','Sabotér',        2,'MAGIC', 0,[dr('STONES',5)],      'RARE'],
  ['068','Demoralizace',   2,'MAGIC', 0,[dr('ATTACK',5)],      'RARE'],
  ['069','Dvojitý agent',  4,'MAGIC', 0,[sr('MAGIC',3), sr('ATTACK',3), ar('CHAOS',1)], 'EPIC'],
  ['070','Krize zásobování',4,'MAGIC',0,[dr('STONES',5), dr('ATTACK',5)], 'EPIC'],
  ['071','Špión',          3,'MAGIC', 0,[sr('MAGIC',2), sr('ATTACK',2), sr('STONES',2), ar('CHAOS',2)], 'EPIC'],
  ['099','Vampirismus hradu',8,'MAGIC',0,[sca(10)],            'EPIC'],

  // ── Líz karet ─────────────────────────────────────────────────────────────
  ['D01','Průzkumník',     1,'MAGIC', 1,[dc(1)],               'COMMON'],
  ['D02','Věštba',         3,'MAGIC', 0,[dc(2)],               'RARE'],
  ['D03','Kronika',        5,'MAGIC', 0,[dc(3)],               'EPIC'],
  ['D04','Bojová taktika', 2,'ATTACK',0,[ap(4), dc(1)],        'COMMON'],
  ['D05','Stavební plány', 3,'STONES',1,[bw(4), dc(1)],        'COMMON'],
  ['D06','Elitní zvěd',    4,'ATTACK',0,[ap(8), dc(1)],        'RARE'],
  ['D07','Tajná knihovna', 5,'MAGIC', 0,[dc(2), am('MAGIC',1)],'EPIC'],
  ['D08','Vize',           2,'MAGIC', 0,[cd(rA('MAGIC',4), dc(2))], 'RARE'],

  // ── Chaos generátory (platí MAGIC) ───────────────────────────────────────
  ['C01','Chaotická jiskra',0,'MAGIC',1,[ar('CHAOS',2)],       'RARE'],
  ['C02','Entropie',        3,'MAGIC',0,[ar('CHAOS',5), dr('MAGIC',2)], 'EPIC'],
  ['C03','Chaotický důl',   4,'MAGIC',0,[am('CHAOS',1)],       'LEGENDARY'],
  ['C04','Krádež chaosu',   2,'MAGIC',1,[sr('CHAOS',3)],       'RARE'],
  ['C24','Temný rituál',    2,'MAGIC',1,[ar('CHAOS',5)],       'RARE'],
  ['C25','Nestabilní vír',  1,'MAGIC',1,[ar('CHAOS',2), ar('MAGIC',2)], 'COMMON'],
  ['C26','Krvavá oběť',     1,'MAGIC',1,[ar('CHAOS',4)],       'RARE'],
  ['C27','Odraz magie',     2,'MAGIC',0,[cd(rA('MAGIC',5), ar('CHAOS',7))], 'EPIC'],
  ['C28','Chaotická trofej',1,'MAGIC',1,[sr('ATTACK',2), ar('CHAOS',3)], 'COMMON'],
  ['C29','Bouřlivá mysl',   5,'MAGIC',0,[ar('CHAOS',3), am('CHAOS',1)], 'EPIC'],
  ['C30','Chrám chaosu',    7,'MAGIC',0,[am('CHAOS',2)],       'LEGENDARY'],
  ['C31','Chaotický výměník',4,'MAGIC',0,[ar('CHAOS',4), dr('MAGIC',2), dr('ATTACK',2), dr('STONES',2)], 'EPIC'],

  // ── Chaos karty (platí CHAOS) ─────────────────────────────────────────────
  ['C05','Chaotický výbuch',7,'CHAOS',0,[ac(15)],              'EPIC'],
  ['C06','Bouře chaosu',    6,'CHAOS',0,[ap(20)],              'LEGENDARY'],
  ['C07','Chaotický štít',  4,'CHAOS',0,[bw(20)],              'EPIC'],
  ['C08','Zázrak chaosu',   5,'CHAOS',0,[bc(15)],              'LEGENDARY'],
  ['C09','Chaotická krize', 6,'CHAOS',0,[dr('MAGIC',6), dr('ATTACK',6), dr('STONES',6)], 'LEGENDARY'],
  ['C10','Chaotický drak', 11,'CHAOS',0,[ap(15), ac(12)],      'LEGENDARY'],
  ['C11','Chaos a řád',     4,'CHAOS',0,[bc(8), bw(8)],        'EPIC'],
  ['C12','Anarchie',        8,'CHAOS',0,[sr('MAGIC',5), sr('ATTACK',5), sr('STONES',5)], 'LEGENDARY'],
  ['C32','Vzájemná zkáza',  3,'CHAOS',0,[ac(10), bc(-10)],     'EPIC'],

  // ── Chaos ničení dolů ─────────────────────────────────────────────────────
  ['C13','Sabotáž',         5,'CHAOS',0,[dm('MAGIC',1),  bm('MAGIC',2)],                           'EPIC'],
  ['C14','Ničení kamenolomu',5,'CHAOS',0,[dm('STONES',1), bm('STONES',2)],                          'EPIC'],
  ['C15','Zákeřnost',       5,'CHAOS',0,[dm('ATTACK',1), bm('ATTACK',2)],                           'EPIC'],
  ['C16','Velká sabotáž',   7,'CHAOS',0,[dm('MAGIC',1),  bm('MAGIC',3), dm('STONES',1), bm('STONES',3)], 'LEGENDARY'],

  // ── Chaos krádež/ničení karet ─────────────────────────────────────────────
  ['C17','Telekineze',      3,'CHAOS',0,[sc(1)],               'EPIC'],
  ['C18','Chaos loupe',     5,'CHAOS',0,[sc(2)],               'LEGENDARY'],
  ['C19','Spálená knihovna',4,'CHAOS',0,[bn(2)],               'EPIC'],
  ['C20','Prázdná mysl',    6,'CHAOS',0,[bn(3)],               'LEGENDARY'],

  // ── Chaos přidání karet do balíčku ───────────────────────────────────────
  ['C21','Replikace',       1,'CHAOS',0,[ad('008',3)],         'RARE'],
  ['C22','Chaos manufaktura',2,'CHAOS',0,[ad('C05',2)],        'EPIC'],
  ['C23','Klonování',       1,'CHAOS',0,[ad('001',2)],         'RARE'],

  // ── Testovací ─────────────────────────────────────────────────────────────
  ['T01','Goblin šaman',    3,'MAGIC', 0,[ar('MAGIC',5)],      'RARE'],
];

// ── Sestavení mapy ────────────────────────────────────────────────────────────
const ALL_CARDS = RAW.map(([id, name, cost, costType, isCombo, effects, rarity]) => ({
  id, name, cost, costType, isCombo: !!isCombo, effects,
  rarity: rarity || 'COMMON',
  maxCopies: MAX_COPIES[rarity || 'COMMON'] || 4,
  baseId: id    // pro instance je baseId = id šablony
}));

const CARD_MAP = new Map(ALL_CARDS.map(c => [c.id, c]));

/** Náhodný balíček 30 karet (stejný algoritmus jako Kotlin: pool×2, shuffle, take 30) */
function randomDeck(extraPool = []) {
  const pool = [...ALL_CARDS, ...ALL_CARDS];
  for (let i = pool.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [pool[i], pool[j]] = [pool[j], pool[i]];
  }
  return pool.slice(0, 30).map(c => makeInstance(c));
}

/** Validuj a postav balíček z předaných base ID. Při chybě vrátí náhodný. */
function buildDeckFromIds(baseIds) {
  if (!Array.isArray(baseIds) || baseIds.length !== 30) return randomDeck();
  const counts = {};
  const cards = [];
  for (const id of baseIds) {
    const tmpl = CARD_MAP.get(id);
    if (!tmpl) return randomDeck();
    counts[id] = (counts[id] || 0) + 1;
    if (counts[id] > tmpl.maxCopies) return randomDeck();
    cards.push(makeInstance(tmpl));
  }
  shuffle(cards);
  return cards;
}

let _instanceCounter = 0;
function makeInstance(tmpl) {
  return { ...tmpl, id: `${tmpl.id}_${++_instanceCounter}`, baseId: tmpl.id };
}

function shuffle(arr) {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}

module.exports = { ALL_CARDS, CARD_MAP, randomDeck, buildDeckFromIds, makeInstance, shuffle, MAX_COPIES };
