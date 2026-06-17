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
#hdr{background:#13101a;border-bottom:1px solid #251f30;padding:6px 16px;
     display:flex;align-items:center;gap:10px;flex-shrink:0}
#hdr h1{font-size:11px;color:#d4a843;letter-spacing:2px;white-space:nowrap}
.hname{font-size:13px;font-weight:bold;padding:3px 10px;border-radius:5px;
       border:1px solid #2a2430;background:#1a1320;white-space:nowrap}
.hname-a{color:#3dbfad;border-color:#3dbfad55}
.hname-b{color:#e8784d;border-color:#e8784d55}
.hvs{color:#5a5060;font-size:11px;flex-shrink:0}
.badge{padding:2px 8px;border-radius:10px;font-size:11px;border:1px solid;font-weight:600}
.badge-win{background:#d4a84322;border-color:#d4a84355;color:#d4a843}
.badge-mode{background:#2a2430;border-color:#3a3440;color:#9a8ea0;font-size:10px}
.flex1{flex:1}
.dur{font-size:11px;color:#6a6070;white-space:nowrap}
#back{color:#3dbfad;text-decoration:none;font-size:11px;white-space:nowrap}
#back:hover{text-decoration:underline}

/* ── Main layout ─────────────────────────────────────────────────────────── */
#main{display:flex;flex:1;overflow:hidden;min-height:0}

/* ── Side panels ─────────────────────────────────────────────────────────── */
.panel{width:210px;flex-shrink:0;background:#0d0b12;overflow-y:auto;
       display:flex;flex-direction:column}
.panel-a{border-right:2px solid #1a1625}
.panel-b{border-left:2px solid #1a1625}

/* Player header */
.p-name{padding:10px 12px 8px;font-size:14px;font-weight:700;
        border-bottom:1px solid #1a1625;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.p-name-a{color:#3dbfad}.p-name-b{color:#e8784d}

/* Castle HP – big display */
.hp-block{padding:10px 12px 8px;border-bottom:1px solid #1a1625}
.hp-row{display:flex;align-items:baseline;gap:6px;margin-bottom:6px}
.hp-icon{font-size:16px}
.hp-num{font-size:26px;font-weight:800;line-height:1}
.hp-num-a{color:#3dbfad}.hp-num-b{color:#e8784d}
.hp-frac{font-size:12px;color:#5a5060}
.bar-bg{height:5px;background:#1a1625;border-radius:3px;overflow:hidden;margin-bottom:5px}
.bar{height:100%;border-radius:3px;transition:width .4s,background .4s}
.wall-row{display:flex;align-items:center;gap:6px}
.wall-lbl{font-size:11px;color:#7a7090}
.wall-num{font-size:13px;font-weight:600;color:#8aa8cc}

/* Resources */
.res-block{padding:8px 12px;border-bottom:1px solid #1a1625}
.res-title{font-size:9px;color:#5a5060;letter-spacing:1px;text-transform:uppercase;margin-bottom:5px}
.res-grid{display:grid;grid-template-columns:1fr 1fr;gap:4px}
.res-item{display:flex;align-items:center;gap:4px;border-radius:5px;padding:4px 6px;border:1px solid}
.res-MAGIC{background:#1a0e28;border-color:#4a2070}
.res-ATTACK{background:#1e0c0c;border-color:#6a2020}
.res-STONES{background:#0e1420;border-color:#2a3a50}
.res-CHAOS{background:#1a1400;border-color:#4a3a00}
.ri{font-size:13px;flex-shrink:0}
.rv{font-size:14px;font-weight:700}
.res-MAGIC .rv{color:#c084fc}
.res-ATTACK .rv{color:#f87171}
.res-STONES .rv{color:#93c5fd}
.res-CHAOS .rv{color:#fbbf24}
.rm{font-size:10px;color:#6a6070;margin-left:auto}

/* Hand */
.hand-block{padding:8px 12px;flex:1;display:flex;flex-direction:column;min-height:0}
.hand-meta{display:flex;gap:6px;margin-bottom:7px}
.hmeta-badge{background:#13101a;border:1px solid #252030;border-radius:4px;
             padding:2px 7px;font-size:10px;color:#8a8090}
.hand-title{font-size:9px;color:#5a5060;letter-spacing:1px;text-transform:uppercase;margin-bottom:5px}
.hand-cards{display:flex;flex-direction:column;gap:3px;overflow-y:auto}
.hcard{display:flex;align-items:center;gap:6px;border-radius:6px;padding:4px 7px;
       border-left:3px solid #2a2430;background:#13101a;overflow:hidden}
.hcard.MAGIC{border-left-color:#a855f7;background:#140d20}
.hcard.ATTACK{border-left-color:#ef4444;background:#180c0c}
.hcard.STONES{border-left-color:#6b89a8;background:#0d1218}
.hcard.CHAOS{border-left-color:#d4a843;background:#141000}
.hcard-art{width:24px;height:34px;border-radius:3px;object-fit:cover;flex-shrink:0;background:#1e1a2a}
.hcard-name{font-size:10px;color:#d0c0a0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;flex:1}
.hcard-cost{font-size:10px;color:#8a8090;flex-shrink:0}

/* ── Center ──────────────────────────────────────────────────────────────── */
#ctr{flex:1;display:flex;flex-direction:column;min-width:0;overflow:hidden}

/* Card + action section */
#action-sec{display:flex;gap:14px;padding:14px;align-items:flex-start;flex-shrink:0}

.played-card{position:relative;width:126px;flex-shrink:0}
.played-card img{width:126px;height:176px;border-radius:10px;object-fit:cover;
                 border:2px solid #3a3040;display:block;background:#1e1a2a;
                 transition:border-color .3s,box-shadow .3s}
.played-card img.glow-MAGIC{border-color:#a855f7;box-shadow:0 0 12px #a855f755}
.played-card img.glow-ATTACK{border-color:#ef4444;box-shadow:0 0 12px #ef444455}
.played-card img.glow-STONES{border-color:#6b89a8;box-shadow:0 0 12px #6b89a855}
.played-card img.glow-CHAOS{border-color:#d4a843;box-shadow:0 0 12px #d4a84355}
.card-overlay{position:absolute;bottom:0;left:0;right:0;
              background:linear-gradient(transparent 25%,rgba(0,0,0,.92));
              border-radius:0 0 9px 9px;padding:24px 8px 8px}
.cn{font-size:12px;font-weight:700;color:#fff;text-align:center;
    text-shadow:0 1px 6px #000;line-height:1.2}
.cc{display:flex;justify-content:center;margin-top:4px}
.cost-badge{font-size:10px;padding:2px 8px;border-radius:6px;border:1px solid;font-weight:700}
.cost-MAGIC{background:#a855f733;border-color:#a855f788;color:#d8aaff}
.cost-ATTACK{background:#ef444433;border-color:#ef444488;color:#fca5a5}
.cost-STONES{background:#6b89a833;border-color:#6b89a888;color:#bfdbfe}
.cost-CHAOS{background:#d4a84333;border-color:#d4a84388;color:#fde68a}
.rarity-badge{position:absolute;top:7px;left:7px;font-size:8px;letter-spacing:.5px;
              padding:2px 6px;border-radius:4px;font-weight:700}
.r-LEGENDARY{background:#d4a843cc;color:#fff7e0}
.r-EPIC{background:#a855f7cc;color:#f3e8ff}
.r-RARE{background:#3b82f6cc;color:#dbeafe}
.r-COMMON{display:none}

/* Action info panel */
.ev-info{flex:1;min-width:0;display:flex;flex-direction:column;gap:8px}
.ev-actor{font-size:18px;font-weight:800;color:#ede0c4}
.ev-action-badge{display:inline-flex;align-items:center;gap:5px;padding:5px 14px;
                 border-radius:6px;font-size:13px;border:1px solid;font-weight:600;
                 align-self:flex-start}
.action-play{background:#0d2520;border-color:#3dbfad88;color:#3dffd0}
.action-discard{background:#1a1a20;border-color:#6a6070;color:#b0a090}

.extras{display:flex;flex-direction:column;gap:5px}
.ex{font-size:12px;padding:6px 10px;border-radius:6px;border:1px solid;line-height:1.4}
.ex-lost{color:#fca5a5;background:#1a0a0a;border-color:#ef444444}
.ex-decision{color:#d8aaff;background:#0e0a18;border-color:#a855f744}

/* End screen */
.end-card{margin:14px;background:#16120a;border:2px solid #d4a84333;border-radius:12px;
          padding:36px;text-align:center}
.end-title{font-size:28px;font-weight:800;color:#d4a843;margin-bottom:8px}
.end-sub{color:#7a6e5f;font-size:14px}

/* ── Log panel ───────────────────────────────────────────────────────────── */
#logbox{flex:1;background:#0a0810;border-top:2px solid #1a1625;
        display:flex;flex-direction:column;min-height:0}
#log-hdr{font-size:10px;font-weight:700;color:#d4a843;letter-spacing:1.5px;
         text-transform:uppercase;padding:7px 14px 6px;border-bottom:1px solid #1a1625;
         flex-shrink:0;background:#0d0b12}
#loglist{flex:1;overflow-y:auto;padding:2px 0}
.li{display:block;font-size:12px;line-height:1.5;padding:4px 14px;
    cursor:pointer;border-left:3px solid #222;color:#bbb}
.li:hover{background:#111120;color:#fff}
.li.cur{background:#111120;color:#fff;font-weight:700}
.li.liA{border-left-color:#3dbfad;color:#a0f0d8}
.li.liB{border-left-color:#e8784d;color:#f0c0a0}
.li.liE{border-left-color:#d4a843;color:#f0d880}

/* ── Nav bar ─────────────────────────────────────────────────────────────── */
#nav{background:#0d0b12;border-top:2px solid #1a1625;padding:8px 14px;
     display:flex;align-items:center;gap:8px;flex-shrink:0}
.nb{background:#13101a;border:1px solid #252030;color:#ede0c4;border-radius:5px;
    padding:5px 10px;cursor:pointer;font-size:13px;transition:background .1s;line-height:1}
.nb:hover{background:#1e1a2e}.nb:disabled{opacity:.3;cursor:default}
#pbtn{background:#0d2520;border-color:#3dbfad66;color:#3dbfad;font-size:14px}
#pbtn:hover{background:#133530}
#prog{flex:1;-webkit-appearance:none;appearance:none;height:4px;border-radius:2px;
      background:#1a1625;cursor:pointer;outline:none}
#prog::-webkit-slider-thumb{-webkit-appearance:none;width:13px;height:13px;
      border-radius:50%;background:#d4a843;cursor:pointer}
#prog::-moz-range-thumb{width:13px;height:13px;border-radius:50%;background:#d4a843;border:none}
#fc{font-size:11px;color:#6a6070;white-space:nowrap;min-width:60px;text-align:center}
#ti{font-size:12px;font-weight:600;background:#13101a;border:1px solid #252030;
    border-radius:4px;padding:3px 10px;color:#d4a843;white-space:nowrap}
.spd{background:#13101a;border:1px solid #252030;color:#8a8090;border-radius:4px;
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
    <div class="p-name p-name-a" id="na"></div>
    <div class="hp-block">
      <div class="hp-row">
        <span class="hp-icon">🏰</span>
        <span class="hp-num hp-num-a" id="hpa">—</span>
        <span class="hp-frac" id="hpfa"></span>
      </div>
      <div class="bar-bg"><div class="bar" id="bca"></div></div>
      <div class="wall-row">
        <span class="wall-lbl">🧱 Hradby</span>
        <span class="wall-num" id="vwa">—</span>
      </div>
    </div>
    <div class="res-block">
      <div class="res-title">Suroviny</div>
      <div class="res-grid" id="ra"></div>
    </div>
    <div class="hand-block">
      <div class="hand-meta" id="da"></div>
      <div class="hand-title">Ruka</div>
      <div class="hand-cards" id="ha"></div>
    </div>
  </div>

  <!-- Center -->
  <div id="ctr">
    <div id="action-sec">
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
    <div class="p-name p-name-b" id="nb"></div>
    <div class="hp-block">
      <div class="hp-row">
        <span class="hp-icon">🏰</span>
        <span class="hp-num hp-num-b" id="hpb">—</span>
        <span class="hp-frac" id="hpfb"></span>
      </div>
      <div class="bar-bg"><div class="bar" id="bcb"></div></div>
      <div class="wall-row">
        <span class="wall-lbl">🧱 Hradby</span>
        <span class="wall-num" id="vwb">—</span>
      </div>
    </div>
    <div class="res-block">
      <div class="res-title">Suroviny</div>
      <div class="res-grid" id="rb"></div>
    </div>
    <div class="hand-block">
      <div class="hand-meta" id="db"></div>
      <div class="hand-title">Ruka</div>
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
  renderPanel('a', fr.state?.A);
  renderPanel('b', fr.state?.B);
  renderCard(fr);
}

function renderEnd() {
  $('ti').textContent = 'Konec';
  const sec = $('action-sec');
  sec.innerHTML = '';
  const d = document.createElement('div');
  d.className = 'end-card';
  const title = META.winner==='A' ? '🏆 '+META.nameA+' vítězí!'
              : META.winner==='B' ? '🏆 '+META.nameB+' vítězí!' : '🤝 Remíza';
  const dur = META.durationSec
    ? Math.floor(META.durationSec/60)+':'+String(META.durationSec%60).padStart(2,'0') : '';
  d.innerHTML = '<div class="end-title">'+he(title)+'</div>'+(dur?'<div class="end-sub">Délka hry: '+dur+'</div>':'');
  sec.appendChild(d);
}

function renderCard(fr) {
  // Restore action-sec if end screen replaced it
  if (!$('pc-wrap')) {
    $('action-sec').innerHTML = `
      <div class="played-card" id="pc-wrap">
        <img id="pc-img" src="" alt="">
        <div class="card-overlay"><div class="cn" id="pc-name"></div><div class="cc" id="pc-cost"></div></div>
        <div class="rarity-badge" id="pc-rarity"></div>
      </div>
      <div class="ev-info">
        <div class="ev-actor" id="ev-actor"></div>
        <div id="ev-action"></div>
        <div class="extras" id="ev-ext"></div>
      </div>`;
  }
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

  $('pc-img').className = ct ? 'glow-'+ct : '';
  $('pc-img').style.borderColor = '';

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

function renderPanel(s, st) {
  if (!st) return;
  const isA = s==='a';
  const wt  = isA ? META.winTargetA : META.winTargetB;

  // HP
  const cpct = Math.max(0,Math.min(100,(st.hp/wt)*100));
  const bc = $(s==='a'?'bca':'bcb');
  bc.style.width = cpct+'%';
  bc.style.background = cpct>60?'#3dbfad':cpct>30?'#d4a843':'#ef4444';
  $(s==='a'?'hpa':'hpb').textContent = st.hp;
  $(s==='a'?'hpfa':'hpfb').textContent = '/ '+wt;

  // Wall
  $(s==='a'?'vwa':'vwb').textContent = st.wall||0;

  // Resources
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

  // Deck meta
  $(s==='a'?'da':'db').innerHTML =
    '<span class="hmeta-badge">🂠 Balíček: '+(st.deck||0)+'</span>'
    +'<span class="hmeta-badge">🗑 Odhoz: '+(st.discard||0)+'</span>';

  // Hand
  const handEl = $(s==='a'?'ha':'hb');
  handEl.innerHTML = '';
  const hand = st.hand||[];
  if (!hand.length) {
    handEl.innerHTML = '<div style="color:#5a5060;font-size:10px;padding:4px 0">Prázdná ruka</div>';
  } else {
    for (const c of hand) {
      const cd2 = CARDS[c.id]||{};
      const art = cd2.art||'art_default';
      const ct2 = c.costType||cd2.costType||'';
      const cost = (c.cost!=null?c.cost:(cd2.cost!=null?cd2.cost:'?'))+(RI[ct2]||'');
      const d = document.createElement('div');
      d.className = 'hcard '+(ct2||'');
      d.innerHTML = '<img class="hcard-art" src="/art/'+he(art)+'.webp" loading="lazy">'
        +'<div style="flex:1;min-width:0"><div class="hcard-name">'+he(c.name||cd2.name||c.id)+'</div>'
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
