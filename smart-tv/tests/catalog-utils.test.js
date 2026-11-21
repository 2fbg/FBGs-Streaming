const test = require('node:test');
const assert = require('node:assert/strict');
const {
  BG_SYNC_LABELS,
  PLAYLIST_TIMEOUTS,
  normalizeBgSyncFrequency,
  getBgSyncIntervalMs,
  shouldSyncServer,
  computeCatalogMetrics,
  parseSafeJsonResponse,
  normalizeCatalogMode,
  isDirectPlaylistUrl,
  shouldPreferXtreamJson,
  buildXtreamXmltvUrl,
  extractSafeHost,
  formatDurationPtBr,
  formatBytesMb,
  formatSpeedMbPerSec,
  detectHtmlPayload,
  createStallAwareAbortController,
  formatFriendlyLoadError
} = require('../mk21-tv/core/CatalogUtils.js');

test('computeCatalogMetrics summarizes channels, VOD, series, groups, and EPG counts', () => {
  const catalog = {
    LIVE: [
      { name: 'Globo', group: 'Abertos', tvgId: 'globo.br' },
      { name: 'SBT', group: 'Abertos', tvgId: '' },
      { name: 'ESPN', group: 'Esportes', current_program: 'Futebol' }
    ],
    MOVIE: [
      { name: 'Filme A', group: 'Ação' },
      { name: 'Filme B', group: 'Ação' }
    ],
    SERIES: [
      { name: 'Série X S01E01', group: 'Drama' }
    ]
  };
  const metrics = computeCatalogMetrics(catalog, { '1': [], '2': [], '3': [], '4': [] }, true);
  assert.equal(metrics.totalChannels, 3);
  assert.equal(metrics.totalVOD, 2);
  assert.equal(metrics.totalSeries, 1);
  assert.equal(metrics.totalGroups, 4);
  assert.equal(metrics.totalEPG, 4);
  assert.equal(metrics.totalItems, 6);
  assert.equal(metrics.hasXmltv, true);
});

test('parseSafeJsonResponse handles valid JSON, HTML pages, and HTTP 401/403 auth errors cleanly', () => {
  const valid = parseSafeJsonResponse('{"user_info":{"auth":1}}', 200);
  assert.equal(valid.user_info.auth, 1);

  assert.throws(
    () => parseSafeJsonResponse('<html><body>404 Not Found</body></html>', 200),
    (err) => err.isHtmlResponse === true && !/Unexpected token/i.test(err.message)
  );

  assert.throws(
    () => parseSafeJsonResponse('Unauthorized', 401),
    (err) => err.isAuthError === true && err.status === 401
  );
});

test('formatFriendlyLoadError translates raw JSON syntax errors and preserves detailed timeout/HTML diagnostics', () => {
  const friendly = formatFriendlyLoadError(new SyntaxError('Unexpected token < in JSON at position 0'));
  assert.match(friendly, /HTML inválido/i);

  const htmlMsg = formatFriendlyLoadError(new Error('HTTP 404 (página HTML em vez de M3U)'));
  assert.equal(htmlMsg, 'HTTP 404 (página HTML em vez de M3U)');

  const stallMsg = formatFriendlyLoadError(new Error('tempo esgotado (sem dados) após 30s · 12,4 MB em 30s · 0,4 MB/s'));
  assert.match(stallMsg, /tempo esgotado \(sem dados\) após 30s/);

  const rawAbort = formatFriendlyLoadError(new Error('The user aborted a request.'));
  assert.match(rawAbort, /Conexão cancelada ou tempo esgotado/i);
});

