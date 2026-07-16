/**
 * Termiti – Lobby + Game server (Etapa 3)
 * WebSocket server: registrace hráčů, matchmaking, server-authoritative hra
 *
 * Port: 8765
 * Path: /lobby
 *
 * ── Lobby protokol ──────────────────────────────────────────────────────────
 * Klient → Server:
 *   { type:"JOIN",              protocolVersion:N, name:"...", avatar:"..." }
 *   { type:"QUEUE_JOIN" }
 *   { type:"QUEUE_LEAVE" }
 *   { type:"PING" }
 *
 * Server → Klient:
 *   { type:"WELCOME",           online:N, queue:N }
 *   { type:"COUNT",             online:N, queue:N }
 *   { type:"QUEUE_OK" }
 *   { type:"MATCH_FOUND",       gameId:"...", opponentName:"...", opponentAvatar:"...", side:"A"|"B" }
 *   { type:"VERSION_MISMATCH",  server:N, client:N, msg:"..." }
 *   { type:"ERROR",             msg:"..." }
 *   { type:"PONG" }
 *
 * ── Game protokol ────────────────────────────────────────────────────────────
 * Klient → Server:
 *   { type:"MULLIGAN_DONE",     gameId:"...", returnIds:["001_1",...] }
 *   { type:"GAME_ACTION",       gameId:"...", action:"PLAY_CARD"|"DISCARD_CARD"|"END_TURN"|"SKIP_TURN", data:{...} }
 *   { type:"GAME_ACTION",       gameId:"...", action:"DECISION_RESPONSE", chosenId:"..." }
 *
 * Server → Klient:
 *   { type:"GAME_MULLIGAN",     hand:[...] }
 *   { type:"MULLIGAN_OK",       hand:[...] }
 *   { type:"OPPONENT_MULLIGAN_DONE" }
 *   { type:"GAME_STATE",        activeSide, isMyTurn, turnNumber, myState, oppState, log }
 *   { type:"DECISION_REQUEST", effectType, cardType?, picks, options:[{id,baseId,name,...}], timeoutMs }
 *   { type:"CARD_LOST",         cardId, action:"STOLEN"|"BURNED" }
 *   { type:"GAME_OVER",              winner:"A"|"B"|"DRAW", winnerName, youWin }
 *   { type:"GAME_ERROR",             msg:"..." }
 *   { type:"OPPONENT_LEFT" }
 *   { type:"OPPONENT_DISCONNECTED",  timeoutSec:N }
 *   { type:"OPPONENT_RECONNECTED" }
 */

'use strict';

const http      = require('http');
const WebSocket = require('ws');
const { v4: uuidv4 } = require('uuid');
const { GameSession } = require('./game/GameSession');
const { ratingSystem } = require('./game/RatingSystem');
const { cleanupOldLogs } = require('./game/GameLogger');
const { parseReplay, listReplays, buildListHtml, buildViewerHtml } = require('./game/ReplayViewer');

// Smaž staré logy při startu + jednou denně
cleanupOldLogs();
setInterval(cleanupOldLogs, 24 * 60 * 60 * 1000);

const PORT = 8765;
const PATH = '/lobby';

/**
 * Verze síťového protokolu klient↔server. Klient ji posílá v JOIN; při neshodě
 * server odmítne registraci zprávou VERSION_MISMATCH (klient → "aktualizuj appku").
 * BUMP při KAŽDÉ breaking změně protokolu/sdílených karetních dat.
 * Musí odpovídat PROTOCOL_VERSION v app/.../OnlineLobbyViewModel.kt.
 */
const PROTOCOL_VERSION = 1;

// ── HTTP server (sdílený pro WS + REST + HTML) ────────────────────────────────

// ── Crash log adresář ─────────────────────────────────────────────────────────
const fs           = require('fs');
const nodePath     = require('path');
const ART_DIR      = nodePath.join(__dirname, 'art');
// Ukládáme do logs/crash_logs/ – tento adresář má ReadWritePaths v systemd (User=nobody)
const CRASH_LOG_DIR = nodePath.join(__dirname, 'logs', 'crash_logs');
try {
  if (!fs.existsSync(CRASH_LOG_DIR)) fs.mkdirSync(CRASH_LOG_DIR, { recursive: true });
} catch (e) {
  console.warn('[crash] Nelze vytvořit adresář crash_logs:', e.message);
}

// ─────────────────────────────────────────────────────────────────────────────

