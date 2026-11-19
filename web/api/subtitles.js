/**
 * MK21 Play — Proxy OpenSubtitles REST API v1 + Conversor SRT → WebVTT + Ajuste de Sincronia
 *
 * Endpoints:
 *   GET /api/subtitles?action=search&query=Matrix&lang=pt-br,pt-pt,en
 *   GET /api/subtitles?action=download&file_id=12345&offset=0.5
 *
 * Requer OPENSUBTITLES_API_KEY configurada nas variáveis de ambiente da Vercel.
 * Converte automaticamente SRT para WebVTT e aplica offset de sincronia (±30s)
 * para consumo direto em Smart TV (<track>), Web e Android (Media3 ExoPlayer).
 */

const OPENSUBTITLES_BASE = 'https://api.opensubtitles.com/api/v1';
const USER_AGENT = 'MK21Play v2.9';
const MAX_OFFSET_SEC = 60;

function setCors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
}

function sendJson(res, status, data) {
  setCors(res);
  res.statusCode = status;
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.end(JSON.stringify(data));
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

/**
 * Converte SRT para WebVTT válido (preservando se já for WebVTT).
 */
function srtToVtt(inputText) {
  const clean = String(inputText || '').replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n').trim();
  if (!clean) return 'WEBVTT\n\n';
  if (/^WEBVTT\b/i.test(clean)) return clean + '\n';

  const lines = clean.split('\n');
  const out = ['WEBVTT', ''];
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    // Converte linha de timestamp SRT (00:01:02,500 --> 00:01:05,000) para ponto WebVTT
    if (/^\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}\s*-->\s*\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}/.test(line.trim())) {
      out.push(line.replace(/(\d{1,2}:\d{2}:\d{2}),(\d{1,3})/g, '$1.$2'));
    } else {
      out.push(line);
    }
  }
  return out.join('\n') + '\n';
}

/**
 * Desloca todos os timestamps de um texto WebVTT/SRT em `offsetSec` segundos.
 */
function shiftVttTimestamps(inputText, offsetSec) {
  const vtt = srtToVtt(inputText);
  const offset = Number(offsetSec);
  if (!Number.isFinite(offset) || Math.abs(offset) < 0.001) return vtt;
  const clampedOffsetMs = Math.max(-MAX_OFFSET_SEC, Math.min(MAX_OFFSET_SEC, offset)) * 1000;

  return vtt.replace(
    /(\d{1,2}:\d{2}:\d{2}\.\d{1,3})(\s*-->\s*)(\d{1,2}:\d{2}:\d{2}\.\d{1,3})/g,
    (match, startRaw, arrow, endRaw) => {
      const sMs = parseTimestampMs(startRaw);
      const eMs = parseTimestampMs(endRaw);
      if (!Number.isFinite(sMs) || !Number.isFinite(eMs)) return match;
      const newStart = Math.max(0, sMs + clampedOffsetMs);
      const newEnd = Math.max(newStart + 200, eMs + clampedOffsetMs);
      return `${formatVttTimestamp(newStart)}${arrow}${formatVttTimestamp(newEnd)}`;
    }
  );
}

function sanitizeLanguages(raw) {
  const clean = String(raw || 'pt-br,pt-pt,en')
    .toLowerCase()
    .replace(/[^a-z0-9,-]/g, '')
    .split(',')
    .filter(Boolean)
    .slice(0, 5);
  return clean.length ? clean.join(',') : 'pt-br,pt-pt,en';
}

