const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');

const { buildPayload, signToken } = require('../../api/license/_token.js');
const { createActivateHandler } = require('../../api/license/activate.js');
const { createRefreshHandler } = require('../../api/license/refresh.js');

function generateTestKeyPair() {
  return crypto.generateKeyPairSync('ec', {
    namedCurve: 'prime256v1',
    publicKeyEncoding: { type: 'spki', format: 'pem' },
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
  });
}

function createMockRes() {
  const res = {
    statusCode: 200,
    headers: {},
    body: null,
    setHeader(k, v) {
      this.headers[k.toLowerCase()] = v;
    },
    status(code) {
      this.statusCode = code;
      return this;
    },
    json(obj) {
      this.body = obj;
      return this;
    },
    end(raw = '') {
      if (raw) {
        try {
          this.body = JSON.parse(raw);
        } catch {
          this.body = raw;
        }
      }
    },
  };
  return res;
}

function verifyMkl1Token(token, publicKeyPem) {
  const parts = token.split('.');
  assert.equal(parts.length, 3);
  assert.equal(parts[0], 'MKL1');
  const body = `${parts[0]}.${parts[1]}`;
  const sig = Buffer.from(parts[2], 'base64url');
  const ok = crypto.verify(
    'sha256',
    Buffer.from(body, 'utf8'),
    { key: publicKeyPem, dsaEncoding: 'der' },
    sig
  );
  assert.equal(ok, true, 'ECDSA DER signature must verify against P-256 public key');
  return JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
}

test('signToken produces MKL1 ECDSA P-256 DER token verifiable with public key (and escaped PEM)', () => {
  const { publicKey, privateKey } = generateTestKeyPair();
  const escapedPrivate = privateKey.replace(/\n/g, '\\n');
  const payload = buildPayload('AA:BB:CC:DD:EE:FF', 'MK-ORDER-1', 'pro', 365, 1_750_000_000);
  const token = signToken(payload, escapedPrivate);

  const decoded = verifyMkl1Token(token, publicKey);
  assert.equal(decoded.d, 'AA:BB:CC:DD:EE:FF');
  assert.equal(decoded.t, 'pro');
  assert.equal(decoded.o, 'MK-ORDER-1');
  assert.equal(decoded.iat, 1_750_000_000);
  assert.equal(decoded.exp, 1_750_000_000 + 365 * 86400);
});

test('activate rejects non-POST, missing fields, unknown order, expired order, and exhausted seats', async () => {
  const { privateKey } = generateTestKeyPair();
  const baseNow = 1_750_000_000_000;

  // Non-POST
  {
    const handler = createActivateHandler({ getSql: () => async () => [], getPrivateKey: () => privateKey });
    const res = createMockRes();
    await handler({ method: 'GET' }, res);
    assert.equal(res.statusCode, 405);
  }

  // Missing fields
  {
    const handler = createActivateHandler({ getSql: () => async () => [], getPrivateKey: () => privateKey });
    const res = createMockRes();
    await handler({ method: 'POST', body: { code: '   ', deviceId: 'AA:BB:CC:DD:EE:FF' } }, res);
    assert.equal(res.statusCode, 400);
  }

  // Order not found -> 404
  {
    const handler = createActivateHandler({
      getSql: () => async () => [],
      getPrivateKey: () => privateKey,
    });
    const res = createMockRes();
    await handler({ method: 'POST', body: { code: 'INVALIDO', deviceId: 'AA:BB:CC:DD:EE:FF' } }, res);
    assert.equal(res.statusCode, 404);
  }

  // Expired order -> 403
  {
    const handler = createActivateHandler({
      now: () => baseNow,
      getPrivateKey: () => privateKey,
      getSql: () => async (strings) => {
        const q = strings.join(' ');
        if (q.includes('FROM orders')) {
          return [{ code: 'ORD-EXP', tier: 'pro', seats: 2, expires_at: new Date(baseNow - 1000).toISOString() }];
        }
        return [];
      },
    });
    const res = createMockRes();
    await handler({ method: 'POST', body: { code: 'ORD-EXP', deviceId: 'AA:BB:CC:DD:EE:FF' } }, res);
    assert.equal(res.statusCode, 403);
  }

  // Exhausted seats -> 409
  {
    const handler = createActivateHandler({
      now: () => baseNow,
      getPrivateKey: () => privateKey,
      getSql: () => async (strings) => {
        const q = strings.join(' ');
        if (q.includes('FROM orders')) {
          return [{ code: 'ORD-FULL', tier: 'pro', seats: 2, expires_at: null }];
        }
        if (q.includes('SELECT device_id, order_code, revoked')) {
          return [];
        }
        if (q.includes('COUNT(*)::int AS count')) {
          return [{ count: 2 }];
        }
        return [];
      },
    });
    const res = createMockRes();
    await handler({ method: 'POST', body: { code: 'ORD-FULL', deviceId: 'AA:BB:CC:DD:EE:FF' } }, res);
    assert.equal(res.statusCode, 409);
  }
});

