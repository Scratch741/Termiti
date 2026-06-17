'use strict';
/**
 * ReplayViewer – vizuální přehrávač NDJSON logů online her.
 */

const fs   = require('fs');
const path = require('path');

const LOGS_DIR  = path.join(__dirname, '..', 'logs');
const CARD_DATA = (() => {
  try { return JSON.parse(fs.readFileSync(path.join(__dirname, 'card_data.json'), 'utf8')); }
  catch { return {}; }
})();

function esc(s) {
  return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}
function fmtDur(sec) {
  if (!sec) return '?';
  return Math.floor(sec/60) + ':' + String(sec%60).padStart(2,'0');
}

// ── parseReplay ───────────────────────────────────────────────────────────────
function parseReplay(gameId) {
  let files;
  try { files = fs.readdirSync(LOGS_DIR); } catch { return null; }
  const filename = files.find(f => f.endsWith('_' + gameId + '.jsonl'));
  if (!filename) return null;
  try {
    const raw = fs.readFileSync(path.join(LOGS_DIR, filename), 'utf8');
    return raw.trim().split('\n').filter(Boolean).map(line => {
      try { return JSON.parse(line); } catch { return null; }
    }).filter(Boolean);
  } catch { return null; }
}

// ── listReplays ───────────────────────────────────────────────────────────────
function listReplays() {
  let files;
  try { files = fs.readdirSync(LOGS_DIR).filter(f => f.endsWith('.jsonl')); }
  catch { return []; }

  return files.map(f => {
    const m = f.match(/^(\d{4}-\d{2}-\d{2})_(.+)\.jsonl$/);
    if (!m) return null;
    const [, date, gameId] = m;
    try {
      const stat   = fs.statSync(path.join(LOGS_DIR, f));
      const raw    = fs.readFileSync(path.join(LOGS_DIR, f), 'utf8');
      const events = raw.trim().split('\n').filter(Boolean).map(l => {
        try { return JSON.parse(l); } catch { return null; }
      }).filter(Boolean);
      const startEv = events.find(e => e.event === 'game_start');
      const endEv   = events.find(e => e.event === 'game_end');
      return {
        gameId, date,
        sizeBytes:   stat.size,
        nameA:       startEv?.playerA?.name || '?',
        nameB:       startEv?.playerB?.name || '?',
        mode:        startEv?.mode          || 'normal',
        winner:      endEv?.winner          || null,
        durationSec: endEv?.durationSec     || null,
        actionCount: events.filter(e => e.event === 'action').length
      };
    } catch { return null; }
  })
  .filter(Boolean)
  .sort((a,b) => b.date.localeCompare(a.date) || b.gameId.localeCompare(a.gameId));
}

