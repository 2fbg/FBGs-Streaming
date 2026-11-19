/**
 * MK21 Play — Preferências avançadas da Smart TV (paridade Saimo TV).
 *
 * Módulo PURO (testável em Node com storage injetável):
 *  - Volume separado para TV Ao Vivo vs VOD (Filmes/Séries);
 *  - Áudio Inteligente (normalização / ganho dinâmico);
 *  - LCN editável salvo por servidor;
 *  - "Minhas Listas" (até 10 listas M3U por link ou arquivo) com sanitização de URL
 *    e extração automática de `url-tvg` / `x-tvg-url`.
 */

const STORAGE_KEYS = {
  VOL_LIVE: 'mk21_vol_live_v1',
  VOL_VOD: 'mk21_vol_vod_v1',
  SMART_AUDIO: 'mk21_smart_audio_v1',
  CUSTOM_LCN_PREFIX: 'mk21_custom_lcn_v1_',
  CUSTOM_M3U_LISTS: 'mk21_custom_m3u_lists_v1'
};

const MAX_CUSTOM_LISTS = 10;

const SMART_AUDIO_MODES = {
  off: { label: 'Desligado', gain: 1.0, compress: false },
  normal: { label: 'Normalizar (Equilibrado)', gain: 1.25, compress: true, threshold: -22, ratio: 4 },
  boost: { label: 'Boost (+60% Canais Fracos)', gain: 1.6, compress: true, threshold: -26, ratio: 6 }
};

function defaultStorage() {
  try {
    if (typeof localStorage !== 'undefined') return localStorage;
  } catch (e) {}
  return null;
}

function isVodType(contentType) {
  const t = String(contentType || '').toUpperCase();
  return t === 'MOVIE' || t === 'SERIES' || t === 'VOD';
}

function clampVolume(value, fallback) {
  const num = Number(value);
  if (!Number.isFinite(num)) return Number.isFinite(fallback) ? fallback : 1.0;
  return Math.min(1, Math.max(0, Math.round(num * 100) / 100));
}

function getVolumeForType(contentType, store) {
  const s = store || defaultStorage();
  const key = isVodType(contentType) ? STORAGE_KEYS.VOL_VOD : STORAGE_KEYS.VOL_LIVE;
  if (!s) return 1.0;
  try {
    const raw = s.getItem(key);
    if (raw === null || raw === undefined || raw === '') return 1.0;
    return clampVolume(parseFloat(raw), 1.0);
  } catch (e) {
    return 1.0;
  }
}

function setVolumeForType(contentType, volume, store) {
  const s = store || defaultStorage();
  const clamped = clampVolume(volume, 1.0);
  const key = isVodType(contentType) ? STORAGE_KEYS.VOL_VOD : STORAGE_KEYS.VOL_LIVE;
  if (s) {
    try { s.setItem(key, String(clamped)); } catch (e) {}
  }
  return clamped;
}

function getSmartAudioMode(store) {
  const s = store || defaultStorage();
  if (!s) return 'off';
  try {
    const raw = String(s.getItem(STORAGE_KEYS.SMART_AUDIO) || 'off').trim().toLowerCase();
    return Object.prototype.hasOwnProperty.call(SMART_AUDIO_MODES, raw) ? raw : 'off';
  } catch (e) {
    return 'off';
  }
}

function setSmartAudioMode(mode, store) {
  const clean = Object.prototype.hasOwnProperty.call(SMART_AUDIO_MODES, mode) ? mode : 'off';
  const s = store || defaultStorage();
  if (s) {
    try { s.setItem(STORAGE_KEYS.SMART_AUDIO, clean); } catch (e) {}
  }
  return clean;
}

function cycleSmartAudioMode(currentMode, store) {
  const order = ['off', 'normal', 'boost'];
  const idx = order.indexOf(currentMode);
  const next = order[(idx + 1) % order.length];
  return setSmartAudioMode(next, store);
}

function getCustomLcnMap(serverId, store) {
  const s = store || defaultStorage();
  const sid = String(serverId || 'default').trim();
  if (!s) return {};
  try {
    const parsed = JSON.parse(s.getItem(STORAGE_KEYS.CUSTOM_LCN_PREFIX + sid) || '{}');
    return (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) ? parsed : {};
  } catch (e) {
    return {};
  }
}

function setChannelCustomLcn(serverId, channelKey, channelNumber, store) {
  const s = store || defaultStorage();
  const sid = String(serverId || 'default').trim();
  const key = String(channelKey || '').trim();
  if (!key) return getCustomLcnMap(sid, s);

  const map = getCustomLcnMap(sid, s);
  const parsed = parseInt(String(channelNumber || '').replace(/\D+/g, ''), 10);
  if (!Number.isFinite(parsed) || parsed <= 0 || parsed > 99999) {
    delete map[key];
  } else {
    // Evita colisão: remove outro canal que tinha exatamente o mesmo número
    Object.keys(map).forEach((existingKey) => {
      if (existingKey !== key && Number(map[existingKey]) === parsed) {
        delete map[existingKey];
      }
    });
    map[key] = parsed;
  }

  if (s) {
    try { s.setItem(STORAGE_KEYS.CUSTOM_LCN_PREFIX + sid, JSON.stringify(map)); } catch (e) {}
  }
  return map;
}

