'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const syncApi = require('../../api/sync.js');
const CloudSync = require('../mk21-tv/core/CloudSync.js');

function createMockRes() {
  const headers = {};
  let body = '';
  return {
    statusCode: 200,
    setHeader(k, v) { headers[k.toLowerCase()] = v; },
    getHeader(k) { return headers[k.toLowerCase()]; },
    end(chunk) { if (chunk) body += chunk; },
    json() { return JSON.parse(body || '{}'); },
  };
}

test('normalizeSyncCode e generateSyncCode seguem formato MK21-XXXX-XXXX', () => {
  assert.equal(syncApi.normalizeSyncCode('ab12cd34'), 'MK21-AB12-CD34');
  assert.equal(syncApi.normalizeSyncCode('mk21-ab12-cd34'), 'MK21-AB12-CD34');
  assert.equal(syncApi.normalizeSyncCode('123'), '');
  assert.equal(syncApi.isValidSyncCode('MK21-A9K2-7PLM'), true);
  assert.equal(syncApi.isValidSyncCode('abc'), false);

  const generated = syncApi.generateSyncCode();
  assert.equal(syncApi.isValidSyncCode(generated), true);
  assert.equal(CloudSync.isValidSyncCode(CloudSync.generateSyncCode()), true);
});

test('mergeSyncPayloads resolve conflitos com Last-Write-Wins (LWW) e respeita tombstones', () => {
  const remote = {
    favorites: [
      { key: 'http://tv/1', name: 'Globo', url: 'http://tv/1', deleted: false, updatedAt: 1000 },
      { key: 'http://tv/2', name: 'SBT', url: 'http://tv/2', deleted: false, updatedAt: 3000 },
    ],
    continueWatching: [
      { key: 'http://vod/a', name: 'Matrix', url: 'http://vod/a', positionSec: 600, durationSec: 7200, updatedAt: 2000 },
    ],
    updatedAt: 3000,
  };
  const incoming = {
    favorites: [
      { key: 'http://tv/1', name: 'Globo', url: 'http://tv/1', deleted: true, updatedAt: 4000 },
      { key: 'http://tv/2', name: 'SBT', url: 'http://tv/2', deleted: true, updatedAt: 1500 },
      { key: 'http://tv/3', name: 'ESPN', url: 'http://tv/3', deleted: false, updatedAt: 4500 },
    ],
    continueWatching: [
      { key: 'http://vod/a', name: 'Matrix', url: 'http://vod/a', positionSec: 1850, durationSec: 7200, updatedAt: 5000 },
    ],
    updatedAt: 5000,
  };

  const merged = syncApi.mergeSyncPayloads(remote, incoming, 5000);
  const byKeyFav = new Map(merged.favorites.map((f) => [f.key, f]));
  assert.equal(byKeyFav.get('http://tv/1').deleted, true, 'tombstone mais novo prevalece');
  assert.equal(byKeyFav.get('http://tv/2').deleted, false, 'favorito remoto mais novo prevalece sobre exclusao antiga');
  assert.equal(byKeyFav.get('http://tv/3').name, 'ESPN');
  assert.equal(merged.continueWatching[0].positionSec, 1850, 'posicao VOD mais recente vence');
});

test('handler /api/sync retorna 503 sem DATABASE_URL e executa GET/POST com SQL in-memory', async () => {
  const noDbHandler = syncApi.createSyncHandler({ getSql: () => null });
  const res503 = createMockRes();
  await noDbHandler({ method: 'GET', url: '/api/sync?code=MK21-ABCD-1234' }, res503);
  assert.equal(res503.statusCode, 503);

  const store = new Map();
  const mockSql = async (strings, ...values) => {
    const q = strings.join(' ');
    if (q.includes('SELECT payload')) {
      const code = values[0];
      const rec = store.get(code);
      return rec ? [{ payload: rec, updated_at: new Date().toISOString() }] : [];
    }
    if (q.includes('INSERT INTO user_sync_state')) {
      const [code, payloadJson] = values;
      store.set(code, JSON.parse(payloadJson));
      return [];
    }
    return [];
  };

  const handler = syncApi.createSyncHandler({ getSql: () => mockSql, skipEnsureTable: true, now: () => 10000 });

  // POST salva e devolve estado consolidado
  const resPost = createMockRes();
  await handler({
    method: 'POST',
    url: '/api/sync',
    body: {
      code: 'mk21-abcd-1234',
      payload: {
        favorites: [{ name: 'Globo HD', url: 'http://srv/live/1.ts', updatedAt: 9000 }],
        continueWatching: [{ name: 'Duna', url: 'http://srv/movie/1.mp4', positionSec: 940, durationSec: 9000, updatedAt: 9500 }],
      },
    },
  }, resPost);
  assert.equal(resPost.statusCode, 200);
  const postJson = resPost.json();
  assert.equal(postJson.ok, true);
  assert.equal(postJson.code, 'MK21-ABCD-1234');
  assert.equal(postJson.payload.favorites.length, 1);
  assert.equal(postJson.payload.continueWatching[0].positionSec, 940);

  // GET lê o estado salvo
  const resGet = createMockRes();
  await handler({ method: 'GET', url: '/api/sync?code=ABCD1234' }, resGet);
  assert.equal(resGet.statusCode, 200);
  assert.equal(resGet.json().payload.favorites[0].name, 'Globo HD');

  // Código inválido retorna 400
  const res400 = createMockRes();
  await handler({ method: 'GET', url: '/api/sync?code=12' }, res400);
  assert.equal(res400.statusCode, 400);
});

test('CloudSync Smart TV constrói payload, aplica merge e chama endpoint', async () => {
  const payload = CloudSync.buildSmartTvPayload(
    new Set(['http://srv/live/10.ts']),
    [{ name: 'Filme X', url: 'http://srv/movie/99.mp4', currentTime: 320, duration: 5400, timestamp: 8000 }],
    (url) => ({ name: 'SporTV', group: 'Esportes', contentType: 'LIVE', url }),
    8500
  );
  assert.equal(payload.favorites[0].name, 'SporTV');
  assert.equal(payload.continueWatching[0].positionSec, 320);

  const applied = CloudSync.applyMergedToSmartTv(
    {
      favorites: [
        { url: 'http://srv/live/10.ts', deleted: true, updatedAt: 9000 },
        { url: 'http://srv/live/20.ts', deleted: false, updatedAt: 9100 },
      ],
      continueWatching: [
        { name: 'Filme X', url: 'http://srv/movie/99.mp4', positionSec: 1200, durationSec: 5400, updatedAt: 9200 },
      ],
    },
    new Set(['http://srv/live/10.ts']),
    [{ name: 'Filme X', url: 'http://srv/movie/99.mp4', currentTime: 320, duration: 5400, timestamp: 8000 }]
  );
  assert.equal(applied.favoriteUrls.has('http://srv/live/10.ts'), false);
  assert.equal(applied.favoriteUrls.has('http://srv/live/20.ts'), true);
  assert.equal(applied.continueWatchingList[0].currentTime, 1200);

  const netRes = await CloudSync.syncWithServer('MK21-WXYZ-8899', payload, {
    fetch: async () => ({
      ok: true,
      status: 200,
      json: async () => ({ ok: true, code: 'MK21-WXYZ-8899', payload, updatedAt: 9999 }),
    }),
  });
  assert.equal(netRes.ok, true);
  assert.equal(netRes.code, 'MK21-WXYZ-8899');
});
