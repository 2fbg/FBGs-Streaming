/**
 * MK21 Play — Upload Automático de Logs de Diagnóstico para Google Drive (HOMOLOGAÇÃO)
 *
 * Temporário para o ciclo de homologação em Smart TVs (LG webOS / Samsung Tizen),
 * onde `<a download>` não abre o seletor do Google Drive como no Android APK.
 *
 * Para desativar rapidamente após a homologação: defina `HOMOLOG_LOGS_ENABLED=false`
 * na Vercel ou remova este arquivo + `MK21_HOMOLOG_DRIVE_LOGS_ENABLED` no app TV.
 *
 * POST /api/homolog-logs
 *   Body JSON: { content, fileName?, platform?, appVersion?, device?, reason? }
 *
 * Credenciais suportadas na Vercel (nunca no cliente):
 *   Opção A (Google Drive API v3 — Service Account):
 *     - GOOGLE_DRIVE_SERVICE_ACCOUNT_JSON (JSON completo da conta de serviço)
 *       OU GOOGLE_DRIVE_CLIENT_EMAIL + GOOGLE_DRIVE_PRIVATE_KEY
 *     - Compartilhe a pasta de destino com o client_email como Editor.
 *   Opção B (Google Apps Script Web App):
 *     - GOOGLE_DRIVE_WEBHOOK_URL (URL https://script.google.com/macros/s/.../exec)
 *   Destino padrão:
 *     - GOOGLE_DRIVE_LOGS_FOLDER_ID (padrão: 1LqB3dl5ALzgVqUDlYaHzL9XWvv5_ZuGB)
 */
const crypto = require('node:crypto');

const DEFAULT_DRIVE_FOLDER_ID = '1LqB3dl5ALzgVqUDlYaHzL9XWvv5_ZuGB';
const MAX_BODY_BYTES = 512 * 1024; // 512 KB
const DRIVE_SCOPE = 'https://www.googleapis.com/auth/drive.file';
const GOOGLE_TOKEN_URL = 'https://oauth2.googleapis.com/token';
const GOOGLE_DRIVE_UPLOAD_URL =
  'https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&supportsAllDrives=true&fields=id,name,webViewLink';

function setCors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, X-Homolog-Token');
  res.setHeader('Cache-Control', 'no-store');
}

function sendJson(res, status, data) {
  setCors(res);
  res.statusCode = status;
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.end(JSON.stringify(data));
}