const httpServer = http.createServer((req, res) => {
  const url   = new URL(req.url, `http://localhost:${PORT}`);
  const path  = url.pathname;

  // CORS pro Android klienty
  res.setHeader('Access-Control-Allow-Origin', '*');

  // ── GET /leaderboard?mode=normal&limit=20 ────────────────────────────────
  if (path === '/leaderboard') {
    const mode  = url.searchParams.get('mode')  || 'normal';
    const limit = Math.min(parseInt(url.searchParams.get('limit') || '20', 10), 100);
    const data  = ratingSystem.getLeaderboard(mode, limit);
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.end(JSON.stringify({ mode, players: data, total: ratingSystem.getTotalPlayers() }));
    return;
  }

  // ── GET /time → autoritativní server čas (anti-cheat pro denní questy) ──
  if (path === '/time') {
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.end(JSON.stringify({ serverTimeMs: Date.now() }));
    return;
  }

  // ── GET /art/<name>.webp → card art thumbnails ──────────────────────────
  if (path.startsWith('/art/')) {
    const name = nodePath.basename(path);
    if (!/^art_[\w]+\.webp$/.test(name)) { res.writeHead(400); res.end('bad'); return; }
    const full = nodePath.join(ART_DIR, name);
    if (!fs.existsSync(full)) { res.writeHead(404); res.end('not found'); return; }
    res.setHeader('Content-Type', 'image/webp');
    res.setHeader('Cache-Control', 'public, max-age=86400');
    fs.createReadStream(full).pipe(res);
    return;
  }

  // ── GET /replays → seznam všech replay souborů ───────────────────────────
  if (path === '/replays') {
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.end(buildListHtml(listReplays()));
    return;
  }

  // ── GET /replay?id=<gameId> → vizuální přehrávač ─────────────────────────
  if (path === '/replay') {
    const gameId = url.searchParams.get('id') || '';
    if (!gameId || !/^[0-9a-f-]{8,36}$/i.test(gameId)) {
      res.writeHead(400);
      res.end('Chybí nebo neplatný parametr id');
      return;
    }
    const events = parseReplay(gameId);
    if (!events) {
      res.writeHead(404);
      res.end('Replay nenalezen: ' + gameId);
      return;
    }
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.end(buildViewerHtml(gameId, events));
    return;
  }

  // ── GET /replay-data?id=<gameId> → raw JSON pole eventů ─────────────────
  if (path === '/replay-data') {
    const gameId = url.searchParams.get('id') || '';
    if (!gameId || !/^[0-9a-f-]{8,36}$/i.test(gameId)) {
      res.writeHead(400);
      res.end('{"error":"bad id"}');
      return;
    }
    const events = parseReplay(gameId);
    if (!events) {
      res.writeHead(404);
      res.end('{"error":"not found"}');
      return;
    }
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.end(JSON.stringify(events));
    return;
  }

  // ── POST /crash-report → uloží crash log z Android klienta ─────────────
  if (path === '/crash-report' && req.method === 'POST') {
    let body = '';
    req.on('data', chunk => { body += chunk; if (body.length > 64_000) body = body.slice(0, 64_000); });
    req.on('end', () => {
      try {
        const report   = JSON.parse(body);
        const ts       = new Date().toISOString().replace(/[:.]/g, '-');
        const type     = report.type === 'non_fatal' ? 'warn' : 'crash';
        const filename = `${type}_${ts}.json`;
        fs.writeFileSync(nodePath.join(CRASH_LOG_DIR, filename), JSON.stringify(report, null, 2), 'utf8');
        console.log(`[crash] Uložen ${filename} — ${report.screen || '?'} / ${report.lastAction || '?'}`);
        res.setHeader('Content-Type', 'application/json');
        res.end('{"ok":true}');
      } catch (e) {
        res.writeHead(400);
        res.end('{"error":"bad json"}');
      }
    });
    return;
  }

  // ── GET /crash-logs → HTML přehled crash logů ────────────────────────────
  if (path === '/crash-logs') {
    const files = fs.existsSync(CRASH_LOG_DIR)
      ? fs.readdirSync(CRASH_LOG_DIR).filter(f => f.endsWith('.json')).sort().reverse()
      : [];
    const rows = files.map(f => {
      let info = {};
      try { info = JSON.parse(fs.readFileSync(nodePath.join(CRASH_LOG_DIR, f), 'utf8')); } catch (_) {}
      const isCrash = f.startsWith('crash_');
      const color   = isCrash ? '#bf2d2d' : '#d4a843';
      const badge   = isCrash ? '💥 CRASH' : '⚠️ warn';
      return `<tr>
        <td style="color:${color};font-weight:bold">${badge}</td>
        <td>${esc(info.timestamp || f)}</td>
        <td>${esc(info.version || '?')}</td>
        <td>${esc(info.device || '?')}</td>
        <td>${esc(info.screen || '?')}</td>
        <td>${esc(info.lastAction || '?')}</td>
        <td><a href="/crash-log?id=${encodeURIComponent(f)}" style="color:#3dbfad">detail</a></td>
      </tr>`;
    }).join('');
    const html = `<!DOCTYPE html><html lang="cs"><head>
      <meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
      <title>Termiti – Crash Logy</title>
      <style>
        body{background:#0d0a0e;color:#ede0c4;font-family:monospace;padding:24px;max-width:1100px;margin:0 auto}
        h1{color:#d4a843;letter-spacing:3px}
        table{width:100%;border-collapse:collapse;margin-top:16px;font-size:13px}
        th{background:#1a1320;color:#7a6e5f;padding:6px 10px;text-align:left;letter-spacing:1px}
        td{padding:6px 10px;border-bottom:1px solid #1e1a2a;vertical-align:top}
        tr:hover td{background:#13101a}
        a{color:#3dbfad}
        .empty{color:#7a6e5f;margin-top:32px}
      </style></head><body>
      <h1>🪲 TERMITI – CRASH LOGY</h1>
      <p style="color:#7a6e5f">${files.length} záznam(ů) | <a href="/">← Žebříček</a></p>
      ${files.length === 0 ? '<p class="empty">Žádné crash logy.</p>' : `
      <table>
        <tr><th>Typ</th><th>Čas</th><th>Verze</th><th>Zařízení</th><th>Obrazovka</th><th>Poslední akce</th><th></th></tr>
        ${rows}
      </table>`}
      </body></html>`;
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.end(html);
    return;
  }

  // ── GET /crash-log?id=<filename> → detail jednoho logu ───────────────────
  if (path === '/crash-log') {
    const id       = url.searchParams.get('id') || '';
    const filename = nodePath.basename(id); // bezpečnostní sanitace
    const filepath = nodePath.join(CRASH_LOG_DIR, filename);
    if (!filename.endsWith('.json') || !fs.existsSync(filepath)) {
      res.writeHead(404); res.end('Not found'); return;
    }
    let report = {};
    try { report = JSON.parse(fs.readFileSync(filepath, 'utf8')); } catch (_) {}
    const stack = esc(report.stacktrace || '(žádný stacktrace)').replace(/\n/g, '<br>').replace(/\t/g, '&nbsp;&nbsp;&nbsp;&nbsp;');
    const html = `<!DOCTYPE html><html lang="cs"><head>
      <meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
      <title>Crash – ${esc(filename)}</title>
      <style>
        body{background:#0d0a0e;color:#ede0c4;font-family:monospace;padding:24px;max-width:900px;margin:0 auto}
        h1{color:#bf2d2d;letter-spacing:2px}
        .meta{background:#1a1320;border:1px solid #2a2030;border-radius:8px;padding:16px;margin:16px 0}
        .meta b{color:#d4a843}
        .stack{background:#0a080d;border:1px solid #2a2030;border-radius:8px;padding:16px;font-size:12px;line-height:1.6;overflow-x:auto;color:#ede0c4}
        a{color:#3dbfad}
      </style></head><body>
      <h1>🪲 ${report.type === 'non_fatal' ? '⚠️ NON-FATAL' : '💥 CRASH'}</h1>
      <p><a href="/crash-logs">← Zpět na seznam</a></p>
      <div class="meta">
        <b>Čas:</b> ${esc(report.timestamp || '?')}<br>
        <b>Verze:</b> ${esc(report.version || '?')} (build ${esc(String(report.versionCode || '?'))})<br>
        <b>Zařízení:</b> ${esc(report.device || '?')}<br>
        <b>Android:</b> ${esc(report.android || '?')}<br>
        <b>Vlákno:</b> ${esc(report.thread || '?')}<br>
        <b>Obrazovka:</b> ${esc(report.screen || '?')}<br>
        <b>Poslední akce:</b> ${esc(report.lastAction || '?')}
        ${report.tag ? `<br><b>Tag:</b> ${esc(report.tag)} — ${esc(report.message || '')}` : ''}
      </div>
      <div class="stack">${stack}</div>
      </body></html>`;
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.end(html);
    return;
  }

  // ── GET / → HTML stats stránka ───────────────────────────────────────────
  if (path === '/' || path === '/stats') {
    const modes  = ['normal', 'super_random'];
    const labels = { normal: 'Constructed', super_random: 'Super Náhodný' };
    let rows = '';
    for (const mode of modes) {
      const players = ratingSystem.getLeaderboard(mode, 50);
      if (players.length === 0) continue;
      rows += `<h2>${labels[mode] || mode}</h2><table>
        <tr><th>#</th><th>Hráč</th><th>⭐ Rating</th><th>W</th><th>L</th><th>%</th></tr>`;
      for (const p of players) {
        const wr = p.games > 0 ? Math.round((p.wins / p.games) * 100) : 0;
        rows += `<tr><td>${p.rank}</td><td>${esc(p.name)}</td>
          <td><b>${p.rating}</b></td><td>${p.wins}</td><td>${p.losses}</td><td>${wr}%</td></tr>`;
      }
      rows += '</table>';
    }
    const recentReplays = listReplays().slice(0, 5);
    const replayRows = recentReplays.map(r => {
      const winnerStr = r.winner === 'A' ? '🏆 ' + esc(r.nameA)
                      : r.winner === 'B' ? '🏆 ' + esc(r.nameB)
                      : r.winner === 'DRAW' ? '🤝 Remíza' : '—';
      const dur = r.durationSec
        ? Math.floor(r.durationSec/60) + ':' + String(r.durationSec%60).padStart(2,'0') : '?';
      return `<tr>
        <td class="ts">${esc(r.date)}</td>
        <td><b>${esc(r.nameA)}</b> <span style="color:#7a6e5f">vs</span> <b>${esc(r.nameB)}</b></td>
        <td>${winnerStr}</td>
        <td class="ts">${dur}</td>
        <td><a class="rlink" href="/replay?id=${esc(r.gameId)}">▶ Přehrát</a></td>
      </tr>`;
    }).join('');

    const html = `<!DOCTYPE html><html lang="cs"><head>
      <meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
      <title>Termiti – Žebříček</title>
      <style>
        body{background:#0d0a0e;color:#ede0c4;font-family:sans-serif;padding:24px;max-width:900px;margin:0 auto}
        h1{color:#d4a843;letter-spacing:3px}h2{color:#3dbfad;margin-top:32px;letter-spacing:1px;font-size:15px}
        table{width:100%;border-collapse:collapse;margin-top:8px}
        th{background:#1a1320;color:#7a6e5f;font-size:11px;letter-spacing:1px;padding:6px 10px;text-align:left}
        td{padding:6px 10px;border-bottom:1px solid #1e1a2a}
        tr:hover td{background:#13101a}
        td:nth-child(3){color:#d4a843;font-weight:bold}
        .badge{background:#3dbfad22;border:1px solid #3dbfad55;color:#3dbfad;padding:2px 8px;border-radius:4px;font-size:12px}
        .ts{color:#7a6e5f;font-size:11px}
        .nav{display:flex;gap:8px;margin:14px 0 24px}
        .btn{display:inline-block;padding:7px 16px;border-radius:6px;font-size:13px;text-decoration:none;
             border:1px solid;transition:background .15s;cursor:pointer}
        .btn-teal{background:#3dbfad22;border-color:#3dbfad55;color:#3dbfad}
        .btn-teal:hover{background:#3dbfad44}
        .btn-gold{background:#d4a84322;border-color:#d4a84355;color:#d4a843}
        .btn-gold:hover{background:#d4a84344}
        .btn-muted{background:#1a1320;border-color:#2a2430;color:#7a6e5f}
        .btn-muted:hover{background:#2a2430;color:#ede0c4}
        .rlink{color:#3dbfad;text-decoration:none;font-size:12px}
        .rlink:hover{text-decoration:underline}
        .more{display:inline-block;margin-top:8px;font-size:12px;color:#3dbfad;text-decoration:none}
        .more:hover{text-decoration:underline}
      </style></head><body>
      <h1>🏆 TERMITI</h1>
      <div class="nav">
        <a class="btn btn-gold" href="/">🏆 Žebříček</a>
        <a class="btn btn-teal" href="/replays">🎬 Replaye</a>
        <a class="btn btn-muted" href="/crash-logs">🐛 Crash logy</a>
      </div>
      <p><span class="badge">🟢 ${players ? players.size : 0} online</span></p>
      ${replayRows ? `
        <h2>🎬 Poslední hry</h2>
        <table>
          <tr><th>Datum</th><th>Hráči</th><th>Výsledek</th><th>Délka</th><th></th></tr>
          ${replayRows}
        </table>
        <a class="more" href="/replays">Zobrazit všechny replaye →</a>
      ` : ''}
      <h2>📊 Žebříček</h2>
      ${rows || '<p>Žádná data.</p>'}
      <p class="ts" style="margin-top:16px">Aktualizováno: ${new Date().toLocaleString('cs-CZ', { timeZone: 'Europe/Prague' })}</p>
      </body></html>`;
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.end(html);
    return;
  }

  res.writeHead(404);
  res.end('Not found');
});

