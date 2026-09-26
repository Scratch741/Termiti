// Vloží do deckbuilder.html kompletní seznam karet (EMBEDDED_CARDS), aby nástroj
// fungoval hned po otevření – bez ručního načítání cards.json.
//
// Zdroj: server/game/cards.js (herní data) + app/src/main/assets/lang/cs.json
// (skutečné popisy ze hry). Blok mezi značkami <<EMBEDDED_CARDS>> se přepisuje celý.
//
// Spuštění z kořene repa:  node tools/regen_deckbuilder_cards.js
const fs = require('fs');
const crypto = require('crypto');

const ROOT = process.cwd();
const HTML = ROOT + '/app/src/main/java/com/example/termiti/deckbuilder.html';
const cards = require(ROOT + '/server/game/cards.js').ALL_CARDS;
const cs = JSON.parse(fs.readFileSync(ROOT + '/app/src/main/assets/lang/cs.json', 'utf8')).cards;

const out = cards.map(c => {
  const o = {
    id: c.id, name: (cs[c.id] && cs[c.id].name) || c.name,
    cost: c.cost, costType: c.costType, rarity: c.rarity,
    effects: c.effects,
  };
  if (c.isCombo) o.isCombo = true;
  if (c.isXCost) o.isXCost = true;
  if (c.isPlaceholder) o.isPlaceholder = true;
  if (c.discardEffects && c.discardEffects.length) o.discardEffects = c.discardEffects;
  if (cs[c.id] && cs[c.id].desc) o.desc = cs[c.id].desc;
  return o;
});

const json = JSON.stringify(out);
const version = crypto.createHash('sha1').update(json).digest('hex').slice(0, 10);

const START = '// <<EMBEDDED_CARDS>>';
const END = '// <</EMBEDDED_CARDS>>';
const block = START + '\n' +
  '// GENEROVÁNO z server/game/cards.js + lang/cs.json – needituj ručně.\n' +
  '// Regenerace: node tools/regen_deckbuilder_cards.js\n' +
  'const EMBEDDED_CARDS_VERSION = ' + JSON.stringify(version) + ';\n' +
  'const EMBEDDED_CARDS = ' + json + ';\n' +
  END;

let html = fs.readFileSync(HTML, 'utf8');
const i = html.indexOf(START), j = html.indexOf(END);
if (i === -1 || j === -1) {
  // první vložení: těsně před inicializaci stránky
  const anchor = 'async function init() {';
  if (html.split(anchor).length !== 2) throw new Error('kotva pro první vložení nenalezena jednoznačně');
  html = html.replace(anchor, block + '\n\n' + anchor);
} else {
  html = html.slice(0, i) + block + html.slice(j + END.length);
}
// ── ART_MAP: karta -> obrázek, ze CardPresentation.kt ──────────────────────
// Bez záznamu stránka zkouší art_<id>.png, který neexistuje – nové karty pak
// v deckbuilderu neměly obrázek, i když ve hře ho mají.
const kt = fs.readFileSync(ROOT + '/app/src/main/java/com/example/termiti/CardPresentation.kt', 'utf8');
const art = {};
const re = /"([0-9A-Z]{3})" to CardPres\(([\s\S]{0,400}?)R\.drawable\.(\w+)/g;
let m;
while ((m = re.exec(kt))) art[m[1]] = m[3];

const a = html.indexOf('const ART_MAP');
if (a === -1) throw new Error('ART_MAP v deckbuilder.html nenalezen');
let depth = 0, k = html.indexOf('{', a);
while (true) { if (html[k] === '{') depth++; else if (html[k] === '}') depth--; k++; if (depth === 0) break; }
if (html[k] === ';') k++;
const artLiteral = 'const ART_MAP = {\n' +
  '  // GENEROVÁNO z CardPresentation.kt – node tools/regen_deckbuilder_cards.js\n' +
  Object.keys(art).map(id => '  ' + JSON.stringify(id) + ':' + JSON.stringify(art[id])).join(',\n') +
  '\n};';
html = html.slice(0, a) + artLiteral + html.slice(k);

const noArt = out.filter(c => !art[c.id]).map(c => c.id);

fs.writeFileSync(HTML, html, 'utf8');
console.log('EMBEDDED_CARDS vloženo: ' + out.length + ' karet, verze ' + version +
            ', s herním popisem ' + out.filter(c => c.desc).length);
console.log('ART_MAP přegenerován: ' + Object.keys(art).length + ' obrázků' +
            (noArt.length ? ' | bez obrázku: ' + noArt.join(', ') : ''));