test('normalizeBgSyncFrequency and shouldSyncServer match APK options and legacy aliases', () => {
  assert.equal(normalizeBgSyncFrequency('startup'), 'startup');
  assert.equal(normalizeBgSyncFrequency('daily'), 'daily');
  assert.equal(normalizeBgSyncFrequency('weekly'), 'weekly');
  assert.equal(normalizeBgSyncFrequency('manual'), 'manual');
  assert.equal(normalizeBgSyncFrequency('Uma vez ao dia'), 'daily');
  assert.equal(normalizeBgSyncFrequency('Uma vez por semana'), 'weekly');
  assert.equal(normalizeBgSyncFrequency('1H'), 'startup');
  assert.equal(normalizeBgSyncFrequency('6h'), 'daily');
  assert.equal(normalizeBgSyncFrequency('24h'), 'daily');
  assert.equal(normalizeBgSyncFrequency('invalid'), 'daily');

  assert.equal(BG_SYNC_LABELS.startup, 'A cada inicialização');
  assert.equal(BG_SYNC_LABELS.daily, 'Uma vez ao dia');
  assert.equal(BG_SYNC_LABELS.weekly, 'Uma vez por semana');
  assert.equal(BG_SYNC_LABELS.manual, 'Desativado (Apenas manual)');

  assert.equal(getBgSyncIntervalMs('manual'), 0);
  assert.equal(getBgSyncIntervalMs('startup'), 0);
  assert.equal(getBgSyncIntervalMs('daily'), 86400000);
  assert.equal(getBgSyncIntervalMs('weekly'), 604800000);

  const now = 1700000000000;
  assert.equal(shouldSyncServer(0, 'manual', now), false);
  assert.equal(shouldSyncServer(0, 'daily', now), true);
  assert.equal(shouldSyncServer(now - 1000, 'startup', now), true);
  assert.equal(shouldSyncServer(now - 3600000, 'daily', now), false);
  assert.equal(shouldSyncServer(now - 90000000, 'daily', now), true);
  assert.equal(shouldSyncServer(now - 86400000, 'weekly', now), false);
  assert.equal(shouldSyncServer(now - 700000000, 'weekly', now), true);
});

test('catalog mode defaults to Xtream JSON primary and builds /xmltv.php EPG URL', () => {
  assert.equal(normalizeCatalogMode(null), 'json');
  assert.equal(normalizeCatalogMode(''), 'json');
  assert.equal(normalizeCatalogMode('unknown'), 'json');
  assert.equal(normalizeCatalogMode('auto'), 'auto');
  assert.equal(normalizeCatalogMode('m3u'), 'm3u');

  assert.equal(isDirectPlaylistUrl('http://host/get.php?username=a&password=b'), true);
  assert.equal(isDirectPlaylistUrl('http://host/list.m3u8'), true);
  assert.equal(isDirectPlaylistUrl('http://tocombox21.cyou'), false);

  assert.equal(shouldPreferXtreamJson('http://tocombox21.cyou', null), true);
  assert.equal(shouldPreferXtreamJson('http://tocombox21.cyou', 'auto'), true);
  assert.equal(shouldPreferXtreamJson('http://tocombox21.cyou', 'm3u'), false);
  assert.equal(shouldPreferXtreamJson('http://tocombox21.cyou/get.php?username=a&password=b', 'json'), false);

  assert.equal(
    buildXtreamXmltvUrl('http://ovo54.sbs///', 'user+1', 'p@ss'),
    'http://ovo54.sbs/xmltv.php?username=user%2B1&password=p%40ss'
  );
  assert.equal(buildXtreamXmltvUrl('', 'user', 'pass'), '');
  assert.equal(buildXtreamXmltvUrl('http://ovo54.sbs', 'user', ''), '');
});

test('network diagnostics helpers redact credentials, detect HTML/XUI responses, and format pt-BR metrics', () => {
  assert.equal(extractSafeHost('http://user:secret@ovo54.sbs:8080/get.php?username=u&password=p'), 'ovo54.sbs:8080');
  assert.equal(extractSafeHost('https://app.tigerouro.shop/player_api.php?username=u&password=p'), 'app.tigerouro.shop');

  assert.equal(formatDurationPtBr(0), '0s');
  assert.equal(formatDurationPtBr(30000), '30s');
  assert.equal(formatDurationPtBr(72000), '1min 12s');
  assert.equal(formatDurationPtBr(123000), '2min 03s');

  assert.equal(formatBytesMb(108 * 1024 * 1024), '108,0 MB');
  assert.equal(formatBytesMb(12.4 * 1024 * 1024), '12,4 MB');
  assert.equal(formatSpeedMbPerSec(12 * 1024 * 1024, 30000), '0,4 MB/s');

  assert.equal(detectHtmlPayload('', 'text/html; charset=utf-8'), true);
  assert.equal(detectHtmlPayload('<html><title>XUI - Debug Mode</title></html>', 'text/plain'), true);
  assert.equal(detectHtmlPayload('XUI - Debug Mode: Error 404', ''), true);
  assert.equal(detectHtmlPayload('#EXTM3U\n#EXTINF:-1,Globo\nhttp://host/1.ts', 'audio/x-mpegurl'), false);
});

