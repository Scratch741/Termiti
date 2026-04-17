/**
 * Termiti – Lobby server (Etapa 2)
 * WebSocket server: registrace hráčů, online count, matchmaking fronta
 *
 * Port: 8765
 * Path: /lobby
 *
 * Protokol (JSON přes WebSocket):
 *   Klient → Server:
 *     { type:"JOIN",        name:"..." }
 *     { type:"QUEUE_JOIN" }
 *     { type:"QUEUE_LEAVE" }
 *     { type:"PING" }
 *
 *   Server → Klient:
 *     { type:"WELCOME",     online:N, queue:N }
 *     { type:"COUNT",       online:N, queue:N }
 *     { type:"QUEUE_OK" }
 *     { type:"MATCH_FOUND", gameId:"...", opponentName:"...", side:"A"|"B" }
 *     { type:"ERROR",       msg:"..." }
 *     { type:"PONG" }
 */

'use strict';

const WebSocket = require('ws');
const { v4: uuidv4 } = require('uuid');

const PORT = 8765;
const PATH = '/lobby';

// ── Server ────────────────────────────────────────────────────────────────────

const wss = new WebSocket.Server({ port: PORT });

// Hráči: Map<WebSocket, { id, name, inQueue }>
const players = new Map();

// Matchmaking fronta (seřazená podle příchodu)
const queue = [];

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
    queue:  queue.length
  });
  for (const ws of players.keys()) {
    if (ws.readyState === WebSocket.OPEN) ws.send(msg);
  }
}

function removeFromQueue(ws) {
  const idx = queue.indexOf(ws);
  if (idx !== -1) queue.splice(idx, 1);
  const p = players.get(ws);
  if (p) p.inQueue = false;
}

function log(tag, msg) {
  const ts = new Date().toISOString().replace('T', ' ').slice(0, 19);
  console.log(`[${ts}] [${tag}] ${msg}`);
}

// ── Matchmaking ───────────────────────────────────────────────────────────────

function tryMatch() {
  while (queue.length >= 2) {
    const wsA = queue.shift();
    const wsB = queue.shift();

    const pA = players.get(wsA);
    const pB = players.get(wsB);

    // Jeden z hráčů se mezitím odpojil → vrátíme platného zpět
    const aOk = pA && wsA.readyState === WebSocket.OPEN;
    const bOk = pB && wsB.readyState === WebSocket.OPEN;

    if (!aOk && !bOk) continue;
    if (!aOk) { queue.unshift(wsB); if (pB) pB.inQueue = true; continue; }
    if (!bOk) { queue.unshift(wsA); if (pA) pA.inQueue = true; continue; }

    pA.inQueue = false;
    pB.inQueue = false;

    const gameId = uuidv4();
    log('MATCH', `${pA.name} vs ${pB.name} | game ${gameId}`);

    send(wsA, { type: 'MATCH_FOUND', gameId, opponentName: pB.name, side: 'A' });
    send(wsB, { type: 'MATCH_FOUND', gameId, opponentName: pA.name, side: 'B' });

    broadcastCount();
  }
}

// ── Příchozí spojení ──────────────────────────────────────────────────────────

wss.on('connection', (ws, req) => {
  // Zkontroluj cestu
  const url = req.url || '';
  if (!url.startsWith(PATH)) {
    ws.close(1008, 'wrong path');
    return;
  }

  const ip = req.socket.remoteAddress;
  log('+', `Spojení z ${ip}`);

  ws.on('message', (raw) => {
    let msg;
    try { msg = JSON.parse(raw); }
    catch { return; }

    const player = players.get(ws);

    switch (msg.type) {

      // ── Registrace hráče ───────────────────────────────────────────────────
      case 'JOIN': {
        if (player) {
          send(ws, { type: 'ERROR', msg: 'Už jsi přihlášen' });
          return;
        }
        const name = String(msg.name ?? '').trim().slice(0, 20);
        if (!name) {
          send(ws, { type: 'ERROR', msg: 'Přezdívka nesmí být prázdná' });
          return;
        }
        // Unikátnost jména
        const taken = [...players.values()].some(p => p.name === name);
        if (taken) {
          send(ws, { type: 'ERROR', msg: `Přezdívka "${name}" je obsazena` });
          return;
        }

        players.set(ws, { id: uuidv4(), name, inQueue: false });
        log('JOIN', `${name} (celkem online: ${players.size})`);

        send(ws, {
          type:   'WELCOME',
          online: players.size,
          queue:  queue.length
        });
        broadcastCount();
        break;
      }

      // ── Vstup do matchmaking fronty ────────────────────────────────────────
      case 'QUEUE_JOIN': {
        if (!player) { send(ws, { type: 'ERROR', msg: 'Nejsi přihlášen' }); return; }
        if (player.inQueue) return;

        player.inQueue = true;
        queue.push(ws);
        log('QUEUE', `${player.name} vstoupil do fronty (${queue.length} čeká)`);

        send(ws, { type: 'QUEUE_OK' });
        broadcastCount();
        tryMatch();
        break;
      }

      // ── Odchod z fronty ────────────────────────────────────────────────────
      case 'QUEUE_LEAVE': {
        if (!player) return;
        removeFromQueue(ws);
        log('QUEUE', `${player.name} opustil frontu`);
        broadcastCount();
        break;
      }

      // ── Keepalive ──────────────────────────────────────────────────────────
      case 'PING': {
        send(ws, { type: 'PONG' });
        break;
      }

      default:
        log('WARN', `Neznámý typ zprávy: ${msg.type}`);
    }
  });

  ws.on('close', (code, reason) => {
    const player = players.get(ws);
    if (player) {
      removeFromQueue(ws);
      players.delete(ws);
      log('-', `${player.name} odpojen (kód ${code}) | online: ${players.size}`);
      broadcastCount();
    }
  });

  ws.on('error', (err) => {
    log('ERR', err.message);
  });
});

log('START', `Lobby server běží na ws://0.0.0.0:${PORT}${PATH}`);
log('START', `Online: 0 hráčů`);
