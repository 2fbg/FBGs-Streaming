const { neon } = require('@neondatabase/serverless');
const { buildPayload, signToken, readJsonBody, sendJson } = require('./_token');

function createActivateHandler(deps = {}) {
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

    const rawCode = body && typeof body.code === 'string' ? body.code.trim() : '';
    const rawDeviceId = body && typeof body.deviceId === 'string' ? body.deviceId.trim() : '';

    if (!rawCode || rawCode.length > 64 || !rawDeviceId || rawDeviceId.length > 128) {
      return sendJson(res, 400, { error: 'Campos obrigatórios: code e deviceId.' });
    }

    const normalizedCode = rawCode.toUpperCase();
    const normalizedDeviceId = rawDeviceId;

    const privateKey = getPrivateKey();
    if (!privateKey || !String(privateKey).trim()) {
      return sendJson(res, 500, { error: 'Servidor de licenças mal configurado.' });
    }

    try {
      const sql = getSql();
      const orders = await sql`
        SELECT code, tier, seats, expires_at
        FROM orders
        WHERE code = ${normalizedCode}
        LIMIT 1
      `;
      const order = orders && orders[0];
      if (!order) {
        return sendJson(res, 404, { error: 'Código inválido.' });
      }

      const nowMs = nowFn();
      if (order.expires_at && new Date(order.expires_at).getTime() <= nowMs) {
        return sendJson(res, 403, { error: 'Pedido expirado.' });
      }

      // Impede que um dispositivo explicitamente revogado para este pedido se reative sozinho
      const existing = await sql`
        SELECT device_id, order_code, revoked
        FROM licenses
        WHERE device_id = ${normalizedDeviceId}
        LIMIT 1
      `;
      if (existing && existing[0] && existing[0].revoked && existing[0].order_code === normalizedCode) {
        return sendJson(res, 403, { error: 'Dispositivo revogado para este pedido.' });
      }

      // Conta outros aparelhos ativos no mesmo pedido (reinstalação no mesmo deviceId não consome novo assento)
      const countRows = await sql`
        SELECT COUNT(*)::int AS count
        FROM licenses
        WHERE order_code = ${normalizedCode}
          AND revoked = false
          AND device_id <> ${normalizedDeviceId}
      `;
      const activeOtherSeats = Number((countRows && countRows[0] && countRows[0].count) || 0);
      const maxSeats = Number(order.seats || 1);
      if (activeOtherSeats >= maxSeats) {
        return sendJson(res, 409, { error: 'Limite de aparelhos atingido.' });
      }

      const nowSec = Math.floor(nowMs / 1000);
      const ttlDays = 365;
      const expEpoch = nowSec + ttlDays * 86400;
      const expiresAtIso = new Date(expEpoch * 1000).toISOString();
      const tier = order.tier || 'pro';

      await sql`
        INSERT INTO licenses (device_id, order_code, tier, expires_at, revoked, last_seen)
        VALUES (${normalizedDeviceId}, ${normalizedCode}, ${tier}, ${expiresAtIso}, false, now())
        ON CONFLICT (device_id) DO UPDATE
          SET order_code = EXCLUDED.order_code,
              tier = EXCLUDED.tier,
              expires_at = EXCLUDED.expires_at,
              revoked = false,
              last_seen = now()
      `;

      const payload = buildPayload(normalizedDeviceId, normalizedCode, tier, ttlDays, nowSec, expEpoch);
      const token = signToken(payload, privateKey);

      return sendJson(res, 200, {
        token,
        tier,
        expiresAt: expiresAtIso,
      });
    } catch (err) {
      console.error('License activate error:', err && err.message ? err.message : err);
      return sendJson(res, 500, { error: 'Erro interno ao ativar licença.' });
    }
  };
}

const defaultHandler = createActivateHandler();
defaultHandler.createActivateHandler = createActivateHandler;

module.exports = defaultHandler;
