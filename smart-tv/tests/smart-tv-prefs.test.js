const test = require('node:test');
const assert = require('node:assert/strict');
const SmartTvPrefs = require('../mk21-tv/core/SmartTvPrefs.js');
const ZapHud = require('../mk21-tv/core/ZapHud.js');

function createMemoryStorage() {
  const map = new Map();
  return {
    getItem(k) { return map.has(k) ? map.get(k) : null; },
    setItem(k, v) { map.set(k, String(v)); },
    removeItem(k) { map.delete(k); }
  };
}

test('volume separado persiste valores independentes para Ao Vivo e VOD com clamp [0, 1]', () => {
  const store = createMemoryStorage();
  assert.equal(SmartTvPrefs.getVolumeForType('LIVE', store), 1.0);
  assert.equal(SmartTvPrefs.getVolumeForType('MOVIE', store), 1.0);

  SmartTvPrefs.setVolumeForType('LIVE', 0.45, store);
  SmartTvPrefs.setVolumeForType('MOVIE', 0.85, store);

  assert.equal(SmartTvPrefs.getVolumeForType('LIVE', store), 0.45);
  assert.equal(SmartTvPrefs.getVolumeForType('MOVIE', store), 0.85);
  assert.equal(SmartTvPrefs.getVolumeForType('SERIES', store), 0.85);

  assert.equal(SmartTvPrefs.setVolumeForType('LIVE', 2.5, store), 1.0);
  assert.equal(SmartTvPrefs.setVolumeForType('LIVE', -0.4, store), 0);
});

test('Audio Inteligente alterna corretamente entre off -> normal -> boost -> off', () => {
  const store = createMemoryStorage();
  assert.equal(SmartTvPrefs.getSmartAudioMode(store), 'off');

  const m1 = SmartTvPrefs.cycleSmartAudioMode('off', store);
  assert.equal(m1, 'normal');
  assert.equal(SmartTvPrefs.getSmartAudioMode(store), 'normal');

  const m2 = SmartTvPrefs.cycleSmartAudioMode(m1, store);
  assert.equal(m2, 'boost');

  const m3 = SmartTvPrefs.cycleSmartAudioMode(m2, store);
  assert.equal(m3, 'off');
});

test('LCN editavel por servidor aplica overrides sem colisoes no ZapHud.buildChannelNumberMap', () => {
  const store = createMemoryStorage();
  const channels = [
    { id: 'c1', name: 'Globo', url: 'http://a/1' },
    { id: 'c2', name: 'SBT', url: 'http://a/2' },
    { id: 'c3', name: 'Record', url: 'http://a/3' }
  ];

  const kGlobo = ZapHud.channelKey(channels[0]);
  const kSbt = ZapHud.channelKey(channels[1]);
  const kRecord = ZapHud.channelKey(channels[2]);

  SmartTvPrefs.setChannelCustomLcn('srv1', kGlobo, '500', store);
  SmartTvPrefs.setChannelCustomLcn('srv1', kSbt, '2', store);

  const overrides = SmartTvPrefs.getCustomLcnMap('srv1', store);
  const map = ZapHud.buildChannelNumberMap(channels, overrides);

  assert.equal(map.get(kGlobo), 500);
  assert.equal(map.get(kSbt), 2);
  // Record nao pode colidir com 2, recebe o proximo livre (1)
  assert.equal(map.get(kRecord), 1);

  // Remover override com valor vazio restaura padrao
  SmartTvPrefs.setChannelCustomLcn('srv1', kGlobo, '', store);
  assert.equal(SmartTvPrefs.getCustomLcnMap('srv1', store)[kGlobo], undefined);
});

test('maskSourceLabel nunca expoe a URL ou dominio do servidor', () => {
  const xtreamLabel = SmartTvPrefs.maskSourceLabel(0, {
    name: '⭐ Servidor Principal',
    url: 'http://segredo.provedor-privado.net:8080'
  });
  assert.ok(!xtreamLabel.includes('segredo'));
  assert.ok(!xtreamLabel.includes('http'));
  assert.equal(xtreamLabel, 'Fonte 1 • Servidor Xtream');

  const m3uLabel = SmartTvPrefs.maskSourceLabel(2, {
    name: 'Minha Lista',
    url: 'https://host.local/playlist.m3u8?token=abc'
  });
  assert.ok(!m3uLabel.includes('token'));
  assert.equal(m3uLabel, 'Fonte 3 • Lista M3U');

  const fileLabel = SmartTvPrefs.maskSourceLabel(3, {
    name: 'Canais.m3u',
    sourceKind: 'm3u_file',
    url: 'local://m3u/Canais'
  });
  assert.equal(fileLabel, 'Fonte 4 • Arquivo M3U Local');
});

test('Minhas Listas valida URLs, extrai url-tvg e respeita o teto de 10 listas', () => {
  const store = createMemoryStorage();
  assert.equal(SmartTvPrefs.validateM3uUrl('https://exemplo.com/lista.m3u8'), true);
  assert.equal(SmartTvPrefs.validateM3uUrl('javascript:alert(1)'), false);
  assert.equal(SmartTvPrefs.validateM3uUrl('file:///etc/passwd'), false);

  const epg = SmartTvPrefs.parseM3uHeaderXmltvUrl(
    '#EXTM3U url-tvg="https://epg.exemplo.com/guia.xml,https://backup.exemplo.com/epg.xml"\n#EXTINF:-1,Globo\n'
  );
  assert.equal(epg, 'https://epg.exemplo.com/guia.xml');

  for (let i = 0; i < SmartTvPrefs.MAX_CUSTOM_LISTS; i++) {
    const res = SmartTvPrefs.addCustomM3uList({
      name: `Lista ${i + 1}`,
      kind: 'url',
      url: `https://exemplo.com/l${i + 1}.m3u8`
    }, store);
    assert.equal(res.ok, true);
  }

  const overflow = SmartTvPrefs.addCustomM3uList({
    name: 'Lista 11',
    kind: 'url',
    url: 'https://exemplo.com/l11.m3u8'
  }, store);
  assert.equal(overflow.ok, false);
  assert.equal(SmartTvPrefs.loadCustomM3uLists(store).length, 10);
});
