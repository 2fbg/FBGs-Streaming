const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const { EventEmitter } = require('node:events');

const homologLogs = require('../../api/homolog-logs.js');

function createMockRes() {
  const headers = {};
  return {
    statusCode: 200,
    headers,
    body: '',
    setHeader(name, value) {
      headers[String(name).toLowerCase()] = value;
    },
    end(chunk) {
      if (chunk) this.body += String(chunk);
    },
    json() {
      return this.body ? JSON.parse(this.body) : {};
    },
  };
}

function createMockReq(method, payload, extraHeaders = {}) {
  const req = new EventEmitter();
  req.method = method;
  req.headers = extraHeaders;
  req.body = payload;
  req.destroy = () => {};
  return req;
}

test('homolog-logs returns 503 configured=false when no Drive credentials or webhook are set', async () => {
  const handler = homologLogs.createHomologLogsHandler({
    getEnv: () => ({}),
    fetch: async () => {
      throw new Error('fetch should not be called');
    },
  });
  const res = createMockRes();
  await handler(createMockReq('POST', { content: '[INFO] teste' }), res);
  assert.equal(res.statusCode, 503);
  const data = res.json();
  assert.equal(data.ok, false);
  assert.equal(data.configured, false);
  assert.equal(data.folderId, '1LqB3dl5ALzgVqUDlYaHzL9XWvv5_ZuGB');
});

test('homolog-logs returns 410 when HOMOLOG_LOGS_ENABLED=false', async () => {
  const handler = homologLogs.createHomologLogsHandler({
    getEnv: () => ({ HOMOLOG_LOGS_ENABLED: 'false' }),
  });
  const res = createMockRes();
  await handler(createMockReq('POST', { content: 'log' }), res);
  assert.equal(res.statusCode, 410);
  assert.equal(res.json().disabled, true);
});

test('homolog-logs uploads via Google Drive API v3 Service Account to target folder and redacts credentials', async () => {
  const { privateKey } = crypto.generateKeyPairSync('rsa', {
    modulusLength: 2048,
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
    publicKeyEncoding: { type: 'spki', format: 'pem' },
  });

  const calls = [];
  const mockFetch = async (url, init) => {
    calls.push({ url, init });
    if (String(url).includes('oauth2.googleapis.com/token')) {
      return {
        ok: true,
        status: 200,
        async text() {
          return JSON.stringify({ access_token: 'ya29.mock-token' });
        },
      };
    }
    if (String(url).includes('googleapis.com/upload/drive/v3/files')) {
      return {
        ok: true,
        status: 200,
        async text() {
          return JSON.stringify({
            id: 'drive_file_123',
            name: 'mk21-tv-v2.9.7.txt',
            webViewLink: 'https://drive.google.com/file/d/drive_file_123/view',
          });
        },
      };
    }
    throw new Error('Unexpected URL: ' + url);
  };

  const handler = homologLogs.createHomologLogsHandler({
    getEnv: () => ({
      GOOGLE_DRIVE_SERVICE_ACCOUNT_JSON: JSON.stringify({
        client_email: 'mk21-homolog@project.iam.gserviceaccount.com',
        private_key: privateKey,
      }),
    }),
    fetch: mockFetch,
    now: () => Date.UTC(2026, 10, 21, 12, 0, 0),
  });

  const res = createMockRes();
  await handler(
    createMockReq('POST', {
      content: 'URL: http://secretuser:secretpass@ovo54.sbs/get.php?username=alice&password=bob\nStream: http://host/live/alice/bob/123.ts',
      fileName: '../../evil/mk21-tv-v2.9.7.txt',
      platform: 'webos-tv',
      appVersion: '2.9.7',
      reason: 'manual',
    }),
    res
  );

  assert.equal(res.statusCode, 200);
  const data = res.json();
  assert.equal(data.ok, true);
  assert.equal(data.uploaded, true);
  assert.equal(data.mode, 'drive_api_v3');
  assert.equal(data.fileId, 'drive_file_123');
  assert.equal(data.folderId, '1LqB3dl5ALzgVqUDlYaHzL9XWvv5_ZuGB');

  assert.equal(calls.length, 2);
  const uploadBody = String(calls[1].init.body);
  assert.match(uploadBody, /"parents":\["1LqB3dl5ALzgVqUDlYaHzL9XWvv5_ZuGB"\]/);
  assert.match(uploadBody, /"name":"mk21-tv-v2\.9\.7\.txt"/);
  assert.doesNotMatch(uploadBody, /secretuser|secretpass|alice|bob/);
  assert.match(uploadBody, /\[REDACTED\]/);
});

test('homolog-logs supports Apps Script webhook fallback and blocks non-Google SSRF webhook URLs', async () => {
  // SSRF protection: arbitrary domain is rejected and treated as unconfigured
  assert.equal(
    homologLogs.parseWebhookUrlFromEnv({ GOOGLE_DRIVE_WEBHOOK_URL: 'https://evil.example.com/hook' }),
    ''
  );
  assert.equal(
    homologLogs.parseWebhookUrlFromEnv({ GOOGLE_DRIVE_WEBHOOK_URL: 'https://script.google.com/macros/s/ABC123/exec' }),
    'https://script.google.com/macros/s/ABC123/exec'
  );

  let forwardedPayload = null;
  const handler = homologLogs.createHomologLogsHandler({
    getEnv: () => ({
      GOOGLE_DRIVE_WEBHOOK_URL: 'https://script.google.com/macros/s/ABC123/exec',
    }),
    fetch: async (_url, init) => {
      forwardedPayload = JSON.parse(init.body);
      return {
        ok: true,
        status: 200,
        async text() {
          return JSON.stringify({ ok: true, fileId: 'gs_file_9', fileName: forwardedPayload.fileName });
        },
      };
    },
  });

  const res = createMockRes();
  await handler(createMockReq('POST', { content: 'Diagnostico OK', platform: 'webos-tv' }), res);
  assert.equal(res.statusCode, 200);
  const data = res.json();
  assert.equal(data.ok, true);
  assert.equal(data.mode, 'apps_script_webhook');
  assert.equal(forwardedPayload.folderId, '1LqB3dl5ALzgVqUDlYaHzL9XWvv5_ZuGB');
});

test('homolog-logs enforces 512 KB limit and optional X-Homolog-Token', async () => {
  const handler = homologLogs.createHomologLogsHandler({
    getEnv: () => ({
      GOOGLE_DRIVE_WEBHOOK_URL: 'https://script.google.com/macros/s/ABC123/exec',
      HOMOLOG_LOGS_TOKEN: 'tok_secret_123',
    }),
  });

  const resUnauthorized = createMockRes();
  await handler(createMockReq('POST', { content: 'hello' }), resUnauthorized);
  assert.equal(resUnauthorized.statusCode, 401);

  const resTooBig = createMockRes();
  await handler(
    createMockReq('POST', { content: 'A'.repeat(520 * 1024) }, { 'x-homolog-token': 'tok_secret_123' }),
    resTooBig
  );
  assert.equal(resTooBig.statusCode, 413);
});
