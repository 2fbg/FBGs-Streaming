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

const PLAYLIST_TIMEOUTS = {
  initialResponseMs: 20000,
  stallWatchdogMs: 30000,
  hardCeilingMs: 300000,
  backgroundSyncMs: 240000
};

function normalizeCatalogMode(mode) {
  const clean = String(mode || '').trim().toLowerCase();
  if (clean === 'm3u' || clean === 'auto' || clean === 'json') return clean;
  return 'json';
}

function isDirectPlaylistUrl(rawUrl) {
  const u = String(rawUrl || '').trim().toLowerCase();
  return u.includes('get.php') || u.includes('.m3u') || u.includes('.ts') || u.includes('.m3u8');
}

function shouldPreferXtreamJson(rawUrl, mode) {
  if (isDirectPlaylistUrl(rawUrl)) return false;
  return normalizeCatalogMode(mode) !== 'm3u';
}

function buildXtreamXmltvUrl(baseUrl, user, pass) {
  const cleanBase = String(baseUrl || '').trim().replace(/\/+$/, '');
  const u = String(user || '').trim();
  const p = String(pass || '').trim();
  if (!cleanBase || !u || !p) return '';
  return `${cleanBase}/xmltv.php?username=${encodeURIComponent(u)}&password=${encodeURIComponent(p)}`;
}

