/**
 * MK21 Play — Ancoragem de Trial por Dispositivo (Anti-Reset em Reinstalação)
 *
 * Persiste `first_seen_at` na tabela `device_trials` do Neon Postgres indexado por
 * `device_id` (Virtual MAC derivado do ANDROID_ID). Ao desinstalar e reinstalar o app,
 * o servidor devolve a data original da primeira instalação — impedindo reset infinito
 * do período de avaliação de 5 dias e bloqueando manipulação do relógio local.
 *
 * POST /api/license/trial  { deviceId }
 *  -> { ok, deviceId, firstSeenAtMs, serverNowMs, trialDaysTotal, trialDaysRemaining, expired, licensed, installCount }
 */
const { readJsonBody, sendJson } = require('./_token');

const TRIAL_DAYS = 5;
const TRIAL_MS = TRIAL_DAYS * 24 * 60 * 60 * 1000;

function normalizeDeviceId(raw) {
  return String(raw || '').trim().toUpperCase().slice(0, 128);
}

function createTrialHandler(deps = {}) {
  const getSql = deps.getSql || (() => {
    const url = process.env.DATABASE_URL;
    if (!url) return null;
    const { neon } = require('@neondatabase/serverless');
    return neon(url);
  });
  const nowFn = deps.now || (() => Date.now());
  let schemaReady = Boolean(deps.skipEnsureTable);

  async function ensureTable(sql) {
    if (schemaReady) return;
    await sql`
      CREATE TABLE IF NOT EXISTS device_trials (
        device_id TEXT PRIMARY KEY,
        first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
        last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
        install_count INT NOT NULL DEFAULT 1
      )
    `;
    schemaReady = true;
  }

  return async function handler(req, res) {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') {
      res.statusCode = 204;
      return res.end();
    }

    if (req.method !== 'POST') {
      res.setHeader('Allow', 'POST, OPTIONS');
      return sendJson(res, 405, { ok: false, error: 'Método não permitido.' });
    }

    let body;
    try {
      body = await readJsonBody(req);
    } catch (err) {
      if (err && err.message === 'BODY_TOO_LARGE') {
        return sendJson(res, 413, { ok: false, error: 'Payload excede o limite permitido.' });
      }
      return sendJson(res, 400, { ok: false, error: 'JSON inválido.' });
    }

    const deviceId = normalizeDeviceId(body && body.deviceId);
    if (!deviceId || deviceId.length < 6) {
      return sendJson(res, 400, { ok: false, error: 'Campo obrigatório: deviceId.' });
    }

    const sql = getSql();
    if (!sql) {
      return sendJson(res, 503, { ok: false, error: 'Banco de dados indisponível.' });
    }

    try {
      await ensureTable(sql);
      const nowMs = nowFn();
      const nowIso = new Date(nowMs).toISOString();

      // Upsert idempotente: preserva `first_seen_at` original para sempre (imune a desinstalação/reinstalação).
      const rows = await sql`
        INSERT INTO device_trials (device_id, first_seen_at, last_seen_at, install_count)
        VALUES (${deviceId}, ${nowIso}, ${nowIso}, 1)
        ON CONFLICT (device_id) DO UPDATE
          SET last_seen_at = ${nowIso},
              install_count = device_trials.install_count + 1
        RETURNING first_seen_at, install_count
      `;
      const row = rows && rows[0];
      const firstSeenAtMs = row && row.first_seen_at
        ? new Date(row.first_seen_at).getTime()
        : nowMs;
      const installCount = Number((row && row.install_count) || 1);

      // Verifica também se o dispositivo já possui licença ativa no servidor
      let licensed = false;
      try {
        const licRows = await sql`
          SELECT expires_at, revoked
          FROM licenses
          WHERE device_id = ${deviceId}
          LIMIT 1
        `;
        const lic = licRows && licRows[0];
        if (lic && !lic.revoked && lic.expires_at && new Date(lic.expires_at).getTime() > nowMs) {
          licensed = true;
        }
      } catch (_) {
        // Tabela licenses pode não existir em ambientes mínimos
      }

      const elapsedMs = Math.max(0, nowMs - firstSeenAtMs);
      const remainingMs = Math.max(0, TRIAL_MS - elapsedMs);
      const trialDaysRemaining = licensed
        ? 9999
        : (remainingMs <= 0 ? 0 : Math.min(TRIAL_DAYS, Math.ceil(remainingMs / (24 * 60 * 60 * 1000))));
      const expired = !licensed && remainingMs <= 0;

      return sendJson(res, 200, {
        ok: true,
        deviceId,
        firstSeenAtMs,
        serverNowMs: nowMs,
        trialDaysTotal: TRIAL_DAYS,
        trialDaysRemaining,
        expired,
        licensed,
        installCount,
      });
    } catch (err) {
      console.error('Trial anchor error:', err && err.message ? err.message : err);
      return sendJson(res, 500, { ok: false, error: 'Erro interno ao consultar avaliação.' });
    }
  };
}

const defaultHandler = createTrialHandler();
defaultHandler.createTrialHandler = createTrialHandler;
defaultHandler.TRIAL_DAYS = TRIAL_DAYS;

module.exports = defaultHandler;