// ── buildListHtml ─────────────────────────────────────────────────────────────
function buildListHtml(replays) {
  const rows = replays.map(r => {
    const winnerStr = r.winner === 'A'    ? '🏆 ' + esc(r.nameA)
                    : r.winner === 'B'    ? '🏆 ' + esc(r.nameB)
                    : r.winner === 'DRAW' ? '🤝 Remíza' : '—';
    const modeStr = r.mode === 'super_random' ? '🎲' : '⚔️';
    const kb = (r.sizeBytes/1024).toFixed(1);
    return `<tr>
      <td class="muted">${esc(r.date)}</td>
      <td>${modeStr} <b>${esc(r.nameA)}</b> <span class="vs">vs</span> <b>${esc(r.nameB)}</b></td>
      <td>${winnerStr}</td>
      <td class="muted">${r.actionCount} akcí · ${fmtDur(r.durationSec)}</td>
      <td class="muted">${kb}&nbsp;KB</td>
      <td>
        <a class="rlink" href="/replay?id=${esc(r.gameId)}">▶ Přehrát</a>
        &nbsp;<a class="rlink-dim" href="/replay-data?id=${esc(r.gameId)}">JSON</a>
      </td>
    </tr>`;
  }).join('');

  return `<!DOCTYPE html>
<html lang="cs"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Termiti – Replay</title>
<style>
  body{background:#0d0a0e;color:#ede0c4;font-family:'Segoe UI',system-ui,sans-serif;padding:24px;max-width:1100px;margin:0 auto}
  h1{color:#d4a843;letter-spacing:3px;margin-bottom:4px}
  .nav{display:flex;gap:8px;margin:12px 0 24px}
  .btn{display:inline-block;padding:6px 14px;border-radius:6px;font-size:12px;text-decoration:none;border:1px solid;transition:background .15s}
  .btn-gold{background:#d4a84322;border-color:#d4a84355;color:#d4a843}.btn-gold:hover{background:#d4a84444}
  .btn-teal{background:#3dbfad22;border-color:#3dbfad55;color:#3dbfad}.btn-teal:hover{background:#3dbfad44}
  .btn-dim{background:#1a1320;border-color:#2a2430;color:#7a6e5f}.btn-dim:hover{background:#2a2430;color:#ede0c4}
  table{width:100%;border-collapse:collapse;margin-top:8px}
  th{background:#1a1320;color:#7a6e5f;font-size:11px;letter-spacing:1px;padding:7px 12px;text-align:left}
  td{padding:8px 12px;border-bottom:1px solid #1e1a2a;vertical-align:middle;font-size:13px}
  tr:hover td{background:#13101a}
  .muted{color:#7a6e5f;font-size:12px}.vs{color:#7a6e5f;font-size:11px;padding:0 4px}
  .rlink{color:#3dbfad;text-decoration:none}.rlink:hover{text-decoration:underline}
  .rlink-dim{color:#5a5060;text-decoration:none;font-size:11px}.rlink-dim:hover{color:#7a6e5f}
  .empty{color:#7a6e5f;margin-top:24px}
</style></head><body>
<h1>🎬 TERMITI – REPLAY</h1>
<div class="nav">
  <a class="btn btn-gold" href="/">🏆 Žebříček</a>
  <a class="btn btn-teal" href="/replays">🎬 Replaye</a>
  <a class="btn btn-dim"  href="/crash-logs">🐛 Crash logy</a>
</div>
${replays.length === 0
  ? '<p class="empty">Žádné záznamy.</p>'
  : `<table>
    <tr><th>Datum</th><th>Hráči</th><th>Výsledek</th><th>Délka / Akce</th><th>Velikost</th><th></th></tr>
    ${rows}
  </table>`}
</body></html>`;
}