function esc(s) {
  return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

// ── WebSocket server (sdílí HTTP server) ──────────────────────────────────────

const wss = new WebSocket.Server({ server: httpServer, path: PATH });

// Hráči v lobby: Map<WebSocket, { id, name, inQueue, gameId|null }>
const players = new Map();

// Matchmaking fronty
const queue      = [];        // normální / vlastní balíček
const superQueue = [];        // super náhodný (50 karet, 15/15/15/5)

// Aktivní hry: Map<gameId, GameSession>
const games = new Map();

// ── Reconnect ─────────────────────────────────────────────────────────────────
// Hráči, kteří se odpojili uprostřed hry a čekají na reconnect
// Map<name, { id, name, avatar, level, deviceId, gameId, side, reconnectDeadline }>
// reconnectDeadline = absolutní ms timestamp – prochází disconnect→reconnect→disconnect
// beze změny, takže při opakovaných odpojeních se čas neobnovuje.
const disconnectedPlayers = new Map();
// Aktivní reconnect timery: Map<name, timeoutHandle>
const reconnectTimers = new Map();
// Grace period před ukončením hry po odpojení
const RECONNECT_TIMEOUT_SEC = 60;

// ── Helpers ───────────────────────────────────────────────────────────────────

function send(ws, data) {
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(data));
  }
}

