// Přegeneruje server/game/card_data.json (cache pro ReplayViewer.js) z cards.js
// + názvů artu z CardPresentation.kt. Ručně udržovaná kopie zaostávala:
// 183 z 192 karet a staré názvy.
const fs = require('fs');
const path = require('path');

const ROOT = process.cwd();
const cards = require(ROOT + '/server/game/cards.js').ALL_CARDS;
const kt = fs.readFileSync(ROOT + '/app/src/main/java/com/example/termiti/CardPresentation.kt', 'utf8');

// id -> art drawable (R.drawable.art_xxx z CardPres bloku dané karty)
const art = {};
const re = /"([0-9A-Z]{3})" to CardPres\(([\s\S]{0,400}?)R\.drawable\.(\w+)/g;
let m;
while ((m = re.exec(kt))) art[m[1]] = m[3];

const out = {};
for (const c of cards) {
  out[c.id] = {
    rarity:   c.rarity,
    cost:     c.cost,
    costType: c.costType,
    name:     c.name,
    art:      art[c.id] || 'art_default',
  };
}
const p = path.join(ROOT, 'server/game/card_data.json');
fs.writeFileSync(p, JSON.stringify(out), 'utf8');
const missing = Object.keys(out).filter(id => !art[id]);
console.log('card_data.json přegenerováno: ' + Object.keys(out).length + ' karet'
  + (missing.length ? ' | bez artu: ' + missing.join(', ') : ''));
