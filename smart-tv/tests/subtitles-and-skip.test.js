'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const subsApi = require('../../api/subtitles.js');
const SubtitleAndSkip = require('../mk21-tv/core/SubtitleAndSkip.js');

function createMockRes() {
  const headers = {};
  let body = '';
  return {
    statusCode: 200,
    setHeader(k, v) { headers[k.toLowerCase()] = v; },
    getHeader(k) { return headers[k.toLowerCase()]; },
    end(chunk) { if (chunk) body += chunk; },
    text() { return body; },
    json() { return JSON.parse(body || '{}'); },
  };
}

const SAMPLE_SRT = [
  '1',
  '00:00:01,500 --> 00:00:04,000',
  'Olá, mundo!',
  '',
  '2',
  '00:00:10,000 --> 00:00:12,500',
  'Segunda legenda.',
].join('\n');

test('srtToVtt e shiftVttTimestamps convertem e deslocam timestamps sem ficar negativos', () => {
  const vtt = subsApi.srtToVtt(SAMPLE_SRT);
  assert.ok(vtt.startsWith('WEBVTT\n'));
  assert.ok(vtt.includes('00:00:01.500 --> 00:00:04.000'));

  const plusHalf = subsApi.shiftVttTimestamps(SAMPLE_SRT, 0.5);
  assert.ok(plusHalf.includes('00:00:02.000 --> 00:00:04.500'));
  assert.ok(plusHalf.includes('00:00:10.500 --> 00:00:13.000'));

  const minusTwo = SubtitleAndSkip.shiftVttTimestamps(SAMPLE_SRT, -2.0);
  assert.ok(minusTwo.includes('00:00:00.000 --> 00:00:02.000'));
  assert.ok(minusTwo.includes('00:00:08.000 --> 00:00:10.500'));
});

test('buildSubtitleQuery limpa tags técnicas preservando episódio e detectVodSkipAction detecta abertura e créditos', () => {
  assert.equal(
    SubtitleAndSkip.buildSubtitleQuery('[4K] Breaking Bad S01E02 (2008) DUBLADO'),
    'Breaking Bad S01E02'
  );
  assert.equal(SubtitleAndSkip.formatOffsetLabel(0.5), '+0.5s');
  assert.equal(SubtitleAndSkip.formatOffsetLabel(-1.5), '-1.5s');
  assert.equal(SubtitleAndSkip.formatOffsetLabel(0), '0.0s');

  // Canal ao vivo nunca mostra botão de pulo
  assert.equal(SubtitleAndSkip.detectVodSkipAction(30, 3600, 'LIVE'), null);

  // Vídeo curto (< 10 min) nunca mostra
  assert.equal(SubtitleAndSkip.detectVodSkipAction(30, 300, 'MOVIE'), null);

  // Janela de abertura em série (15s..180s -> avança +85s)
  const intro = SubtitleAndSkip.detectVodSkipAction(20, 2700, 'SERIES');
  assert.ok(intro);
  assert.equal(intro.type, 'skip_intro');
  assert.ok(intro.label.includes('Pular Abertura'));
  assert.equal(intro.targetSec, 105);

  // Janela de créditos finais em filme
  const credits = SubtitleAndSkip.detectVodSkipAction(7140, 7200, 'MOVIE');
  assert.ok(credits);
  assert.equal(credits.type, 'skip_credits');
  assert.ok(credits.label.includes('Pular Créditos'));
});

test('handler /api/subtitles retorna 503 sem API key e executa search + download SRT→VTT', async () => {
  const unconfigured = subsApi.createSubtitlesHandler({ getApiKey: () => '' });
  const res503 = createMockRes();
  await unconfigured({ method: 'GET', url: '/api/subtitles?action=search&query=Matrix' }, res503);
  assert.equal(res503.statusCode, 503);

  const mockFetch = async (url) => {
    if (String(url).includes('/subtitles?')) {
      return {
        ok: true,
        status: 200,
        json: async () => ({
          data: [
            {
              attributes: {
                language: 'pt-br',
                release: 'The.Matrix.1999.1080p',
                download_count: 5400,
                fps: 23.976,
                files: [{ file_id: 98765, file_name: 'matrix.pt-br.srt' }],
              },
            },
          ],
        }),
      };
    }
    if (String(url).includes('/download')) {
      return {
        ok: true,
        status: 200,
        json: async () => ({ link: 'https://cdn.opensubtitles.example/matrix.srt' }),
      };
    }
    return {
      ok: true,
      status: 200,
      text: async () => SAMPLE_SRT,
    };
  };

  const handler = subsApi.createSubtitlesHandler({
    getApiKey: () => 'test-key-123',
    fetch: mockFetch,
  });

  const resSearch = createMockRes();
  await handler({ method: 'GET', url: '/api/subtitles?action=search&query=Matrix&lang=pt-br' }, resSearch);
  assert.equal(resSearch.statusCode, 200);
  const searchJson = resSearch.json();
  assert.equal(searchJson.ok, true);
  assert.equal(searchJson.items.length, 1);
  assert.equal(searchJson.items[0].fileId, 98765);

  const resDl = createMockRes();
  await handler({ method: 'GET', url: '/api/subtitles?action=download&file_id=98765&offset=1.0' }, resDl);
  assert.equal(resDl.statusCode, 200);
  assert.equal(resDl.getHeader('content-type'), 'text/vtt; charset=utf-8');
  assert.ok(resDl.text().startsWith('WEBVTT\n'));
  assert.ok(resDl.text().includes('00:00:02.500 --> 00:00:05.000'));
});