function createSubtitlesHandler(deps = {}) {
  const getApiKey = deps.getApiKey || (() => (process.env.OPENSUBTITLES_API_KEY || '').trim());
  const fetchImpl = deps.fetch || globalThis.fetch;

  return async function handler(req, res) {
    if (req.method === 'OPTIONS') {
      setCors(res);
      res.statusCode = 204;
      return res.end();
    }
    if (req.method !== 'GET') {
      res.setHeader('Allow', 'GET, OPTIONS');
      return sendJson(res, 405, { ok: false, error: 'Método não permitido.' });
    }

    const apiKey = getApiKey();
    if (!apiKey) {
      return sendJson(res, 503, {
        ok: false,
        error: 'OPENSUBTITLES_API_KEY não configurada no servidor Vercel.',
      });
    }

    const parsedUrl = new URL(req.url || '/', 'https://mk21.local');
    const action = (parsedUrl.searchParams.get('action') || 'search').toLowerCase();

    try {
      if (action === 'search') {
        const query = (parsedUrl.searchParams.get('query') || '').trim().slice(0, 120);
        if (query.length < 2) {
          return sendJson(res, 400, { ok: false, error: 'Informe ao menos 2 caracteres no parâmetro query.' });
        }
        const languages = sanitizeLanguages(parsedUrl.searchParams.get('lang'));
        const searchUrl = `${OPENSUBTITLES_BASE}/subtitles?query=${encodeURIComponent(query)}&languages=${encodeURIComponent(languages)}&order_by=download_count`;

        const resp = await fetchImpl(searchUrl, {
          headers: {
            'Api-Key': apiKey,
            'User-Agent': USER_AGENT,
            Accept: 'application/json',
          },
        });
        if (!resp.ok) {
          return sendJson(res, 502, { ok: false, error: `OpenSubtitles retornou HTTP ${resp.status}.` });
        }

        const json = await resp.json();
        const rawItems = Array.isArray(json && json.data) ? json.data : [];
        const items = [];
        for (let i = 0; i < rawItems.length && items.length < 15; i++) {
          const attr = (rawItems[i] && rawItems[i].attributes) || {};
          const files = Array.isArray(attr.files) ? attr.files : [];
          const file = files[0];
          if (!file || !file.file_id) continue;
          items.push({
            fileId: Number(file.file_id),
            fileName: String(file.file_name || attr.release || 'legenda.srt'),
            release: String(attr.release || file.file_name || query),
            language: String(attr.language || 'pt-br'),
            downloads: Number(attr.download_count || 0),
            fps: Number(attr.fps || 0),
          });
        }

        res.setHeader('Cache-Control', 'public, s-maxage=900, stale-while-revalidate=300');
        return sendJson(res, 200, { ok: true, query, items });
      }

      if (action === 'download') {
        const fileId = parseInt(parsedUrl.searchParams.get('file_id') || '', 10);
        if (!Number.isFinite(fileId) || fileId <= 0) {
          return sendJson(res, 400, { ok: false, error: 'Parâmetro file_id inválido.' });
        }
        const offsetSec = parseFloat(parsedUrl.searchParams.get('offset') || '0') || 0;

        const dlResp = await fetchImpl(`${OPENSUBTITLES_BASE}/download`, {
          method: 'POST',
          headers: {
            'Api-Key': apiKey,
            'User-Agent': USER_AGENT,
            'Content-Type': 'application/json',
            Accept: 'application/json',
          },
          body: JSON.stringify({ file_id: fileId }),
        });
        if (!dlResp.ok) {
          return sendJson(res, 502, { ok: false, error: `OpenSubtitles download retornou HTTP ${dlResp.status}.` });
        }
        const dlData = await dlResp.json();
        const link = String((dlData && dlData.link) || '').trim();
        if (!/^https:\/\//i.test(link)) {
          return sendJson(res, 502, { ok: false, error: 'Link de legenda inválido retornado pelo provedor.' });
        }

        const subResp = await fetchImpl(link, { headers: { 'User-Agent': USER_AGENT } });
        if (!subResp.ok) {
          return sendJson(res, 502, { ok: false, error: `Falha ao baixar arquivo de legenda (HTTP ${subResp.status}).` });
        }
        const rawText = await subResp.text();
        const vttText = shiftVttTimestamps(rawText, offsetSec);

        setCors(res);
        res.statusCode = 200;
        res.setHeader('Content-Type', 'text/vtt; charset=utf-8');
        res.setHeader('Cache-Control', 'public, s-maxage=3600');
        return res.end(vttText);
      }

      return sendJson(res, 400, { ok: false, error: 'Parâmetro action inválido (use search ou download).' });
    } catch (err) {
      console.error('Subtitles API error:', err && err.message ? err.message : err);
      return sendJson(res, 500, { ok: false, error: 'Erro interno ao consultar OpenSubtitles.' });
    }
  };
}

const defaultHandler = createSubtitlesHandler();
defaultHandler.createSubtitlesHandler = createSubtitlesHandler;
defaultHandler.parseTimestampMs = parseTimestampMs;
defaultHandler.formatVttTimestamp = formatVttTimestamp;
defaultHandler.srtToVtt = srtToVtt;
defaultHandler.shiftVttTimestamps = shiftVttTimestamps;

module.exports = defaultHandler;
