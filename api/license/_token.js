const crypto = require('crypto');

function b64u(buf) {
  return Buffer.from(buf).toString('base64url');
}

function normalizePem(raw) {
  if (typeof raw !== 'string') return '';
  return raw.replace(/\\n/g, '\n').trim();
}

function buildPayload(
  deviceId,
  orderCode,
  tier = 'pro',
  ttlDays = 365,
  nowSec = Math.floor(Date.now() / 1000),
  explicitExp = null
) {
  const cleanDevice = String(deviceId || '').trim();
  const cleanOrder = String(orderCode || '').trim();
  const cleanTier = String(tier || 'pro').trim() || 'pro';
  if (!cleanDevice) {
    throw new Error('deviceId ausente para geração do token.');
  }
  if (!cleanOrder) {
    throw new Error('orderCode ausente para geração do token.');
  }
  const exp =
    typeof explicitExp === 'number' && Number.isFinite(explicitExp) && explicitExp > 0
      ? Math.floor(explicitExp)
      : nowSec + ttlDays * 86400;

  return {
    d: cleanDevice,
    t: cleanTier,
    iat: nowSec,
    exp,
    o: cleanOrder,
  };
}

function signToken(payload, privateKeyPem) {
  const pem = normalizePem(privateKeyPem);
  if (!pem) {
    throw new Error('LICENSE_SIGNING_KEY não configurada.');
  }
  const body = 'MKL1.' + b64u(Buffer.from(JSON.stringify(payload), 'utf8'));
  const sig = crypto.sign('sha256', Buffer.from(body, 'utf8'), {
    key: pem,
    dsaEncoding: 'der',
  });
  return `${body}.${b64u(sig)}`;
}

async function readJsonBody(req) {
  if (req && req.body !== undefined && req.body !== null) {
    if (typeof req.body === 'object' && !Buffer.isBuffer(req.body)) {
      return req.body;
    }
    if (typeof req.body === 'string') {
      return req.body.trim() ? JSON.parse(req.body) : {};
    }
    if (Buffer.isBuffer(req.body)) {
      const str = req.body.toString('utf8').trim();
      return str ? JSON.parse(str) : {};
    }
  }

  if (!req || typeof req[Symbol.asyncIterator] !== 'function') {
    return {};
  }

  const chunks = [];
  let totalBytes = 0;
  for await (const chunk of req) {
    const buf = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    totalBytes += buf.length;
    if (totalBytes > 16384) {
      throw new Error('BODY_TOO_LARGE');
    }
    chunks.push(buf);
  }
  const raw = Buffer.concat(chunks).toString('utf8').trim();
  if (!raw) return {};
  return JSON.parse(raw);
}

function sendJson(res, statusCode, payload) {
  if (typeof res.status === 'function' && typeof res.json === 'function') {
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    return res.status(statusCode).json(payload);
  }
  res.statusCode = statusCode;
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.end(JSON.stringify(payload));
}

module.exports = {
  b64u,
  normalizePem,
  buildPayload,
  signToken,
  readJsonBody,
  sendJson,
};
