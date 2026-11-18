const test = require('node:test');
const assert = require('node:assert/strict');
const EpgGuide = require('../mk21-tv/core/EpgGuide.js');

const HOUR = 3600000;
const MIN = 60000;

// 2026-03-10 20:00:00 -0300 -> epoch ms (referência fixa dos testes)
const T20 = Date.UTC(2026, 2, 10, 23, 0, 0);

test('toTimestampMs converte epoch em segundos, epoch em ms, XMLTV e ISO', () => {
  assert.equal(EpgGuide.toTimestampMs(1773200000), 1773200000000);
  assert.equal(EpgGuide.toTimestampMs(1773200000000), 1773200000000);
  assert.equal(EpgGuide.toTimestampMs('1773200000'), 1773200000000);
  assert.equal(EpgGuide.toTimestampMs('1773200000000'), 1773200000000);

  // XMLTV com fuso explicito (-0300) -> 20:00 local = 23:00 UTC
  assert.equal(EpgGuide.toTimestampMs('20260310200000 -0300'), T20);
  // ISO sem fuso e assumido como UTC
  assert.equal(EpgGuide.toTimestampMs('2026-03-10T23:00:00'), T20);

  assert.ok(Number.isNaN(EpgGuide.toTimestampMs('')));
  assert.ok(Number.isNaN(EpgGuide.toTimestampMs(null)));
  assert.ok(Number.isNaN(EpgGuide.toTimestampMs('lixo')));
});

test('normalizeProgram aceita payload Xtream e devolve start/stop em ms', () => {
  const p = EpgGuide.normalizeProgram({
    title: 'Jornal Nacional',
    description: 'Principal jornal da noite',
    start_timestamp: String(T20 / 1000),
    stop_timestamp: String((T20 + HOUR) / 1000)
  });

  assert.ok(p);
  assert.equal(p.title, 'Jornal Nacional');
  assert.equal(p.desc, 'Principal jornal da noite');
  assert.equal(p.start, T20);
  assert.equal(p.stop, T20 + HOUR);
  assert.equal(p.durationMs, HOUR);
});

test('normalizeProgram rejeita programas sem janela válida', () => {
  assert.equal(EpgGuide.normalizeProgram(null), null);
  assert.equal(EpgGuide.normalizeProgram({ title: 'x' }), null);
  // stop antes do start
  assert.equal(EpgGuide.normalizeProgram({ title: 'x', start: T20, stop: T20 - 1000 }), null);
  // stop igual ao start
  assert.equal(EpgGuide.normalizeProgram({ title: 'x', start: T20, stop: T20 }), null);
});

test('normalizePrograms ordena por horário e descarta os inválidos', () => {
  const list = EpgGuide.normalizePrograms([
    { title: 'B', start: T20 + HOUR, stop: T20 + 2 * HOUR },
    { title: 'invalido' },
    { title: 'A', start: T20, stop: T20 + HOUR }
  ]);

  assert.equal(list.length, 2);
  assert.equal(list[0].title, 'A');
  assert.equal(list[1].title, 'B');
});

test('buildTimeline marca o programa no ar e calcula o progresso', () => {
  const now = T20 + 30 * MIN;
  const timeline = EpgGuide.buildTimeline([
    { title: 'Anterior', start: T20 - HOUR, stop: T20 },
    { title: 'Agora', start: T20, stop: T20 + HOUR },
    { title: 'Depois', start: T20 + HOUR, stop: T20 + 2 * HOUR }
  ], now);

  assert.equal(timeline.length, 3);
  assert.equal(timeline[0].isPast, true);
  assert.equal(timeline[0].isLive, false);
  assert.equal(timeline[1].isLive, true);
  assert.equal(timeline[1].progressPct, 50);
  assert.equal(timeline[2].isLive, false);
  assert.equal(timeline[2].isPast, false);
});

test('pickNowNext devolve o programa atual e o seguinte', () => {
  const now = T20 + 30 * MIN;
  const { current, next } = EpgGuide.pickNowNext([
    { title: 'Agora', start: T20, stop: T20 + HOUR },
    { title: 'Depois', start: T20 + HOUR, stop: T20 + 2 * HOUR }
  ], now);

  assert.equal(current.title, 'Agora');
  assert.equal(next.title, 'Depois');
});

test('pickNowNext sem nada no ar devolve o próximo futuro', () => {
  const now = T20 - 2 * HOUR;
  const { current, next } = EpgGuide.pickNowNext([
    { title: 'Agora', start: T20, stop: T20 + HOUR },
    { title: 'Depois', start: T20 + HOUR, stop: T20 + 2 * HOUR }
  ], now);

  assert.equal(current.title, 'Agora');
  assert.equal(next.title, 'Depois');
});