test('state-based timeout policy enforces 20s initial, 30s per-chunk stall watchdog, 5m ceiling, and 4m bg sync', () => {
  assert.equal(PLAYLIST_TIMEOUTS.initialResponseMs, 20000);
  assert.equal(PLAYLIST_TIMEOUTS.stallWatchdogMs, 30000);
  assert.equal(PLAYLIST_TIMEOUTS.hardCeilingMs, 300000);
  assert.equal(PLAYLIST_TIMEOUTS.backgroundSyncMs, 240000);

  // Fake deterministic clock for fast state-machine assertions
  const makeFakeClock = () => {
    let now = 0;
    let nextId = 1;
    const timers = new Map();
    return {
      api: {
        setTimeout(fn, delay) {
          const id = nextId++;
          timers.set(id, { fn, at: now + delay });
          return id;
        },
        clearTimeout(id) {
          timers.delete(id);
        }
      },
      advance(ms) {
        const target = now + ms;
        while (true) {
          let earliestId = null;
          let earliestAt = Infinity;
          for (const [id, t] of timers.entries()) {
            if (t.at <= target && t.at < earliestAt) {
              earliestAt = t.at;
              earliestId = id;
            }
          }
          if (earliestId === null) break;
          const entry = timers.get(earliestId);
          timers.delete(earliestId);
          now = entry.at;
          entry.fn();
        }
        now = target;
      }
    };
  };

  // 1) Initial response timeout fires at 20s if no headers arrive
  const c1 = makeFakeClock();
  const w1 = createStallAwareAbortController(null, { timerApi: c1.api });
  c1.advance(19999);
  assert.equal(w1.getAbortReason(), null);
  c1.advance(1);
  assert.equal(w1.getAbortReason(), 'initial_timeout');
  assert.match(w1.describeAbortReason(), /sem resposta inicial após 20s/);
  w1.cleanup();

  // 2) Receiving response + chunks keeps stream alive well past 45s (e.g. 90s), then stalls after 30s of silence
  const c2 = makeFakeClock();
  const w2 = createStallAwareAbortController(null, { timerApi: c2.api });
  c2.advance(5000);
  w2.markResponseReceived();
  for (let step = 0; step < 5; step++) {
    c2.advance(20000);
    w2.touchChunk();
    assert.equal(w2.getAbortReason(), null);
  }
  c2.advance(30000);
  assert.equal(w2.getAbortReason(), 'stall_timeout');
  assert.match(w2.describeAbortReason(), /sem dados\) após 30s/);
  w2.cleanup();

  // 3) Hard ceiling (5 min) aborts even if chunks keep trickling
  const c3 = makeFakeClock();
  const w3 = createStallAwareAbortController(null, { timerApi: c3.api });
  w3.markResponseReceived();
  for (let step = 0; step < 15; step++) {
    c3.advance(20000);
    if (!w3.getAbortReason()) w3.touchChunk();
  }
  assert.equal(w3.getAbortReason(), 'ceiling_timeout');
  assert.match(w3.describeAbortReason(), /limite máximo de 5min 00s/);
  w3.cleanup();

  // 4) Parent signal cancellation is classified as 'parent'
  const c4 = makeFakeClock();
  const parent = new AbortController();
  const w4 = createStallAwareAbortController(parent.signal, { timerApi: c4.api });
  parent.abort();
  assert.equal(w4.getAbortReason(), 'parent');
  w4.cleanup();
});

