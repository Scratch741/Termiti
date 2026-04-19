/**
 * Termiti – Lobby + Game server (Etapa 3)
 * WebSocket server: registrace hráčů, matchmaking, server-authoritative hra
 *
 * Port: 8765
 * Path: /lobby
 *
 * ── Lobby protokol ──────────────────────────────────────────────────────────
 * Klient → Server:
 *   { type:"JOIN",              name:"..." }
 *   { type:"QUEUE_JOIN" }
 *   { type:"QUEUE_LEAVE" }
 *   { type:"PING" }
 *
 * Server → Klient:
 *   { type:"WELCOME",           online:N, queue:N }
 *   { type:"COUNT",             online:N, queue:N }
 *   { type:"QUEUE_OK" }
 *   { type:"MATCH_FOUND",       gameId:"...", opponentName:"...", side:"A"|"B" }
 *   { type:"ERROR",             msg:"..." }
 *   { type:"PONG" }
 *
 * ── Game protokol ────────────────────────────────────────────────────────────
 * Klient → Server:
 *   { type:"MULLIGAN_DONE",     gameId:"...", returnIds:["001_1",...] }
 *   { type:"GAME_ACTION",       gameId:"...", action:"PLAY_CARD"|"DISCARD_CARD"|"END_TURN"|"SKIP_TURN", data:{...} }
 *
 * Server → Klient:
 *   { type:"GAME_MULLIGAN",     hand:[...] }
 *   { type:"MULLIGAN_OK",       hand:[...] }
 *   { type:"OPPONENT_MULLIGAN_DONE" }
 *   { type:"GAME_STATE",        activeSide, isMyTurn, turnNumber, myState, oppState, log }
 *   { type:"CARD_LOST",         cardId, action:"STOLEN"|"BURNED" }
 *   { type:"GAME_OVER",         winner:"A"|"B"|"DRAW", winnerName, youWin }
 *   { type:"GAME_ERROR",        msg:"..." }
 *   { type:"OPPONENT_LEFT" }
 */

'use strict';

const WebSocket = require('ws');
const { v4: uuidv4 } = require('uuid');
const { GameSession } = require('./game/GameSession');

const PORT = 8765;
const PATH = '/lobby';

// ── Server ────────────────────────────────────────────────────────────────────

const wss = new WebSocket.Server({ port: PORT });

// Hráči v lobby: Map<WebSocket, { id, name, inQueue, gameId|null }>
const players = new Map();

// Matchmaking fronta
const queue = [];

// Aktivní hry: Map<gameId, GameSession>
const games = new Map();

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

    const aOk = pA && wsA.readyState === WebSocket.OPEN;
    const bOk = pB && wsB.readyState === WebSocket.OPEN;

    if (!aOk && !bOk) continue;
    if (!aOk) { queue.unshift(wsB); if (pB) pB.inQueue = true; continue; }
    if (!bOk) { queue.unshift(wsA); if (pA) pA.inQueue = true; continue; }

    pA.inQueue = false;
    pB.inQueue = false;

    const gameId = uuidv4();
    log('MATCH', `${pA.name} vs ${pB.name} | game ${gameId}`);

    // Ulož odkaz na hru do záznamu hráče
    pA.gameId = gameId;
    pB.gameId = gameId;
    pA.side   = 'A';
    pB.side   = 'B';

    // Informuj klienty (lobby zpráva – stejná jako Etapa 2)
    send(wsA, { type: 'MATCH_FOUND', gameId, opponentName: pB.name, side: 'A' });
    send(wsB, { type: 'MATCH_FOUND', gameId, opponentName: pA.name, side: 'B' });

    // Callback volaný při ukončení hry – uvolní hráče do lobby
    const onGameEnd = (gid) => {
      if (players.get(wsA)) { players.get(wsA).gameId = null; players.get(wsA).side = null; }
      if (players.get(wsB)) { players.get(wsB).gameId = null; players.get(wsB).side = null; }
      games.delete(gid);
      log('GAME', `Session ${gid} ukončena a uvolněna`);
      broadcastCount();
    };

    // Vytvoř herní session a spusť ji (předej volitelné deck IDs)
    const session = new GameSession(gameId, wsA, pA.name, wsB, pB.name, pA.deckIds, pB.deckIds, onGameEnd);
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

// ── Příchozí spojení ──────────────────────────────────────────────────────────

wss.on('connection', (ws, req) => {
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

      // ── Registrace hráče ─────────────────────────────────────────────────────
      case 'JOIN': {
        if (player) {
          send(ws, { type: 'ERROR', msg: 'Už jsi přihlášen' });
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

        // Zkontroluj, zda nick není obsazený
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
              if (session && session.phase !== 'ended') {
                // Přepoj hráče do existující hry
                players.set(ws, { id: p.id, name, deviceId, inQueue: false,
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

        players.set(ws, { id: uuidv4(), name, deviceId, inQueue: false, gameId: null, side: null });
        log('JOIN', `${name} (online: ${players.size})`);

        send(ws, { type: 'WELCOME', online: players.size, queue: queue.length });
        broadcastCount();
        break;
      }

      // ── Matchmaking ───────────────────────────────────────────────────────────
      case 'QUEUE_JOIN': {
        if (!player) { send(ws, { type: 'ERROR', msg: 'Nejsi přihlášen' }); return; }
        if (player.inQueue || player.gameId) return;

        // DEBUG: loguj co přišlo
        console.log(`[QUEUE_JOIN] ${player.name}: deckIds type=${typeof msg.deckIds}, isArray=${Array.isArray(msg.deckIds)}, length=${Array.isArray(msg.deckIds) ? msg.deckIds.length : 'N/A'}, raw=${JSON.stringify(msg.deckIds)?.slice(0,80)}`);

        // Ulož volitelně přijaté IDs balíčku (30 base ID) pro sestavení balíčku
        player.deckIds = Array.isArray(msg.deckIds) ? msg.deckIds : null;

        player.inQueue = true;
        queue.push(ws);
        log('QUEUE', `${player.name} čeká (${queue.length} ve frontě)${player.deckIds ? ' [vlastní balíček]' : ''}`);

        send(ws, { type: 'QUEUE_OK' });
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
        if (!session) { send(ws, { type: 'GAME_ERROR', msg: 'Hra nenalezena' }); return; }

        const returnIds = Array.isArray(msg.returnIds) ? msg.returnIds : [];
        session.handleMulligan(player.side, returnIds);
        break;
      }

      // ── Herní akce ────────────────────────────────────────────────────────────
      case 'GAME_ACTION': {
        if (!player) return;
        const session = games.get(msg.gameId || player.gameId);
        if (!session) { send(ws, { type: 'GAME_ERROR', msg: 'Hra nenalezena' }); return; }

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

      // Informuj soupeře, pokud probíhá hra
      if (player.gameId) {
        const session = games.get(player.gameId);
        if (session && session.phase !== 'ended') {
          // Rozhodnutí: soupeř vyhrál
          const opponent = player.side === 'A' ? 'B' : 'A';
          session._endGame(opponent);
          // Pošli speciální zprávu soupeři
          session._send(opponent, { type: 'OPPONENT_LEFT' });
        }
        games.delete(player.gameId);
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

log('START', `Server běží na ws://0.0.0.0:${PORT}${PATH}`);
