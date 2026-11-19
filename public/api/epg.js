/**
 * MK21 Play — Endpoint serverless de EPG público (sem credencial), inspirado no Saimo TV.
 *
 * Consulta grade pública brasileira (meuguia.tv) com:
 *  - allowlist estrita de host (meuguia.tv apenas — zero SSRF);
 *  - cache em memória de 15 minutos por canal;
 *  - parser leve por regex (sem dependências DOM pesadas);
 *  - normalização de nomes de canais IPTV (remove FHD/HD/4K/H265/etc.).
 *
 * Rota: GET /api/epg?channel=Globo+SP
 */

const ALLOWED_HOST = 'meuguia.tv';
const CACHE_TTL_MS = 15 * 60 * 1000;
const FETCH_TIMEOUT_MS = 8000;
const MAX_CACHE_ENTRIES = 180;

const ALLOWED_ORIGINS = new Set([
  'https://bgstreaming.vercel.app',
  'https://fbgs-streaming.vercel.app',
  'https://fbg-streaming.vercel.app',
  'https://mk21play.vercel.app',
  'http://localhost:3000',
  'http://localhost:5173',
  'http://127.0.0.1:3000',
  'http://127.0.0.1:5173',
  'null' // LG webOS / Samsung Tizen file:// origins
]);

/** Mapa de nomes normalizados -> slug público do meuguia.tv */
const CHANNEL_SLUG_MAP = [
  ['globo', 'GRD'],
  ['tv globo', 'GRD'],
  ['rede globo', 'GRD'],
  ['record', 'REC'],
  ['record tv', 'REC'],
  ['sbt', 'SBT'],
  ['band', 'BAN'],
  ['bandeirantes', 'BAN'],
  ['redetv', 'RTV'],
  ['rede tv', 'RTV'],
  ['cultura', 'CUL'],
  ['tv cultura', 'CUL'],
  ['tv brasil', 'TBR'],
  ['gazeta', 'GAZ'],
  ['sportv 3', 'SP3'],
  ['sportv3', 'SP3'],
  ['sportv 2', 'SP2'],
  ['sportv2', 'SP2'],
  ['sportv', 'SPO'],
  ['espn 4', 'ES4'],
  ['espn4', 'ES4'],
  ['espn 3', 'ES3'],
  ['espn3', 'ES3'],
  ['espn 2', 'ES2'],
  ['espn2', 'ES2'],
  ['espn', 'ESP'],
  ['premiere', 'PRE'],
  ['combate', 'CMB'],
  ['bandsports', 'BSP'],
  ['globonews', 'GLN'],
  ['globo news', 'GLN'],
  ['cnn brasil', 'CNN'],
  ['bandnews', 'BNE'],
  ['band news', 'BNE'],
  ['jovem pan news', 'JPN'],
  ['multishow', 'MSW'],
  ['gnt', 'GNT'],
  ['viva', 'VIV'],
  ['bis', 'BIS'],
  ['off', 'OFF'],
  ['canal off', 'OFF'],
  ['telecine premium', 'TC1'],
  ['telecine action', 'TC2'],
  ['telecine touch', 'TC3'],
  ['telecine fun', 'TC4'],
  ['telecine pipoca', 'TC5'],
  ['telecine cult', 'TC6'],
  ['hbo plus', 'HPL'],
  ['hbo family', 'HFA'],
  ['hbo 2', 'HB2'],
  ['hbo2', 'HB2'],
  ['hbo', 'HBO'],
  ['cinemax', 'CMX'],
  ['megapix', 'MPX'],
  ['tnt series', 'TNS'],
  ['tnt', 'TNT'],
  ['space', 'SPA'],
  ['warner', 'WBT'],
  ['sony', 'SET'],
  ['axn', 'AXN'],
  ['universal', 'USA'],
  ['studio universal', 'STU'],
  ['star channel', 'FOX'],
  ['fx', 'FXB'],
  ['a&e', 'AEE'],
  ['history 2', 'H2B'],
  ['history', 'HIS'],
  ['discovery kids', 'DIK'],
  ['discovery home', 'DHH'],
  ['discovery turbo', 'DTU'],
  ['discovery', 'DIS'],
  ['animal planet', 'APL'],
  ['national geographic', 'NGC'],
  ['natgeo', 'NGC'],
  ['cartoon network', 'CAR'],
  ['cartoonito', 'BMR'],
  ['nickelodeon', 'NIC'],
  ['nick jr', 'NJR'],
  ['gloob', 'GLB'],
  ['disney channel', 'DNY']
];

