const test = require('node:test');
const assert = require('node:assert/strict');
const {
  BG_SYNC_LABELS,
  normalizeBgSyncFrequency,
  getBgSyncIntervalMs,
  shouldSyncServer,
  computeCatalogMetrics,
  parseSafeJsonResponse,
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

test('formatFriendlyLoadError translates raw JSON syntax errors to readable Portuguese', () => {
  const friendly = formatFriendlyLoadError(new SyntaxError('Unexpected token < in JSON at position 0'));
  assert.match(friendly, /HTML inválido/i);
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