// ── buildViewerHtml ───────────────────────────────────────────────────────────
function buildViewerHtml(gameId, events) {
  const startEv = events.find(e => e.event === 'game_start');
  const endEv   = events.find(e => e.event === 'game_end');

  const frames = [];
  let pending = null;
  for (const ev of events) {
    if (ev.event === 'action') {
      if (pending) frames.push(pending);
      pending = { ...ev, extra: [] };
    } else if (pending && (ev.event === 'card_lost' || ev.event === 'decision')) {
      pending.extra.push(ev);
    } else if (ev.event === 'game_end') {
      if (pending) { frames.push(pending); pending = null; }
      frames.push({ ...ev, extra: [] });
    }
  }
  if (pending) frames.push(pending);

  const meta = {
    gameId,
    nameA:       startEv?.playerA?.name      || 'Hráč A',
    nameB:       startEv?.playerB?.name      || 'Hráč B',
    winTargetA:  startEv?.winTarget?.A       || 70,
    winTargetB:  startEv?.winTarget?.B       || 70,
    mode:        startEv?.mode               || 'normal',
    winner:      endEv?.winner               || null,
    durationSec: endEv?.durationSec          || null
  };

  const safe = v => JSON.stringify(v).replace(/<\/script>/gi,'<\\/script>');

  return `<!DOCTYPE html>
<html lang="cs"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Replay – ${esc(meta.nameA)} vs ${esc(meta.nameB)}</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{background:#0d0a0e;color:#ede0c4;font-family:'Segoe UI',system-ui,sans-serif;
     height:100vh;display:flex;flex-direction:column;overflow:hidden}

/* ── Header ─────────────────────────────────────────────────────────────── */
#hdr{background:#13101a;border-bottom:1px solid #251f30;padding:8px 16px;
     display:flex;align-items:center;gap:10px;flex-shrink:0;flex-wrap:wrap}
#hdr h1{font-size:12px;color:#d4a843;letter-spacing:2px}
.hname{font-size:12px;font-weight:bold;padding:3px 10px;border-radius:5px;
       border:1px solid #2a2430;background:#1a1320}
.hname-a{color:#3dbfad;border-color:#3dbfad44}
.hname-b{color:#e8784d;border-color:#e8784d44}
.vs{color:#7a6e5f;font-size:11px}
.badge{padding:2px 9px;border-radius:10px;font-size:11px;border:1px solid;font-weight:600}
.badge-win{background:#d4a84322;border-color:#d4a84355;color:#d4a843}
.badge-mode{background:#3dbfad22;border-color:#3dbfad55;color:#3dbfad}
.flex1{flex:1}
.dur{font-size:11px;color:#7a6e5f}
#back{color:#3dbfad;text-decoration:none;font-size:11px}#back:hover{text-decoration:underline}

/* ── Main layout ─────────────────────────────────────────────────────────── */
#main{display:flex;flex:1;overflow:hidden}

/* ── Side panels ─────────────────────────────────────────────────────────── */
.panel{width:195px;flex-shrink:0;background:#0f0c14;overflow-y:auto;
       display:flex;flex-direction:column}
.panel-a{border-right:1px solid #1e1a2a}
.panel-b{border-left:1px solid #1e1a2a}
.panel-hdr{padding:8px 10px;background:#13101a;border-bottom:1px solid #1e1a2a;
           font-size:12px;font-weight:bold;text-align:center;
           overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.panel-hdr-a{color:#3dbfad}.panel-hdr-b{color:#e8784d}

.stats-block{padding:8px 10px;border-bottom:1px solid #1a1625;display:flex;flex-direction:column;gap:5px}
.stat-row{display:flex;align-items:center;gap:5px}
.stat-ico{font-size:12px;width:18px;text-align:center;flex-shrink:0}
.bar-bg{flex:1;height:6px;background:#1e1a2a;border-radius:3px;overflow:hidden}
.bar{height:100%;border-radius:3px;transition:width .3s,background .3s}
.stat-val{font-size:10px;color:#7a6e5f;width:44px;text-align:right;flex-shrink:0}

.res-block{padding:6px 10px;border-bottom:1px solid #1a1625}
.res-grid{display:grid;grid-template-columns:1fr 1fr;gap:3px}
.res-item{display:flex;align-items:center;gap:3px;background:#13101a;border-radius:4px;padding:3px 5px}
.ri{font-size:11px;width:15px;text-align:center}
.rv{font-size:11px;font-weight:bold}
.rm{font-size:9px;color:#5a5060;margin-left:1px}
.res-MAGIC .rv{color:#a855f7}.res-ATTACK .rv{color:#ef4444}
.res-STONES .rv{color:#94a3b8}.res-CHAOS .rv{color:#d4a843}

.hand-block{padding:6px 10px;flex:1}
.sect-lbl{font-size:9px;color:#7a6e5f;letter-spacing:1px;text-transform:uppercase;margin-bottom:5px}
.deck-badges{display:flex;gap:4px;margin-bottom:6px}
.dbadge{background:#1a1320;border:1px solid #2a2430;border-radius:3px;padding:1px 5px;font-size:9px;color:#7a6e5f}
.hand-cards{display:flex;flex-direction:column;gap:3px}
.hcard{display:flex;align-items:center;gap:5px;background:#13101a;border-radius:5px;
       padding:4px 6px;border-left:2px solid #2a2430;overflow:hidden}
.hcard.MAGIC{border-left-color:#a855f7}.hcard.ATTACK{border-left-color:#ef4444}
.hcard.STONES{border-left-color:#94a3b8}.hcard.CHAOS{border-left-color:#d4a843}
.hcard-art{width:22px;height:30px;border-radius:2px;object-fit:cover;flex-shrink:0;background:#1e1a2a}
.hcard-name{font-size:9px;color:#ede0c4;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.hcard-cost{font-size:8px;color:#7a6e5f}

/* ── Center ──────────────────────────────────────────────────────────────── */
#ctr{flex:1;display:flex;flex-direction:column;gap:10px;padding:14px;overflow-y:auto;min-width:0}

#played-area{display:flex;gap:14px;align-items:flex-start;flex-shrink:0}

/* Card visual */
.played-card{position:relative;width:115px;flex-shrink:0}
.played-card img{width:115px;height:161px;border-radius:9px;object-fit:cover;
                 border:2px solid #3a3040;display:block;background:#1e1a2a;
                 transition:border-color .3s}
.card-overlay{position:absolute;bottom:0;left:0;right:0;
              background:linear-gradient(transparent 30%,rgba(0,0,0,.9));
              border-radius:0 0 8px 8px;padding:22px 7px 7px}
.cn{font-size:11px;font-weight:bold;color:#fff;text-align:center;
    text-shadow:0 1px 4px #000;line-height:1.2}
.cc{display:flex;justify-content:center;margin-top:3px}
.cost-badge{font-size:10px;padding:1px 8px;border-radius:6px;border:1px solid;font-weight:bold}
.cost-MAGIC{background:#a855f722;border-color:#a855f766;color:#c084fc}
.cost-ATTACK{background:#ef444422;border-color:#ef444466;color:#f87171}
.cost-STONES{background:#94a3b822;border-color:#94a3b866;color:#cbd5e1}
.cost-CHAOS{background:#d4a84322;border-color:#d4a84366;color:#fbbf24}
.cost-NONE{background:#1e1a2a;border-color:#2a2430;color:#7a6e5f}
.rarity-badge{position:absolute;top:6px;right:6px;font-size:8px;letter-spacing:.5px;
              padding:1px 5px;border-radius:3px;font-weight:bold}
.r-LEGENDARY{background:#d4a84399;color:#fff7e0}.r-EPIC{background:#a855f799;color:#f3e8ff}
.r-RARE{background:#3b82f699;color:#dbeafe}.r-COMMON{display:none}

/* Actor / action info */
.ev-info{flex:1;min-width:0}
.ev-actor{font-size:13px;font-weight:bold;margin-bottom:4px}
.ev-action-badge{display:inline-block;padding:3px 10px;border-radius:5px;
                 font-size:11px;border:1px solid;margin-bottom:10px}
.action-play{background:#3dbfad22;border-color:#3dbfad55;color:#3dbfad}
.action-discard{background:#7a6e5f22;border-color:#7a6e5f55;color:#7a6e5f}

.extras{display:flex;flex-direction:column;gap:5px}
.ex{font-size:11px;padding:6px 10px;border-radius:6px;border:1px solid}
.ex-lost{color:#f87171;background:#1a0d0d;border-color:#ef444433}
.ex-decision{color:#c084fc;background:#110d18;border-color:#a855f733}

.end-card{background:#16120a;border:1px solid #d4a84333;border-radius:10px;
          padding:32px;text-align:center;flex-shrink:0}
.end-title{font-size:24px;font-weight:bold;color:#d4a843;margin-bottom:8px}
.end-sub{color:#7a6e5f;font-size:13px}

/* Mini log */
#logbox{background:#0f0c14;border:1px solid #1e1a2a;border-radius:8px;flex-shrink:0}
#log-hdr{font-size:9px;color:#7a6e5f;letter-spacing:1px;text-transform:uppercase;
         padding:7px 10px 5px;border-bottom:1px solid #1a1625}
#loglist{max-height:100px;overflow-y:auto;display:flex;flex-direction:column}
.li{font-size:10px;padding:4px 10px;cursor:pointer;border-left:3px solid transparent;
    color:#7a6e5f;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.li:hover{background:#13101a;color:#ede0c4}
.li.cur{background:#1a1625;color:#ede0c4}
.li.liA{border-left-color:#3dbfad55}.li.liB{border-left-color:#e8784d55}.li.liE{border-left-color:#d4a84355}

/* ── Nav bar ─────────────────────────────────────────────────────────────── */
#nav{background:#13101a;border-top:1px solid #251f30;padding:8px 14px;
     display:flex;align-items:center;gap:8px;flex-shrink:0}
.nb{background:#1a1320;border:1px solid #2a2430;color:#ede0c4;border-radius:5px;
    padding:5px 10px;cursor:pointer;font-size:13px;transition:background .1s;line-height:1}
.nb:hover{background:#2a2430}.nb:disabled{opacity:.3;cursor:default}
#pbtn{background:#3dbfad22;border-color:#3dbfad55;color:#3dbfad;font-size:14px}
#pbtn:hover{background:#3dbfad44}
#prog{flex:1;-webkit-appearance:none;appearance:none;height:4px;border-radius:2px;
      background:#1e1a2a;cursor:pointer;outline:none}
#prog::-webkit-slider-thumb{-webkit-appearance:none;width:12px;height:12px;
      border-radius:50%;background:#d4a843;cursor:pointer}
#prog::-moz-range-thumb{width:12px;height:12px;border-radius:50%;background:#d4a843;border:none}
#fc{font-size:11px;color:#7a6e5f;white-space:nowrap;min-width:56px;text-align:center}
#ti{font-size:11px;background:#1a1320;border:1px solid #2a2430;border-radius:4px;
    padding:3px 9px;color:#d4a843;white-space:nowrap}
.spd{background:#1a1320;border:1px solid #2a2430;color:#7a6e5f;border-radius:4px;
     padding:3px 5px;font-size:10px;cursor:pointer}
</style>
</head><body>

<div id="hdr">
  <h1>🎬 REPLAY</h1>
  <span class="hname hname-a" id="hna"></span>
  <span class="vs">vs</span>
  <span class="hname hname-b" id="hnb"></span>
  <span id="hwinner" class="badge badge-win" style="display:none"></span>
  <span id="hmode" class="badge badge-mode"></span>
  <div class="flex1"></div>
  <span id="hdur" class="dur"></span>
  <a id="back" href="/replays">← Replaye</a>
</div>

<div id="main">
  <!-- Panel A -->
  <div class="panel panel-a">
    <div class="panel-hdr panel-hdr-a" id="na"></div>
    <div class="stats-block">
      <div class="stat-row">
        <span class="stat-ico">🏰</span>
        <div class="bar-bg"><div class="bar" id="bca"></div></div>
        <span class="stat-val" id="vca"></span>
      </div>
      <div class="stat-row">
        <span class="stat-ico">🧱</span>
        <div class="bar-bg"><div class="bar" id="bwa" style="background:#5b7faa"></div></div>
        <span class="stat-val" id="vwa"></span>
      </div>
    </div>
    <div class="res-block"><div class="res-grid" id="ra"></div></div>
    <div class="hand-block">
      <div class="deck-badges" id="da"></div>
      <div class="sect-lbl">Ruka</div>
      <div class="hand-cards" id="ha"></div>
    </div>
  </div>

  <!-- Center -->
  <div id="ctr">
    <div id="played-area">
      <div class="played-card" id="pc-wrap">
        <img id="pc-img" src="" alt="">
        <div class="card-overlay">
          <div class="cn" id="pc-name"></div>
          <div class="cc" id="pc-cost"></div>
        </div>
        <div class="rarity-badge" id="pc-rarity"></div>
      </div>
      <div class="ev-info">
        <div class="ev-actor" id="ev-actor"></div>
        <div id="ev-action"></div>
        <div class="extras" id="ev-ext"></div>
      </div>
    </div>
    <div id="logbox">
      <div id="log-hdr">📜 Průběh hry</div>
      <div id="loglist"></div>
    </div>
  </div>

  <!-- Panel B -->
  <div class="panel panel-b">
    <div class="panel-hdr panel-hdr-b" id="nb"></div>
    <div class="stats-block">
      <div class="stat-row">
        <span class="stat-ico">🏰</span>
        <div class="bar-bg"><div class="bar" id="bcb"></div></div>
        <span class="stat-val" id="vcb"></span>
      </div>
      <div class="stat-row">
        <span class="stat-ico">🧱</span>
        <div class="bar-bg"><div class="bar" id="bwb" style="background:#5b7faa"></div></div>
        <span class="stat-val" id="vwb"></span>
      </div>
    </div>
    <div class="res-block"><div class="res-grid" id="rb"></div></div>
    <div class="hand-block">
      <div class="deck-badges" id="db"></div>
      <div class="sect-lbl">Ruka</div>
      <div class="hand-cards" id="hb"></div>
    </div>
  </div>
</div>

<div id="nav">
  <button class="nb" id="bf"    title="Začátek [Home]">⏮</button>
  <button class="nb" id="bprev" title="Předchozí [←]">⏪</button>
  <button class="nb" id="pbtn" title="Play [Mezerník]">▶</button>
  <button class="nb" id="bnext" title="Další [→]">⏩</button>
  <button class="nb" id="bl"   title="Konec [End]">⏭</button>
  <input type="range" id="prog" min="0" value="0">
  <span id="fc">– / –</span>
  <span id="ti">Tah –</span>
  <select class="spd" id="spd">
    <option value="2200">0.5×</option>
    <option value="1400" selected>1×</option>
    <option value="800">1.5×</option>
    <option value="350">3×</option>
  </select>
</div>

<script>
const META   = ${safe(meta)};
const FRAMES = ${safe(frames)};
const CARDS  = ${safe(CARD_DATA)};

const RI = { MAGIC:'🔮', ATTACK:'⚔️', STONES:'🪨', CHAOS:'🌀' };

// Header
$('hna').textContent = $('na').textContent = META.nameA;
$('hnb').textContent = $('nb').textContent = META.nameB;
$('hmode').textContent = META.mode === 'super_random' ? '🎲 Super Náhodný' : '⚔️ Constructed';
if (META.winner) {
  const wb = $('hwinner');
  wb.style.display = '';
  wb.textContent = META.winner==='A' ? '🏆 '+META.nameA
                 : META.winner==='B' ? '🏆 '+META.nameB : '🤝 Remíza';
}
if (META.durationSec) {
  $('hdur').textContent = Math.floor(META.durationSec/60)+':'+String(META.durationSec%60).padStart(2,'0');
}

// Mini log
const ll = $('loglist');
FRAMES.forEach((fr,i) => {
  const el = document.createElement('div');
  el.className = 'li '+(fr.event==='game_end'?'liE':fr.side==='A'?'liA':'liB');
  el.dataset.i = i;
  if (fr.event === 'game_end') {
    el.textContent = '🏁 Konec · '+(META.winner==='A'?'🏆 '+META.nameA
                                   :META.winner==='B'?'🏆 '+META.nameB:'🤝 Remíza');
  } else {
    const who = fr.side==='A' ? META.nameA : META.nameB;
    const act = fr.action==='play' ? 'zahrál' : 'zahodil';
    const cost = fr.costPaid!=null ? ' · '+fr.costPaid+(RI[fr.costType]||'') : '';
    el.textContent = 'T'+fr.turn+' '+who+' '+act+' '+(fr.cardName||'—')+cost;
  }
  el.addEventListener('click', () => { pause(); goto(i); });
  ll.appendChild(el);
});

// Controls
const prog = $('prog');
prog.max = Math.max(0, FRAMES.length-1);
prog.addEventListener('input', () => { pause(); goto(+prog.value); });
$('bf').addEventListener('click',    () => { pause(); goto(0); });
$('bprev').addEventListener('click', () => { pause(); goto(fi-1); });
$('pbtn').addEventListener('click',  togglePlay);
$('bnext').addEventListener('click', () => { pause(); goto(fi+1); });
$('bl').addEventListener('click',    () => { pause(); goto(FRAMES.length-1); });
document.addEventListener('keydown', e => {
  if (e.target.tagName==='INPUT'||e.target.tagName==='SELECT') return;
  if (e.key==='ArrowLeft')  { pause(); goto(fi-1); }
  if (e.key==='ArrowRight') { pause(); goto(fi+1); }
  if (e.key==='Home')       { pause(); goto(0); }
  if (e.key==='End')        { pause(); goto(FRAMES.length-1); }
  if (e.key===' ')          { e.preventDefault(); togglePlay(); }
});

let fi=0, playing=false, timer=null;
function togglePlay() { playing ? pause() : play(); }
function play() {
  if (fi>=FRAMES.length-1) goto(0);
  playing=true; $('pbtn').textContent='⏸'; tick();
}
function pause() {
  playing=false; $('pbtn').textContent='▶';
  if (timer) { clearTimeout(timer); timer=null; }
}
function tick() {
  if (!playing) return;
  timer = setTimeout(() => {
    if (!playing) return;
    if (fi<FRAMES.length-1) { goto(fi+1); tick(); } else pause();
  }, +($('spd').value)||1400);
}

function goto(idx) {
  if (!FRAMES.length) return;
  fi = Math.max(0, Math.min(FRAMES.length-1, idx));
  prog.value = fi;
  $('fc').textContent = (fi+1)+' / '+FRAMES.length;
  $('bf').disabled    = fi===0;
  $('bprev').disabled = fi===0;
  $('bnext').disabled = fi===FRAMES.length-1;
  $('bl').disabled    = fi===FRAMES.length-1;
  document.querySelectorAll('.li').forEach(el => el.classList.toggle('cur', +el.dataset.i===fi));
  const cur = document.querySelector('.li.cur');
  if (cur) cur.scrollIntoView({ block:'nearest' });
  render(FRAMES[fi]);
}

function render(fr) {
  if (fr.event === 'game_end') { renderEnd(); return; }
  $('ti').textContent = 'Tah '+fr.turn;
  renderPanel('a', fr.state?.A, fr.side==='A');
  renderPanel('b', fr.state?.B, fr.side==='B');
  renderCard(fr);
}

function renderEnd() {
  $('ti').textContent = 'Konec';
  $('pc-wrap').style.display = 'none';
  $('ev-actor').innerHTML = '';
  $('ev-action').innerHTML = '';
  const ext = $('ev-ext');
  ext.innerHTML = '';
  const d = document.createElement('div');
  d.className = 'end-card';
  const title = META.winner==='A' ? '🏆 '+META.nameA+' vítězí!'
              : META.winner==='B' ? '🏆 '+META.nameB+' vítězí!' : '🤝 Remíza';
  const dur = META.durationSec
    ? Math.floor(META.durationSec/60)+':'+String(META.durationSec%60).padStart(2,'0') : '';
  d.innerHTML = '<div class="end-title">'+he(title)+'</div>'+(dur?'<div class="end-sub">Délka: '+dur+'</div>':'');
  ext.appendChild(d);
}

function renderCard(fr) {
  $('pc-wrap').style.display = '';
  const cid   = fr.cardBaseId || fr.cardId || '';
  const cd    = CARDS[cid] || {};
  const art   = cd.art || 'art_default';
  const rarity = cd.rarity || 'COMMON';
  const ct    = fr.costType || cd.costType || '';

  $('pc-img').src = '/art/'+art+'.webp';
  $('pc-name').textContent = fr.cardName || cd.name || '?';
  $('pc-rarity').textContent = {LEGENDARY:'★ Legenda',EPIC:'◆ Epic',RARE:'◇ Rare',COMMON:''}[rarity]||'';
  $('pc-rarity').className = 'rarity-badge r-'+rarity;

  const borderColor = {MAGIC:'#a855f7',ATTACK:'#ef4444',STONES:'#94a3b8',CHAOS:'#d4a843'}[ct]||'#3a3040';
  $('pc-img').style.borderColor = borderColor;

  const costNum = fr.costPaid!=null ? fr.costPaid : (cd.cost!=null?cd.cost:'?');
  const costStr = (fr.isXCost?'X=':'')+costNum+(RI[ct]||'')+' '+ct;
  $('pc-cost').innerHTML = ct ? '<span class="cost-badge cost-'+ct+'">'+he(costStr)+'</span>' : '';

  const who = fr.side==='A' ? META.nameA : META.nameB;
  const col = fr.side==='A' ? '#3dbfad' : '#e8784d';
  $('ev-actor').innerHTML = '<span style="color:'+col+'">'+he(who)+'</span>';

  const isPlay = fr.action==='play';
  $('ev-action').innerHTML = '<span class="ev-action-badge '+(isPlay?'action-play':'action-discard')+'">'
    +(isPlay?'▶ zahrál':'⊗ zahodil')+'</span>';

  const ext = $('ev-ext');
  ext.innerHTML = '';
  for (const ex of (fr.extra||[])) {
    const d = document.createElement('div');
    if (ex.event==='card_lost') {
      d.className = 'ex ex-lost';
      const by = ex.causedBy==='A' ? META.nameA : META.nameB;
      d.textContent = (ex.action==='BURNED'?'🔥 Spáleno':'🃏 Ukradeno')+': '+(ex.cardName||'?')+' ('+by+')';
    } else if (ex.event==='decision') {
      d.className = 'ex ex-decision';
      const ws = ex.side==='A' ? META.nameA : META.nameB;
      d.textContent = '🎯 '+ws+' zvolil: '+(ex.chosenName||ex.chosenId||'—');
    }
    ext.appendChild(d);
  }
}

function renderPanel(s, st, isActive) {
  if (!st) return;
  const isA = s==='a';
  const wt  = isA ? META.winTargetA : META.winTargetB;
  const nm  = isA ? META.nameA : META.nameB;
  const cls = isA ? 'panel-hdr panel-hdr-a' : 'panel-hdr panel-hdr-b';
  $(s==='a'?'na':'nb').textContent = nm;
  $(s==='a'?'na':'nb').className = cls;

  const cpct = Math.max(0,Math.min(100,(st.hp/wt)*100));
  const bc = $(s==='a'?'bca':'bcb');
  bc.style.width = cpct+'%';
  bc.style.background = cpct>60?'#3dbfad':cpct>30?'#d4a843':'#ef4444';
  $(s==='a'?'vca':'vcb').textContent = st.hp+'/'+wt;

  const bw = $(s==='a'?'bwa':'bwb');
  bw.style.width = Math.min(100,st.wall||0)+'%';
  $(s==='a'?'vwa':'vwb').textContent = st.wall||0;

  const resEl = $(s==='a'?'ra':'rb');
  resEl.innerHTML = '';
  const mines = st.mines||{};
  for (const r of ['MAGIC','ATTACK','STONES','CHAOS']) {
    const d = document.createElement('div');
    d.className = 'res-item res-'+r;
    d.innerHTML = '<span class="ri">'+RI[r]+'</span>'
      +'<span class="rv">'+((st.res&&st.res[r])||0)+'</span>'
      +'<span class="rm">+' +(mines[r]||0)+'</span>';
    resEl.appendChild(d);
  }

  $(s==='a'?'da':'db').innerHTML =
    '<span class="dbadge">🂠 '+(st.deck||0)+'</span>'
    +'<span class="dbadge">🗑 '+(st.discard||0)+'</span>';

  const handEl = $(s==='a'?'ha':'hb');
  handEl.innerHTML = '';
  const hand = st.hand||[];
  if (!hand.length) {
    handEl.innerHTML = '<div style="color:#5a5060;font-size:9px">Prázdná ruka</div>';
  } else {
    for (const c of hand) {
      const cd2 = CARDS[c.id]||{};
      const art = cd2.art||'art_default';
      const ct2 = c.costType||cd2.costType||'';
      const cost = (c.cost!=null?c.cost:(cd2.cost!=null?cd2.cost:'?'))+(RI[ct2]||'');
      const d = document.createElement('div');
      d.className = 'hcard '+(ct2||'');
      d.innerHTML = '<img class="hcard-art" src="/art/'+he(art)+'.webp" loading="lazy">'
        +'<div><div class="hcard-name">'+he(c.name||cd2.name||c.id)+'</div>'
        +'<div class="hcard-cost">'+he(cost)+'</div></div>';
      handEl.appendChild(d);
    }
  }
}

function $(id) { return document.getElementById(id); }
function he(s) { return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }

if (FRAMES.length>0) goto(0);
</script>
</body></html>`;
}

module.exports = { parseReplay, listReplays, buildListHtml, buildViewerHtml };
