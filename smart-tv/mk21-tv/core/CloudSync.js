/**
 * MK21 Play — Módulo puro de Sincronização em Nuvem (Smart TV ↔ Android ↔ Web)
 *
 * Sincroniza favoritos e "Continuar Assistindo" (progresso VOD) com `/api/sync`
 * usando merge determinístico Last-Write-Wins (LWW) via código `MK21-XXXX-XXXX`.
 */

const SYNC_ENDPOINT = 'https://bgstreaming.vercel.app/api/sync';
const SYNC_CODE_STORAGE_KEY = 'mk21_cloud_sync_code_v1';
const SYNC_LAST_AT_KEY = 'mk21_cloud_sync_last_v1';
const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

function defaultStorage() {
  try {
    if (typeof localStorage !== 'undefined') return localStorage;
  } catch (e) {}
  return null;
}

function normalizeSyncCode(raw) {
  const cleaned = String(raw || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
  const body = cleaned.startsWith('MK21') ? cleaned.slice(4) : cleaned;
  if (body.length < 8 || body.length > 16) return '';
  return `MK21-${body.slice(0, 4)}-${body.slice(4, 8)}`;
}

function isValidSyncCode(raw) {
  return /^MK21-[A-Z0-9]{4}-[A-Z0-9]{4}$/.test(normalizeSyncCode(raw));
}

function generateSyncCode() {
  let out = '';
  const buf = new Uint8Array(8);
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    crypto.getRandomValues(buf);
  } else {
    for (let i = 0; i < 8; i++) buf[i] = Math.floor(Math.random() * 256);
  }
  for (let i = 0; i < 8; i++) {
    out += CODE_ALPHABET[buf[i] % CODE_ALPHABET.length];
  }
  return `MK21-${out.slice(0, 4)}-${out.slice(4, 8)}`;
}

function getStoredSyncCode(store) {
  const s = store || defaultStorage();
  if (!s) return '';
  try {
    return normalizeSyncCode(s.getItem(SYNC_CODE_STORAGE_KEY) || '');
  } catch (e) {
    return '';
  }
}

function setStoredSyncCode(code, store) {
  const s = store || defaultStorage();
  const normalized = normalizeSyncCode(code);
  if (s) {
    try {
      if (normalized) s.setItem(SYNC_CODE_STORAGE_KEY, normalized);
      else s.removeItem(SYNC_CODE_STORAGE_KEY);
    } catch (e) {}
  }
  return normalized;
}

function getLastSyncAt(store) {
  const s = store || defaultStorage();
  if (!s) return 0;
  try {
    return parseInt(s.getItem(SYNC_LAST_AT_KEY) || '0', 10) || 0;
  } catch (e) {
    return 0;
  }
}

function setLastSyncAt(ts, store) {
  const s = store || defaultStorage();
  if (s) {
    try { s.setItem(SYNC_LAST_AT_KEY, String(ts || Date.now())); } catch (e) {}
  }
}