function broadcastCount() {
  const msg = JSON.stringify({
    type:   'COUNT',
    online: players.size,
    queue:  queue.length + superQueue.length
  });
  for (const ws of players.keys()) {
    if (ws.readyState === WebSocket.OPEN) ws.send(msg);
  }
}

// ── Heartbeat (detekce "zombie" spojení) ───────────────────────────────────────
// Bez tohoto: když klient zmizí bez čistého FIN/RST (výpadek WiFi, OS uspí appku,
// zabitá appka na pozadí – běžné na mobilu), TCP socket zůstane na serveru
// v limbu a ws.readyState hlásí OPEN klidně donekonečna (dokud OS TCP stack
// timeout nevyprší – řádově hodiny). Hráč tak zůstane navždy "duch" v players/
// queue/superQueue: nafoukne online count (nekonzistentně mezi zařízeními, podle
// toho kdy který klient dostal broadcastCount()) a matchmaking dvou SKUTEČNĚ
// online hráčů selže, pokud fronta obsahuje ducha (tryMatchFromQueue ho sice
// při shiftu odfiltruje díky readyState kontrole, ale jen když se DO fronty
// dostane – jinak prostě zůstává jako "online", i když spojení je mrtvé).
//
// Standardní ws-library řešení: pravidelný ping, pokud klient neodpoví pong
// do dalšího intervalu → terminate() (spustí 'close' → standardní cleanup).
function heartbeat() { this.isAlive = true; }