function extractSafeHost(rawUrl) {
  const input = String(rawUrl || '').trim();
  if (!input) return 'servidor';
  try {
    const parsed = new URL(input);
    return parsed.host || input;
  } catch (e) {
    const stripped = input.replace(/^https?:\/\//i, '').split(/[/?#]/)[0];
    return stripped.replace(/^[^@]+@/, '') || 'servidor';
  }
}

function formatDurationPtBr(ms) {
  const safeMs = Math.max(0, Number(ms) || 0);
  const totalSec = Math.round(safeMs / 1000);
  if (totalSec < 60) return totalSec + 's';
  const mins = Math.floor(totalSec / 60);
  const secs = totalSec % 60;
  return mins + 'min ' + String(secs).padStart(2, '0') + 's';
}

function formatBytesMb(bytes) {
  const mb = Math.max(0, Number(bytes) || 0) / (1024 * 1024);
  return mb.toFixed(1).replace('.', ',') + ' MB';
}

function formatSpeedMbPerSec(bytes, elapsedMs) {
  const sec = Math.max(0.1, (Number(elapsedMs) || 0) / 1000);
  const mbPerSec = (Math.max(0, Number(bytes) || 0) / (1024 * 1024)) / sec;
  return mbPerSec.toFixed(1).replace('.', ',') + ' MB/s';
}

function detectHtmlPayload(sampleText, contentType) {
  const ct = String(contentType || '').toLowerCase();
  if (ct.includes('text/html') || ct.includes('application/xhtml')) return true;
  const head = String(sampleText || '').trim().slice(0, 320).toLowerCase();
  if (!head) return false;
  if (head.startsWith('<!doctype html') || head.startsWith('<html') || head.startsWith('<head') || head.startsWith('<body')) {
    return true;
  }
  if (head.includes('<!doctype html') || head.includes('<html') || head.includes('xui - debug mode')) {
    return true;
  }
  return false;
}

function createStallAwareAbortController(parentSignal, options) {
  const opts = options || {};
  const initialResponseMs = Number(opts.initialResponseMs) > 0 ? Number(opts.initialResponseMs) : PLAYLIST_TIMEOUTS.initialResponseMs;
  const stallWatchdogMs = Number(opts.stallWatchdogMs) > 0 ? Number(opts.stallWatchdogMs) : PLAYLIST_TIMEOUTS.stallWatchdogMs;
  const hardCeilingMs = Number(opts.hardCeilingMs) > 0 ? Number(opts.hardCeilingMs) : PLAYLIST_TIMEOUTS.hardCeilingMs;
  const setTimer = (opts.timerApi && opts.timerApi.setTimeout) || setTimeout;
  const clearTimer = (opts.timerApi && opts.timerApi.clearTimeout) || clearTimeout;

  const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
  let abortReason = null;
  let cleaned = false;
  let initialTimer = null;
  let stallTimer = null;
  let ceilingTimer = null;

  const triggerAbort = (reason) => {
    if (cleaned || abortReason) return;
    abortReason = reason;
    if (controller) {
      try { controller.abort(); } catch (e) {}
    }
  };

  const onParentAbort = () => triggerAbort('parent');
  if (parentSignal) {
    if (parentSignal.aborted) {
      triggerAbort('parent');
    } else if (parentSignal.addEventListener) {
      parentSignal.addEventListener('abort', onParentAbort, { once: true });
    }
  }

  const armStallWatchdog = () => {
    if (cleaned || abortReason) return;
    if (stallTimer) clearTimer(stallTimer);
    stallTimer = setTimer(() => triggerAbort('stall_timeout'), stallWatchdogMs);
  };

  if (!abortReason) {
    initialTimer = setTimer(() => triggerAbort('initial_timeout'), initialResponseMs);
    ceilingTimer = setTimer(() => triggerAbort('ceiling_timeout'), hardCeilingMs);
  }

  return {
    signal: controller ? controller.signal : null,
    markResponseReceived() {
      if (initialTimer) {
        clearTimer(initialTimer);
        initialTimer = null;
      }
      armStallWatchdog();
    },
    touchChunk() {
      if (initialTimer) {
        clearTimer(initialTimer);
        initialTimer = null;
      }
      armStallWatchdog();
    },
    getAbortReason() {
      return abortReason;
    },
    describeAbortReason() {
      if (abortReason === 'initial_timeout') {
        return 'tempo esgotado (sem resposta inicial após ' + formatDurationPtBr(initialResponseMs) + ')';
      }
      if (abortReason === 'stall_timeout') {
        return 'tempo esgotado (sem dados) após ' + formatDurationPtBr(stallWatchdogMs);
      }
      if (abortReason === 'ceiling_timeout') {
        return 'tempo esgotado (limite máximo de ' + formatDurationPtBr(hardCeilingMs) + ')';
      }
      if (abortReason === 'parent') {
        return 'conexão cancelada';
      }
      return '';
    },
    cleanup() {
      cleaned = true;
      if (initialTimer) { clearTimer(initialTimer); initialTimer = null; }
      if (stallTimer) { clearTimer(stallTimer); stallTimer = null; }
      if (ceilingTimer) { clearTimer(ceilingTimer); ceilingTimer = null; }
      if (parentSignal && parentSignal.removeEventListener) {
        parentSignal.removeEventListener('abort', onParentAbort);
      }
    }
  };
}

function formatFriendlyLoadError(err) {
  const msg = err && err.message ? String(err.message) : String(err || '');
  if (!msg) return 'A conexão foi interrompida antes da lista terminar.';
  if (/unexpected token\s*<?\s*in json|json\.parse/i.test(msg)) {
    return 'O servidor respondeu em formato HTML inválido em vez de JSON/M3U.';
  }
  if (/página html em vez de/i.test(msg)) {
    return msg;
  }
  if (/tempo esgotado/i.test(msg)) {
    return msg;
  }
  if (/failed to fetch|networkerror|load failed/i.test(msg)) {
    return 'Não foi possível conectar ao servidor. Verifique a rede ou troque de fonte.';
  }
  if (/aborted|abort/i.test(msg)) {
    return 'Conexão cancelada ou tempo esgotado ao aguardar o servidor.';
  }
  return msg;
}

function isPollutedLegacyFastCacheMetrics(metrics, lastSyncMs, isActiveServer) {
  if (!metrics || typeof metrics !== 'object') return false;
  if (isActiveServer) return false;
  if (Number(lastSyncMs) > 0) return false;
  const ch = Number(metrics.totalChannels) || 0;
  const vod = Number(metrics.totalVOD) || 0;
  const ser = Number(metrics.totalSeries) || 0;
  return ch === 400 && vod === 0 && ser === 0;
}

function computeGlobalServersMetrics(servers, perServerMetricsMap, activeServerId, activeCatalogMetrics, lastSyncMap) {
  const list = Array.isArray(servers) ? servers : [];
  const metricsMap = perServerMetricsMap && typeof perServerMetricsMap === 'object' ? perServerMetricsMap : {};
  const syncMap = lastSyncMap && typeof lastSyncMap === 'object' ? lastSyncMap : {};
  const seen = new Set();

  let totalChannels = 0;
  let totalVOD = 0;
  let totalSeries = 0;
  let totalGroups = 0;
  let totalEPG = 0;
  let syncedServers = 0;

  for (let i = 0; i < list.length; i++) {
    const srv = list[i];
    if (!srv || !srv.id || seen.has(srv.id)) continue;
    seen.add(srv.id);

    const isActive = Boolean(activeServerId && srv.id === activeServerId);
    const stored = metricsMap[srv.id] || null;
    const lastSync = Number(syncMap[srv.id]) || 0;
    const storedClean = (stored && !isPollutedLegacyFastCacheMetrics(stored, lastSync, isActive)) ? stored : null;

    let chosen = storedClean;
    if (isActive && activeCatalogMetrics && Number(activeCatalogMetrics.totalItems) > 0) {
      const storedTotal = storedClean ? (Number(storedClean.totalChannels || 0) + Number(storedClean.totalVOD || 0) + Number(storedClean.totalSeries || 0)) : 0;
      const activeTotal = Number(activeCatalogMetrics.totalChannels || 0) + Number(activeCatalogMetrics.totalVOD || 0) + Number(activeCatalogMetrics.totalSeries || 0);
      chosen = activeTotal >= storedTotal ? activeCatalogMetrics : storedClean;
    }

    if (!chosen) continue;
    const ch = Math.max(0, Number(chosen.totalChannels) || 0);
    const vod = Math.max(0, Number(chosen.totalVOD) || 0);
    const ser = Math.max(0, Number(chosen.totalSeries) || 0);
    if ((ch + vod + ser) <= 0) continue;

    totalChannels += ch;
    totalVOD += vod;
    totalSeries += ser;
    totalGroups += Math.max(0, Number(chosen.totalGroups) || 0);
    totalEPG += Math.max(0, Number(chosen.totalEPG) || 0);
    syncedServers++;
  }

  return {
    totalChannels,
    totalVOD,
    totalSeries,
    totalGroups,
    totalEPG,
    totalItems: totalChannels + totalVOD + totalSeries,
    syncedServers,
    totalServers: seen.size
  };
}

function computeServerLoadPercentage(serverId, progressMap, perServerMetrics, lastSyncMs, isActiveServer) {
  const entry = (progressMap && serverId && progressMap[serverId]) ? progressMap[serverId] : null;
  if (entry && entry.status === 'loading') {
    const pct = Math.max(1, Math.min(99, Math.round(Number(entry.percent) || 1)));
    return { percent: pct, status: 'loading', label: pct + '%' };
  }

  const syncTs = Number(lastSyncMs) || 0;
  const cleanMetrics = (perServerMetrics && !isPollutedLegacyFastCacheMetrics(perServerMetrics, syncTs, Boolean(isActiveServer)))
    ? perServerMetrics
    : null;
  const hasCachedData = Boolean(
    (cleanMetrics && ((Number(cleanMetrics.totalChannels) || 0) + (Number(cleanMetrics.totalVOD) || 0) + (Number(cleanMetrics.totalSeries) || 0)) > 0) ||
    syncTs > 0 ||
    (entry && entry.status === 'done')
  );

  if (hasCachedData) {
    return { percent: 100, status: 'done', label: '100%' };
  }
  if (entry && entry.status === 'error') {
    return { percent: 0, status: 'error', label: '0%' };
  }
  return { percent: 0, status: 'idle', label: '0%' };
}

function decideCachedServerSwitch(cacheStats, lastSyncMs, bgSyncFrequency, nowMs) {
  const stats = cacheStats || {};
  const liveCount = Math.max(0, Number(stats.liveCount) || 0);
  const vodCount = Math.max(0, Number(stats.vodCount) || 0);
  const seriesCount = Math.max(0, Number(stats.seriesCount) || 0);
  const totalCount = liveCount + vodCount + seriesCount;
  const fromFullStore = Boolean(stats.fromFullStore);

  if (totalCount <= 0) {
    return { useCacheImmediately: false, isFullCache: false, shouldBackgroundRefresh: true };
  }

  const isFullCache = fromFullStore || (vodCount + seriesCount) > 0;
  const now = typeof nowMs === 'number' && Number.isFinite(nowMs) ? nowMs : Date.now();
  const lastSync = Number(lastSyncMs) || 0;

  if (!isFullCache) {
    return { useCacheImmediately: true, isFullCache: false, shouldBackgroundRefresh: true };
  }

  // Se o servidor já concluiu sincronização completa recentemente (ex.: em 2º plano),
  // nunca dispara novo download ao selecioná-lo — respeita a frequência configurada e janela de 30 min.
  const recentlySynced = lastSync > 0 && (now - lastSync) < 30 * 60 * 1000;
  const freqNeedsSync = lastSync <= 0 ? false : shouldSyncServer(lastSync, bgSyncFrequency, now);
  const shouldBackgroundRefresh = !recentlySynced && freqNeedsSync;

  return {
    useCacheImmediately: true,
    isFullCache: true,
    shouldBackgroundRefresh
  };
}

/**
 * Acumulador streaming de métricas em O(1) RAM (sem manter arrays de 50k–100k itens no heap).
 * Usado no sync em segundo plano e no carregamento sob demanda em Smart TVs (LG webOS / Tizen)
 * para evitar OOM kill e fornecer contagens exatas de categorias (groupCounts) para o IndexedDB.
 */
function createStreamingCatalogMetricsAccumulator() {
  let totalChannels = 0;
  let totalVOD = 0;
  let totalSeries = 0;
  let epgTaggedChannels = 0;
  const groupSet = new Set();
  const groupMaps = {
    LIVE: new Map(),
    MOVIE: new Map(),
    SERIES: new Map()
  };

  const toGroupList = (map) => {
    const out = [];
    map.forEach((entry) => {
      out.push({
        name: entry.name,
        count: entry.count,
        adultCount: entry.adultCount
      });
    });
    return out;
  };

  return {
    add(item) {
      if (!item || typeof item !== 'object') return;
      const t = item.contentType;
      if (t === 'LIVE') {
        totalChannels++;
        if (item.tvgId && String(item.tvgId).trim()) epgTaggedChannels++;
      } else if (t === 'MOVIE') {
        totalVOD++;
      } else if (t === 'SERIES') {
        totalSeries++;
      } else {
        return;
      }
      const g = (item.group && String(item.group).trim()) ? String(item.group).trim() : 'Geral';
      if (item.group && groupSet.size < 4000) {
        groupSet.add(g);
      }
      const typeMap = groupMaps[t];
      if (typeMap) {
        let entry = typeMap.get(g);
        if (!entry) {
          if (typeMap.size < 4000) {
            entry = { name: g, count: 0, adultCount: 0 };
            typeMap.set(g, entry);
          }
        }
        if (entry) {
          entry.count++;
          if (item.isAdult) entry.adultCount++;
        }
      }
    },
    getTotalItems() {
      return totalChannels + totalVOD + totalSeries;
    },
    getGroupCounts() {
      return {
        LIVE: toGroupList(groupMaps.LIVE),
        MOVIE: toGroupList(groupMaps.MOVIE),
        SERIES: toGroupList(groupMaps.SERIES)
      };
    },
    snapshot(hasXmltv) {
      return {
        totalChannels,
        totalVOD,
        totalSeries,
        totalGroups: groupSet.size,
        totalEPG: epgTaggedChannels > 0 ? epgTaggedChannels : (hasXmltv ? totalChannels : 0),
        totalItems: totalChannels + totalVOD + totalSeries
      };
    }
  };
}

const COMPACT_KIND_TO_PREFIX = { live: 'l', movie: 'm', series: 's' };
const COMPACT_PREFIX_TO_KIND = { l: 'live', m: 'movie', s: 'series' };

/**
 * Converte uma URL padrão Xtream (`<base>/(live|movie|series)/<user>/<pass>/<id>.<ext>`)
 * em uma referência compacta (`l:<id>.<ext>`, `m:<id>.<ext>`, `s:<id>.xtream`) sem repetir
 * o domínio nem persistir credenciais em 55k+ registros do IndexedDB.
 * URLs M3U diretas, redirecionadas ou de outros domínios são preservadas intactas em `url`.
 */
function compactStreamUrl(rawUrl, serverBaseUrl, creds) {
  const urlStr = String(rawUrl || '').trim();
  if (!urlStr) return { ref: '', url: '' };
  const cleanBase = String(serverBaseUrl || '').trim().replace(/\/+$/, '');
  const u = creds && creds.user ? String(creds.user).trim() : '';
  const p = creds && creds.pass ? String(creds.pass).trim() : '';
  if (!cleanBase || !u || !p) return { ref: '', url: urlStr };
  if (!urlStr.startsWith(cleanBase + '/')) return { ref: '', url: urlStr };

  const rest = urlStr.slice(cleanBase.length + 1);
  const parts = rest.split('/');
  if (parts.length !== 4) return { ref: '', url: urlStr };

  const kind = parts[0].toLowerCase();
  const prefix = COMPACT_KIND_TO_PREFIX[kind];
  if (!prefix) return { ref: '', url: urlStr };

  const encU = encodeURIComponent(u);
  const encP = encodeURIComponent(p);
  const userMatches = parts[1] === encU || parts[1] === u;
  const passMatches = parts[2] === encP || parts[2] === p;
  if (!userMatches || !passMatches) return { ref: '', url: urlStr };

  const tail = parts[3];
  if (!tail || tail.includes('?') || tail.includes('#')) return { ref: '', url: urlStr };

  return { ref: prefix + ':' + tail, url: '' };
}

/**
 * Reconstrói a URL completa de reprodução a partir do registro compacto do IndexedDB.
 * Retrocompatível: registros antigos que já armazenam `url` completa retornam `url` diretamente.
 */
function expandStreamUrl(record, serverBaseUrl, creds) {
  if (!record || typeof record !== 'object') return '';
  if (record.url && typeof record.url === 'string' && record.url.trim()) {
    return record.url.trim();
  }
  const ref = typeof record.ref === 'string' ? record.ref.trim() : '';
  if (!ref || ref.length < 3 || ref.charAt(1) !== ':') return '';

  const kind = COMPACT_PREFIX_TO_KIND[ref.charAt(0)];
  if (!kind) return '';
  const tail = ref.slice(2);
  if (!tail) return '';

  const cleanBase = String(serverBaseUrl || '').trim().replace(/\/+$/, '');
  const u = creds && creds.user ? String(creds.user).trim() : '';
  const p = creds && creds.pass ? String(creds.pass).trim() : '';
  if (!cleanBase || !u || !p) return '';

  return cleanBase + '/' + kind + '/' + encodeURIComponent(u) + '/' + encodeURIComponent(p) + '/' + tail;
}

/**
 * Serializa um ring buffer compacto dos logs de diagnóstico para sobreviver a reinícios
 * forçados por limite de memória da Smart TV (LG webOS).
 */
function serializePersistedDevLogs(entries, maxEntries, maxBytes) {
  const limitEntries = Math.max(20, Math.min(300, Number(maxEntries) || 180));
  const limitBytes = Math.max(8192, Math.min(128000, Number(maxBytes) || 48000));
  if (!Array.isArray(entries) || entries.length === 0) return '[]';

  const compact = [];
  const start = Math.max(0, entries.length - limitEntries);
  for (let i = start; i < entries.length; i++) {
    const e = entries[i];
    if (!e || typeof e !== 'object') continue;
    compact.push({
      time: String(e.time || '').slice(0, 16),
      type: String(e.type || 'info').slice(0, 12),
      msg: String(e.msg || '').slice(0, 320)
    });
  }

  let json = JSON.stringify(compact);
  while (json.length > limitBytes && compact.length > 10) {
    compact.splice(0, Math.max(1, Math.floor(compact.length * 0.2)));
    json = JSON.stringify(compact);
  }
  return json;
}

function parsePersistedDevLogs(raw, maxEntries) {
  const limitEntries = Math.max(20, Math.min(300, Number(maxEntries) || 180));
  if (!raw || typeof raw !== 'string') return [];
  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    const out = [];
    const start = Math.max(0, parsed.length - limitEntries);
    for (let i = start; i < parsed.length; i++) {
      const e = parsed[i];
      if (!e || typeof e !== 'object' || !e.msg) continue;
      out.push({
        time: String(e.time || '--:--:--').slice(0, 16),
        type: String(e.type || 'info').slice(0, 12),
        msg: String(e.msg).slice(0, 320)
      });
    }
    return out;
  } catch (e) {
    return [];
  }
}