function validateM3uUrl(url) {
  const clean = String(url || '').trim();
  if (!clean || clean.length > 2048) return false;
  if (/^(javascript|data|file|vbscript):/i.test(clean)) return false;
  return /^https?:\/\/[^\s]+$/i.test(clean);
}

function parseM3uHeaderXmltvUrl(m3uContent) {
  if (typeof m3uContent !== 'string' || !m3uContent) return '';
  const firstLineEnd = m3uContent.indexOf('\n');
  const header = firstLineEnd > 0 ? m3uContent.slice(0, Math.min(firstLineEnd, 4096)) : m3uContent.slice(0, 4096);
  const match = header.match(/(?:url-tvg|x-tvg-url)=["']([^"']+)["']/i);
  if (!match || !match[1]) return '';
  const firstUrl = match[1].split(',')[0].trim();
  return validateM3uUrl(firstUrl) ? firstUrl : '';
}

function maskSourceLabel(index, server) {
  const num = (Number.isFinite(index) && index >= 0) ? index + 1 : 1;
  const rawName = (server && server.name) ? String(server.name).replace(/^⭐\s*/, '').trim() : '';
  const url = String((server && server.url) || '').trim();
  const isM3u = (server && server.sourceKind === 'm3u_file')
    || /\.(m3u8?|txt)(\?|$)/i.test(url)
    || /get\.php/i.test(url);
  const kindLabel = (server && server.sourceKind === 'm3u_file')
    ? 'Arquivo M3U Local'
    : (isM3u ? 'Lista M3U' : 'Servidor Xtream');
  return rawName ? `Fonte ${num} • ${kindLabel}` : `Fonte ${num}`;
}

function loadCustomM3uLists(store) {
  const s = store || defaultStorage();
  if (!s) return [];
  try {
    const parsed = JSON.parse(s.getItem(STORAGE_KEYS.CUSTOM_M3U_LISTS) || '[]');
    return Array.isArray(parsed) ? parsed.slice(0, MAX_CUSTOM_LISTS) : [];
  } catch (e) {
    return [];
  }
}

function saveCustomM3uLists(lists, store) {
  const s = store || defaultStorage();
  const safe = Array.isArray(lists) ? lists.slice(0, MAX_CUSTOM_LISTS) : [];
  if (s) {
    try { s.setItem(STORAGE_KEYS.CUSTOM_M3U_LISTS, JSON.stringify(safe)); } catch (e) {}
  }
  return safe;
}

function addCustomM3uList(entry, store) {
  const lists = loadCustomM3uLists(store);
  const name = String((entry && entry.name) || '').trim().slice(0, 60);
  const kind = (entry && entry.kind === 'file') ? 'file' : 'url';
  const url = kind === 'url' ? String((entry && entry.url) || '').trim() : '';

  if (!name) return { ok: false, error: 'Informe um nome para a lista.', lists };
  if (kind === 'url' && !validateM3uUrl(url)) {
    return { ok: false, error: 'URL M3U inválida. Use http:// ou https://.', lists };
  }
  if (lists.length >= MAX_CUSTOM_LISTS) {
    return { ok: false, error: `Limite máximo de ${MAX_CUSTOM_LISTS} listas M3U atingido.`, lists };
  }

  const item = {
    id: (entry && entry.id) || ('m3u_' + Date.now() + '_' + lists.length),
    name,
    kind,
    url,
    epgUrl: validateM3uUrl(entry && entry.epgUrl) ? String(entry.epgUrl).trim() : '',
    addedAt: Date.now()
  };
  lists.push(item);
  saveCustomM3uLists(lists, store);
  return { ok: true, item, lists };
}

function removeCustomM3uList(id, store) {
  const lists = loadCustomM3uLists(store).filter((it) => it && it.id !== id);
  return saveCustomM3uLists(lists, store);
}

module.exports = {
  STORAGE_KEYS,
  MAX_CUSTOM_LISTS,
  SMART_AUDIO_MODES,
  isVodType,
  clampVolume,
  getVolumeForType,
  setVolumeForType,
  getSmartAudioMode,
  setSmartAudioMode,
  cycleSmartAudioMode,
  getCustomLcnMap,
  setChannelCustomLcn,
  validateM3uUrl,
  parseM3uHeaderXmltvUrl,
  maskSourceLabel,
  loadCustomM3uLists,
  saveCustomM3uLists,
  addCustomM3uList,
  removeCustomM3uList
};
