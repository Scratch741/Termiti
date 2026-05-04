/**
 * Termiti – Lobby + Game server (Etapa 3)
 * WebSocket server: registrace hráčů, matchmaking, server-authoritative hra
 *
 * Port: 8765
 * Path: /lobby
 *
 * ── Lobby protokol ──────────────────────────────────────────────────────────
 * Klient → Server:
 *   { type:"JOIN",              name:"...", avatar:"..." }
 *   { type:"QUEUE_JOIN" }
 *   { type:"QUEUE_LEAVE" }
 *   { type:"PING" }
 *
 * Server → Klient:
 *   { type:"WELCOME",           online:N, queue:N }
 *   { type:"COUNT",             online:N, queue:N }
 *   { type:"QUEUE_OK" }
 *   { type:"MATCH_FOUND",       gameId:"...", opponentName:"...", opponentAvatar:"...", side:"A"|"B" }
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
 *   { type:"GAME_OVER",              winner:"A"|"B"|"DRAW", winnerName, youWin }
 *   { type:"GAME_ERROR",             msg:"..." }
 *   { type:"OPPONENT_LEFT" }
 *   { type:"OPPONENT_DISCONNECTED",  timeoutSec:N }
 *   { type:"OPPONENT_RECONNECTED" }
 */

'use strict';

const WebSocket = require('ws');
const { v4: uuidv4 } = require('uuid');
const { GameSession } = require('./game/GameSession');
const { ratingSystem } = require('./game/RatingSystem');

const PORT = 8765;
const PATH = '/lobby';

// ── Server ────────────────────────────────────────────────────────────────────

const wss = new WebSocket.Server({ port: PORT });

// Hráči v lobby: Map<WebSocket, { id, name, inQueue, gameId|null }>
const players = new Map();

// Matchmaking fronty
const queue      = [];        // normální / vlastní balíček
const superQueue = [];        // super náhodný (50 karet, 15/15/15/5)

// Aktivní hry: Map<gameId, GameSession>
const games = new Map();

// ── Reconnect ─────────────────────────────────────────────────────────────────
// Hráči, kteří se odpojili uprostřed hry a čekají na reconnect
// Map<name, { id, name, avatar, level, deviceId, gameId, side }>
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

    send(wsA, { type: 'MATCH_FOUND', gameId, opponentName: pB.name, opponentAvatar: pB.avatar ?? '👺', opponentLevel: pB.level ?? 1, opponentRating: ratingB, myRating: ratingA, side: 'A', mode });
    send(wsB, { type: 'MATCH_FOUND', gameId, opponentName: pA.name, opponentAvatar: pA.avatar ?? '👺', opponentLevel: pA.level ?? 1, opponentRating: ratingA, myRating: ratingB, side: 'B', mode });

    const onGameEnd = (gid) => {
      if (players.get(wsA)) { players.get(wsA).gameId = null; players.get(wsA).side = null; }
      if (players.get(wsB)) { players.get(wsB).gameId = null; players.get(wsB).side = null; }
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
                              inQueue: false, gameId: dp.gameId, side: dp.side });
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
                players.set(ws, { id: p.id, name, avatar: p.avatar ?? '⚔️', level: p.level ?? 1, deviceId, inQueue: false,
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

        const avatar = [...String(msg.avatar ?? '⚔️').replace(/[\x00-\x1F\x7F]/g, '')].slice(0, 2).join('') || '⚔️';
        const level  = Math.max(1, Math.min(9999, parseInt(msg.level) || 1));
        // Pasivní schopnosti – přijmi max 2 známá ID, ignoruj neznámá (anti-cheat)
        const KNOWN_ABILITIES = new Set(['extra_castle','extra_wall','extra_magic','extra_attack','extra_stones','extra_chaos']);
        const rawAbilities    = Array.isArray(msg.activeAbilities) ? msg.activeAbilities : [];
        const activeAbilities = rawAbilities
          .filter(a => typeof a === 'string' && KNOWN_ABILITIES.has(a))
          .slice(0, 2);
        players.set(ws, { id: uuidv4(), name, avatar, level, deviceId, activeAbilities, inQueue: false, gameId: null, side: null });
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

          // Ulož hráče pro případný reconnect
          disconnectedPlayers.set(player.name, {
            id: player.id, name: player.name, avatar: player.avatar,
            level: player.level, deviceId: player.deviceId,
            activeAbilities: player.activeAbilities ?? [],
            gameId: player.gameId, side: player.side
          });

          // Informuj soupeře – má čas RECONNECT_TIMEOUT_SEC sekund
          session._send(opponent, {
            type: 'OPPONENT_DISCONNECTED',
            timeoutSec: RECONNECT_TIMEOUT_SEC
          });

          // Spusť odpočet – po vypršení ukončíme hru
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
          }, RECONNECT_TIMEOUT_SEC * 1000);
          reconnectTimers.set(player.name, timer);

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

log('START', `Server běží na ws://0.0.0.0:${PORT}${PATH}`);
