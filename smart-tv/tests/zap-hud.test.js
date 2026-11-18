const test = require('node:test');
const assert = require('node:assert/strict');
const ZapHud = require('../mk21-tv/core/ZapHud.js');

const HOUR = 3600000;
const MIN = 60000;
const T20 = Date.UTC(2026, 2, 10, 23, 0, 0);

test('buildZapInfo monta o cartao do HUD com NO AR e A SEGUIR', () => {
  const now = T20 + 30 * MIN;
  const info = ZapHud.buildZapInfo({
    channel: { name: 'Globo', logo: 'https://exemplo/g.png', group: 'Abertos' },
    channelNumber: 12,
    programs: [
      { title: 'Jornal', start: T20, stop: T20 + HOUR },
      { title: 'Esporte', start: T20 + HOUR, stop: T20 + 2 * HOUR }
    ],
    nowMs: now
  });

  assert.equal(info.name, 'Globo');
  assert.equal(info.number, 12);
  assert.equal(info.hasNumber, true);
  assert.equal(info.currentTitle, 'Jornal');
  assert.equal(info.nextTitle, 'Esporte');
  assert.equal(info.isLive, true);
  assert.equal(info.hasEpg, true);
  assert.equal(info.progressPct, 50);
  assert.equal(info.countdownMs, 30 * MIN);
  assert.equal(info.countdownText, '30:00');
});

test('buildZapInfo sem numero de canal deixa o campo vazio', () => {
  const info = ZapHud.buildZapInfo({ channel: { name: 'SBT' }, nowMs: T20 });
  assert.equal(info.hasNumber, false);
  assert.equal(info.number, '');
  assert.equal(info.hasEpg, false);
});

test('buildZapInfo sem canal nenhum devolve valores seguros', () => {
  const info = ZapHud.buildZapInfo({ nowMs: T20 });
  assert.equal(info.name, 'Canal');
  assert.equal(info.currentTitle, '');
  assert.equal(info.countdownText, '--:--');
});

test('shouldAutoHide fecha o HUD depois do limite e antes nao', () => {
  assert.equal(ZapHud.shouldAutoHide(1000), false);
  assert.equal(ZapHud.shouldAutoHide(ZapHud.ZAP_AUTO_HIDE_MS), true);
  assert.equal(ZapHud.shouldAutoHide(ZapHud.ZAP_AUTO_HIDE_MS + 1), true);
  assert.equal(ZapHud.shouldAutoHide(-5), false);
  assert.equal(ZapHud.shouldAutoHide(NaN), false);
});

test('shouldAutoHide respeita um limite customizado', () => {
  assert.equal(ZapHud.shouldAutoHide(2000, 1000), true);
  assert.equal(ZapHud.shouldAutoHide(500, 1000), false);
});

test('parseChannelNumber aceita apenas digitos validos', () => {
  assert.equal(ZapHud.parseChannelNumber('12'), 12);
  assert.equal(ZapHud.parseChannelNumber('007'), 7);
  assert.equal(ZapHud.parseChannelNumber(''), null);
  assert.equal(ZapHud.parseChannelNumber('abc'), null);
  assert.equal(ZapHud.parseChannelNumber('0'), null);
  assert.equal(ZapHud.parseChannelNumber(null), null);
});

test('buildChannelNumberMap numeracao segue a ordem da lista e ignora duplicados', () => {
  const map = ZapHud.buildChannelNumberMap([
    { name: 'Globo', tvgId: 'globo.br' },
    { name: 'SBT', tvgId: 'sbt.br' },
    { name: 'Globo HD', tvgId: 'globo.br' }
  ]);

  assert.equal(map.size, 2);
  assert.equal(ZapHud.resolveChannelNumber(map, { name: 'Globo', tvgId: 'globo.br' }), 1);
  assert.equal(ZapHud.resolveChannelNumber(map, { name: 'SBT', tvgId: 'sbt.br' }), 2);
});

test('resolveChannelNumber cai no indice quando o canal nao esta no mapa', () => {
  const map = ZapHud.buildChannelNumberMap([{ name: 'Globo' }]);
  assert.equal(ZapHud.resolveChannelNumber(map, { name: 'Desconhecido' }, 4), 5);
  assert.equal(ZapHud.resolveChannelNumber(map, null, null), null);
});

test('channelKey prioriza tvg-id e normaliza o nome', () => {
  assert.equal(ZapHud.channelKey({ tvgId: ' Globo.BR ', name: 'x' }), 'id:globo.br');
  assert.equal(ZapHud.channelKey({ name: '  Globo  ' }), 'name:globo');
  assert.equal(ZapHud.channelKey(null), '');
});
