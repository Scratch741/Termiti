/**
 * Zátěžový test serveru: spustí VLASTNÍ instanci serveru z této složky na zvláštním portu
 * (výchozí 8766, ostrý server na 8765 se nepoužívá) a připojí k ní N botů, kteří hrají
 * v párech: mulligan bez výměny, svůj tah po 2–4 s ukončí. Měří se jen proces testovací
 * instance – paměť, procesor a zpoždění smyčky událostí (jak dlouho čeká zpráva, než ji
 * server zpracuje).
 *
 * Testovací instance zapisuje hodnocení a logy do data/ a logs/ VEDLE tohoto souboru,
 * takže na serveru ji spouštěj z naklonovaného repozitáře (např. /tmp/termiti/server),
 * ne z /opt/termiti-server – jinak by si boti zapsali hodnocení do ostrých dat.
 *
 * Použití:  node loadtest.js [hráčů=200] [měření s=30] [náběh s=0]
 *   náběh 0 = všichni boti se připojí naráz (jako po restartu serveru)
 */
'use strict';

const path = require('path');

// ── Režim testovací instance (spuštěné níž přes fork) ───────────────────────
if (process.argv[2] === '--server') {
  const { monitorEventLoopDelay } = require('perf_hooks');
  const lag = monitorEventLoopDelay({ resolution: 10 });
  lag.enable();
  require(path.join(__dirname, 'server.js'));
  process.on('message', (cmd) => {
    if (cmd === 'reset') { lag.reset(); return; }
    process.send({
      mem: process.memoryUsage(), cpu: process.cpuUsage(),
      lagP50: lag.percentile(50) / 1e6, lagP99: lag.percentile(99) / 1e6, lagMax: lag.max / 1e6
    });
  });
  return;
}

const { fork } = require('child_process');
const WebSocket = require('ws');

const N       = Math.max(2, +process.argv[2] || 200);
const SECONDS = +process.argv[3] || 30;
const RAMP    = +process.argv[4] || 0;
const PORT    = +process.env.LOADTEST_PORT || 8766;
if (PORT === 8765) { console.error('Port 8765 patří ostrému serveru – zvol jiný (LOADTEST_PORT).'); process.exit(1); }

const srv = fork(__filename, ['--server'], { env: { ...process.env, PORT: String(PORT) }, silent: true });
srv.stdout.on('data', () => {}); srv.stderr.on('data', () => {});
const ask = () => new Promise(r => { srv.once('message', r); srv.send('stats'); });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const cpuPct = (a, b, ms) => (100 * ((b.cpu.user + b.cpu.system) - (a.cpu.user + a.cpu.system)) / 1000 / ms).toFixed(0);

let welcome = 0, matched = 0, states = 0, over = 0, errors = 0, closed = 0;
let tAllWelcome = 0, tAllMatched = 0;
const sockets = [];

function bot(i, t0) {
  const ws = new WebSocket(`ws://127.0.0.1:${PORT}/lobby`);
  sockets.push(ws);
  ws.on('open', () => ws.send(JSON.stringify({ type: 'JOIN', protocolVersion: 1, name: 'Bot' + i, avatar: 'player_icon_1', deviceId: 'loadtest-' + i })));
  ws.on('message', raw => {
    const d = JSON.parse(raw);
    if (d.type === 'WELCOME') {
      if (++welcome === N) tAllWelcome = Date.now() - t0;
      ws.send(JSON.stringify({ type: 'QUEUE_JOIN' }));
    } else if (d.type === 'MATCH_FOUND') {
      ws.gameId = d.gameId;
      if (++matched === N - (N % 2)) tAllMatched = Date.now() - t0;
    } else if (d.type === 'GAME_MULLIGAN') {
      setTimeout(() => ws.readyState === 1 && ws.send(JSON.stringify({ type: 'MULLIGAN_DONE', gameId: ws.gameId, returnIds: [] })), 2000);
    } else if (d.type === 'GAME_STATE') {
      states++;
      if (d.isMyTurn) setTimeout(() => ws.readyState === 1 &&
        ws.send(JSON.stringify({ type: 'GAME_ACTION', gameId: ws.gameId, action: 'END_TURN' })), 2000 + Math.random() * 2000);
    } else if (d.type === 'GAME_OVER') over++;
  });
  ws.on('error', () => errors++);
  ws.on('close', () => closed++);
}

(async () => {
  await sleep(1500);                       // start serveru
  const s0 = await ask();
  const t0 = Date.now();
  for (let i = 0; i < N; i++) {
    bot(i, t0);
    if (RAMP > 0) await sleep(RAMP * 1000 / N);
  }
  // Fáze připojení: dokud nejsou všichni ve hře (nejvýš 60 s)
  while (!tAllMatched && Date.now() - t0 < 60000) await sleep(100);
  const s1 = await ask();
  const joinMs = Date.now() - t0;
  await sleep(5000);                       // doběhnou mulligany
  srv.send('reset');
  const s2 = await ask(); const st2 = states; const t2 = Date.now();
  await sleep(SECONDS * 1000);
  const s3 = await ask(); const ms = Date.now() - t2;

  const mb = x => (x / 1048576).toFixed(0);
  console.log(`Zátěžový test: ${N} botů (${Math.floor(N / 2)} her), port ${PORT}, náběh ${RAMP} s`);
  console.log(`  připojení: WELCOME ${welcome}/${N} za ${tAllWelcome || '–'} ms, ve hře ${matched} za ${tAllMatched || '–'} ms, chyb ${errors}, odpojených ${closed}`);
  console.log(`  procesor při připojování: ${cpuPct(s0, s1, joinMs)} % jednoho jádra`);
  console.log(`  hraní (${SECONDS} s): procesor ${cpuPct(s2, s3, ms)} % jednoho jádra, ${((states - st2) / ms * 1000).toFixed(0)} GAME_STATE/s`);
  console.log(`  zpoždění zpracování zpráv: medián ${s3.lagP50.toFixed(0)} ms, 99 % ${s3.lagP99.toFixed(0)} ms, max ${s3.lagMax.toFixed(0)} ms`);
  console.log(`  paměť testovací instance: ${mb(s0.mem.rss)} → ${mb(s3.mem.rss)} MB`);
  for (const ws of sockets) ws.terminate();
  srv.kill();
  process.exit(0);
})();
