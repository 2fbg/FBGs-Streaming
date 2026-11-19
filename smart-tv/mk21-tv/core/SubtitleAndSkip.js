/**
 * MK21 Play — Legendas OpenSubtitles + Sincronia (±s) + Pulo de Abertura/Créditos em VOD
 *
 * Módulo puro testável em Node (sem DOM).
 */

const SUBTITLES_ENDPOINT = 'https://bgstreaming.vercel.app/api/subtitles';
const MIN_OFFSET_SEC = -30;
const MAX_OFFSET_SEC = 30;
const INTRO_START_MIN_SEC = 15;
const INTRO_START_MAX_SEC = 180;
const INTRO_SKIP_DURATION_SEC = 85;
const CREDITS_TAIL_SEC = 90;

function clampOffset(sec) {
  const n = Number(sec);
  if (!Number.isFinite(n)) return 0;
  const rounded = Math.round(n * 2) / 2; // passos de 0.5s
  return Math.max(MIN_OFFSET_SEC, Math.min(MAX_OFFSET_SEC, rounded));
}

function formatOffsetLabel(sec) {
  const v = clampOffset(sec);
  const sign = v > 0 ? '+' : '';
  return `${sign}${v.toFixed(1)}s`;
}

function parseTimestampMs(raw) {
  const m = String(raw || '').trim().match(/^(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})$/);
  if (!m) return NaN;
  const hh = parseInt(m[1], 10);
  const mm = parseInt(m[2], 10);
  const ss = parseInt(m[3], 10);
  const ms = parseInt(m[4].padEnd(3, '0'), 10);
  return ((hh * 3600 + mm * 60 + ss) * 1000) + ms;
}

function formatVttTimestamp(ms) {
  const clamped = Math.max(0, Math.round(ms));
  const hours = Math.floor(clamped / 3600000);
  const mins = Math.floor((clamped % 3600000) / 60000);
  const secs = Math.floor((clamped % 60000) / 1000);
  const millis = clamped % 1000;
  return (
    String(hours).padStart(2, '0') + ':' +
    String(mins).padStart(2, '0') + ':' +
    String(secs).padStart(2, '0') + '.' +
    String(millis).padStart(3, '0')
  );
}

function srtToVtt(inputText) {
  const clean = String(inputText || '').replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n').trim();
  if (!clean) return 'WEBVTT\n\n';
  if (/^WEBVTT\b/i.test(clean)) return clean + '\n';

  const lines = clean.split('\n');
  const out = ['WEBVTT', ''];
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (/^\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}\s*-->\s*\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}/.test(line.trim())) {
      out.push(line.replace(/(\d{1,2}:\d{2}:\d{2}),(\d{1,3})/g, '$1.$2'));
    } else {
      out.push(line);
    }
  }
  return out.join('\n') + '\n';
}

function shiftVttTimestamps(inputText, offsetSec) {
  const vtt = srtToVtt(inputText);
  const offset = clampOffset(offsetSec);
  if (Math.abs(offset) < 0.001) return vtt;
  const offsetMs = offset * 1000;

  return vtt.replace(
    /(\d{1,2}:\d{2}:\d{2}\.\d{1,3})(\s*-->\s*)(\d{1,2}:\d{2}:\d{2}\.\d{1,3})/g,
    (match, startRaw, arrow, endRaw) => {
      const sMs = parseTimestampMs(startRaw);
      const eMs = parseTimestampMs(endRaw);
      if (!Number.isFinite(sMs) || !Number.isFinite(eMs)) return match;
      const ns = Math.max(0, sMs + offsetMs);
      const ne = Math.max(ns + 200, eMs + offsetMs);
      return `${formatVttTimestamp(ns)}${arrow}${formatVttTimestamp(ne)}`;
    }
  );
}

/**
 * Limpa nome de filme/episódio para uma busca precisa no OpenSubtitles.
 */
function buildSubtitleQuery(rawTitle) {
  return String(rawTitle || '')
    .replace(/\[[^\]]*\]|\([^)]*\)/g, ' ')
    .replace(/\b(4k|uhd|fhd|hd|sd|h265|hevc|h264|dublado|legendado|dual|nacional|pt-br)\b/gi, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 100);
}

/**
 * Detecta quando exibir o botão flutuante "Pular Abertura" ou "Pular Créditos" em VOD.
 * Nunca dispara em TV Ao Vivo.
 */
function detectVodSkipAction(currentTimeSec, durationSec, contentType) {
  const type = String(contentType || '').toUpperCase();
  if (type !== 'SERIES' && type !== 'MOVIE' && type !== 'VOD') return null;

  const cur = Number(currentTimeSec);
  const dur = Number(durationSec);
  if (!Number.isFinite(cur) || !Number.isFinite(dur) || dur < 600) return null;

  // Janela de abertura (episódios de séries ou filmes longos): entre 15s e 180s
  if (type === 'SERIES' && cur >= INTRO_START_MIN_SEC && cur <= INTRO_START_MAX_SEC) {
    const targetSec = Math.min(dur - 30, Math.round(cur + INTRO_SKIP_DURATION_SEC));
    return {
      type: 'skip_intro',
      label: '⏩ Pular Abertura (+85s)',
      targetSec
    };
  }

  // Janela de créditos finais: últimos 90s (ou após 93% do conteúdo)
  const remaining = dur - cur;
  const pct = cur / dur;
  if (remaining > 8 && (remaining <= CREDITS_TAIL_SEC || pct >= 0.93)) {
    return {
      type: 'skip_credits',
      label: type === 'SERIES' ? '⏭️ Próximo / Pular Créditos' : '⏭️ Pular Créditos',
      targetSec: Math.max(cur, Math.round(dur - 3))
    };
  }

  return null;
}

module.exports = {
  SUBTITLES_ENDPOINT,
  MIN_OFFSET_SEC,
  MAX_OFFSET_SEC,
  clampOffset,
  formatOffsetLabel,
  parseTimestampMs,
  formatVttTimestamp,
  srtToVtt,
  shiftVttTimestamps,
  buildSubtitleQuery,
  detectVodSkipAction
};