/**
 * Normaliza título de canal/filme/série para unificação e failover entre múltiplos servidores.
 * Remove acentos, prefixos de país/provedor, tags de qualidade/codec e pontuação.
 */
function normalizeUnifiedTitle(name) {
  let s = String(name || '').toLowerCase();
  try {
    s = s.normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  } catch (e) {}
  s = s
    .replace(/\[[^\]]*\]|\([^)]*\)/g, ' ')
    .replace(/\|/g, ' ')
    .replace(/\b(4k|uhd|fhd|full\s*hd|hd|sd|2k|1080p|720p|480p|h\.?265|h\.?264|hevc|60fps|50fps|hdr|hdr10|dolby|atmos|aac|br|bra|brasil|pt|pt-br|dub|dublado|leg|legendado|dual|backup|bkp|alt|alternativo|vip|plus|low|delay|h265|h264)\b/g, ' ')
    .replace(/[^a-z0-9]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  return s;
}

/**
 * Pontua a qualidade provável de um stream pelo nome para priorizar a melhor fonte na busca unificada e failover.
 */
function computeStreamQualityScore(name) {
  const s = String(name || '').toUpperCase();
  let score = 15;
  if (/\b(4K|UHD|2160P)\b/.test(s)) score = 40;
  else if (/\b(FHD|FULL\s*HD|1080P)\b/.test(s)) score = 30;
  else if (/\b(HD|720P)\b/.test(s)) score = 22;
  else if (/\b(SD|480P|360P)\b/.test(s)) score = 10;

  if (/\b(H\.?265|HEVC)\b/.test(s)) score += 3;
  if (/\b(60FPS|50FPS)\b/.test(s)) score += 2;
  if (/\b(BACKUP|BKP|ALT|ALTERNATIVO|LOW)\b/.test(s)) score -= 5;
  return score;
}