test('activate issues valid MKL1 token and allows same device reinstall', async () => {
  const { publicKey, privateKey } = generateTestKeyPair();
  const baseNow = 1_750_000_000_000;
  let upserted = false;

  const handler = createActivateHandler({
    now: () => baseNow,
    getPrivateKey: () => privateKey,
    getSql: () => async (strings, ...values) => {
      const q = strings.join(' ');
      if (q.includes('FROM orders')) {
        assert.equal(values[0], 'ORD-OK');
        return [{ code: 'ORD-OK', tier: 'pro', seats: 1, expires_at: null }];
      }
      if (q.includes('SELECT device_id, order_code, revoked')) {
        return [{ device_id: 'AA:BB:CC:DD:EE:FF', order_code: 'ORD-OK', revoked: false }];
      }
      if (q.includes('COUNT(*)::int AS count')) {
        return [{ count: 0 }];
      }
      if (q.includes('INSERT INTO licenses')) {
        upserted = true;
        return [];
      }
      return [];
    },
  });

  const res = createMockRes();
  await handler({ method: 'POST', body: { code: ' ord-ok ', deviceId: 'AA:BB:CC:DD:EE:FF' } }, res);
  assert.equal(res.statusCode, 200);
  assert.equal(upserted, true);
  assert.equal(res.body.tier, 'pro');
  const decoded = verifyMkl1Token(res.body.token, publicKey);
  assert.equal(decoded.d, 'AA:BB:CC:DD:EE:FF');
  assert.equal(decoded.o, 'ORD-OK');
});

test('refresh rejects revoked or expired licenses and refreshes valid ones', async () => {
  const { publicKey, privateKey } = generateTestKeyPair();
  const baseNow = 1_750_000_000_000;

  // Revoked -> 403
  {
    const handler = createRefreshHandler({
      now: () => baseNow,
      getPrivateKey: () => privateKey,
      getSql: () => async () => [
        {
          device_id: 'AA:BB:CC:DD:EE:FF',
          order_code: 'ORD-1',
          tier: 'pro',
          expires_at: new Date(baseNow + 86400_000).toISOString(),
          revoked: true,
        },
      ],
    });
    const res = createMockRes();
    await handler({ method: 'POST', body: { deviceId: 'AA:BB:CC:DD:EE:FF' } }, res);
    assert.equal(res.statusCode, 403);
  }

  // Active -> 200 with updated last_seen and valid signature
  {
    let updatedLastSeen = false;
    const expiryIso = new Date(baseNow + 20 * 86400_000).toISOString();
    const handler = createRefreshHandler({
      now: () => baseNow,
      getPrivateKey: () => privateKey,
      getSql: () => async (strings) => {
        const q = strings.join(' ');
        if (q.includes('FROM licenses')) {
          return [
            {
              device_id: 'AA:BB:CC:DD:EE:FF',
              order_code: 'ORD-1',
              tier: 'pro',
              expires_at: expiryIso,
              revoked: false,
            },
          ];
        }
        if (q.includes('UPDATE licenses')) {
          updatedLastSeen = true;
          return [];
        }
        return [];
      },
    });
    const res = createMockRes();
    await handler({ method: 'POST', body: { deviceId: 'AA:BB:CC:DD:EE:FF' } }, res);
    assert.equal(res.statusCode, 200);
    assert.equal(updatedLastSeen, true);
    const decoded = verifyMkl1Token(res.body.token, publicKey);
    assert.equal(decoded.exp, Math.floor(new Date(expiryIso).getTime() / 1000));
  }
});
