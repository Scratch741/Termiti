'use strict';
/**
 * Databáze karet – portováno z Kotlin allCards v Gameviewmodel.kt
 * Formát řádku: [id, name, cost, costType, isCombo, effects[], rarity]
 */

// ── Effect helpers ────────────────────────────────────────────────────────────
const ar = (t,n)    => ({ type:'AddResource',       resType:t, amount:n });
const am = (t,n=1)  => ({ type:'AddMine',           resType:t, amount:n });
const bw = (n)      => ({ type:'BuildWall',          amount:n });
const bc = (n)      => ({ type:'BuildCastle',        amount:n });
const ap = (n)      => ({ type:'AttackPlayer',       amount:n });
const aw = (n)      => ({ type:'AttackWall',         amount:n });
const ac = (n)      => ({ type:'AttackCastle',       amount:n });
const sr = (t,n)    => ({ type:'StealResource',      resType:t, amount:n });
const dr = (t,n)    => ({ type:'DrainResource',      resType:t, amount:n });
const cd = (c,e)    => ({ type:'ConditionalEffect',  condition:c, effect:e });
const dm = (t,n=1)  => ({ type:'DestroyMine',        resType:t, amount:n });
const bm = (t,turns)=> ({ type:'BlockMine',          resType:t, turns });
const sc = (n=1)    => ({ type:'StealCard',          count:n });
const bn = (n=1)    => ({ type:'BurnCard',           count:n });
const ad = (id,n)   => ({ type:'AddCardsToDeck',     cardId:id, count:n });
const aod= (id,n)   => ({ type:'AddToOpponentDeck',  cardId:id, count:n });
const tod= (e)      => ({ type:'TrapOnDraw',          effect:e });
const dc = (n=1)    => ({ type:'DrawCard',           count:n });
const dbb= (n=1)    => ({ type:'DrawBoth',           count:n });
const cnp= (n=2)    => ({ type:'CloneNextPlayed',    count:n });
const sca= (n)      => ({ type:'StealCastle',        amount:n });
// X-kost efekty
const xap= (d=2)    => ({ type:'XScaledAttackPlayer', divisor:d });
const xac= (d=2)    => ({ type:'XScaledAttackCastle', divisor:d });
const xbc= (d=2)    => ({ type:'XScaledBuildCastle',  divisor:d });
const xdr= (tA,tB,d=2) => ({ type:'XScaledDualResource', typeA:tA, typeB:tB, divisor:d });
const swh= ()       => ({ type:'SwapHands' });
const rnh= ()       => ({ type:'RandomizeHands' });
const grc= (ct)     => ({ type:'GiveRandomCard', costType:ct });
const mhc= (d,opp=false) => ({ type:'ModifyHandCost', delta:d, ...(opp ? {targetOpponent:true} : {}) });
const dpc= (ct=null)=> ({ type:'DrawPerCardPlayed', ...(ct ? {cardType:ct} : {}) });
const grp= (t,n,ct=null) => ({ type:'GainResourcePerCardPlayed', resType:t, amount:n, ...(ct ? {cardType:ct} : {}) });
const gcp= (n,ct=null)   => ({ type:'GainCastlePerCardPlayed',   amount:n,             ...(ct ? {cardType:ct} : {}) });
const ss = ()            => ({ type:'ShapeShift' });
const cvM= (from,to)     => ({ type:'ConvertMine', from, to });
// Rozhodnutí
const dbo= (n=4)         => ({ type:'DecisionBurnOpponent',  picks:n });
const dct= (ct,n=4,cr=0) => ({ type:'DecisionChooseType',    cardType:ct, picks:n, ...(cr ? {costReduction:cr} : {}) });
const dfd= (n=4)         => ({ type:'DecisionFromDiscard',   picks:n });
const dfk= (n=4)         => ({ type:'DecisionFromDeck',      picks:n });
const dfkd=(n=4)         => ({ type:'DecisionDrawFromDeck',  picks:n });
const dmine= ()          => ({ type:'DecisionMine' });
const sj   = ()          => ({ type:'SmartJoker' });
const mma  = (b,bon)     => ({ type:'MomentumAttack', base:b, bonusPerAttack:bon });
const psh  = ()          => ({ type:'PeekAndStealHand' });
const dcr  = (opts)      => ({ type:'DecisionChooseResource', options:opts });

// Conditions
const rA  = (t,v) => ({ type:'ResourceAbove',           resType:t, threshold:v });
const rMO = (t)   => ({ type:'ResourceMoreThanOpponent', resType:t });
const wA  = (v)   => ({ type:'WallAbove',     threshold:v });
const wB  = (v)   => ({ type:'WallBelow',     threshold:v });
const cA  = (v)   => ({ type:'CastleAbove',   threshold:v });
const cB  = (v)   => ({ type:'CastleBelow',   threshold:v });