/**
 * Mescla e deduplica resultados de busca (Omnisearch) oriundos de múltiplos servidores.
 * Mantém a melhor fonte como item principal (priorizando o servidor ativo quando empatado/disponível)
 * e anexa `alternateUrls`, `alternateSources` e `sourceCount` para failover automático.
 */
function mergeUnifiedSearchResults(entries, preferredServerId, limit) {
  const list = Array.isArray(entries) ? entries : [];
  const maxItems = Math.max(1, Number(limit) || 80);
  const groups = new Map();
  const order = [];

  for (let i = 0; i < list.length; i++) {
    const raw = list[i];
    if (!raw || typeof raw !== 'object') continue;
    const type = String(raw.contentType || 'LIVE').toUpperCase();
    const tvg = raw.tvgId ? String(raw.tvgId).trim().toLowerCase() : '';
    const normTitle = normalizeUnifiedTitle(raw.name || '');
    const key = type + '::' + (type === 'LIVE' && tvg ? ('tvg:' + tvg) : ('title:' + (normTitle || String(raw.url || i))));

    let bucket = groups.get(key);
    if (!bucket) {
      bucket = [];
      groups.set(key, bucket);
      order.push(key);
    }
    bucket.push(raw);
  }

  const merged = [];
  for (let i = 0; i < order.length && merged.length < maxItems; i++) {
    const bucket = groups.get(order[i]);
    if (!bucket || !bucket.length) continue;

    // Deduplica URLs idênticas preservando metadados do servidor
    const uniqueCandidates = [];
    const seenUrls = new Set();
    for (let j = 0; j < bucket.length; j++) {
      const c = bucket[j];
      const u = c.url ? String(c.url).trim() : '';
      if (u && seenUrls.has(u)) continue;
      if (u) seenUrls.add(u);
      uniqueCandidates.push({
        item: c,
        url: u,
        serverId: c.serverId || '',
        serverName: c.serverName || '',
        isPreferred: Boolean(preferredServerId && c.serverId === preferredServerId),
        quality: computeStreamQualityScore(c.name)
      });
    }

    uniqueCandidates.sort((a, b) => {
      if (a.isPreferred !== b.isPreferred) return a.isPreferred ? -1 : 1;
      if (b.quality !== a.quality) return b.quality - a.quality;
      return 0;
    });

    const primary = Object.assign({}, uniqueCandidates[0].item);
    const distinctServers = new Set();
    const altSources = [];
    const altUrls = [];

    for (let j = 0; j < uniqueCandidates.length; j++) {
      const c = uniqueCandidates[j];
      if (c.serverId) distinctServers.add(c.serverId);
      if (j > 0 && c.url) {
        altUrls.push(c.url);
        altSources.push({
          url: c.url,
          serverId: c.serverId,
          serverName: c.serverName,
          name: c.item.name || primary.name,
          quality: c.quality
        });
      }
    }

    primary.serverId = uniqueCandidates[0].serverId || primary.serverId || '';
    primary.serverName = uniqueCandidates[0].serverName || primary.serverName || '';
    primary.sourceCount = Math.max(1, distinctServers.size || uniqueCandidates.length);
    if (altUrls.length > 0) {
      const existingAlts = Array.isArray(primary.alternateUrls) ? primary.alternateUrls : [];
      primary.alternateUrls = Array.from(new Set(existingAlts.concat(altUrls)));
      primary.alternateSources = altSources;
    }
    merged.push(primary);
  }

  return merged;
}