test('computeGlobalServersMetrics sums ALL synced servers and ignores legacy 400-channel universal cache pollution', () => {
  const {
    isPollutedLegacyFastCacheMetrics,
    computeGlobalServersMetrics
  } = require('../mk21-tv/core/CatalogUtils.js');

  const servers = [
    { id: 'vlog', name: 'VLOG' },
    { id: 'tannix', name: 'TANNIX' },
    { id: 'lub', name: 'LUB TV' },
    { id: 'cb6000', name: 'CB6000' }
  ];

  // Legacy bug polluted unsynced servers (lastSync=0) with 400 LIVE / 0 VOD / 0 SERIES
  assert.equal(
    isPollutedLegacyFastCacheMetrics({ totalChannels: 400, totalVOD: 0, totalSeries: 0, totalEPG: 170 }, 0, false),
    true
  );
  assert.equal(
    isPollutedLegacyFastCacheMetrics({ totalChannels: 400, totalVOD: 0, totalSeries: 0, totalEPG: 170 }, 1700000000000, false),
    false
  );

  const perMap = {
    vlog: { totalChannels: 1235, totalVOD: 22203, totalSeries: 8699, totalEPG: 170, totalGroups: 120, totalItems: 32137 },
    tannix: { totalChannels: 1303, totalVOD: 44379, totalSeries: 9523, totalEPG: 269, totalGroups: 210, totalItems: 55205 },
    lub: { totalChannels: 400, totalVOD: 0, totalSeries: 0, totalEPG: 170, totalGroups: 10, totalItems: 400 },
    cb6000: null
  };
  const syncMap = {
    vlog: 1700000000000,
    tannix: 1700000005000,
    lub: 0,
    cb6000: 0
  };

  // Even when switching (activeCatalogMetrics empty), Geral still reflects VLOG + TANNIX (never 0/0/0/0!)
  const global = computeGlobalServersMetrics(servers, perMap, 'tannix', { totalChannels: 0, totalVOD: 0, totalSeries: 0, totalItems: 0 }, syncMap);
  assert.equal(global.totalChannels, 1235 + 1303);
  assert.equal(global.totalVOD, 22203 + 44379);
  assert.equal(global.totalSeries, 8699 + 9523);
  assert.equal(global.totalEPG, 170 + 269);
  assert.equal(global.syncedServers, 2);
  assert.equal(global.totalServers, 4);
});

test('computeServerLoadPercentage returns 0%, live loading %, and 100% accurately per server', () => {
  const { computeServerLoadPercentage } = require('../mk21-tv/core/CatalogUtils.js');

  assert.deepEqual(
    computeServerLoadPercentage('cb6000', {}, null, 0, false),
    { percent: 0, status: 'idle', label: '0%' }
  );
  assert.deepEqual(
    computeServerLoadPercentage('tannix', { tannix: { percent: 42.4, status: 'loading' } }, null, 0, false),
    { percent: 42, status: 'loading', label: '42%' }
  );
  assert.deepEqual(
    computeServerLoadPercentage('tannix', {}, { totalChannels: 1303, totalVOD: 44379, totalSeries: 9523 }, 1700000000000, false),
    { percent: 100, status: 'done', label: '100%' }
  );
  // Legacy polluted 400-channel unsynced entry must report 0%, not 100%
  assert.deepEqual(
    computeServerLoadPercentage('lub', {}, { totalChannels: 400, totalVOD: 0, totalSeries: 0 }, 0, false),
    { percent: 0, status: 'idle', label: '0%' }
  );
});

test('decideCachedServerSwitch opens background-synced server immediately without re-downloading', () => {
  const { decideCachedServerSwitch } = require('../mk21-tv/core/CatalogUtils.js');
  const now = 1700000000000;

  // TANNIX finished background sync 5 minutes ago (55,205 items in catalog index) -> no re-download!
  const hitDaily = decideCachedServerSwitch(
    { liveCount: 1303, vodCount: 44379, seriesCount: 9523, fromFullStore: true },
    now - 5 * 60 * 1000,
    'daily',
    now
  );
  assert.deepEqual(hitDaily, {
    useCacheImmediately: true,
    isFullCache: true,
    shouldBackgroundRefresh: false
  });

  // Even on "startup" frequency, a server synced 5 min ago in this session must NOT re-download on select
  const hitStartupRecent = decideCachedServerSwitch(
    { liveCount: 1303, vodCount: 44379, seriesCount: 9523, fromFullStore: true },
    now - 5 * 60 * 1000,
    'startup',
    now
  );
  assert.equal(hitStartupRecent.shouldBackgroundRefresh, false);

  // Fast cache only (400 live channels, 0 VOD/Series, not fromFullStore) -> opens fast + silently fetches rest
  const fastOnly = decideCachedServerSwitch(
    { liveCount: 400, vodCount: 0, seriesCount: 0, fromFullStore: false },
    now - 5 * 60 * 1000,
    'daily',
    now
  );
  assert.deepEqual(fastOnly, {
    useCacheImmediately: true,
    isFullCache: false,
    shouldBackgroundRefresh: true
  });

  // Stale full cache (> 24h on daily frequency) -> opens from cache and refreshes silently
  const staleDaily = decideCachedServerSwitch(
    { liveCount: 1303, vodCount: 44379, seriesCount: 9523, fromFullStore: true },
    now - 26 * 60 * 60 * 1000,
    'daily',
    now
  );
  assert.equal(staleDaily.useCacheImmediately, true);
  assert.equal(staleDaily.shouldBackgroundRefresh, true);
});

