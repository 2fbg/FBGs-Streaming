const test = require('node:test');
const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');

const proxy = require('../../api/proxy.js');

function createMockRes() {
    const res = new EventEmitter();
    res.statusCode = 200;
    res.headers = {};
    res.headersSent = false;
    res.writableEnded = false;
    let body = '';
    res.setHeader = (key, val) => {
        res.headers[key.toLowerCase()] = val;
    };
    res.writeHead = (code, headers = {}) => {
        res.statusCode = code;
        for (const [k, v] of Object.entries(headers)) {
            res.headers[k.toLowerCase()] = v;
        }
        res.headersSent = true;
    };
    res.end = (chunk = '') => {
        if (chunk) body += chunk.toString();
        res.headersSent = true;
        res.writableEnded = true;
        res._body = body;
    };
    return res;
}

test('isPrivateHost blocks IPv4, IPv6, IPv4-mapped, decimal, hex, and internal domains', () => {
    const blocked = [
        '127.0.0.1',
        'localhost',
        'sub.localhost',
        '10.0.0.1',
        '172.16.0.1',
        '172.31.255.254',
        '192.168.1.1',
        '169.254.169.254',
        '100.64.0.1',
        '0.0.0.0',
        '::1',
        '[::1]',
        'fc00::1',
        'fd12:3456::1',
        'fe80::1',
        '::ffff:127.0.0.1',
        '::ffff:169.254.169.254',
        '::ffff:7f00:1',
        '2130706433',
        '0x7f000001',
        '0177.0.0.1',
        'service.internal',
        'printer.local'
    ];

    for (const host of blocked) {
        assert.equal(proxy.isPrivateHost(host), true, `Expected ${host} to be blocked`);
    }

    assert.equal(proxy.isPrivateHost('8.8.8.8'), false);
    assert.equal(proxy.isPrivateHost('stream.example.com'), false);
    assert.equal(proxy.isPrivateHost('2606:4700:4700::1111'), false);
});

test('resolveAndValidateHost blocks domains resolving to private IPs (anti DNS rebinding)', async () => {
    const rebindingLookup = async () => [{ address: '127.0.0.1', family: 4 }];
    const v6RebindingLookup = async () => [{ address: '::ffff:169.254.169.254', family: 6 }];
    const publicLookup = async () => [{ address: '93.184.216.34', family: 4 }];

    const r1 = await proxy.resolveAndValidateHost('rebind.example.com', rebindingLookup);
    assert.equal(r1.allowed, false);

    const r2 = await proxy.resolveAndValidateHost('rebind6.example.com', v6RebindingLookup);
    assert.equal(r2.allowed, false);

    const r3 = await proxy.resolveAndValidateHost('cdn.example.com', publicLookup);
    assert.equal(r3.allowed, true);
});

test('CORS allowlist allows official origins, same-host HTTPS deployments, and rejects arbitrary third-party sites', async () => {
    assert.equal(proxy.isAllowedOrigin('https://bgstreaming.vercel.app'), true);
    assert.equal(proxy.isAllowedOrigin('https://bgstreaming-preview.vercel.app'), true);
    assert.equal(proxy.isAllowedOrigin('http://localhost:3000'), true);
    assert.equal(proxy.isAllowedOrigin('https://evil.example.com'), false);
    assert.equal(
        proxy.isAllowedOrigin('https://mk21-custom.example.com', {
            headers: { 'x-forwarded-host': 'mk21-custom.example.com' }
        }),
        true
    );

    const handler = proxy.createProxyHandler({
        lookup: async () => [{ address: '93.184.216.34', family: 4 }]
    });

    const evilReq = new EventEmitter();
    evilReq.method = 'GET';
    evilReq.url = '/api/proxy?url=https://example.com/playlist.m3u8';
    evilReq.headers = { origin: 'https://evil.example.com' };
    const evilRes = createMockRes();
    await handler(evilReq, evilRes);
    assert.equal(evilRes.statusCode, 403);
    assert.equal(evilRes.headers['access-control-allow-origin'], undefined);

    const crossSiteReq = new EventEmitter();
    crossSiteReq.method = 'GET';
    crossSiteReq.url = '/api/proxy?url=https://example.com/playlist.m3u8';
    crossSiteReq.headers = { 'sec-fetch-site': 'cross-site' };
    const crossSiteRes = createMockRes();
    await handler(crossSiteReq, crossSiteRes);
    assert.equal(crossSiteRes.statusCode, 403);
});

test('handler rejects DNS-resolved private hosts with 403 and no internal error leak', async () => {
    const handler = proxy.createProxyHandler({
        lookup: async () => [{ address: '10.10.0.5', family: 4 }]
    });
    const req = new EventEmitter();
    req.method = 'GET';
    req.url = '/api/proxy?url=http://sneaky.example.com/live.ts';
    req.headers = { origin: 'https://bgstreaming.vercel.app' };
    req.socket = { remoteAddress: '203.0.113.10' };

    const res = createMockRes();
    await handler(req, res);

    assert.equal(res.statusCode, 403);
    assert.equal(res.headers['access-control-allow-origin'], 'https://bgstreaming.vercel.app');
    assert.match(res._body, /redes locais e privadas/i);
});
