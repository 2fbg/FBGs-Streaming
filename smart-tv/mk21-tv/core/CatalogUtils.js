/**
 * MK21 Play — Utilitários puros de Métricas do Catálogo, Parsing Seguro JSON e Sync em Segundo Plano.
 * Compartilhado entre o bundle da Smart TV (app.js / OTA) e os testes unitários Node.
 */

const BG_SYNC_INTERVALS_MS = {
  startup: 0,
  daily: 86400000,
  weekly: 604800000,
  manual: -1
};

const BG_SYNC_LABELS = {
  startup: 'A cada inicialização',
  daily: 'Uma vez ao dia',
  weekly: 'Uma vez por semana',
  manual: 'Desativado (Apenas manual)'
};

const LEGACY_FREQ_ALIASES = {
  '1h': 'startup',
  '6h': 'daily',
  '24h': 'daily',
  'a cada inicialização': 'startup',
  'a cada inicializacao': 'startup',
  'uma vez ao dia': 'daily',
  'uma vez por semana': 'weekly',
  'desativado (apenas manual)': 'manual',
  'apenas manual': 'manual'
};

function normalizeBgSyncFrequency(freq) {
  const clean = String(freq || '').trim().toLowerCase();
  if (Object.prototype.hasOwnProperty.call(BG_SYNC_INTERVALS_MS, clean)) {
    return clean;
  }
  if (Object.prototype.hasOwnProperty.call(LEGACY_FREQ_ALIASES, clean)) {
    return LEGACY_FREQ_ALIASES[clean];
  }
  return 'daily';
}

function getBgSyncIntervalMs(freq) {
  const key = normalizeBgSyncFrequency(freq);
  const val = BG_SYNC_INTERVALS_MS[key];
  return val > 0 ? val : 0;
}

function shouldSyncServer(lastSyncMs, freq, nowMs) {
  const key = normalizeBgSyncFrequency(freq);
  if (key === 'manual') return false;
  const last = Number(lastSyncMs) || 0;
  if (last <= 0) return true;
  if (key === 'startup') return true;
  const now = typeof nowMs === 'number' && Number.isFinite(nowMs) ? nowMs : Date.now();
  const interval = getBgSyncIntervalMs(key);
  return (now - last) >= interval;
}

function computeCatalogMetrics(catalog, epgCache, hasXmltv) {
  const cat = catalog || {};
  const liveList = Array.isArray(cat.LIVE) ? cat.LIVE : [];
  const movieList = Array.isArray(cat.MOVIE) ? cat.MOVIE : [];
  const seriesList = Array.isArray(cat.SERIES) ? cat.SERIES : [];

  const groups = new Set();
  let epgTagged = 0;

  for (let i = 0; i < liveList.length; i++) {
    const item = liveList[i];
    if (!item) continue;
    if (item.group) groups.add(String(item.group).trim());
    if (item.tvgId || item.current_program) epgTagged++;
  }
  for (let i = 0; i < movieList.length; i++) {
    const item = movieList[i];
    if (item && item.group) groups.add(String(item.group).trim());
  }
  for (let i = 0; i < seriesList.length; i++) {
    const item = seriesList[i];
    if (item && item.group) groups.add(String(item.group).trim());
  }

  let epgCacheCount = 0;
  if (epgCache && typeof epgCache === 'object') {
    epgCacheCount = Object.keys(epgCache).length;
  }

  return {
    totalChannels: liveList.length,
    totalVOD: movieList.length,
    totalSeries: seriesList.length,
    totalGroups: groups.size,
    totalEPG: Math.max(epgTagged, epgCacheCount),
    totalItems: liveList.length + movieList.length + seriesList.length,
    hasXmltv: Boolean(hasXmltv)
  };
}

function parseSafeJsonResponse(rawText, httpStatus) {
  const text = String(rawText || '').trim();
  const status = Number(httpStatus) || 0;

  if (status === 401 || status === 403) {
    const authErr = new Error('Credenciais inválidas ou acesso negado (HTTP ' + status + ').');
    authErr.isAuthError = true;
    authErr.status = status;
    throw authErr;
  }

  if (!text) {
    throw new Error('Servidor retornou resposta vazia.');
  }

  const head = text.slice(0, 200).toLowerCase();
  if (head.startsWith('<') || head.includes('<!doctype html') || head.includes('<html')) {
    const htmlErr = new Error('Servidor retornou página HTML em vez de dados JSON.');
    htmlErr.isHtmlResponse = true;
    throw htmlErr;
  }

  if (/invalid username|invalid password|unauthorized/i.test(head)) {
    const authErr = new Error('Usuário ou senha inválidos no servidor.');
    authErr.isAuthError = true;
    throw authErr;
  }

  try {
    return JSON.parse(text);
  } catch (e) {
    const parseErr = new Error('Resposta inválida do servidor (formato não reconhecido).');
    parseErr.cause = e;
    throw parseErr;
  }
}

function formatFriendlyLoadError(err) {
  const msg = err && err.message ? String(err.message) : String(err || '');
  if (!msg) return 'A conexão foi interrompida antes da lista terminar.';
  if (/unexpected token\s*<?\s*in json|json\.parse/i.test(msg)) {
    return 'O servidor respondeu em formato HTML inválido em vez de JSON/M3U.';
  }
  if (/failed to fetch|networkerror|load failed/i.test(msg)) {
    return 'Não foi possível conectar ao servidor. Verifique a rede ou troque de fonte.';
  }
  if (/aborted|abort/i.test(msg)) {
    return 'Conexão cancelada ou tempo esgotado ao aguardar o servidor.';
  }
  return msg;
}

module.exports = {
  BG_SYNC_INTERVALS_MS,
  BG_SYNC_LABELS,
  normalizeBgSyncFrequency,
  getBgSyncIntervalMs,
  shouldSyncServer,
  computeCatalogMetrics,
  parseSafeJsonResponse,
  formatFriendlyLoadError
};