function buildSmartTvPayload(favoriteUrls, continueWatchingList, catalogLookup, nowMs) {
  const now = Number.isFinite(nowMs) ? nowMs : Date.now();
  const favs = [];
  const favArr = favoriteUrls instanceof Set ? Array.from(favoriteUrls) : (Array.isArray(favoriteUrls) ? favoriteUrls : []);
  for (let i = 0; i < favArr.length && favs.length < 500; i++) {
    const url = String(favArr[i] || '').trim();
    if (!/^https?:\/\//i.test(url)) continue;
    const meta = typeof catalogLookup === 'function' ? catalogLookup(url) : null;
    favs.push({
      key: url,
      name: (meta && meta.name) || 'Favorito',
      url,
      logo: (meta && meta.logo) || '',
      group: (meta && meta.group) || '',
      contentType: (meta && meta.contentType) || 'LIVE',
      deleted: false,
      updatedAt: now
    });
  }

  const cont = [];
  const list = Array.isArray(continueWatchingList) ? continueWatchingList : [];
  for (let i = 0; i < list.length && cont.length < 200; i++) {
    const it = list[i];
    if (!it || !it.url || !/^https?:\/\//i.test(it.url)) continue;
    cont.push({
      key: it.url,
      name: it.name || 'VOD',
      url: it.url,
      logo: it.logo || '',
      group: it.group || '',
      contentType: it.contentType || 'MOVIE',
      positionSec: Math.max(0, Math.round(Number(it.currentTime || it.positionSec || 0) || 0)),
      durationSec: Math.max(0, Math.round(Number(it.duration || it.durationSec || 0) || 0)),
      deleted: false,
      updatedAt: Number(it.timestamp || it.updatedAt || now) || now
    });
  }

  return { favorites: favs, continueWatching: cont, updatedAt: now };
}

function applyMergedToSmartTv(mergedPayload, currentFavSet, currentContinueList) {
  const favSet = new Set(currentFavSet instanceof Set ? currentFavSet : []);
  const remoteFavs = (mergedPayload && Array.isArray(mergedPayload.favorites)) ? mergedPayload.favorites : [];
  for (let i = 0; i < remoteFavs.length; i++) {
    const f = remoteFavs[i];
    if (!f || !f.url) continue;
    if (f.deleted) favSet.delete(f.url);
    else favSet.add(f.url);
  }

  const byUrl = new Map();
  const curList = Array.isArray(currentContinueList) ? currentContinueList : [];
  for (let i = 0; i < curList.length; i++) {
    const it = curList[i];
    if (it && it.url) byUrl.set(it.url, it);
  }

  const remoteCont = (mergedPayload && Array.isArray(mergedPayload.continueWatching)) ? mergedPayload.continueWatching : [];
  for (let i = 0; i < remoteCont.length; i++) {
    const r = remoteCont[i];
    if (!r || !r.url) continue;
    if (r.deleted) {
      byUrl.delete(r.url);
      continue;
    }
    const existing = byUrl.get(r.url);
    const existingTs = (existing && (existing.timestamp || existing.updatedAt)) || 0;
    const remoteTs = r.updatedAt || 0;
    if (!existing || remoteTs >= existingTs) {
      const dur = r.durationSec || (existing && existing.duration) || 0;
      const pos = r.positionSec || 0;
      const pct = dur > 0 ? Math.min(100, Math.max(1, Math.round((pos / dur) * 100))) : 0;
      byUrl.set(r.url, {
        ...(existing || {}),
        name: r.name || (existing && existing.name) || 'VOD',
        url: r.url,
        logo: r.logo || (existing && existing.logo) || '',
        group: r.group || (existing && existing.group) || '',
        contentType: r.contentType || (existing && existing.contentType) || 'MOVIE',
        currentTime: pos,
        duration: dur,
        progressPct: pct,
        timestamp: remoteTs || Date.now()
      });
    }
  }

  const mergedContinue = Array.from(byUrl.values())
    .sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0))
    .slice(0, 60);

  return {
    favoriteUrls: favSet,
    continueWatchingList: mergedContinue
  };
}

async function syncWithServer(code, localPayload, options) {
  const opts = options || {};
  const fetchImpl = opts.fetch || globalThis.fetch;
  const endpoint = opts.endpoint || SYNC_ENDPOINT;
  const normalized = normalizeSyncCode(code);
  if (!isValidSyncCode(normalized)) {
    return { ok: false, error: 'Código de sincronização inválido.' };
  }
  if (typeof fetchImpl !== 'function') {
    return { ok: false, error: 'Fetch indisponível neste dispositivo.' };
  }

  try {
    const resp = await fetchImpl(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: normalized, payload: localPayload || {} })
    });
    const data = await resp.json().catch(() => ({}));
    if (!resp.ok || !data.ok) {
      return { ok: false, error: data.error || `Servidor respondeu HTTP ${resp.status}` };
    }
    return { ok: true, code: normalized, payload: data.payload, updatedAt: data.updatedAt };
  } catch (e) {
    return { ok: false, error: (e && e.message) ? e.message : 'Falha de rede na sincronização.' };
  }
}

module.exports = {
  SYNC_ENDPOINT,
  SYNC_CODE_STORAGE_KEY,
  normalizeSyncCode,
  isValidSyncCode,
  generateSyncCode,
  getStoredSyncCode,
  setStoredSyncCode,
  getLastSyncAt,
  setLastSyncAt,
  buildSmartTvPayload,
  applyMergedToSmartTv,
  syncWithServer
};