// ── Rarity max copies ─────────────────────────────────────────────────────────
const MAX_COPIES = { COMMON:3, RARE:2, EPIC:2, LEGENDARY:1 };

// ── Raw card data ─────────────────────────────────────────────────────────────
// [id, name, cost, costType, isCombo, effects, rarity]
const RAW = [
  // ── Útok (platí ATTACK) ──────────────────────────────────────────────────
  ['001','Rychlý útok',    2,'ATTACK',1,[ap(6)],               'COMMON'],
  ['008','Šípy',           1,'ATTACK',1,[ac(3)],               'COMMON'],
  ['003','Ohnivá koule',   3,'MAGIC', 0,[ac(8)],               'RARE'],
  ['007','Silný úder',     4,'ATTACK',0,[ap(11)],              'COMMON'],
  ['006','Převaha síly',   3,'ATTACK',0,[cd(rMO('ATTACK'), ac(10))],   'RARE'],
  ['017','Válečný sekyrník',4,'ATTACK',0,[ap(8),sr('ATTACK',2)],       'COMMON'],

  // ── Stavba (platí STONES) ────────────────────────────────────────────────
  ['002','Kamenná zeď',    3,'STONES',0,[bw(9), ar('STONES',1)],'COMMON'],
  ['010','Palisáda',       2,'STONES',0,[bw(6), ar('STONES',1)],'COMMON'],
  ['005','Posila hradu',   2,'STONES',0,[bc(5)],               'COMMON'],
  ['009','Pevné základy',  4,'STONES',0,[bc(8), ar('STONES',1)],'COMMON'],
  ['018','Mohutná věž',    5,'STONES',0,[bw(15)],              'RARE'],

  // ── Zdroje okamžité (platí MAGIC) ────────────────────────────────────────
  ['004','Magie',          0,'MAGIC', 1,[ar('MAGIC',2)],       'RARE'],
  ['011','Zásoby kamene',  1,'MAGIC', 1,[ar('STONES',3)],      'RARE'],
  ['012','Mobilizace',     1,'MAGIC', 1,[ar('ATTACK',3)],      'RARE'],

  // ── Doly (platí MAGIC) ───────────────────────────────────────────────────
  ['013','Magický pramen', 3,'MAGIC', 0,[am('MAGIC',1)],       'RARE'],
  ['014','Kamenolom',      3,'MAGIC', 0,[am('STONES',1)],      'RARE'],
  ['015','Výcvikový tábor',3,'MAGIC', 0,[am('ATTACK',1)],      'RARE'],
  ['016','Velký pramen',   5,'MAGIC', 0,[am('MAGIC',2)],       'EPIC'],

  // ── Útok – rozšíření ─────────────────────────────────────────────────────
  ['019','Zápalné šípy',   1,'ATTACK',1,[aw(5)],               'COMMON'],
  ['020','Beranidlo',      3,'ATTACK',1,[aw(12)],              'RARE'],
  ['021','Dělostřelectvo', 6,'ATTACK',0,[ap(15)],              'EPIC'],
  ['022','Přímý zásah',    3,'ATTACK',0,[ac(8)],               'RARE'],
  ['023','Dvojitý úder',   5,'ATTACK',0,[ac(7), ap(7)],        'EPIC'],
  ['024','Berserk',        4,'ATTACK',0,[cd(wB(5), ac(12))],   'RARE'],
  ['025','Protiútok',      3,'ATTACK',0,[cd(wB(10), ac(10))],  'RARE'],
  ['026','Ostřelovač',     3,'ATTACK',0,[ac(5), cd(rA('ATTACK',5), ac(5))], 'EPIC'],
  ['027','Válečné bubny',  2,'ATTACK',1,[ap(4), grp('ATTACK',2,'Útok')], 'RARE'],

  // ── Stavba – rozšíření ───────────────────────────────────────────────────
  ['028','Záplata',        1,'STONES',1,[bc(3)],               'COMMON'],
  ['029','Opevnění',       2,'STONES',0,[bw(6),bc(2)],          'COMMON'],
  ['030','Kamenný val',    4,'STONES',0,[bw(13)],              'RARE'],
  ['031','Renovace',       3,'STONES',0,[bc(7)],               'COMMON'],
  ['032','Citadela',       6,'STONES',0,[bc(13)],              'EPIC'],
  ['033','Zemní val',      2,'STONES',0,[cd(wB(8), bw(11))],   'RARE'],
  ['034','Opravář',        3,'STONES',0,[cd(wA(15), bc(9))],   'RARE'],
  ['035','Základní kámen', 3,'STONES',0,[bw(5), bc(4)],        'COMMON'],
  ['036','Hradní příkop',  3,'STONES',0,[bw(7), cd(cB(35), bw(5))], 'EPIC'],

  // ── Zdroje – rozšíření ───────────────────────────────────────────────────
  ['037','Rychlá magie',   1,'MAGIC', 1,[ar('MAGIC',4)],       'RARE'],
  ['038','Vojenský rozkaz',3,'MAGIC', 1,[ar('ATTACK',6)],      'RARE'],
  ['039','Stavební boom',  3,'MAGIC', 1,[ar('STONES',6)],      'RARE'],
  ['040','Alchymie',       2,'MAGIC', 0,[cd(rA('MAGIC',4), ar('ATTACK',8))], 'RARE'],
  ['041','Magické trio',   3,'MAGIC', 1,[ar('MAGIC',2), ar('ATTACK',2), ar('STONES',2)], 'RARE'],

  // ── Doly – rozšíření ──────────────────────────────────────────────────────
  ['042','Velký kamenolom',5,'MAGIC', 0,[am('STONES',2)],      'EPIC'],
  ['043','Výcvikové centrum',5,'MAGIC',0,[am('ATTACK',2)],     'EPIC'],
  ['044','Trifekta dolů',  6,'MAGIC', 0,[am('MAGIC',1), am('ATTACK',1), am('STONES',1)], 'EPIC'],
  ['045','Očarované doly', 7,'MAGIC', 0,[am('MAGIC',3)],       'LEGENDARY'],

  // ── Útok – Arcomage / Mravenci inspirace ──────────────────────────────────
  ['046','Goblin',         1,'ATTACK',0,[ap(2), sr('MAGIC',1), ar('CHAOS',1)], 'RARE'],
  ['047','Ogr',            3,'ATTACK',0,[ap(9)],               'COMMON'],
  ['048','Upír',           4,'ATTACK',1,[ap(6), ar('MAGIC',3)],        'RARE'],
  ['049','Jed',            3,'ATTACK',0,[ap(3), dr('MAGIC',3), ar('CHAOS',2)], 'RARE'],
  ['050','Podkopání hradeb',4,'ATTACK',0,[ap(8), dr('STONES',4)],       'RARE'],
  ['051','Drak',          11,'ATTACK',0,[ap(14), ac(8), ar('CHAOS',4)],'LEGENDARY'],
  ['052','Démon',         14,'ATTACK',0,[ac(16), ar('CHAOS',4)],       'LEGENDARY'],
  ['053','Plamenomet',     3,'ATTACK',0,[aw(10), ar('ATTACK',2)],      'RARE'],
  ['054','Válečný pochod', 5,'ATTACK',0,[ap(13), ar('ATTACK',2)],      'EPIC'],
  ['055','Poslední vzdor', 2,'ATTACK',0,[ap(3), ar('ATTACK',1), cd(wB(5), ac(8))], 'EPIC'],
  ['056','Nájezdník',      3,'ATTACK',0,[sr('ATTACK',3), ap(4)],       'RARE'],

  // ── Stavba – Arcomage / Mravenci inspirace ────────────────────────────────
  ['057','Bašta',          3,'STONES',0,[bw(3), bc(5)],         'RARE'],
  ['058','Obranný val',    0,'STONES',1,[bw(4)],               'EPIC'],
  ['059','Pevnostní hrad', 4,'STONES',0,[bw(4), bc(6), dr('ATTACK',1)], 'RARE'],
  ['060','Chrám',         10,'STONES',0,[bc(18)],              'EPIC'],
  ['061','Tunely',         2,'STONES',0,[bc(4), bm('ATTACK',1)], 'EPIC'],
  ['062','Obranná aliance',5,'STONES',0,[bw(6), bc(5), ar('STONES',2)], 'EPIC'],
  ['063','Věž strážní',    5,'STONES',0,[bw(11), am('STONES',1)],      'RARE'],
  ['064','Zásobník',       3,'STONES',0,[cd(cB(40), bc(10))],  'EPIC'],

  // ── Sabotáž a krádež (platí MAGIC) ────────────────────────────────────────
  ['065','Lupič',          2,'MAGIC', 0,[sr('ATTACK',4)],      'COMMON'],
  ['066','Kamenná daň',    2,'MAGIC', 0,[sr('STONES',4)],      'COMMON'],
  ['067','Sabotér',        2,'MAGIC', 0,[dr('STONES',5)],      'RARE'],
  ['068','Demoralizace',   2,'MAGIC', 0,[dr('ATTACK',5)],      'RARE'],
  ['069','Dvojitý agent',  4,'MAGIC', 0,[sr('MAGIC',3), sr('ATTACK',3), ar('CHAOS',2)], 'EPIC'],
  ['070','Krize zásobování',4,'MAGIC',0,[dr('STONES',5), dr('ATTACK',5)], 'EPIC'],
  ['071','Špión',          3,'MAGIC', 0,[sr('MAGIC',1), sr('ATTACK',1), sr('STONES',1), sr('CHAOS',1)], 'EPIC'],

  // ── Zdroje + doly – Arcomage / Mravenci inspirace ──────────────────────────
  ['072','Zbrojnice',      4,'MAGIC', 0,[ar('ATTACK',2), am('ATTACK',1)], 'EPIC'],
  ['073','Škola magie',    4,'MAGIC', 0,[ar('MAGIC',2), am('MAGIC',1)],   'EPIC'],
  ['074','Tržiště',        4,'MAGIC', 1,[ar('MAGIC',3), ar('ATTACK',3), ar('STONES',3)], 'RARE'],
  ['075','Rozmach těžby',  6,'MAGIC', 0,[am('MAGIC',2), am('STONES',1)],  'EPIC'],
  ['076','Vojenská základna',6,'MAGIC',0,[am('ATTACK',2), am('STONES',1)],'EPIC'],
  ['077','Přeměna magie',  3,'MAGIC', 0,[cd(rA('MAGIC',8), ar('ATTACK',10))], 'RARE'],
  ['078','Upíří drak',    10,'ATTACK',0,[ac(10), sr('MAGIC',4), sr('ATTACK',4), ar('CHAOS',4)], 'LEGENDARY'],
  ['079','Obléhání',       5,'ATTACK',0,[ap(12), dr('MAGIC',3), ar('CHAOS',2)], 'EPIC'],
  ['080','Velkovýroba',   10,'MAGIC', 0,[am('MAGIC',2), am('ATTACK',2), am('STONES',2)], 'LEGENDARY'],

  // ── Stavba – nové posily (STONES buff) ─────────────────────────────────────
  ['129','Příprava',       1,'STONES',0,[bw(2),bc(2),{ type:'DiscountRandomCard', delta:2, cardType:'Stavba', count:1 }], 'EPIC'],
  ['081','Rychlá hradba',  2,'STONES',1,[bw(6)],               'RARE'],
  ['082','Masivní zeď',    6,'STONES',0,[bw(18)],              'RARE'],
  ['083','Nouzové opevnění',3,'STONES',0,[cd(wB(10), bw(14))], 'RARE'],
  ['084','Velká oprava',   4,'STONES',0,[bw(5), bc(7)],        'COMMON'],
  ['085','Královská obnova',7,'STONES',0,[bc(15)],             'EPIC'],
  ['086','Zednická rota',  5,'STONES',0,[bw(8), bc(6)],        'RARE'],
  ['087','Pevnost',        6,'STONES',0,[bw(10), bc(8)],       'EPIC'],
  ['088','Zesílené hradby',4,'STONES',0,[bw(10), cd(cA(30), bw(6))], 'RARE'],
  ['089','Architekt',      4,'STONES',0,[bw(5), am('STONES',1)],'RARE'],
  ['090','Rozšíření těžby', 7,'STONES',0,[am('STONES',2)],      'EPIC'],
  ['091','Barikády',       3,'STONES',0,[bw(9), dr('ATTACK',2)],'RARE'],
  ['092','Strategická výstavba',2,'STONES',0,[bw(5), dfkd()], 'EPIC'],
  ['093','Cihla na cihlu', 3,'STONES',1,[bc(5), gcp(3,'Stavba')],'EPIC'],
  ['094','Sklad materiálu',3,'STONES',1,[bw(6), ar('STONES',2)],'RARE'],
  ['095','Obchod s kamenem',2,'STONES',0,[ar('STONES',5)],     'COMMON'],
  ['096','Nedobytná pevnost',10,'STONES',0,[bw(18), bc(8)],    'LEGENDARY'],
  ['097','Obnova království',14,'STONES',0,[bc(25)],           'LEGENDARY'],

  // ── Chaos (platí CHAOS) ──────────────────────────────────────────────────
  ['C01','Chaotická jiskra',0,'CHAOS',1,[ar('CHAOS',2)],       'RARE'],
  ['C02','Entropie',        3,'MAGIC',0,[ar('CHAOS',5), dr('MAGIC',2)], 'EPIC'],
  ['C03','Chaotický důl',   4,'MAGIC',0,[am('CHAOS',1)],       'LEGENDARY'],
  ['C04','Krádež chaosu',   1,'MAGIC',1,[sr('CHAOS',3)],       'RARE'],

  // Karty platící Chaosem – silné efekty
  ['C05','Chaotický výbuch',7,'CHAOS',0,[ac(14)],              'EPIC'],
  ['C06','Bouře chaosu',    6,'CHAOS',0,[ap(20)],              'LEGENDARY'],
  ['C07','Chaotický štít',  4,'CHAOS',0,[bw(16)],              'EPIC'],
  ['C08','Zázrak chaosu',   5,'CHAOS',0,[bc(15)],              'LEGENDARY'],
  ['C09','Chaotická krize', 7,'CHAOS',0,[dr('MAGIC',6), dr('ATTACK',6), dr('STONES',6), dr('CHAOS',6)], 'LEGENDARY'],
  ['C10','Chaotický drak', 11,'CHAOS',0,[ap(15), ac(12)],      'LEGENDARY'],
  ['C11','Chaos a řád',     4,'CHAOS',0,[bc(8), bw(8)],        'EPIC'],
  ['C12','Anarchie',        9,'CHAOS',0,[sr('MAGIC',5), sr('ATTACK',5), sr('STONES',5), sr('CHAOS',5)], 'LEGENDARY'],

  // ── Chaos – ničení dolů ───────────────────────────────────────────────────
  ['C13','Sabotáž',         5,'CHAOS',0,[dm('MAGIC',1),  bm('MAGIC',2)],                           'EPIC'],
  ['C14','Ničení kamenolomu',5,'CHAOS',0,[dm('STONES',1), bm('STONES',2)],                          'EPIC'],
  ['C15','Zákeřnost',       5,'CHAOS',0,[dm('ATTACK',1), bm('ATTACK',2)],                           'EPIC'],
  ['C16','Velká sabotáž',   7,'CHAOS',0,[dm('MAGIC',1),  bm('MAGIC',3), dm('STONES',1), bm('STONES',3)], 'LEGENDARY'],

  // ── Chaos – krádež karet ──────────────────────────────────────────────────
  ['C17','Telekineze',      3,'CHAOS',0,[sc(1)],               'EPIC'],
  ['C18','Krádež osudu',    5,'CHAOS',0,[sc(2)],               'LEGENDARY'],
  ['C33','Krádež identity', 8,'CHAOS',0,[swh()],               'LEGENDARY'],
  ['C34','Shapeshifter',    0,'CHAOS',0,[ss()],                'EPIC'],
  ['C35','Chaotická přeměna',4,'CHAOS',0,[cvM('MAGIC','CHAOS')], 'EPIC'],
  ['C36','Skrytá bomba',    4,'CHAOS',0,[aod('C37',3)],          'RARE'],
  ['C37','Bomba',           0,'CHAOS',0,[tod(ac(5))],             'COMMON', false, 0, true], // placeholder – vkládána pouze efektem C36
  ['C38','Explodovaná bomba',0,'CHAOS',0,[],                      'COMMON', false, 0, true], // placeholder po výbuchu, isPlaceholder:true
  ['C39','Velký zmatek',    7,'CHAOS',0,[rnh()],                  'LEGENDARY'],

  // ── Chaos – ničení karet ──────────────────────────────────────────────────
  ['C19','Spálená knihovna',4,'CHAOS',0,[bn(2)],               'EPIC'],
  ['C20','Prázdná mysl',    7,'CHAOS',0,[bn(3)],               'LEGENDARY'],

  // ── Chaos – přidání karet do balíčku ──────────────────────────────────────
  ['C21','Replikace',       1,'CHAOS',0,[ad('008',3)],         'RARE'],
  ['C22','Chaos manufaktura',2,'CHAOS',0,[ad('C05',2)],        'EPIC'],
  ['C23','Klonování',       1,'CHAOS',0,[ad('001',2)],         'RARE'],

  // ── Chaos – nové generátory ───────────────────────────────────────────────
  ['C24','Temný rituál',    2,'MAGIC', 1,[ar('CHAOS',5)],       'RARE'],
  ['C25','Nestabilní vír',  1,'MAGIC', 1,[ar('CHAOS',2), ar('MAGIC',2)], 'RARE'],
  ['C26','Krvavá oběť',     1,'MAGIC', 1,[ar('CHAOS',3)],       'RARE'],
  ['C27','Odraz magie',     2,'MAGIC', 0,[cd(rA('MAGIC',5), ar('CHAOS',7))], 'EPIC'],
  ['C28','Chaotická trofej',1,'MAGIC', 1,[sr('ATTACK',2), ar('CHAOS',2)], 'RARE'],
  ['C29','Bouřlivá mysl',   5,'MAGIC', 0,[ar('CHAOS',3), am('CHAOS',1)], 'EPIC'],
  ['C30','Chrám chaosu',    7,'MAGIC', 0,[am('CHAOS',2)],       'LEGENDARY'],
  ['C31','Chaotický výměník',4,'MAGIC', 0,[ar('CHAOS',4), dr('MAGIC',2), dr('ATTACK',2), dr('STONES',2), dr('CHAOS',2)], 'EPIC'],
  ['C32','Vzájemná zkáza',  3,'CHAOS', 0,[ac(10), bc(-10)],     'EPIC'],

  // ── Lízni karet ───────────────────────────────────────────────────────────
  ['D01','Průzkumník',     2,'MAGIC', 1,[dc(1)],               'RARE'],
  ['D02','Věštba',         3,'MAGIC', 0,[dc(2)],               'RARE'],
  ['D03','Kronika',        5,'MAGIC', 0,[dc(3)],               'EPIC'],
  ['D04','Bojová taktika', 2,'ATTACK',0,[ap(4), dc(1)],        'COMMON'],
  ['D05','Stavební plány', 2,'STONES',1,[bw(4), dc(1)],        'RARE'],
  ['D06','Elitní zvěd',    4,'ATTACK',0,[ap(8), dc(1)],        'COMMON'],
  ['D07','Tajná knihovna', 5,'MAGIC', 0,[dc(2), am('MAGIC',1)],'EPIC'],
  ['D08','Vize',           2,'MAGIC', 0,[cd(rA('MAGIC',4), dc(2))], 'RARE'],
  ['D09','Inspirace',      3,'MAGIC', 1,[dpc('Magie')],              'EPIC'],

  // ── Speciální útočné karty ────────────────────────────────────────────────
  ['098','Hod cihlou',     3,'ATTACK',0,[bw(-4), ap(11)],      'RARE'],
  ['099','Temný přenos',   8,'MAGIC', 0,[sca(10)],             'EPIC'],
  ['100','Krvavý úder',    6,'ATTACK',0,[ap(5), sca(6)],       'RARE'],
  ['104','Válečný trénink',4,'ATTACK',0,[ap(5), am('ATTACK',1)],'EPIC'],
  ['107','Prokletí',       3,'MAGIC', 0,[ap(3), dr('ATTACK',3), dr('STONES',3), ar('CHAOS',2)], 'EPIC'],
  ['108','Likvidace',      4,'CHAOS', 0,[dbo()],               'EPIC'],
  ['109','Rekrut',         3,'ATTACK',1,[dct('Útok',4,2)],     'RARE'],
  ['110','Vzpomínka',      2,'MAGIC', 0,[dfd()],               'EPIC'],
  ['111','Intuice',        2,'MAGIC', 0,[dfk()],               'EPIC'],
  ['112','Stavitel',       3,'STONES',1,[dct('Stavba',4,2)],   'RARE'],

  ['113','Goblin šaman',    3,'MAGIC', 1,[dct('Magie',4,2)],   'RARE'],
  ['114','Chaotický mudrc', 3,'CHAOS', 1,[dct('Chaos',4,2)],   'RARE'],
  ['115','Studna vědomostí',2,'MAGIC', 1,[dbb(2)],             'EPIC'],
  ['116','Chaotická replikace',2,'CHAOS',1,[cnp(2)],           'EPIC'],
  ['117','Průzkum dolů',      2,'MAGIC',1,[dmine()],           'EPIC'],
  ['118','Magický žolík',    4,'MAGIC',1,[sj()],              'LEGENDARY'],
  ['119','Válečný pokřik',  4,'ATTACK',1,[ap(7),grc('ATTACK')],'EPIC'],
  ['120','Hromadná sleva',  2,'MAGIC', 0,[mhc(-1)],           'EPIC'],
  ['121','Kletba cen',      3,'CHAOS', 0,[mhc(1,true)],       'RARE'],
  ['122','Stavební posila', 4,'STONES',1,[bc(7),grc('STONES')],'EPIC'],
  ['123','Momentum',        3,'ATTACK',0,[mma(2,4)],           'EPIC'],
  ['C40','Zákeřný špeh',   4,'CHAOS', 0,[psh()],              'EPIC'],
  ['C41','Zrcadlo',        3,'MAGIC', 1,[{ type:'Mirror' }], 'EPIC'],
  ['C42','Klon',           2,'MAGIC', 1,[{ type:'Clone'  }], 'LEGENDARY'],

  // ── Nové karty (2026) ─────────────────────────────────────────────────────
  ['124','Alchymistova volba', 2,'MAGIC', 1,[dcr([{resType:'MAGIC',amount:4},{resType:'ATTACK',amount:4},{resType:'STONES',amount:4},{resType:'CHAOS',amount:4}])], 'RARE'],
  ['125','Válečný zápal',      2,'ATTACK',1,[ap(4),grp('CHAOS',2,'Útok')], 'RARE'],
  ['126','Magický proud',      2,'MAGIC', 1,[ar('CHAOS',2), grp('CHAOS',2,'Magie')], 'EPIC'],
  ['127','Pyroblast',          6,'MAGIC', 0,[ac(10),dr('MAGIC',2)],         'EPIC'],
  ['128','Archmág',            9,'MAGIC', 1,[dpc(),grp('MAGIC',1)],         'LEGENDARY'],

  // ── Temný mág – ikonická legenda ──────────────────────────────────────────
  ['L01','Temný mág',         13,'CHAOS', 0,[sr('MAGIC',3),sr('ATTACK',3),sr('STONES',3),ac(18),sca(5)], 'LEGENDARY'],

  // ── Nájezd ────────────────────────────────────────────────────────────────
  ['C43','Nájezd',             1,'ATTACK',1,[ap(2),{ type:'NextCardIsCombo' }], 'RARE'],
  ['130','Rabování',           0,'ATTACK',1,[ar('ATTACK',2), ar('STONES',-2)],  'RARE'],

  // ── Invaze ────────────────────────────────────────────────────────────────
  ['131','Invaze',             7,'ATTACK',0,[ap(8), dm('STONES',1)], 'EPIC'],

  // ── X-kost karty ──────────────────────────────────────────────────────────
  ['101','Náhlá smrt',      0,'ATTACK',0,[xac(2)],             'LEGENDARY', true],
  ['102','Kamenný příval',  0,'STONES',0,[xbc(2)],             'LEGENDARY', true],
  ['103','Magické rozdělení',0,'MAGIC',0,[xdr('ATTACK','STONES',2)], 'LEGENDARY', true],
];