const HEARTBEAT_INTERVAL_MS = 30_000;
const heartbeatTimer = setInterval(() => {
  for (const ws of wss.clients) {
    if (ws.isAlive === false) {
      const p = players.get(ws);
      log('HEARTBEAT', `Zombie spojení ukončeno${p ? ` (${p.name})` : ''} – klient neodpověděl na ping`);
      ws.terminate();
      continue;
    }
    ws.isAlive = false;
    ws.ping();
  }
}, HEARTBEAT_INTERVAL_MS);
wss.on('close', () => clearInterval(heartbeatTimer));

function removeFromQueue(ws) {
  const idx = queue.indexOf(ws);
  if (idx !== -1) queue.splice(idx, 1);
  const si = superQueue.indexOf(ws);
  if (si !== -1) superQueue.splice(si, 1);
  const p = players.get(ws);
  if (p) p.inQueue = false;
}

function log(tag, msg) {
  const ts = new Date().toISOString().replace('T', ' ').slice(0, 19);
  console.log(`[${ts}] [${tag}] ${msg}`);
}

// ── Matchmaking ───────────────────────────────────────────────────────────────

function tryMatchFromQueue(q, mode) {
  while (q.length >= 2) {
    const wsA = q.shift();
    const wsB = q.shift();

    const pA = players.get(wsA);
    const pB = players.get(wsB);

    const aOk = pA && wsA.readyState === WebSocket.OPEN;
    const bOk = pB && wsB.readyState === WebSocket.OPEN;

    if (!aOk && !bOk) continue;
    if (!aOk) { q.unshift(wsB); if (pB) pB.inQueue = true; continue; }
    if (!bOk) { q.unshift(wsA); if (pA) pA.inQueue = true; continue; }

    pA.inQueue = false;
    pB.inQueue = false;

    const gameId = uuidv4();
    log('MATCH', `${pA.name} vs ${pB.name} | game ${gameId} [${mode}]`);

    pA.gameId = gameId; pB.gameId = gameId;
    pA.side   = 'A';    pB.side   = 'B';

    const ratingA = pA.deviceId ? ratingSystem.getRating(pA.deviceId, mode) : null;
    const ratingB = pB.deviceId ? ratingSystem.getRating(pB.deviceId, mode) : null;

    // Skóre v daném módu (výhry/prohry/remízy) – pro VS intro tabulku
    const packStats = (deviceId) => {
      const m = deviceId ? (ratingSystem.getStats(deviceId)?.modes?.[mode] ?? null) : null;
      return m ? { wins: m.wins || 0, losses: m.losses || 0, draws: m.draws || 0, games: m.games || 0 } : null;
    };
    const statsA = packStats(pA.deviceId);
    const statsB = packStats(pB.deviceId);

    send(wsA, { type: 'MATCH_FOUND', gameId, opponentName: pB.name, opponentAvatar: pB.avatar ?? '👺', opponentCardBackSkin: pB.cardBackSkin ?? 'card_back_frame', opponentCastleSkin: pB.castleSkin ?? 'castle_player', opponentLevel: pB.level ?? 1, opponentRating: ratingB, myRating: ratingA, myStats: statsA, opponentStats: statsB, opponentActiveAbilities: pB.activeAbilities ?? [], side: 'A', mode });
    send(wsB, { type: 'MATCH_FOUND', gameId, opponentName: pA.name, opponentAvatar: pA.avatar ?? '👺', opponentCardBackSkin: pA.cardBackSkin ?? 'card_back_frame', opponentCastleSkin: pA.castleSkin ?? 'castle_player', opponentLevel: pA.level ?? 1, opponentRating: ratingA, myRating: ratingB, myStats: statsB, opponentStats: statsA, opponentActiveAbilities: pA.activeAbilities ?? [], side: 'B', mode });

    const onGameEnd = (gid) => {
      // Vyčisti přes přímou WS referenci (standard)
      if (players.get(wsA)) { players.get(wsA).gameId = null; players.get(wsA).side = null; }
      if (players.get(wsB)) { players.get(wsB).gameId = null; players.get(wsB).side = null; }
      // Záchrana: pokud hráč reconnectoval s novou WS, stará reference je mrtvá →
      // projdeme všechny hráče a vymažeme gameId pro ty, kteří patřili do téhle hry.
      for (const [, p] of players) {
        if (p.gameId === gid) { p.gameId = null; p.side = null; }
      }
      games.delete(gid);
      log('GAME', `Session ${gid} ukončena a uvolněna`);
      broadcastCount();
    };

    const session = new GameSession(gameId, wsA, pA.name, wsB, pB.name, pA.deckIds, pB.deckIds, onGameEnd, mode,
      pA.activeAbilities || [], pB.activeAbilities || [], pA.deviceId || null, pB.deviceId || null);
    games.set(gameId, session);
    try {
      session.start();
    } catch (err) {
      log('ERR', `session.start() selhalo pro game ${gameId}: ${err.message}`);
      log('ERR', err.stack);
      send(wsA, { type: 'GAME_ERROR', msg: 'Chyba při spouštění hry. Zkus to znovu.' });
      send(wsB, { type: 'GAME_ERROR', msg: 'Chyba při spouštění hry. Zkus to znovu.' });
      games.delete(gameId);
      pA.gameId = null; pA.side = null;
      pB.gameId = null; pB.side = null;
    }

    broadcastCount();
  }
}