const memoryCache = new Map();

function normalizeChannelName(raw) {
  if (!raw) return '';
  return String(raw)
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/\[[^\]]*\]|\([^)]*\)/g, ' ')
    .replace(/\b(4k|uhd|fhd|hd|sd|h265|hevc|60fps|50fps|br|brasil|pt|ao vivo|alternativo|backup|bkp|sp|rj|mg|df|rs|ba|pe|ce|pr)\b/g, ' ')
    .replace(/[^a-z0-9&]+/g, ' ')
    .trim()
    .replace(/\s+/g, ' ');
}

function resolveChannelSlug(channelName) {
  const norm = normalizeChannelName(channelName);
  if (!norm) return null;

  // Match exato primeiro
  for (let i = 0; i < CHANNEL_SLUG_MAP.length; i++) {
    if (norm === CHANNEL_SLUG_MAP[i][0]) return CHANNEL_SLUG_MAP[i][1];
  }
  // Prefixo / inclusão ordenada (canais mais específicos vêm antes na tabela)
  for (let i = 0; i < CHANNEL_SLUG_MAP.length; i++) {
    const key = CHANNEL_SLUG_MAP[i][0];
    if (norm.startsWith(key + ' ') || norm.includes(' ' + key + ' ') || norm.endsWith(' ' + key) || norm.includes(key)) {
      return CHANNEL_SLUG_MAP[i][1];
    }
  }
  return null;
}