// ── Sestavení mapy ────────────────────────────────────────────────────────────
const ALL_CARDS = RAW.map(([id, name, cost, costType, isCombo, effects, rarity, isXCost, maxCopiesOverride, isPlaceholder]) => ({
  id, name, cost, costType, isCombo: !!isCombo, effects,
  rarity: rarity || 'COMMON',
  maxCopies: maxCopiesOverride !== undefined ? maxCopiesOverride : (MAX_COPIES[rarity || 'COMMON'] || 4),
  isXCost: !!isXCost,
  isPlaceholder: !!isPlaceholder,
  baseId: id    // pro instance je baseId = id šablony
}));

const CARD_MAP = new Map(ALL_CARDS.map(c => [c.id, c]));

/** Pomocné: resType prvního AddMine efektu karty, nebo null */
function mineResType(card) {
  const fx = card.effects.find(f => f.type === 'AddMine');
  return fx ? fx.resType : null;
}

/** Pomocné: je karta generátorem Chaosu? */
function isChaosGen(card) {
  return card.effects.some(f =>
    (f.type === 'AddResource' && f.resType === 'CHAOS') ||
    (f.type === 'AddMine'     && f.resType === 'CHAOS')
  );
}

/**
 * Sdílené jádro generátoru balíčků.
 * Opravy oproti staré verzi:
 *  • uniqueLimit odstraněn → celý pool se prochází, žádná karta není předem vyřazena
 *  • váha cost-0 zvýšena 0.45 → 0.80 (bezplatné karty jsou herně silné)
 *  • rarityCaps: strop celkového počtu karet dané vzácnosti v balíčku
 *    (zabraňuje 8+ různým Legendary kartám v jednom balíčku)
 */
