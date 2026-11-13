const test = require('node:test');
const assert = require('node:assert/strict');
const {
  normalizeBgSyncFrequency,
  getBgSyncIntervalMs,
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

test('normalizeBgSyncFrequency and getBgSyncIntervalMs handle valid and fallback values', () => {
  assert.equal(normalizeBgSyncFrequency('manual'), 'manual');
  assert.equal(normalizeBgSyncFrequency('1H'), '1h');
  assert.equal(normalizeBgSyncFrequency('invalid'), '6h');

  assert.equal(getBgSyncIntervalMs('manual'), 0);
  assert.equal(getBgSyncIntervalMs('1h'), 3600000);
  assert.equal(getBgSyncIntervalMs('6h'), 21600000);
  assert.equal(getBgSyncIntervalMs('24h'), 86400000);
});