/**
 * Monta corretamente a URL de range do Netflix Open Connect (Fast.com).
 * A API OCA espera `/speedtest/range/<inicio>-<fim>` no PATH (e NÃO `?range=` na query string).
 * Passar `&range=0-0` na query fazia o OCA ignorar o range e baixar 26 MB em cada probe de latência.
 */
function buildFastComRangeUrl(baseUrl, startByte, endByte, cacheBuster) {
  const raw = String(baseUrl || '').trim();
  if (!raw) return '';
  const start = Math.max(0, Math.floor(Number(startByte) || 0));
  const end = Math.max(start, Math.floor(Number(endByte) || 0));
  const rangeToken = `${start}-${end}`;
  const buster = cacheBuster ? String(cacheBuster) : `${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;

  let updated = raw;
  if (/\/speedtest\/range\/[^/?#]+/i.test(updated)) {
    updated = updated.replace(/\/speedtest\/range\/[^/?#]+/i, `/speedtest/range/${rangeToken}`);
  } else if (/\/speedtest(?=[/?#]|$)/i.test(updated)) {
    updated = updated.replace(/\/speedtest(?=\/|\?|#|$)/i, `/speedtest/range/${rangeToken}`);
  } else {
    // Fallback seguro para endpoints não-Fast.com
    const sep0 = updated.indexOf('?') !== -1 ? '&' : '?';
    return `${updated}${sep0}range=${rangeToken}&_=${buster}`;
  }

  const sep = updated.indexOf('?') !== -1 ? '&' : '?';
  return `${updated}${sep}_=${buster}`;
}

/**
 * Calcula a vazão sustentada (estilo Fast.com) a partir de amostras periódicas em Mbps.
 * Descarta os 20% inferiores (ramp-up TCP / quedas pontuais) e 10% superiores (picos de cache/burst)
 * para reportar a banda sustentada real sem distorções.
 */
function computeTrimmedThroughputMbps(samples) {
  if (!Array.isArray(samples) || samples.length === 0) return 0;
  const clean = [];
  for (let i = 0; i < samples.length; i++) {
    const v = Number(samples[i]);
    if (Number.isFinite(v) && v > 0) clean.push(v);
  }
  if (!clean.length) return 0;
  if (clean.length <= 2) {
    return clean.reduce((a, b) => a + b, 0) / clean.length;
  }
  clean.sort((a, b) => a - b);
  const lowCut = Math.floor(clean.length * 0.25);
  const highCut = Math.max(lowCut + 1, Math.floor(clean.length * 0.9));
  let sum = 0;
  let count = 0;
  for (let i = lowCut; i < highCut; i++) {
    sum += clean[i];
    count++;
  }
  return count > 0 ? sum / count : clean[clean.length - 1];
}

module.exports = {
  BG_SYNC_INTERVALS_MS,
  BG_SYNC_LABELS,
  PLAYLIST_TIMEOUTS,
  normalizeBgSyncFrequency,
  getBgSyncIntervalMs,
  shouldSyncServer,
  computeCatalogMetrics,
  isPollutedLegacyFastCacheMetrics,
  computeGlobalServersMetrics,
  computeServerLoadPercentage,
  decideCachedServerSwitch,
  createStreamingCatalogMetricsAccumulator,
  compactStreamUrl,
  expandStreamUrl,
  serializePersistedDevLogs,
  parsePersistedDevLogs,
  normalizeUnifiedTitle,
  computeStreamQualityScore,
  mergeUnifiedSearchResults,
  buildFastComRangeUrl,
  computeTrimmedThroughputMbps,
  parseSafeJsonResponse,
  normalizeCatalogMode,
  isDirectPlaylistUrl,
  shouldPreferXtreamJson,
  buildXtreamXmltvUrl,
  extractSafeHost,
  formatDurationPtBr,
  formatBytesMb,
  formatSpeedMbPerSec,
  detectHtmlPayload,
  createStallAwareAbortController,
  formatFriendlyLoadError
};

