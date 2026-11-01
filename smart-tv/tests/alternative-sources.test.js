const test = require('node:test');
const assert = require('node:assert/strict');
const { createGrouper, matchesAcrossServers, sourceUrls } = require('../mk21-tv/core/AlternativeSources.js');

test('groups live entries by tvg-id without depending on display name or category', () => {
  const grouper = createGrouper();
  const primary = grouper.add({
    name: 'News HD',
    group: 'News',
    tvgId: 'news.br',
    contentType: 'LIVE',
    url: 'https://one.example/news.m3u8'
  });
  const alternate = grouper.add({
    name: 'News 4K',
    group: 'Premium',
    tvgId: 'NEWS.BR',
    contentType: 'LIVE',
    url: 'https://two.example/news.m3u8'
  });

  assert.equal(primary.isNew, true);
  assert.equal(alternate.isNew, false);
  assert.equal(alternate.item.name, 'News HD');
  assert.deepEqual(alternate.item.alternateUrls, ['https://two.example/news.m3u8']);
});

test('keeps VOD episodes with the same tvg-id separate when names differ', () => {
  const grouper = createGrouper();
  const first = grouper.add({
    name: 'Show S01E01',
    tvgId: 'show',
    contentType: 'SERIES',
    url: 'https://one.example/episode-1.mp4'
  });
  const second = grouper.add({
    name: 'Show S01E02',
    tvgId: 'show',
    contentType: 'SERIES',
    url: 'https://one.example/episode-2.mp4'
  });

  assert.equal(first.isNew, true);
  assert.equal(second.isNew, true);
});

test('uses normalized name, category, and type when tvg-id is absent', () => {
  const grouper = createGrouper();
  grouper.add({
    name: 'Canal   News',
    group: 'Notícias',
    contentType: 'LIVE',
    url: 'https://one.example/news.m3u8'
  });
  const merged = grouper.add({
    name: 'canal news',
    group: 'notícias',
    contentType: 'LIVE',
    url: 'https://two.example/news.m3u8'
  });
  const differentCategory = grouper.add({
    name: 'Canal News',
    group: 'Esportes',
    contentType: 'LIVE',
    url: 'https://three.example/news.m3u8'
  });

  assert.equal(merged.isNew, false);
  assert.equal(differentCategory.isNew, true);
});

test('deduplicates source URLs and preserves playlist order', () => {
  const grouper = createGrouper();
  const first = grouper.add({
    name: 'Movie',
    group: 'Films',
    contentType: 'MOVIE',
    url: 'https://one.example/movie.mp4'
  });
  grouper.add({
    name: 'Movie',
    group: 'Films',
    contentType: 'MOVIE',
    url: 'https://two.example/movie.mp4'
  });
  const merged = grouper.add({
    name: 'Movie',
    group: 'Films',
    contentType: 'MOVIE',
    url: 'https://two.example/movie.mp4'
  });

  assert.deepEqual(sourceUrls(first.item), [
    'https://one.example/movie.mp4',
    'https://two.example/movie.mp4'
  ]);
  assert.deepEqual(merged.item.alternateUrls, ['https://two.example/movie.mp4']);
});

test('matches equivalent live items across servers despite quality suffixes and categories', () => {
  assert.equal(matchesAcrossServers(
    { name: 'Globo HD', group: 'Abertos', contentType: 'LIVE', tvgId: 'globo.br' },
    { name: 'Globo 4K', group: 'Premium', contentType: 'LIVE', tvgId: 'GLOBO.BR' }
  ), true);
  assert.equal(matchesAcrossServers(
    { name: 'Canal News HD', group: 'Abertos', contentType: 'LIVE' },
    { name: 'CANAL news 1080p', group: 'Premium', contentType: 'LIVE' }
  ), true);
});

test('does not match items from different types or unrelated names', () => {
  assert.equal(matchesAcrossServers(
    { name: 'The Film', contentType: 'MOVIE' },
    { name: 'The Film', contentType: 'SERIES' }
  ), false);
  assert.equal(matchesAcrossServers(
    { name: 'The Film', contentType: 'MOVIE' },
    { name: 'Another Film', contentType: 'MOVIE' }
  ), false);
});