function buildDeckCore(quota, totalTarget, rarityCaps) {
  const cards  = ALL_CARDS.filter(c => !c.id.startsWith('T') && !c.isPlaceholder);
  const counts = {};

  const total      = () => Object.values(counts).reduce((s, n) => s + n, 0);
  const countByCT  = ct => cards.filter(c => c.costType === ct)
                               .reduce((s, c) => s + (counts[c.id] || 0), 0);
  const rarityTotal = r => cards.filter(c => c.rarity === r)
                               .reduce((s, c) => s + (counts[c.id] || 0), 0);
  const chaosCount = ()  => cards.filter(isChaosGen)
                               .reduce((s, c) => s + (counts[c.id] || 0), 0);

  function tryAdd(card) {
    if (total() >= totalTarget) return false;
    const cur = counts[card.id] || 0;
    if (cur >= card.maxCopies) return false;
    const cap = rarityCaps[card.rarity];
    if (cap !== undefined && rarityTotal(card.rarity) >= cap) return false;
    counts[card.id] = cur + 1;
    return true;
  }

  function weight(card) {
    if (card.cost <= 1)  return card.cost === 0 ? 0.80 : 0.85;  // dříve 0.45
    if (card.cost <= 4)  return 1.00;
    return 0.55;
  }

  function weightedShuffle(pool) {
    return pool
      .map(c => ({ c, w: Math.random() * weight(c) }))
      .sort((a, b) => b.w - a.w)
      .map(x => x.c);
  }

  // Krok 1: povinný 1 důl pro každý hlavní zdroj
  for (const res of ['MAGIC', 'ATTACK', 'STONES']) {
    const cands = weightedShuffle(cards.filter(c => mineResType(c) === res));
    for (const c of cands) {
      if (countByCT(c.costType) >= (quota[c.costType] || 0)) continue;
      if (tryAdd(c)) break;
    }
  }

  // Krok 2: alespoň 3 chaos generátory
  const cgCands = weightedShuffle(cards.filter(isChaosGen));
  for (const c of cgCands) {
    if (chaosCount() >= 3) break;
    if (countByCT(c.costType) >= (quota[c.costType] || 0)) continue;
    tryAdd(c);
  }

  // Krok 3: doplnit každý bucket na kvótu
  // Prochází celý pool opakovaně (max 4 průchody = COMMON.maxCopies).
  // tryAdd() zastaví přidávání jakmile karta dosáhne maxCopies nebo rarity-cap.
  for (const [ct, target] of Object.entries(quota)) {
    const pool = weightedShuffle(cards.filter(c => c.costType === ct));
    if (!pool.length) continue;
    for (let pass = 0; pass < 4; pass++) {
      if (countByCT(ct) >= target || total() >= totalTarget) break;
      for (const c of pool) {
        if (countByCT(ct) >= target || total() >= totalTarget) break;
        tryAdd(c);
      }
    }
  }

  // Krok 4: filler do cíle
  if (total() < totalTarget) {
    const filler = weightedShuffle(cards);
    for (let pass = 0; pass < 4; pass++) {
      if (total() >= totalTarget) break;
      for (const c of filler) {
        if (total() >= totalTarget) break;
        tryAdd(c);
      }
    }
  }

  // Rozložit counts → instance pole
  const deck = [];
  for (const [id, n] of Object.entries(counts)) {
    const tmpl = CARD_MAP.get(id);
    if (tmpl) for (let i = 0; i < n; i++) deck.push(makeInstance(tmpl));
  }
  return shuffle(deck);
}

