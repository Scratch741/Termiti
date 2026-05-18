'use strict';
/**
 * ReplayViewer – vizuální přehrávač NDJSON logů online her.
 *
 * Exporty:
 *   parseReplay(gameId)         → pole event-objektů nebo null (soubor nenalezen)
 *   listReplays()               → pole metadat všech .jsonl souborů
 *   buildListHtml(replays)      → HTML stránka se seznamem her
 *   buildViewerHtml(gameId, events) → plná HTML stránka přehrávače
 */

const fs   = require('fs');
const path = require('path');

const LOGS_DIR = path.join(__dirname, '..', 'logs');

// ── Helpers ────────────────────────────────────────────────────────────────────

function esc(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function fmtDuration(sec) {
  if (!sec) return '?';
  return Math.floor(sec / 60) + ':' + String(sec % 60).padStart(2, '0');
}

// ── parseReplay ────────────────────────────────────────────────────────────────

/**
 * Najde soubor *_<gameId>.jsonl a vrátí pole event objektů.
 * Vrátí null pokud soubor neexistuje nebo došlo k chybě.
 */
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

// ── listReplays ────────────────────────────────────────────────────────────────

/**
 * Vrátí seznam všech her s metadaty, seřazený od nejnovější.
 */
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
      const events = raw.trim().split('\n').filter(Boolean).map(line => {
        try { return JSON.parse(line); } catch { return null; }
      }).filter(Boolean);
      const startEv = events.find(e => e.event === 'game_start');
      const endEv   = events.find(e => e.event === 'game_end');
      return {
        gameId,
        date,
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
  .sort((a, b) => {
    const d = b.date.localeCompare(a.date);
    return d !== 0 ? d : b.gameId.localeCompare(a.gameId);
  });
}

// ── buildListHtml ─────────────────────────────────────────────────────────────

function buildListHtml(replays) {
  const rows = replays.map(r => {
    const winnerStr = r.winner === 'A'    ? '🏆 ' + esc(r.nameA) :
                      r.winner === 'B'    ? '🏆 ' + esc(r.nameB) :
                      r.winner === 'DRAW' ? '🤝 Remíza'           : '—';
    const modeStr   = r.mode === 'super_random' ? '🎲' : '⚔️';
    const kb        = (r.sizeBytes / 1024).toFixed(1);
    return `<tr>
      <td class="muted">${esc(r.date)}</td>
      <td>${modeStr} <b>${esc(r.nameA)}</b> <span class="vs">vs</span> <b>${esc(r.nameB)}</b></td>
      <td>${winnerStr}</td>
      <td class="muted">${r.actionCount} akcí · ${fmtDuration(r.durationSec)}</td>
      <td class="muted">${kb}&nbsp;KB</td>
      <td>
        <a href="/replay?id=${esc(r.gameId)}">▶ Přehrát</a>
        &nbsp;
        <a href="/replay-data?id=${esc(r.gameId)}" style="opacity:.5">JSON</a>
      </td>
    </tr>`;
  }).join('');

  return `<!DOCTYPE html>
<html lang="cs">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Termiti – Replay</title>
  <style>
    body{background:#0d0a0e;color:#ede0c4;font-family:'Segoe UI',system-ui,sans-serif;padding:24px;max-width:1100px;margin:0 auto}
    h1{color:#d4a843;letter-spacing:3px;margin-bottom:4px}
    .sub{color:#7a6e5f;font-size:13px;margin-bottom:24px}
    table{width:100%;border-collapse:collapse;margin-top:8px}
    th{background:#1a1320;color:#7a6e5f;font-size:11px;letter-spacing:1px;padding:7px 12px;text-align:left}
    td{padding:7px 12px;border-bottom:1px solid #1e1a2a;vertical-align:middle;font-size:13px}
    tr:hover td{background:#13101a}
    .muted{color:#7a6e5f;font-size:12px}
    .vs{color:#7a6e5f;font-size:11px;padding:0 4px}
    a{color:#3dbfad;text-decoration:none}
    a:hover{text-decoration:underline}
    .empty{color:#7a6e5f;margin-top:24px}
  </style>
</head>
<body>
  <h1>🎬 TERMITI – REPLAY</h1>
  <p class="sub"><a href="/">← Žebříček</a></p>
  ${replays.length === 0
    ? '<p class="empty">Žádné záznamy (hry se logují při každém online zápase).</p>'
    : `<table>
      <tr><th>Datum</th><th>Hráči</th><th>Výsledek</th><th>Délka / Akce</th><th>Velikost</th><th></th></tr>
      ${rows}
    </table>`
  }
</body>
</html>`;
}

// ── buildViewerHtml ───────────────────────────────────────────────────────────

function buildViewerHtml(gameId, events) {
  const startEv = events.find(e => e.event === 'game_start');
  const endEv   = events.find(e => e.event === 'game_end');

  // Sestav snímky: každý event 'action' je snímek; card_lost/decision se přilepí k předchozímu
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
    abilitiesA:  startEv?.playerA?.abilities || [],
    abilitiesB:  startEv?.playerB?.abilities || [],
    mode:        startEv?.mode               || 'normal',
    winner:      endEv?.winner               || null,
    durationSec: endEv?.durationSec          || null
  };

  // Bezpečné vložení do <script> – escape </script> uvnitř dat
  const safeJson = (v) => JSON.stringify(v).replace(/<\/script>/gi, '<\\/script>');

  return `<!DOCTYPE html>
<html lang="cs">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Replay – ${esc(meta.nameA)} vs ${esc(meta.nameB)}</title>
  <style>
    *{box-sizing:border-box;margin:0;padding:0}
    body{background:#0d0a0e;color:#ede0c4;font-family:'Segoe UI',system-ui,sans-serif;
         height:100vh;display:flex;flex-direction:column;overflow:hidden}

    /* ── Header ──────────────────────────────────────────────────────────── */
    #hdr{background:#13101a;border-bottom:1px solid #1e1a2a;padding:7px 14px;
         display:flex;align-items:center;gap:10px;flex-shrink:0;flex-wrap:wrap}
    #hdr h1{font-size:13px;color:#d4a843;letter-spacing:2px;white-space:nowrap}
    .vs{color:#7a6e5f;font-size:11px}
    .badge{padding:2px 9px;border-radius:10px;font-size:11px;border:1px solid}
    .badge-win{background:#d4a84322;border-color:#d4a84355;color:#d4a843}
    .badge-mode{background:#3dbfad22;border-color:#3dbfad55;color:#3dbfad}
    .flex1{flex:1}
    .dur{font-size:11px;color:#7a6e5f}
    #back{color:#3dbfad;text-decoration:none;font-size:11px;white-space:nowrap}
    #back:hover{text-decoration:underline}

    /* ── Main ────────────────────────────────────────────────────────────── */
    #main{display:flex;flex:1;overflow:hidden}

    /* ── Player panels ───────────────────────────────────────────────────── */
    .panel{width:210px;flex-shrink:0;background:#0f0c14;padding:10px;
           overflow-y:auto;display:flex;flex-direction:column;gap:7px}
    .panel.pa{border-right:1px solid #1e1a2a}
    .panel.pb{border-left:1px solid #1e1a2a}
    .pname{font-size:12px;font-weight:bold;text-align:center;padding:4px 8px;
           border-radius:6px;background:#13101a;border:1px solid #2a2430;
           overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
    .pname.active-a{color:#3dbfad;border-color:#3dbfad44}
    .pname.active-b{color:#e8784d;border-color:#e8784d44}

    /* bars */
    .stat-row{display:flex;align-items:center;gap:5px}
    .stat-lbl{width:46px;font-size:10px;color:#7a6e5f;text-align:right;flex-shrink:0}
    .bar-bg{flex:1;height:7px;background:#1e1a2a;border-radius:4px;overflow:hidden}
    .bar{height:100%;border-radius:4px;transition:width .25s ease,background .25s}
    .bar-castle{background:#3dbfad}
    .bar-wall{background:#5b7faa}
    .stat-num{width:52px;font-size:10px;color:#7a6e5f;text-align:right;flex-shrink:0}

    /* resources */
    .res-grid{display:grid;grid-template-columns:1fr 1fr;gap:3px}
    .res-item{display:flex;align-items:center;gap:3px;background:#13101a;
              border-radius:5px;padding:3px 5px;font-size:10px}
    .ri{font-size:12px;width:16px;text-align:center}
    .rv{font-weight:bold}
    .rm{color:#7a6e5f;font-size:9px;margin-left:1px}
    .res-MAGIC  .rv{color:#9b59b6}
    .res-ATTACK .rv{color:#e74c3c}
    .res-STONES .rv{color:#95a5a6}
    .res-CHAOS  .rv{color:#d4a843}

    /* deck */
    .deck-row{display:flex;gap:4px;flex-wrap:wrap}
    .dbadge{background:#1a1320;border:1px solid #2a2430;border-radius:4px;
            padding:2px 6px;font-size:9px;color:#7a6e5f}

    /* hand */
    .sect-lbl{font-size:9px;color:#7a6e5f;letter-spacing:1px;text-transform:uppercase;margin-top:2px}
    .hand-list{display:flex;flex-direction:column;gap:2px}
    .hcard{display:flex;align-items:center;gap:4px;background:#13101a;border-radius:4px;
           padding:3px 6px;font-size:10px;border-left:2px solid #2a2430;
           overflow:hidden}
    .hcard.MAGIC  {border-left-color:#9b59b6}
    .hcard.ATTACK {border-left-color:#e74c3c}
    .hcard.STONES {border-left-color:#95a5a6}
    .hcard.CHAOS  {border-left-color:#d4a843}
    .hcn{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#ede0c4}
    .hcc{font-size:9px;color:#7a6e5f;white-space:nowrap;flex-shrink:0}

    /* ── Center ──────────────────────────────────────────────────────────── */
    #ctr{flex:1;display:flex;flex-direction:column;gap:10px;padding:14px;overflow-y:auto;min-width:0}

    .ev-card{border-radius:10px;padding:14px 16px;border:1px solid #2a2430;background:#13101a;text-align:center}
    .ev-card.play-A{background:#0c1e1b;border-color:#3dbfad44}
    .ev-card.play-B{background:#1e110b;border-color:#e8784d44}
    .ev-card.end{background:#16120a;border-color:#d4a84344}
    .ev-who{font-size:10px;color:#7a6e5f;text-transform:uppercase;letter-spacing:1px;margin-bottom:5px}
    .ev-name{font-size:19px;font-weight:bold;margin-bottom:5px}
    .ev-cbadge{display:inline-block;padding:2px 11px;border-radius:9px;font-size:11px;
               font-weight:bold;margin-bottom:6px;border:1px solid}
    .cbMAGIC {background:#9b59b622;border-color:#9b59b655;color:#9b59b6}
    .cbATTACK{background:#e74c3c22;border-color:#e74c3c55;color:#e74c3c}
    .cbSTONES{background:#95a5a622;border-color:#95a5a655;color:#95a5a6}
    .cbCHAOS {background:#d4a84322;border-color:#d4a84355;color:#d4a843}
    .cbNONE  {background:#1e1a2a;border-color:#2a2430;color:#7a6e5f}
    .ev-meta{font-size:11px;color:#7a6e5f}
    .extras{display:flex;flex-direction:column;gap:3px;margin-top:8px}
    .ex{font-size:10px;padding:3px 9px;border-radius:4px;border:1px solid}
    .ex-lost    {color:#bf2d2d;background:#1a0d0d;border-color:#bf2d2d33}
    .ex-decision{color:#9b59b6;background:#110d18;border-color:#9b59b633}

    /* mini log */
    #minilog{background:#0f0c14;border:1px solid #1e1a2a;border-radius:8px;padding:9px;flex-shrink:0}
    #minilog h3{font-size:9px;color:#7a6e5f;letter-spacing:1px;text-transform:uppercase;margin-bottom:6px}
    #loglist{max-height:90px;overflow-y:auto;display:flex;flex-direction:column;gap:1px}
    .li{font-size:10px;padding:2px 6px;border-radius:3px;color:#7a6e5f;cursor:pointer;
        border-left:2px solid transparent}
    .li:hover{background:#13101a;color:#ede0c4}
    .li.cur{background:#1e1a2a;color:#ede0c4}
    .li.liA{border-left-color:#3dbfad33}
    .li.liB{border-left-color:#e8784d33}
    .li.liE{border-left-color:#d4a84333}

    /* ── Nav bar ─────────────────────────────────────────────────────────── */
    #nav{background:#13101a;border-top:1px solid #1e1a2a;padding:8px 14px;
         display:flex;align-items:center;gap:8px;flex-shrink:0}
    .nb{background:#1a1320;border:1px solid #2a2430;color:#ede0c4;border-radius:5px;
        padding:5px 10px;cursor:pointer;font-size:13px;line-height:1;transition:background .1s}
    .nb:hover{background:#2a2430}
    .nb:disabled{opacity:.3;cursor:default}
    #pbtn{background:#3dbfad22;border-color:#3dbfad44;color:#3dbfad;font-size:14px}
    #pbtn:hover{background:#3dbfad44}
    #prog{flex:1;-webkit-appearance:none;appearance:none;height:5px;border-radius:3px;
          background:#1e1a2a;cursor:pointer;outline:none}
    #prog::-webkit-slider-thumb{-webkit-appearance:none;width:13px;height:13px;
          border-radius:50%;background:#d4a843;cursor:pointer}
    #prog::-moz-range-thumb{width:13px;height:13px;border-radius:50%;background:#d4a843;border:none}
    #fc{font-size:11px;color:#7a6e5f;white-space:nowrap;min-width:60px;text-align:center}
    #ti{font-size:11px;background:#1a1320;border:1px solid #2a2430;border-radius:4px;
        padding:3px 9px;color:#d4a843;white-space:nowrap}
    .spd{background:#1a1320;border:1px solid #2a2430;color:#7a6e5f;border-radius:4px;
         padding:3px 5px;font-size:10px;cursor:pointer}
  </style>
</head>
<body>

<div id="hdr">
  <h1>🎬 REPLAY</h1>
  <span id="ha" class="pname" style="font-size:11px;padding:2px 8px;border-radius:4px"></span>
  <span class="vs">vs</span>
  <span id="hb" class="pname" style="font-size:11px;padding:2px 8px;border-radius:4px"></span>
  <span id="hwinner" class="badge badge-win" style="display:none"></span>
  <span id="hmode"   class="badge badge-mode"></span>
  <div class="flex1"></div>
  <span id="hdur" class="dur"></span>
  <a id="back" href="/replays">← Všechny replaye</a>
</div>

<div id="main">

  <div class="panel pa" id="pa">
    <div class="pname" id="na"></div>
    <div class="stat-row">
      <span class="stat-lbl">🏰 Hrad</span>
      <div class="bar-bg"><div class="bar bar-castle" id="bca" style="width:0"></div></div>
      <span class="stat-num" id="vca"></span>
    </div>
    <div class="stat-row">
      <span class="stat-lbl">🧱 Zeď</span>
      <div class="bar-bg"><div class="bar bar-wall" id="bwa" style="width:0"></div></div>
      <span class="stat-num" id="vwa"></span>
    </div>
    <div class="res-grid" id="ra"></div>
    <div class="deck-row" id="da"></div>
    <div class="sect-lbl">Ruka</div>
    <div class="hand-list" id="ha2"></div>
  </div>

  <div id="ctr">
    <div class="ev-card" id="evc">
      <div class="ev-who"    id="ewho"></div>
      <div class="ev-name"   id="ename"></div>
      <div id="ecb"></div>
      <div class="ev-meta"   id="emeta"></div>
      <div class="extras"    id="eext"></div>
    </div>
    <div id="minilog">
      <h3>📜 Průběh hry</h3>
      <div id="loglist"></div>
    </div>
  </div>

  <div class="panel pb" id="pb">
    <div class="pname" id="nb"></div>
    <div class="stat-row">
      <span class="stat-lbl">🏰 Hrad</span>
      <div class="bar-bg"><div class="bar bar-castle" id="bcb" style="width:0"></div></div>
      <span class="stat-num" id="vcb"></span>
    </div>
    <div class="stat-row">
      <span class="stat-lbl">🧱 Zeď</span>
      <div class="bar-bg"><div class="bar bar-wall" id="bwb" style="width:0"></div></div>
      <span class="stat-num" id="vwb"></span>
    </div>
    <div class="res-grid" id="rb"></div>
    <div class="deck-row" id="db"></div>
    <div class="sect-lbl">Ruka</div>
    <div class="hand-list" id="hb2"></div>
  </div>

</div>

<div id="nav">
  <button class="nb" id="bf"   title="Začátek [Home]">⏮</button>
  <button class="nb" id="bprev" title="Předchozí [←]">⏪</button>
  <button class="nb" id="pbtn" title="Přehrát / Pauza [Mezerník]">▶</button>
  <button class="nb" id="bnext" title="Další [→]">⏩</button>
  <button class="nb" id="bl"   title="Konec [End]">⏭</button>
  <input  type="range" id="prog" min="0" value="0">
  <span id="fc">– / –</span>
  <span id="ti">Tah –</span>
  <select class="spd" id="spd" title="Rychlost">
    <option value="2200">0.5×</option>
    <option value="1400" selected>1×</option>
    <option value="800">1.5×</option>
    <option value="350">3×</option>
  </select>
</div>

<script>
const META   = ${safeJson(meta)};
const FRAMES = ${safeJson(frames)};

// ── Konstanty ────────────────────────────────────────────────────────────────
const RI = { MAGIC:'🔮', ATTACK:'⚔️', STONES:'🪨', CHAOS:'🌀' };
const CL = { MAGIC:'Magie', ATTACK:'Útok', STONES:'Kameny', CHAOS:'Chaos' };

// ── Stav přehrávače ──────────────────────────────────────────────────────────
let fi = 0, playing = false, timer = null;

// ── Inicializace hlavičky ────────────────────────────────────────────────────
$('ha').textContent  = META.nameA;
$('hb').textContent  = META.nameB;
$('na').textContent  = META.nameA;
$('nb').textContent  = META.nameB;
$('hmode').textContent = META.mode === 'super_random' ? '🎲 Super Náhodný' : '⚔️ Constructed';
if (META.winner) {
  const wb = $('hwinner');
  wb.style.display = '';
  wb.textContent = META.winner === 'A' ? '🏆 ' + META.nameA
                 : META.winner === 'B' ? '🏆 ' + META.nameB
                 : '🤝 Remíza';
}
if (META.durationSec) $('hdur').textContent = fmt(META.durationSec);

// ── Mini log ─────────────────────────────────────────────────────────────────
const ll = $('loglist');
FRAMES.forEach((fr, i) => {
  const el = document.createElement('div');
  el.className = 'li ' + (fr.event === 'game_end' ? 'liE' : fr.side === 'A' ? 'liA' : 'liB');
  el.dataset.i = i;
  if (fr.event === 'game_end') {
    const w = META.winner === 'A' ? '🏆 ' + META.nameA
            : META.winner === 'B' ? '🏆 ' + META.nameB
            : '🤝 Remíza';
    el.textContent = 'Konec · ' + w;
  } else {
    const who = fr.side === 'A' ? META.nameA : META.nameB;
    const act = fr.action === 'play' ? 'zahrál' : 'odhodil';
    const c   = fr.costPaid != null ? ' · ' + fr.costPaid + ' ' + (CL[fr.costType] || '') : '';
    el.textContent = 'T' + fr.turn + ' · ' + who + ' ' + act + ' ' + (fr.cardName || '—') + c;
  }
  el.addEventListener('click', () => { pause(); goto(i); });
  ll.appendChild(el);
});

// ── Progress slider ──────────────────────────────────────────────────────────
const prog = $('prog');
prog.max = Math.max(0, FRAMES.length - 1);
prog.addEventListener('input', () => { pause(); goto(+prog.value); });

// ── Tlačítka ─────────────────────────────────────────────────────────────────
$('bf')   .addEventListener('click', () => { pause(); goto(0); });
$('bprev').addEventListener('click', () => { pause(); goto(fi - 1); });
$('pbtn') .addEventListener('click', togglePlay);
$('bnext').addEventListener('click', () => { pause(); goto(fi + 1); });
$('bl')   .addEventListener('click', () => { pause(); goto(FRAMES.length - 1); });

document.addEventListener('keydown', e => {
  const tag = e.target.tagName;
  if (tag === 'INPUT' || tag === 'SELECT') return;
  if (e.key === 'ArrowLeft')  { pause(); goto(fi - 1); }
  if (e.key === 'ArrowRight') { pause(); goto(fi + 1); }
  if (e.key === 'Home')       { pause(); goto(0); }
  if (e.key === 'End')        { pause(); goto(FRAMES.length - 1); }
  if (e.key === ' ')          { e.preventDefault(); togglePlay(); }
});

// ── Autoplay ─────────────────────────────────────────────────────────────────
function togglePlay() {
  if (playing) pause(); else play();
}
function play() {
  if (fi >= FRAMES.length - 1) goto(0);
  playing = true;
  $('pbtn').textContent = '⏸';
  tick();
}
function pause() {
  playing = false;
  $('pbtn').textContent = '▶';
  if (timer) { clearTimeout(timer); timer = null; }
}
function tick() {
  if (!playing) return;
  const delay = +($('spd').value) || 1400;
  timer = setTimeout(() => {
    if (!playing) return;
    if (fi < FRAMES.length - 1) { goto(fi + 1); tick(); }
    else pause();
  }, delay);
}

// ── goto ─────────────────────────────────────────────────────────────────────
function goto(idx) {
  if (!FRAMES.length) return;
  fi = Math.max(0, Math.min(FRAMES.length - 1, idx));
  prog.value = fi;
  $('fc').textContent = (fi + 1) + ' / ' + FRAMES.length;
  $('bf').disabled    = fi === 0;
  $('bprev').disabled = fi === 0;
  $('bnext').disabled = fi === FRAMES.length - 1;
  $('bl').disabled    = fi === FRAMES.length - 1;
  // Zvýraznění v logu
  document.querySelectorAll('.li').forEach(el => {
    el.classList.toggle('cur', +el.dataset.i === fi);
  });
  const cur = document.querySelector('.li.cur');
  if (cur) cur.scrollIntoView({ block: 'nearest' });
  render(FRAMES[fi]);
}

// ── Render snímku ─────────────────────────────────────────────────────────────
function render(fr) {
  if (!fr.state) { renderEvent(fr); return; }
  const t = fr.event === 'game_end' ? (fr.turnNumber || '?') : fr.turn;
  $('ti').textContent = 'Tah ' + t;
  renderSide('a', fr.state.A, fr.side === 'A' ? fr.action : null);
  renderSide('b', fr.state.B, fr.side === 'B' ? fr.action : null);
  renderEvent(fr);
}

function renderSide(s, st, act) {
  const isA = s === 'a';
  const wt  = isA ? META.winTargetA : META.winTargetB;
  const nm  = isA ? META.nameA : META.nameB;
  const ns  = isA ? 'A' : 'B';

  // Jméno / zvýraznění
  const nameEl = $(s === 'a' ? 'na' : 'nb');
  nameEl.className = 'pname ' + (act === 'play' ? ('active-' + ns) : '');
  nameEl.textContent = nm;

  // Hrad
  const cpct = Math.max(0, Math.min(100, (st.hp / wt) * 100));
  const bc   = $(s === 'a' ? 'bca' : 'bcb');
  bc.style.width      = cpct + '%';
  bc.style.background = cpct > 60 ? '#3dbfad' : cpct > 30 ? '#d4a843' : '#e74c3c';
  $(s === 'a' ? 'vca' : 'vcb').textContent = st.hp + '/' + wt;

  // Zeď
  const wpct = Math.max(0, Math.min(100, st.wall));
  $(s === 'a' ? 'bwa' : 'bwb').style.width = wpct + '%';
  $(s === 'a' ? 'vwa' : 'vwb').textContent = st.wall || 0;

  // Zdroje
  const resEl = $(s === 'a' ? 'ra' : 'rb');
  resEl.innerHTML = '';
  const mines = st.mines || {};
  for (const r of ['MAGIC','ATTACK','STONES','CHAOS']) {
    const amt = (st.res && st.res[r]) || 0;
    const mn  = mines[r] || 0;
    const d   = document.createElement('div');
    d.className = 'res-item res-' + r;
    d.innerHTML = '<span class="ri">' + RI[r] + '</span>'
      + '<span class="rv">' + amt + '</span>'
      + '<span class="rm">+' + mn + '⛏</span>';
    resEl.appendChild(d);
  }

  // Balíček / odhoz
  const deckEl = $(s === 'a' ? 'da' : 'db');
  deckEl.innerHTML = '<span class="dbadge">🂠&nbsp;' + (st.deck || 0) + '</span>'
    + '<span class="dbadge">🗑&nbsp;' + (st.discard || 0) + '</span>';

  // Ruka
  const handEl = $(s === 'a' ? 'ha2' : 'hb2');
  handEl.innerHTML = '';
  const hand = st.hand || [];
  if (!hand.length) {
    handEl.innerHTML = '<div style="color:#7a6e5f;font-size:10px">Prázdná ruka</div>';
  } else {
    for (const c of hand) {
      const d = document.createElement('div');
      d.className = 'hcard ' + (c.costType || '');
      const cost = c.cost != null ? (c.cost + ' ' + (RI[c.costType] || '')) : '—';
      d.innerHTML = '<span class="hcn">' + he(c.name || c.id) + '</span>'
        + '<span class="hcc">' + cost + '</span>';
      handEl.appendChild(d);
    }
  }
}

function renderEvent(fr) {
  const ec  = $('evc');
  const ext = $('eext');
  ext.innerHTML = '';

  if (fr.event === 'game_end') {
    ec.className = 'ev-card end';
    $('ewho').textContent   = 'Konec hry';
    $('ename').textContent  = META.winner === 'A' ? '🏆 ' + META.nameA + ' vítězí!'
                            : META.winner === 'B' ? '🏆 ' + META.nameB + ' vítězí!'
                            : '🤝 Remíza';
    $('ecb').innerHTML = '';
    $('emeta').textContent = META.durationSec ? ('Délka: ' + fmt(META.durationSec)) : '';
    return;
  }

  const who = fr.side === 'A' ? META.nameA : META.nameB;
  const act = fr.action === 'play' ? 'zahrál' : 'odhodil';
  ec.className = 'ev-card ' + (fr.action === 'play' ? ('play-' + fr.side) : '');
  $('ewho').textContent  = who + ' ' + act + ':';
  $('ename').textContent = fr.cardName || '—';
  $('emeta').textContent = 'Tah ' + fr.turn;

  if (fr.action === 'play' && fr.costPaid != null) {
    const ct  = fr.costType || '';
    const cls = 'cbMAGIC CBATTACK cbSTONES cbCHAOS'.includes('cb'+ct) ? ('cb'+ct) : 'cbNONE';
    $('ecb').innerHTML = '<span class="ev-cbadge ' + cls + '">'
      + (RI[ct] || '') + ' ' + (fr.isXCost ? 'X=' : '') + fr.costPaid + ' ' + (CL[ct] || '—')
      + '</span>';
  } else {
    $('ecb').innerHTML = '';
  }

  for (const ex of (fr.extra || [])) {
    const d = document.createElement('div');
    if (ex.event === 'card_lost') {
      d.className = 'ex ex-lost';
      const w2 = ex.causedBy === 'A' ? META.nameA : META.nameB;
      d.textContent = (ex.action === 'BURNED' ? '🔥 Spáleno' : '🎭 Ukradeno')
        + ': ' + (ex.cardName || '?') + ' (zavinil: ' + w2 + ')';
    } else if (ex.event === 'decision') {
      d.className = 'ex ex-decision';
      const w2 = ex.side === 'A' ? META.nameA : META.nameB;
      d.textContent = '🎯 ' + w2 + ' zvolil: ' + (ex.chosenName || ex.chosenId || '—');
    }
    ext.appendChild(d);
  }
}

// ── Utils ─────────────────────────────────────────────────────────────────────
function $(id) { return document.getElementById(id); }
function he(s) { return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
function fmt(sec) {
  return Math.floor(sec / 60) + ':' + String(sec % 60).padStart(2,'0');
}

// ── Start ─────────────────────────────────────────────────────────────────────
if (FRAMES.length > 0) goto(0);
</script>
</body>
</html>`;
}

module.exports = { parseReplay, listReplays, buildListHtml, buildViewerHtml };