function base64UrlEncode(input) {
  const buf = Buffer.isBuffer(input) ? input : Buffer.from(String(input), 'utf8');
  return buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

function redactServerSideLogs(rawText) {
  return String(rawText || '')
    .replace(/(https?:\/\/)[^/@\s:]+:[^/@\s]+@/gi, '$1[REDACTED]@')
    .replace(/([?&](?:username|user|password|pass|token|auth|api[_-]?key|secret)=)[^&#\s]*/gi, '$1[REDACTED]')
    .replace(/\/(live|movie|series)\/[^/\s?]+\/[^/\s?]+/gi, '/$1/[REDACTED]/[REDACTED]')
    .replace(/(Bearer\s+)[A-Za-z0-9._~+/-]+=*/gi, '$1[REDACTED]');
}

function sanitizeFileName(rawName, nowDate, platform) {
  const dt = nowDate instanceof Date && !Number.isNaN(nowDate.getTime()) ? nowDate : new Date();
  const stamp = dt.toISOString().replace(/[:.]/g, '-').slice(0, 19);
  const platClean = String(platform || 'smart-tv')
    .toLowerCase()
    .replace(/[^a-z0-9_-]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 24) || 'smart-tv';

  const fallback = `mk21-${platClean}-log-${stamp}.txt`;
  if (!rawName || typeof rawName !== 'string') return fallback;

  const base = rawName
    .trim()
    .replace(/^.*[\\/]/, '')
    .replace(/[^a-zA-Z0-9._-]+/g, '-')
    .replace(/\.{2,}/g, '.')
    .replace(/^-+|-+$/g, '')
    .slice(0, 96);

  if (!base || base.length < 4) return fallback;
  return base.toLowerCase().endsWith('.txt') ? base : `${base}.txt`;
}

function resolveFolderId(env) {
  const raw = String((env && env.GOOGLE_DRIVE_LOGS_FOLDER_ID) || DEFAULT_DRIVE_FOLDER_ID).trim();
  if (!/^[A-Za-z0-9_-]{10,128}$/.test(raw)) return DEFAULT_DRIVE_FOLDER_ID;
  return raw;
}

function parseServiceAccountFromEnv(env) {
  const source = env || {};
  let clientEmail = '';
  let privateKey = '';

  if (source.GOOGLE_DRIVE_SERVICE_ACCOUNT_JSON) {
    try {
      const rawJson = String(source.GOOGLE_DRIVE_SERVICE_ACCOUNT_JSON).trim();
      const decoded = rawJson.startsWith('{')
        ? rawJson
        : Buffer.from(rawJson, 'base64').toString('utf8');
      const parsed = JSON.parse(decoded);
      clientEmail = String(parsed.client_email || '').trim();
      privateKey = String(parsed.private_key || '').trim();
    } catch (_) {
      return null;
    }
  } else if (source.GOOGLE_DRIVE_CLIENT_EMAIL && source.GOOGLE_DRIVE_PRIVATE_KEY) {
    clientEmail = String(source.GOOGLE_DRIVE_CLIENT_EMAIL).trim();
    privateKey = String(source.GOOGLE_DRIVE_PRIVATE_KEY).trim();
  }

  if (!clientEmail || !privateKey) return null;
  const normalizedKey = privateKey.replace(/\\n/g, '\n');
  if (!normalizedKey.includes('BEGIN PRIVATE KEY') && !normalizedKey.includes('BEGIN RSA PRIVATE KEY')) {
    return null;
  }
  return { clientEmail, privateKey: normalizedKey };
}

function parseWebhookUrlFromEnv(env) {
  const raw = String((env && env.GOOGLE_DRIVE_WEBHOOK_URL) || '').trim();
  if (!raw) return '';
  try {
    const parsed = new URL(raw);
    if (parsed.protocol !== 'https:') return '';
    // Restrito ao Google Apps Script para evitar SSRF em URLs arbitrárias.
    const host = parsed.hostname.toLowerCase();
    if (host !== 'script.google.com' && host !== 'script.googleusercontent.com') return '';
    return parsed.toString();
  } catch (_) {
    return '';
  }
}

function buildServiceAccountJwt(sa, nowSec) {
  const iat = Number.isFinite(nowSec) ? Math.floor(nowSec) : Math.floor(Date.now() / 1000);
  const header = base64UrlEncode(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const claimSet = base64UrlEncode(
    JSON.stringify({
      iss: sa.clientEmail,
      scope: DRIVE_SCOPE,
      aud: GOOGLE_TOKEN_URL,
      iat,
      exp: iat + 3600,
    })
  );
  const unsigned = `${header}.${claimSet}`;
  const signer = crypto.createSign('RSA-SHA256');
  signer.update(unsigned);
  signer.end();
  const signature = base64UrlEncode(signer.sign(sa.privateKey));
  return `${unsigned}.${signature}`;
}

function buildMultipartDriveBody(fileName, folderId, contentText, boundary) {
  const metaJson = JSON.stringify({
    name: fileName,
    mimeType: 'text/plain',
    parents: [folderId],
  });
  return [
    `--${boundary}`,
    'Content-Type: application/json; charset=UTF-8',
    '',
    metaJson,
    `--${boundary}`,
    'Content-Type: text/plain; charset=UTF-8',
    '',
    contentText,
    `--${boundary}--`,
    '',
  ].join('\r\n');
}

function timingSafeEquals(a, b) {
  const bufA = Buffer.from(String(a || ''), 'utf8');
  const bufB = Buffer.from(String(b || ''), 'utf8');
  if (bufA.length !== bufB.length) return false;
  return crypto.timingSafeEqual(bufA, bufB);
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
      } catch (_) {
        reject(new Error('INVALID_JSON'));
      }
    });
    req.on('error', reject);
  });
}