/**
 * Vyvážený náhodný balíček – 30 karet (9/9/9/3).
 * Rarity stropy: LEGENDARY ≤ 2, EPIC ≤ 6, RARE ≤ 12.
 */
function balancedDeck() {
  return buildDeckCore(
    { CHAOS: 3, MAGIC: 9, ATTACK: 9, STONES: 9 },
    30,
    { LEGENDARY: 2, EPIC: 6, RARE: 12 }
  );
}

/**
 * Super náhodný balíček – 50 karet (15/15/15/5).
 * Rarity stropy: LEGENDARY ≤ 4, EPIC ≤ 10, RARE ≤ 20.
 */
function superBalancedDeck() {
  return buildDeckCore(
    { CHAOS: 5, MAGIC: 15, ATTACK: 15, STONES: 15 },
    50,
    { LEGENDARY: 4, EPIC: 10, RARE: 20 }
  );
}

/** Původní čistě náhodný balíček (zachován pro zpětnou kompatibilitu) */
function randomDeck() {
  const pool = [...ALL_CARDS, ...ALL_CARDS];
  for (let i = pool.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [pool[i], pool[j]] = [pool[j], pool[i]];
  }
  return pool.slice(0, 30).map(c => makeInstance(c));
}

/** Validuj a postav balíček z předaných base ID. Při chybě vrátí náhodný. */
function buildDeckFromIds(baseIds) {
  if (!Array.isArray(baseIds) || baseIds.length !== 30) {
    console.warn(`[buildDeckFromIds] FAIL: not array or length=${Array.isArray(baseIds) ? baseIds.length : 'N/A'}, fallback na náhodný`);
    return randomDeck();
  }
  const counts = {};
  const cards = [];
  for (const id of baseIds) {
    const tmpl = CARD_MAP.get(id);
    if (!tmpl) {
      console.warn(`[buildDeckFromIds] FAIL: neznámé ID "${id}", fallback na náhodný`);
      return randomDeck();
    }
    counts[id] = (counts[id] || 0) + 1;
    if (counts[id] > tmpl.maxCopies) {
      console.warn(`[buildDeckFromIds] FAIL: ID "${id}" překročilo maxCopies=${tmpl.maxCopies} (count=${counts[id]}), fallback na náhodný`);
      return randomDeck();
    }
    cards.push(makeInstance(tmpl));
  }
  shuffle(cards);
  console.log(`[buildDeckFromIds] OK: sestaven balíček ${cards.length} karet`);
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

module.exports = { ALL_CARDS, CARD_MAP, randomDeck, balancedDeck, superBalancedDeck, buildDeckFromIds, makeInstance, shuffle, MAX_COPIES };