function decodeHtmlEntities(str) {
  return String(str || '')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&#x([0-9a-fA-F]+);/g, (_, hex) => String.fromCodePoint(parseInt(hex, 16)))
    .replace(/&#(\d+);/g, (_, dec) => String.fromCodePoint(parseInt(dec, 10)))
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Extrai a grade de um HTML do meuguia.tv/programacao/canal/<SLUG>.
 * Constrói timestamps em horário de Brasília (-03:00) avançando o dia quando a hora vira.
 */
function parseMeuGuiaHtml(html, referenceDateMs) {
  if (typeof html !== 'string' || !html) return [];
  const nowMs = Number.isFinite(referenceDateMs) ? referenceDateMs : Date.now();

  // Data base no fuso -03:00 (horário oficial de Brasília)
  const brtOffsetMs = -3 * 60 * 60 * 1000;
  const brtNow = new Date(nowMs + brtOffsetMs);
  const baseYear = brtNow.getUTCFullYear();
  const baseMonth = brtNow.getUTCMonth();
  const baseDay = brtNow.getUTCDate();

  const entries = [];
  // Padrão meuguia.tv: <div class='lileft time'>HH:MM</div> ... <h2>Título</h2> ... <h3>Categoria</h3>
  const itemRegex = /<div[^>]*class=["'][^"']*\btime\b[^"']*["'][^>]*>\s*(\d{1,2}):(\d{2})\s*<\/div>[\s\S]{0,600}?<h2[^>]*>([\s\S]*?)<\/h2>(?:[\s\S]{0,250}?<h3[^>]*>([\s\S]*?)<\/h3>)?/gi;
  let match;
  let dayOffset = 0;
  let prevMinutes = -1;

  while ((match = itemRegex.exec(html)) !== null && entries.length < 180) {
    const hour = parseInt(match[1], 10);
    const minute = parseInt(match[2], 10);
    if (!Number.isFinite(hour) || !Number.isFinite(minute) || hour > 23 || minute > 59) continue;

    const totalMin = hour * 60 + minute;
    if (prevMinutes >= 0 && totalMin < prevMinutes) {
      dayOffset += 1;
    }
    prevMinutes = totalMin;

    const title = decodeHtmlEntities(match[3].replace(/<[^>]+>/g, ''));
    const category = decodeHtmlEntities((match[4] || '').replace(/<[^>]+>/g, ''));
    if (!title) continue;

    // Converte horário BRT (-03:00) para UTC epoch ms
    const startUtcMs = Date.UTC(baseYear, baseMonth, baseDay + dayOffset, hour + 3, minute, 0, 0);
    entries.push({
      title,
      desc: category ? `Categoria: ${category}` : '',
      category,
      start: startUtcMs,
      stop: 0
    });
  }

  for (let i = 0; i < entries.length; i++) {
    if (i + 1 < entries.length && entries[i + 1].start > entries[i].start) {
      entries[i].stop = entries[i + 1].start;
    } else {
      entries[i].stop = entries[i].start + 60 * 60 * 1000;
    }
  }

  return entries;
}

function setCorsHeaders(req, res) {
  const origin = req.headers && req.headers.origin;
  if (origin && (ALLOWED_ORIGINS.has(origin) || /^https:\/\/[a-z0-9-]+\.vercel\.app$/i.test(origin))) {
    res.setHeader('Access-Control-Allow-Origin', origin);
  } else {
    res.setHeader('Access-Control-Allow-Origin', '*');
  }
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
}

function setCacheEntry(slug, payload) {
  if (memoryCache.size >= MAX_CACHE_ENTRIES) {
    const oldestKey = memoryCache.keys().next().value;
    if (oldestKey) memoryCache.delete(oldestKey);
  }
  memoryCache.set(slug, { ts: Date.now(), payload });
}

async function handler(req, res) {
  setCorsHeaders(req, res);

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }
  if (req.method !== 'GET') {
    return res.status(405).json({ error: 'Method not allowed' });
  }

  const rawChannel = String((req.query && (req.query.channel || req.query.name)) || '').trim();
  if (!rawChannel || rawChannel.length > 120) {
    return res.status(400).json({ error: 'Parâmetro channel inválido ou ausente.' });
  }

  const slug = resolveChannelSlug(rawChannel);
  if (!slug) {
    res.setHeader('Cache-Control', 'public, max-age=300');
    return res.status(200).json({
      channel: rawChannel,
      slug: null,
      source: 'public-epg',
      programs: []
    });
  }

  const cached = memoryCache.get(slug);
  if (cached && (Date.now() - cached.ts) < CACHE_TTL_MS) {
    res.setHeader('Cache-Control', 'public, max-age=300');
    return res.status(200).json({
      ...cached.payload,
      channel: rawChannel,
      cached: true
    });
  }

  const targetUrl = `https://${ALLOWED_HOST}/programacao/canal/${encodeURIComponent(slug)}`;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);

  try {
    const upstream = await fetch(targetUrl, {
      method: 'GET',
      signal: controller.signal,
      headers: {
        'User-Agent': 'MK21-Play-EPG/2.9 (+https://bgstreaming.vercel.app)',
        'Accept': 'text/html,application/xhtml+xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'pt-BR,pt;q=0.9'
      }
    });

    if (!upstream.ok) {
      return res.status(200).json({
        channel: rawChannel,
        slug,
        source: 'public-epg',
        programs: [],
        upstreamStatus: upstream.status
      });
    }

    const html = await upstream.text();
    const programs = parseMeuGuiaHtml(html, Date.now());
    const payload = {
      channel: rawChannel,
      slug,
      source: 'meuguia',
      updatedAt: Date.now(),
      programs
    };
    setCacheEntry(slug, payload);
    res.setHeader('Cache-Control', 'public, max-age=300');
    return res.status(200).json(payload);
  } catch (err) {
    return res.status(200).json({
      channel: rawChannel,
      slug,
      source: 'public-epg',
      programs: [],
      error: 'Guia público indisponível temporariamente'
    });
  } finally {
    clearTimeout(timer);
  }
}

module.exports = handler;
module.exports.normalizeChannelName = normalizeChannelName;
module.exports.resolveChannelSlug = resolveChannelSlug;
module.exports.parseMeuGuiaHtml = parseMeuGuiaHtml;
module.exports.CHANNEL_SLUG_MAP = CHANNEL_SLUG_MAP;
