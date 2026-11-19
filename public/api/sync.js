/**
 * MK21 Play — Sincronização em Nuvem Multiplataforma (Android ↔ Smart TV ↔ Web)
 *
 * Persiste favoritos e "Continuar Assistindo" (progresso VOD) no Neon Postgres
 * (mesma DATABASE_URL do licenciamento) usando um código de pareamento
 * `MK21-XXXX-XXXX`, com merge determinístico Last-Write-Wins (LWW) para nunca
 * sobrescrever alterações feitas em outro aparelho.
 *
 * GET  /api/sync?code=MK21-XXXX-XXXX   -> obtém o estado sincronizado
 * POST /api/sync  { code, payload }    -> faz merge LWW, salva e devolve estado consolidado
 */
const crypto = require('node:crypto');
const { neon } = require('@neondatabase/serverless');

const MAX_BODY_BYTES = 131072; // 128 KB
const MAX_FAVORITES = 500;
const MAX_CONTINUE = 200;
const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

function setCors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  res.setHeader('Cache-Control', 'no-store');
}

function sendJson(res, status, data) {
  setCors(res);
  res.statusCode = status;
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.end(JSON.stringify(data));
}

function normalizeSyncCode(raw) {
  const cleaned = String(raw || '')
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '');
  const body = cleaned.startsWith('MK21') ? cleaned.slice(4) : cleaned;
  if (body.length < 8 || body.length > 16) return '';
  return `MK21-${body.slice(0, 4)}-${body.slice(4, 8)}`;
}

function isValidSyncCode(raw) {
  return /^MK21-[A-Z0-9]{4}-[A-Z0-9]{4}$/.test(normalizeSyncCode(raw));
}

function generateSyncCode() {
  const bytes = crypto.randomBytes(8);
  let out = '';
  for (let i = 0; i < 8; i++) {
    out += CODE_ALPHABET[bytes[i] % CODE_ALPHABET.length];
  }
  return `MK21-${out.slice(0, 4)}-${out.slice(4, 8)}`;
}

function safeString(val, maxLen) {
  if (typeof val !== 'string') return '';
  return val.trim().slice(0, maxLen);
}

function safeUrl(val) {
  const s = safeString(val, 2048);
  if (!s) return '';
  return /^https?:\/\//i.test(s) ? s : '';
}

function safeEpoch(val, fallback) {
  const n = Number(val);
  return Number.isFinite(n) && n > 0 ? Math.round(n) : fallback;
}

function normalizeItemKey(item) {
  const explicit = safeString(item && item.key, 512);
  if (explicit) return explicit;
  const url = safeUrl(item && item.url);
  if (url) return url;
  const name = safeString(item && item.name, 200).toLowerCase();
  return name ? `name:${name}` : '';
}

function sanitizeSyncPayload(raw, nowMs) {
  const now = Number.isFinite(nowMs) ? nowMs : Date.now();
  const obj = raw && typeof raw === 'object' ? raw : {};

  const favList = [];
  if (Array.isArray(obj.favorites)) {
    for (let i = 0; i < obj.favorites.length && favList.length < MAX_FAVORITES; i++) {
      const it = obj.favorites[i];
      if (!it || typeof it !== 'object') continue;
      const key = normalizeItemKey(it);
      if (!key) continue;
      favList.push({
        key,
        name: safeString(it.name, 200),
        url: safeUrl(it.url),
        logo: safeUrl(it.logo),
        group: safeString(it.group, 120),
        contentType: safeString(it.contentType || 'LIVE', 24).toUpperCase(),
        deleted: Boolean(it.deleted),
        updatedAt: safeEpoch(it.updatedAt, now),
      });
    }
  }

  const contList = [];
  if (Array.isArray(obj.continueWatching)) {
    for (let i = 0; i < obj.continueWatching.length && contList.length < MAX_CONTINUE; i++) {
      const it = obj.continueWatching[i];
      if (!it || typeof it !== 'object') continue;
      const key = normalizeItemKey(it);
      if (!key) continue;
      const pos = Math.max(0, Math.round(Number(it.positionSec || it.currentTime || 0) || 0));
      const dur = Math.max(0, Math.round(Number(it.durationSec || it.duration || 0) || 0));
      contList.push({
        key,
        name: safeString(it.name, 200),
        url: safeUrl(it.url),
        logo: safeUrl(it.logo),
        group: safeString(it.group, 120),
        contentType: safeString(it.contentType || 'MOVIE', 24).toUpperCase(),
        positionSec: pos,
        durationSec: dur,
        deleted: Boolean(it.deleted),
        updatedAt: safeEpoch(it.updatedAt, now),
      });
    }
  }

  return {
    favorites: favList,
    continueWatching: contList,
    updatedAt: safeEpoch(obj.updatedAt, now),
  };
}

function mergeArrayLww(baseArr, incomingArr, limit) {
  const byKey = new Map();
  const apply = (list) => {
    if (!Array.isArray(list)) return;
    for (let i = 0; i < list.length; i++) {
      const item = list[i];
      if (!item || !item.key) continue;
      const prev = byKey.get(item.key);
      if (!prev || (item.updatedAt || 0) >= (prev.updatedAt || 0)) {
        byKey.set(item.key, item);
      }
    }
  };
  apply(baseArr);
  apply(incomingArr);

  return Array.from(byKey.values())
    .sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0))
    .slice(0, limit);
}