test('pickNowNext com grade toda vencida devolve o ultimo e nenhum proximo', () => {
  const now = T20 + 10 * HOUR;
  const { current, next } = EpgGuide.pickNowNext([
    { title: 'Antigo', start: T20 - 3 * HOUR, stop: T20 - 2 * HOUR }
  ], now);

  assert.equal(current.title, 'Antigo');
  assert.equal(next, null);
});

test('pickNowNext com lista vazia devolve null nos dois lados', () => {
  const { current, next } = EpgGuide.pickNowNext([], T20);
  assert.equal(current, null);
  assert.equal(next, null);
});

test('mergeProgramSources junta XMLTV + Xtream sem duplicar o mesmo programa', () => {
  const xmltv = [{ title: 'Jornal', start: T20, stop: T20 + HOUR }];
  const xtream = [
    { title: 'Jornal', start: T20 + 1000, stop: T20 + HOUR + 1000 }, // mesmo programa
    { title: 'Esporte', start: T20 + HOUR, stop: T20 + 2 * HOUR }
  ];

  const merged = EpgGuide.mergeProgramSources(xmltv, xtream);
  assert.equal(merged.length, 2);
  assert.equal(merged[0].title, 'Jornal');
  assert.equal(merged[1].title, 'Esporte');
});

test('mergeProgramSources completa a sinopse ausente com a de outra fonte', () => {
  const merged = EpgGuide.mergeProgramSources(
    [{ title: 'Jornal', start: T20, stop: T20 + HOUR }],
    [{ title: 'Jornal', start: T20, stop: T20 + HOUR, description: 'Com detalhes' }]
  );

  assert.equal(merged.length, 1);
  assert.equal(merged[0].desc, 'Com detalhes');
});

test('mergeProgramSources aceita fontes vazias e ignore nao-listas', () => {
  const merged = EpgGuide.mergeProgramSources(null, undefined, [], [
    { title: 'A', start: T20, stop: T20 + HOUR }
  ]);
  assert.equal(merged.length, 1);
});

test('minutesRemaining conta os minutos que faltam para o fim', () => {
  const now = T20 + 30 * MIN;
  assert.equal(EpgGuide.minutesRemaining({ start: T20, stop: T20 + HOUR }, now), 30);
  // fora da janela -> 0
  assert.equal(EpgGuide.minutesRemaining({ start: T20 - 2 * HOUR, stop: T20 - HOUR }, now), 0);
  assert.equal(EpgGuide.minutesRemaining(null, now), 0);
});

test('formatCountdown formata mm:ss e hh:mm:ss', () => {
  assert.equal(EpgGuide.formatCountdown(65 * 1000), '01:05');
  assert.equal(EpgGuide.formatCountdown(3725 * 1000), '1:02:05');
  assert.equal(EpgGuide.formatCountdown(-1), '--:--');
  assert.equal(EpgGuide.formatCountdown(NaN), '--:--');
});

test('formatClock devolve HH:MM do horario local', () => {
  const d = new Date(2026, 2, 10, 20, 15, 0);
  assert.equal(EpgGuide.formatClockDate(d), '20:15');
  assert.equal(EpgGuide.formatClock(NaN), '--:--');
});

test('summarizeChannel monta o resumo NO AR / A SEGUIR com contagem regressiva', () => {
  const now = T20 + 30 * MIN;
  const summary = EpgGuide.summarizeChannel(
    { name: 'Globo', logo: 'https://exemplo/globo.png', group: 'Abertos' },
    [
      { title: 'Jornal', start: T20, stop: T20 + HOUR },
      { title: 'Esporte', start: T20 + HOUR, stop: T20 + 2 * HOUR }
    ],
    now
  );

  assert.equal(summary.name, 'Globo');
  assert.equal(summary.currentTitle, 'Jornal');
  assert.equal(summary.nextTitle, 'Esporte');
  assert.equal(summary.isLive, true);
  assert.equal(summary.progressPct, 50);
  assert.equal(summary.countdownMs, 30 * MIN);
  assert.equal(summary.countdownText, '30:00');
});

test('summarizeChannel sem EPG devolve campos vazios sem quebrar', () => {
  const summary = EpgGuide.summarizeChannel({ name: 'Canal X' }, [], T20);
  assert.equal(summary.name, 'Canal X');
  assert.equal(summary.currentTitle, '');
  assert.equal(summary.nextTitle, '');
  assert.equal(summary.countdownText, '--:--');
  assert.equal(summary.isLive, false);
});