function createHomologLogsHandler(deps = {}) {
  const fetchImpl = deps.fetch || global.fetch;
  const getEnv = deps.getEnv || (() => process.env);
  const nowFn = deps.now || (() => Date.now());
  const jwtSigner = deps.buildJwt || buildServiceAccountJwt;

  async function uploadViaServiceAccount(sa, folderId, fileName, contentText) {
    const assertion = jwtSigner(sa, Math.floor(nowFn() / 1000));
    const tokenRes = await fetchImpl(GOOGLE_TOKEN_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `grant_type=${encodeURIComponent('urn:ietf:params:oauth:grant-type:jwt-bearer')}&assertion=${encodeURIComponent(assertion)}`,
    });
    const tokenText = await tokenRes.text();
    let tokenData = null;
    try { tokenData = JSON.parse(tokenText); } catch (_) {}
    if (!tokenRes.ok || !tokenData || !tokenData.access_token) {
      const detail = (tokenData && (tokenData.error_description || tokenData.error)) || `HTTP ${tokenRes.status}`;
      throw new Error(`Falha ao autenticar no Google Drive (${detail}).`);
    }

    const boundary = `mk21_homolog_${crypto.randomBytes(10).toString('hex')}`;
    const multipartBody = buildMultipartDriveBody(fileName, folderId, contentText, boundary);
    const uploadRes = await fetchImpl(GOOGLE_DRIVE_UPLOAD_URL, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${tokenData.access_token}`,
        'Content-Type': `multipart/related; boundary=${boundary}`,
      },
      body: multipartBody,
    });
    const uploadText = await uploadRes.text();
    let uploadData = null;
    try { uploadData = JSON.parse(uploadText); } catch (_) {}
    if (!uploadRes.ok || !uploadData || !uploadData.id) {
      const msg = (uploadData && uploadData.error && uploadData.error.message) || `HTTP ${uploadRes.status}`;
      throw new Error(`Falha ao gravar arquivo no Google Drive (${msg}).`);
    }

    return {
      mode: 'drive_api_v3',
      fileId: uploadData.id,
      fileName: uploadData.name || fileName,
      folderId,
      webViewLink: uploadData.webViewLink || `https://drive.google.com/file/d/${uploadData.id}/view`,
    };
  }

  async function uploadViaAppsScriptWebhook(webhookUrl, folderId, fileName, contentText, meta) {
    const res = await fetchImpl(webhookUrl, {
      method: 'POST',
      redirect: 'follow',
      headers: { 'Content-Type': 'application/json; charset=utf-8' },
      body: JSON.stringify({
        folderId,
        fileName,
        mimeType: 'text/plain',
        content: contentText,
        meta,
      }),
    });
    const raw = await res.text();
    let parsed = null;
    try { parsed = JSON.parse(raw); } catch (_) {}
    if (!res.ok || (parsed && parsed.ok === false)) {
      const msg = (parsed && parsed.error) || `HTTP ${res.status}`;
      throw new Error(`Webhook Google Drive falhou (${msg}).`);
    }
    return {
      mode: 'apps_script_webhook',
      fileId: (parsed && parsed.fileId) || '',
      fileName: (parsed && parsed.fileName) || fileName,
      folderId,
      webViewLink: (parsed && parsed.webViewLink) || `https://drive.google.com/drive/folders/${folderId}`,
    };
  }

  return async function handler(req, res) {
    if (req.method === 'OPTIONS') {
      setCors(res);
      res.statusCode = 204;
      return res.end();
    }

    if (req.method !== 'POST') {
      res.setHeader('Allow', 'POST, OPTIONS');
      return sendJson(res, 405, { ok: false, error: 'Método não permitido. Use POST.' });
    }

    const env = getEnv() || {};
    if (String(env.HOMOLOG_LOGS_ENABLED || 'true').trim().toLowerCase() === 'false') {
      return sendJson(res, 410, {
        ok: false,
        disabled: true,
        error: 'Coleta automática de logs de homologação foi desativada.',
      });
    }

    const expectedToken = String(env.HOMOLOG_LOGS_TOKEN || '').trim();
    if (expectedToken) {
      const provided = String((req.headers && (req.headers['x-homolog-token'] || req.headers['X-Homolog-Token'])) || '').trim();
      if (!provided || !timingSafeEquals(provided, expectedToken)) {
        return sendJson(res, 401, { ok: false, error: 'Token de homologação inválido ou ausente.' });
      }
    }

    const sa = parseServiceAccountFromEnv(env);
    const webhookUrl = parseWebhookUrlFromEnv(env);
    if (!sa && !webhookUrl) {
      return sendJson(res, 503, {
        ok: false,
        configured: false,
        folderId: resolveFolderId(env),
        error:
          'Destino Google Drive ainda não configurado na Vercel (defina GOOGLE_DRIVE_SERVICE_ACCOUNT_JSON ou GOOGLE_DRIVE_WEBHOOK_URL).',
      });
    }

    let body;
    try {
      body = await readJsonBody(req);
    } catch (err) {
      if (err && err.message === 'BODY_TOO_LARGE') {
        return sendJson(res, 413, { ok: false, error: 'Arquivo de log excede o limite de 512 KB.' });
      }
      return sendJson(res, 400, { ok: false, error: 'JSON inválido.' });
    }

    const rawContent = body && typeof body.content === 'string' ? body.content.trim() : '';
    if (!rawContent) {
      return sendJson(res, 400, { ok: false, error: 'Conteúdo de log vazio.' });
    }

    const folderId = resolveFolderId(env);
    const nowDate = new Date(nowFn());
    const platform = String((body && body.platform) || 'smart-tv').slice(0, 40);
    const appVersion = String((body && body.appVersion) || '').slice(0, 32);
    const device = String((body && body.device) || '').slice(0, 80);
    const reason = String((body && body.reason) || 'auto').slice(0, 40);
    const fileName = sanitizeFileName(body && body.fileName, nowDate, platform);

    const safeHeader = [
      `# MK21 Homologação — Upload Automático`,
      `# Recebido UTC: ${nowDate.toISOString()}`,
      `# Plataforma: ${platform} | Versão: ${appVersion || '-'} | Motivo: ${reason}`,
      device ? `# Dispositivo: ${device}` : '',
      '',
    ]
      .filter(Boolean)
      .join('\n');

    const redactedContent = safeHeader + '\n' + redactServerSideLogs(rawContent);

    try {
      const result = sa
        ? await uploadViaServiceAccount(sa, folderId, fileName, redactedContent)
        : await uploadViaAppsScriptWebhook(webhookUrl, folderId, fileName, redactedContent, {
            platform,
            appVersion,
            device,
            reason,
          });

      return sendJson(res, 200, {
        ok: true,
        uploaded: true,
        ...result,
      });
    } catch (err) {
      console.error('[homolog-logs] upload error:', err && err.message ? err.message : err);
      return sendJson(res, 502, {
        ok: false,
        uploaded: false,
        error: err && err.message ? err.message : 'Erro ao enviar log para o Google Drive.',
      });
    }
  };
}

const defaultHandler = createHomologLogsHandler();
defaultHandler.createHomologLogsHandler = createHomologLogsHandler;
defaultHandler.DEFAULT_DRIVE_FOLDER_ID = DEFAULT_DRIVE_FOLDER_ID;
defaultHandler.redactServerSideLogs = redactServerSideLogs;
defaultHandler.sanitizeFileName = sanitizeFileName;
defaultHandler.parseServiceAccountFromEnv = parseServiceAccountFromEnv;
defaultHandler.parseWebhookUrlFromEnv = parseWebhookUrlFromEnv;
defaultHandler.resolveFolderId = resolveFolderId;

module.exports = defaultHandler;
