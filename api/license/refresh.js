const { neon } = require('@neondatabase/serverless');
const { buildPayload, signToken, readJsonBody, sendJson } = require('./_token');

function createRefreshHandler(deps = {}) {
  const getSql = deps.getSql || (() => {
    const url = process.env.DATABASE_URL;
    if (!url) throw new Error('DATABASE_URL não configurada.');
    return neon(url);
  });
  const getPrivateKey = deps.getPrivateKey || (() => process.env.LICENSE_SIGNING_KEY || '');
  const nowFn = deps.now || (() => Date.now());

  return async function handler(req, res) {
    if (req.method !== 'POST') {
      res.setHeader('Allow', 'POST');
      return sendJson(res, 405, { error: 'Método não permitido.' });
    }

    let body;
    try {
      body = await readJsonBody(req);
    } catch (err) {
      if (err && err.message === 'BODY_TOO_LARGE') {
        return sendJson(res, 413, { error: 'Payload excede o limite permitido.' });
      }
      return sendJson(res, 400, { error: 'JSON inválido.' });
    }

    const normalizedDeviceId = body && typeof body.deviceId === 'string' ? body.deviceId.trim() : '';
    if (!normalizedDeviceId || normalizedDeviceId.length > 128) {
      return sendJson(res, 400, { error: 'Campo obrigatório: deviceId.' });
    }

    const privateKey = getPrivateKey();
    if (!privateKey || !String(privateKey).trim()) {
      return sendJson(res, 500, { error: 'Servidor de licenças mal configurado.' });
    }

    try {
      const sql = getSql();
      const rows = await sql`
        SELECT device_id, order_code, tier, expires_at, revoked
        FROM licenses
        WHERE device_id = ${normalizedDeviceId}
        LIMIT 1
      `;
      const row = rows && rows[0];
      const nowMs = nowFn();
      if (!row || row.revoked || new Date(row.expires_at).getTime() <= nowMs) {
        return sendJson(res, 403, { error: 'Licença inválida ou revogada.' });
      }

      await sql`
        UPDATE licenses
        SET last_seen = now()
        WHERE device_id = ${normalizedDeviceId}
      `;

      const nowSec = Math.floor(nowMs / 1000);
      const expEpoch = Math.floor(new Date(row.expires_at).getTime() / 1000);
      const tier = row.tier || 'pro';
      const payload = buildPayload(normalizedDeviceId, row.order_code, tier, 365, nowSec, expEpoch);
      const token = signToken(payload, privateKey);

      return sendJson(res, 200, {
        token,
        tier,
        expiresAt: new Date(row.expires_at).toISOString(),
      });
    } catch (err) {
      console.error('License refresh error:', err && err.message ? err.message : err);
      return sendJson(res, 500, { error: 'Erro interno ao renovar licença.' });
    }
  };
}

const defaultHandler = createRefreshHandler();
defaultHandler.createRefreshHandler = createRefreshHandler;

module.exports = defaultHandler;