function tryMatch() {
  tryMatchFromQueue(queue,      'normal');
  tryMatchFromQueue(superQueue, 'super_random');
}

// ── Příchozí spojení ──────────────────────────────────────────────────────────

wss.on('connection', (ws, req) => {
  const url = req.url || '';
  if (!url.startsWith(PATH)) {
    ws.close(1008, 'wrong path');
    return;
  }

  const ip = req.socket.remoteAddress;
  log('+', `Spojení z ${ip}`);

  ws.isAlive = true;
  ws.on('pong', heartbeat);

  // ── Rate limiting ─────────────────────────────────────────────────────────
  let msgCount   = 0;
  let windowStart = Date.now();
  const MSG_LIMIT  = 30;   // max zpráv za okno
  const MSG_WINDOW = 1000; // ms

  ws.on('message', (raw) => {
    // Zkontroluj velikost zprávy
    if (raw.length > 8192) {
      log('RATELIMIT', `Příliš velká zpráva (${raw.length} B) od ${ip}`);
      return;
    }
    // Rate limit: max MSG_LIMIT zpráv za MSG_WINDOW ms
    const now = Date.now();
    if (now - windowStart > MSG_WINDOW) { msgCount = 0; windowStart = now; }
    msgCount++;
    if (msgCount > MSG_LIMIT) {
      log('RATELIMIT', `Flood od ${ip} (${msgCount} zpráv/${MSG_WINDOW}ms) – zahazuji`);
      return;
    }

    let msg;
    try { msg = JSON.parse(raw); }
    catch { return; }

    const player = players.get(ws);

    switch (msg.type) {

      // ── Registrace hráče ─────────────────────────────────────────────────────
      case 'JOIN': {
        if (player) {
          send(ws, { type: 'ERROR', msg: 'Už jsi přihlášen' });
          return;
        }
        // Kontrola verze protokolu – odmítni nekompatibilní (starší/novější) klienty.
        // Spojení nezavíráme, aby klient nespustil reconnect smyčku; jen odmítneme JOIN.
        if (msg.protocolVersion !== PROTOCOL_VERSION) {
          log('JOIN', `Odmítnut: protocolVersion klienta=${msg.protocolVersion}, server=${PROTOCOL_VERSION}`);
          send(ws, {
            type: 'VERSION_MISMATCH',
            server: PROTOCOL_VERSION,
            client: msg.protocolVersion ?? null,
            msg: 'Tvá verze hry je zastaralá. Aktualizuj aplikaci pro hraní online.'
          });
          return;
        }
        // Sanitizace jména: odstraň řídicí znaky, ponech jen tisknutelné znaky + mezery
        const rawName  = String(msg.name ?? '').replace(/[\x00-\x1F\x7F]/g, '').trim();
        const name     = [...rawName].slice(0, 20).join(''); // slice po znacích (bezpečné pro emoji)
        const deviceId = String(msg.deviceId ?? '').replace(/[\x00-\x1F\x7F]/g, '').trim().slice(0, 64);
        if (!name) {
          send(ws, { type: 'ERROR', msg: 'Přezdívka nesmí být prázdná' });
          return;
        }
        if (name.length < 2) {
          send(ws, { type: 'ERROR', msg: 'Přezdívka musí mít alespoň 2 znaky' });
          return;
        }

        // ── Reconnect po odpojení uprostřed hry (grace period) ──────────────────
        const dp = disconnectedPlayers.get(name);
        log('JOIN', `"${name}" – disconnectedPlayers=${!!dp} deviceMatch=${dp ? deviceId===dp.deviceId : 'N/A'}`);
        if (dp && deviceId && deviceId === dp.deviceId) {
          // Zrušit reconnect timer
          const timer = reconnectTimers.get(name);
          if (timer) { clearTimeout(timer); reconnectTimers.delete(name); }
          disconnectedPlayers.delete(name);

          const session = games.get(dp.gameId);
          log('RECONNECT', `Path1 "${name}" – session=${!!session} phase=${session?.phase}`);
          if (session && session.phase !== 'ended') {
            // Přepojit hráče
            players.set(ws, { id: dp.id, name, avatar: dp.avatar, level: dp.level,
                              deviceId, activeAbilities: dp.activeAbilities ?? [],
                              inQueue: false, gameId: dp.gameId, side: dp.side,
                              reconnectDeadline: dp.reconnectDeadline });  // ← deadline přežije další disconnect
            send(ws, { type: 'WELCOME', online: players.size, queue: queue.length });
            session.resendStateTo(dp.side, ws);
            // Informovat soupeře
            const opp = dp.side === 'A' ? 'B' : 'A';
            session._send(opp, { type: 'OPPONENT_RECONNECTED' });
            log('RECONNECT', `"${name}" se vrátil do hry ${dp.gameId} (grace period)`);
            broadcastCount();
            return;
          }
          games.delete(dp.gameId);
          log('RECONNECT', `"${name}" se vrátil, ale hra ${dp.gameId} již skončila`);
        } else if (dp) {
          // Jiné zařízení – zruš čekání (original hráč se ztratil)
          disconnectedPlayers.delete(name);
          const timer = reconnectTimers.get(name);
          if (timer) { clearTimeout(timer); reconnectTimers.delete(name); }
        }

        // Zkontroluj, zda nick není obsazený aktivním spojením
        for (const [existingWs, p] of players.entries()) {
          if (p.name !== name) continue;

          const isAlive = existingWs.readyState === WebSocket.OPEN;
          const sameDevice = deviceId && p.deviceId && deviceId === p.deviceId;

          if (!isAlive || sameDevice) {
            // Mrtvé spojení NEBO stejné zařízení → povol reconnect
            removeFromQueue(existingWs);
            existingWs.terminate();
            players.delete(existingWs);

            // Pokud běží hra a jde o stejné zařízení → NEZASTAVUJ hru, jen updatuj WS
            if (sameDevice && p.gameId) {
              const session = games.get(p.gameId);
              log('RECONNECT', `Path2 "${name}" – session=${!!session} phase=${session?.phase}`);
              if (session && session.phase !== 'ended') {
                // Přepoj hráče do existující hry
                players.set(ws, { id: p.id, name, avatar: p.avatar ?? 'player_icon_1', level: p.level ?? 1, deviceId, inQueue: false,
                                  gameId: p.gameId, side: p.side });
                send(ws, { type: 'WELCOME', online: players.size, queue: queue.length });
                session.resendStateTo(p.side, ws);
                log('RECONNECT', `"${name}" se vrátil do hry ${p.gameId}`);
                broadcastCount();
                return;
              }
              games.delete(p.gameId);
            }
            log('RECONNECT', `"${name}" se vrátil do lobby`);
          } else {
            // Jiné zařízení, živé spojení → blokuj
            send(ws, { type: 'ERROR', msg: `Přezdívka "${name}" je obsazena` });
            return;
          }
        }

        const rawAvatar = String(msg.avatar ?? '⚔️').replace(/[\x00-\x1F\x7F]/g, '').trim();
        const avatar = /^player_icon_\d{1,2}$/.test(rawAvatar)
            ? rawAvatar
            : ([...rawAvatar].slice(0, 2).join('') || '⚔️');
        const level  = Math.max(1, Math.min(9999, parseInt(msg.level) || 1));
        // Skin rubu karty – přijmi jen povolené hodnoty
        const KNOWN_CARD_BACKS = new Set(['card_back_frame', 'card_back_frame_2', 'card_back_frame_3']);
        const cardBackSkin = KNOWN_CARD_BACKS.has(msg.cardBackSkin) ? msg.cardBackSkin : 'card_back_frame';
        // Skin hradu – přijmi jen povolené hodnoty
        const KNOWN_CASTLE_SKINS = new Set(['castle_player', 'castle_player_2', 'castle_player_3']);
        const castleSkin = KNOWN_CASTLE_SKINS.has(msg.castleSkin) ? msg.castleSkin : 'castle_player';
        // Pasivní schopnosti – přijmi max 2 známá ID, ignoruj neznámá (anti-cheat)
        const KNOWN_ABILITIES = new Set([
          'extra_castle','extra_wall','extra_magic','extra_attack','extra_stones','extra_chaos',
          'quick_draw','boost_attack','boost_build','boost_magic','boost_chaos','boost_random',
          'extra_hand_card','iron_bastion'
        ]);
        const rawAbilities    = Array.isArray(msg.activeAbilities) ? msg.activeAbilities : [];
        const activeAbilities = rawAbilities
          .filter(a => typeof a === 'string' && KNOWN_ABILITIES.has(a))
          .slice(0, 2);
        players.set(ws, { id: uuidv4(), name, avatar, cardBackSkin, castleSkin, level, deviceId, activeAbilities, inQueue: false, gameId: null, side: null });
        log('JOIN', `${name} (online: ${players.size})`);

        // Pošli hráči jeho aktuální rating pro všechny módy
        const playerStats = deviceId ? ratingSystem.getStats(deviceId) : null;
        send(ws, {
          type:   'WELCOME',
          online: players.size,
          queue:  queue.length,
          ratings: playerStats ? playerStats.modes : {},
        });
        broadcastCount();
        break;
      }

      // ── Matchmaking ───────────────────────────────────────────────────────────
      case 'QUEUE_JOIN': {
        if (!player) { send(ws, { type: 'ERROR', msg: 'Nejsi přihlášen' }); return; }
        // Auto-cleanup: gameId je nastavené, ale hra už neexistuje (race condition / reconnect bug)
        if (player.gameId && !games.has(player.gameId)) {
          log('QUEUE', `${player.name}: stale gameId ${player.gameId} vymazán před vstupem do fronty`);
          player.gameId = null;
          player.side   = null;
        }
        if (player.inQueue || player.gameId) return;

        const mode = msg.mode === 'super_random' ? 'super_random' : 'normal';

        if (mode === 'super_random') {
          // Super náhodný: ignoruj vlastní balíček, speciální fronta
          player.deckIds = null;
          player.inQueue = true;
          superQueue.push(ws);
          log('QUEUE', `${player.name} čeká v SUPER frontě (${superQueue.length})`);
        } else {
          // Normální fronta
          console.log(`[QUEUE_JOIN] ${player.name}: deckIds type=${typeof msg.deckIds}, isArray=${Array.isArray(msg.deckIds)}, length=${Array.isArray(msg.deckIds) ? msg.deckIds.length : 'N/A'}, raw=${JSON.stringify(msg.deckIds)?.slice(0,80)}`);
          player.deckIds = Array.isArray(msg.deckIds) ? msg.deckIds : null;
          player.inQueue = true;
          queue.push(ws);
          log('QUEUE', `${player.name} čeká (${queue.length} ve frontě)${player.deckIds ? ' [vlastní balíček]' : ''}`);
        }

        send(ws, { type: 'QUEUE_OK', mode });
        broadcastCount();
        tryMatch();
        break;
      }

      case 'QUEUE_LEAVE': {
        if (!player) return;
        removeFromQueue(ws);
        log('QUEUE', `${player.name} opustil frontu`);
        broadcastCount();
        break;
      }

      // ── Keepalive ─────────────────────────────────────────────────────────────
      case 'PING': {
        send(ws, { type: 'PONG' });
        break;
      }

      // ── Mulligan potvrzení ────────────────────────────────────────────────────
      case 'MULLIGAN_DONE': {
        if (!player) return;
        const session = games.get(msg.gameId || player.gameId);
        // Ownership check: WS musí být účastník té konkrétní hry
        if (!session || (session.ws.A !== ws && session.ws.B !== ws)) {
          send(ws, { type: 'GAME_ERROR', msg: 'Hra nenalezena' }); return;
        }

        const returnIds = Array.isArray(msg.returnIds) ? msg.returnIds : [];
        session.handleMulligan(player.side, returnIds);
        break;
      }

      // ── Herní akce ────────────────────────────────────────────────────────────
      case 'GAME_ACTION': {
        if (!player) return;
        const session = games.get(msg.gameId || player.gameId);
        // Ownership check: WS musí být účastník té konkrétní hry
        if (!session || (session.ws.A !== ws && session.ws.B !== ws)) {
          send(ws, { type: 'GAME_ERROR', msg: 'Hra nenalezena' }); return;
        }

        // Vzdání – okamžitě ukonči hru, soupeř vítězí
        if (msg.action === 'FORFEIT') {
          if (session.phase === 'ended') return; // dvojitý klik
          const winner = player.side === 'A' ? 'B' : 'A';
          log('FORFEIT', `${player.name} (${player.side}) se vzdal ve hře ${player.gameId}`);
          session._endGame(winner); // pošle GAME_OVER oběma + zavolá onEnd → vyčistí games mapu
          return;
        }

        session.handleAction(player.side, msg.action, msg.data || {});
        break;
      }

      default:
        log('WARN', `Neznámý typ zprávy: ${msg.type}`);
    }
  });

  ws.on('close', (code) => {
    const player = players.get(ws);
    if (player) {
      removeFromQueue(ws);

      if (player.gameId) {
        const session = games.get(player.gameId);
        if (session && session.phase !== 'ended') {
          const opponent = player.side === 'A' ? 'B' : 'A';

          // code 1000 = úmyslné odpojení (hráč klikl Vzdát se / Odejít)
          // → hru okamžitě ukonči, žádný reconnect
          if (code === 1000) {
            session._endGame(opponent);
            session._send(opponent, { type: 'OPPONENT_LEFT' });
            games.delete(player.gameId);
            log('RECONNECT', `"${player.name}" se úmyslně odpojil (1000) – hra ${player.gameId} ukončena`);
          } else {
            // Výpadek sítě / crash → ulož hráče a dej mu čas na reconnect.
            // reconnectDeadline přežívá cyklus disconnect→reconnect→disconnect:
            // bereme ho z player.reconnectDeadline (nastavené při posledním reconnectu),
            // nebo vytváříme nový pro první odpojení.
            const reconnectDeadline = player.reconnectDeadline
              ?? (Date.now() + RECONNECT_TIMEOUT_SEC * 1000);
            const remainingMs = Math.max(5000, reconnectDeadline - Date.now());
            const timeoutSec  = Math.ceil(remainingMs / 1000);

            disconnectedPlayers.set(player.name, {
              id: player.id, name: player.name, avatar: player.avatar,
              level: player.level, deviceId: player.deviceId,
              activeAbilities: player.activeAbilities ?? [],
              gameId: player.gameId, side: player.side,
              reconnectDeadline,    // ← přenášíme deadline dál
            });

            session._send(opponent, { type: 'OPPONENT_DISCONNECTED', timeoutSec });
            log('RECONNECT', `"${player.name}" odpojen – zbývá ${timeoutSec}s do konce grace period`);

            const timer = setTimeout(() => {
              disconnectedPlayers.delete(player.name);
              reconnectTimers.delete(player.name);
              const s = games.get(player.gameId);
              if (s && s.phase !== 'ended') {
                s._endGame(opponent);
                s._send(opponent, { type: 'OPPONENT_LEFT' });
                games.delete(player.gameId);
              }
              log('RECONNECT', `"${player.name}" se nepřipojil včas – hra ${player.gameId} ukončena`);
            }, remainingMs);
            reconnectTimers.set(player.name, timer);
          }
        } else {
          games.delete(player.gameId);
        }
      }

      players.delete(ws);
      log('-', `${player.name} odpojen (kód ${code}) | online: ${players.size}`);
      broadcastCount();
    }
  });

  ws.on('error', (err) => {
    log('ERR', err.message);
  });
});

httpServer.listen(PORT, () => {
  log('START', `WebSocket:  ws://0.0.0.0:${PORT}${PATH}`);
  log('START', `HTTP API:   http://0.0.0.0:${PORT}/leaderboard?mode=normal`);
  log('START', `HTML stats: http://0.0.0.0:${PORT}/`);
});