function mergeSyncPayloads(remotePayload, incomingPayload, nowMs) {
  const now = Number.isFinite(nowMs) ? nowMs : Date.now();
  const a = sanitizeSyncPayload(remotePayload, now);
  const b = sanitizeSyncPayload(incomingPayload, now);

  return {
    favorites: mergeArrayLww(a.favorites, b.favorites, MAX_FAVORITES),
    continueWatching: mergeArrayLww(a.continueWatching, b.continueWatching, MAX_CONTINUE),
    updatedAt: Math.max(a.updatedAt || 0, b.updatedAt || 0, now),
  };
}

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    if (req.body && typeof req.body === 'object') {
      const size = Buffer.byteLength(JSON.stringify(req.body), 'utf8');
      if (size > MAX_BODY_BYTES) return reject(new Error('BODY_TOO_LARGE'));
      return resolve(req.body);
    }
    let raw = '';
    let bytes = 0;
    req.on('data', (chunk) => {
      bytes += chunk.length;
      if (bytes > MAX_BODY_BYTES) {
        req.destroy();
        return reject(new Error('BODY_TOO_LARGE'));
      }
      raw += chunk.toString('utf8');
    });
    req.on('end', () => {
      try {
        resolve(raw ? JSON.parse(raw) : {});
      } catch (e) {
        reject(new Error('INVALID_JSON'));
      }
    });
    req.on('error', reject);
  });
}

function createSyncHandler(deps = {}) {
  const getSql = deps.getSql || (() => {
    const url = process.env.DATABASE_URL;
    if (!url) return null;
    return neon(url);
  });
  const nowFn = deps.now || (() => Date.now());
  let schemaReady = Boolean(deps.skipEnsureTable);

  async function ensureTable(sql) {
    if (schemaReady) return;
    await sql`
      CREATE TABLE IF NOT EXISTS user_sync_state (
        sync_code TEXT PRIMARY KEY,
        payload JSONB NOT NULL,
        updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
      )
    `;
    schemaReady = true;
  }

  return async function handler(req, res) {
    if (req.method === 'OPTIONS') {
      setCors(res);
      res.statusCode = 204;
      return res.end();
    }

    const sql = getSql();
    if (!sql) {
      return sendJson(res, 503, {
        ok: false,
        error: 'Serviço de sincronização indisponível (DATABASE_URL não configurada no servidor).',
      });
    }

    try {
      await ensureTable(sql);

      if (req.method === 'GET') {
        const parsedUrl = new URL(req.url || '/', 'https://mk21.local');
        const code = normalizeSyncCode(parsedUrl.searchParams.get('code') || '');
        if (!isValidSyncCode(code)) {
          return sendJson(res, 400, { ok: false, error: 'Código de sincronização inválido (formato MK21-XXXX-XXXX).' });
        }

        const rows = await sql`
          SELECT payload, updated_at
          FROM user_sync_state
          WHERE sync_code = ${code}
          LIMIT 1
        `;
        const row = rows && rows[0];
        const nowMs = nowFn();
        const payload = row && row.payload
          ? sanitizeSyncPayload(row.payload, nowMs)
          : { favorites: [], continueWatching: [], updatedAt: 0 };

        return sendJson(res, 200, {
          ok: true,
          code,
          payload,
          updatedAt: payload.updatedAt,
        });
      }

      if (req.method === 'POST') {
        let body;
        try {
          body = await readJsonBody(req);
        } catch (err) {
          if (err && err.message === 'BODY_TOO_LARGE') {
            return sendJson(res, 413, { ok: false, error: 'Payload de sincronização excede 128 KB.' });
          }
          return sendJson(res, 400, { ok: false, error: 'JSON inválido.' });
        }

        const code = normalizeSyncCode(body && body.code);
        if (!isValidSyncCode(code)) {
          return sendJson(res, 400, { ok: false, error: 'Código de sincronização inválido (formato MK21-XXXX-XXXX).' });
        }

        const nowMs = nowFn();
        const existingRows = await sql`
          SELECT payload
          FROM user_sync_state
          WHERE sync_code = ${code}
          LIMIT 1
        `;
        const remotePayload = existingRows && existingRows[0] ? existingRows[0].payload : null;
        const merged = mergeSyncPayloads(remotePayload, body.payload || {}, nowMs);

        await sql`
          INSERT INTO user_sync_state (sync_code, payload, updated_at)
          VALUES (${code}, ${JSON.stringify(merged)}::jsonb, now())
          ON CONFLICT (sync_code) DO UPDATE
            SET payload = EXCLUDED.payload,
                updated_at = now()
        `;

        return sendJson(res, 200, {
          ok: true,
          code,
          payload: merged,
          updatedAt: merged.updatedAt,
        });
      }

      res.setHeader('Allow', 'GET, POST, OPTIONS');
      return sendJson(res, 405, { ok: false, error: 'Método não permitido.' });
    } catch (err) {
      console.error('Sync API error:', err && err.message ? err.message : err);
      return sendJson(res, 500, { ok: false, error: 'Erro interno na sincronização.' });
    }
  };
}

const defaultHandler = createSyncHandler();
defaultHandler.createSyncHandler = createSyncHandler;
defaultHandler.normalizeSyncCode = normalizeSyncCode;
defaultHandler.isValidSyncCode = isValidSyncCode;
defaultHandler.generateSyncCode = generateSyncCode;
defaultHandler.sanitizeSyncPayload = sanitizeSyncPayload;
defaultHandler.mergeSyncPayloads = mergeSyncPayloads;

module.exports = defaultHandler;
