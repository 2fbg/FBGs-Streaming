import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const webObs = require('../../js/observability.js');
const tvObs = require('../mk21-tv/observability.js');

function createMemoryStorage() {
  const map = new Map();
  return {
    getItem(k) { return map.has(k) ? map.get(k) : null; },
    setItem(k, v) { map.set(k, String(v)); },
    removeItem(k) { map.delete(k); },
    _raw: map,
  };
}

test('observability: structured JSONL schema and required fields', () => {
  let fakeNow = 1_700_000_000_000;
  let fakeMono = 10;
  const storage = createMemoryStorage();
  const logger = webObs.createLogger({
    storage,
    platform: 'web',
    version: '2.9.3',
    build: 293,
    now: () => fakeNow,
    mono: () => (fakeMono += 12.5),
  });

  const cid = logger.startTimer('login_req');
  fakeMono += 40;
  const rec = logger.endTimer('login_req', 'login_request_end', {
    phase: 'auth',
    severity: 'INFO',
    screen: 'login',
    meta: { host: 'iptv.example.com', status: 200 },
  });

  assert.equal(rec.schemaVersion, 1);
  assert.equal(rec.platform, 'web');
  assert.equal(rec.version, '2.9.3');
  assert.equal(rec.build, 293);
  assert.equal(rec.screen, 'login');
  assert.equal(rec.event, 'login_request_end');
  assert.equal(rec.phase, 'auth');
  assert.equal(rec.severity, 'INFO');
  assert.equal(rec.correlationId, cid);
  assert.ok(typeof rec.tsUtc === 'string' && rec.tsUtc.endsWith('Z'));
  assert.ok(typeof rec.monoMs === 'number' && rec.monoMs > 0);
  assert.ok(typeof rec.sessionId === 'string' && rec.sessionId.startsWith('ses_'));
  assert.ok(typeof rec.durationMs === 'number' && rec.durationMs >= 40);

  const jsonl = logger.exportJsonl();
  const parsedLines = jsonl.split('\n').filter(Boolean).map((line) => JSON.parse(line));
  assert.ok(parsedLines.length >= 1);
  assert.equal(parsedLines[0].event, 'login_request_end');
});

test('observability: redacts passwords, PINs, tokens, cookies, Authorization, credential URLs and private playlists', () => {
  const storage = createMemoryStorage();
  const logger = webObs.createLogger({ storage, platform: 'webos', version: '2.9.3', build: 293 });

  logger.logEvent('login_error', {
    phase: 'auth',
    severity: 'ERROR',
    message: 'Falha em https://alice:supersecret@tv.example.net/player_api.php?username=alice&password=MyPass123&token=tok999 Authorization: Bearer eyJhbGciOiJIUzI1NiJ9 Cookie: sid=abc12345',
    meta: {
      password: 'MyPassword!',
      pin: '4321',
      token: 'tok_secret_value',
      authorization: 'Bearer xyz123456789',
      cookie: 'session=deadbeef',
      url: 'https://user1:pass1@stream.example.org/live/user1/pass1/101.ts?token=abc',
      playlist: '#EXTM3U\n#EXTINF:-1,Canal Privado\nhttp://u:p@host/live/u/p/1.ts',
      xtreamSample: 'http://cdn.example.org/movie/joao/senha99/555.mp4',
      license: 'MKL1.payload123.sig456',
    },
  });

  logger.flushNow();
  const persisted = storage.getItem('mk21_obs_jsonl_v1') || '';
  const exported = logger.exportJsonl();

  for (const blob of [persisted, exported]) {
    assert.ok(!blob.includes('supersecret'), 'Basic auth password must be redacted');
    assert.ok(!blob.includes('MyPass123'), 'Query password must be redacted');
    assert.ok(!blob.includes('MyPassword!'), 'Meta password must be redacted');
    assert.ok(!blob.includes('4321'), 'PIN must be redacted');
    assert.ok(!blob.includes('tok999'), 'Query token must be redacted');
    assert.ok(!blob.includes('tok_secret_value'), 'Meta token must be redacted');
    assert.ok(!blob.includes('eyJhbGciOiJIUzI1NiJ9'), 'Bearer token must be redacted');
    assert.ok(!blob.includes('sid=abc12345'), 'Cookie header must be redacted');
    assert.ok(!blob.includes('deadbeef'), 'Meta cookie must be redacted');
    assert.ok(!blob.includes('senha99'), 'Xtream path password must be redacted');
    assert.ok(!blob.includes('payload123.sig456'), 'License token must be redacted');
    assert.ok(!blob.includes('#EXTM3U'), 'Raw M3U playlist must be redacted');
    assert.ok(blob.includes('stream.example.org'), 'Safe host should remain available for diagnostics');
  }
});

test('observability: network logging keeps only host, method, status, duration, safe size and correlation', () => {
  const storage = createMemoryStorage();
  const logger = tvObs.createLogger({ storage, platform: 'webos', version: '2.9.3', build: 293 });

  const rec = logger.logNetwork({
    url: 'https://alice:pw@portal.example.com/get.php?username=alice&password=pw&type=m3u_plus',
    method: 'GET',
    status: 200,
    durationMs: 145.6,
    sizeBytes: 102400,
  });

  assert.equal(rec.event, 'network_request');
  assert.equal(rec.meta.host, 'portal.example.com');
  assert.equal(rec.meta.method, 'GET');
  assert.equal(rec.meta.status, 200);
  assert.equal(rec.meta.sizeBytes, 102400);
  assert.ok(!JSON.stringify(rec).includes('alice'));
  assert.ok(!JSON.stringify(rec).includes('m3u_plus'));
});

test('observability: circular buffer limit, TRACE expiration (30-60 min), and non-throwing broken storage', () => {
  let fakeNow = 1_700_000_000_000;
  const brokenStorage = {
    getItem() { throw new Error('QuotaExceeded'); },
    setItem() { throw new Error('QuotaExceeded'); },
    removeItem() { throw new Error('QuotaExceeded'); },
  };
  const resilient = webObs.createLogger({
    storage: brokenStorage,
    maxMemoryEvents: 5,
    now: () => fakeNow,
  });

  // Never crashes even when storage throws
  for (let i = 0; i < 12; i++) {
    resilient.logEvent('playlist_batch_committed', { phase: 'catalog', meta: { batch: i, batchSize: 100 } });
  }
  assert.equal(resilient.getEvents().length, 5);
  assert.equal(resilient.getEvents()[0].meta.batch, 7);
  assert.equal(resilient.getEvents()[4].meta.batch, 11);

  // TRACE expires automatically after 45 min
  resilient.enableTemporaryTrace(45);
  assert.equal(resilient.getLevel(), 'TRACE');
  assert.ok(resilient.logEvent('focus_changed', { severity: 'DEBUG', minLevel: 'TRACE' }) !== null);

  fakeNow += 46 * 60 * 1000;
  assert.equal(resilient.getLevel(), 'NORMAL');
  assert.equal(resilient.logEvent('focus_changed', { severity: 'DEBUG', minLevel: 'TRACE' }), null);

  // OFF drops normal events
  resilient.setLevel('OFF');
  assert.equal(resilient.logEvent('player_open', { severity: 'INFO' }), null);
});
