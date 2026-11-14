// Vercel Serverless Function to act as a secure, fast, and dedicated streaming CORS Proxy for MK21 IPTV Web.
// Since Node/Vercel acts server-side, it bypasses browser CORS and Mixed Content blocks.
// Pipes binary streams directly from the source to prevent out-of-memory errors and timeout limits.

const dns = require('dns');
const http = require('http');
const https = require('https');
const net = require('net');
const { URL } = require('url');

const DEFAULT_ALLOWED_ORIGIN_PATTERNS = [
    /^https:\/\/bgstreaming(?:-[a-z0-9-]+)?\.vercel\.app$/i,
    /^http:\/\/localhost(?::\d+)?$/i,
    /^http:\/\/127\.0\.0\.1(?::\d+)?$/i,
    /^capacitor:\/\/localhost$/i
];

function getEnvAllowedOrigins() {
    const raw = process.env.PROXY_ALLOWED_ORIGINS || '';
    return raw
        .split(',')
        .map((item) => item.trim().toLowerCase())
        .filter(Boolean);
}

function isAllowedOrigin(origin, req = null) {
    if (!origin || typeof origin !== 'string') return false;
    const normalized = origin.trim();
    if (!normalized || normalized === 'null') return false;
    const extraOrigins = getEnvAllowedOrigins();
    if (extraOrigins.includes(normalized.toLowerCase())) {
        return true;
    }
    if (DEFAULT_ALLOWED_ORIGIN_PATTERNS.some((pattern) => pattern.test(normalized))) {
        return true;
    }
    if (req && req.headers) {
        const rawReqHost = req.headers['x-forwarded-host'] || req.headers.host || '';
        const reqHost = String(rawReqHost).split(',')[0].trim().toLowerCase();
        if (reqHost) {
            try {
                const parsedOrigin = new URL(normalized);
                if (parsedOrigin.protocol === 'https:' && parsedOrigin.host.toLowerCase() === reqHost) {
                    return true;
                }
            } catch (_) {
                return false;
            }
        }
    }
    return false;
}

function isPrivateIpv4(ip) {
    const parts = ip.split('.').map(Number);
    if (parts.length !== 4 || parts.some((n) => !Number.isInteger(n) || n < 0 || n > 255)) {
        return true;
    }
    const [a, b, c] = parts;
    if (a === 0) return true; // 0.0.0.0/8
    if (a === 10) return true; // 10.0.0.0/8 (RFC1918)
    if (a === 100 && b >= 64 && b <= 127) return true; // 100.64.0.0/10 (CGNAT)
    if (a === 127) return true; // 127.0.0.0/8 (Loopback)
    if (a === 169 && b === 254) return true; // 169.254.0.0/16 (Link-local / Cloud metadata)
    if (a === 172 && b >= 16 && b <= 31) return true; // 172.16.0.0/12 (RFC1918)
    if (a === 192 && b === 0 && c === 0) return true; // 192.0.0.0/24 (IETF)
    if (a === 192 && b === 0 && c === 2) return true; // 192.0.2.0/24 (TEST-NET-1)
    if (a === 192 && b === 168) return true; // 192.168.0.0/16 (RFC1918)
    if (a === 198 && (b === 18 || b === 19)) return true; // 198.18.0.0/15 (Benchmark)
    if (a === 198 && b === 51 && c === 100) return true; // 198.51.100.0/24 (TEST-NET-2)
    if (a === 203 && b === 0 && c === 113) return true; // 203.0.113.0/24 (TEST-NET-3)
    if (a >= 224) return true; // Multicast + Reserved (224.0.0.0/3)
    return false;
}

function expandIpv6(rawIp) {
    let ip = rawIp.toLowerCase().split('%')[0];
    // Handle IPv4-embedded suffix (e.g., ::ffff:127.0.0.1)
    const lastColon = ip.lastIndexOf(':');
    if (lastColon !== -1 && ip.slice(lastColon + 1).includes('.')) {
        const ipv4Part = ip.slice(lastColon + 1);
        const octets = ipv4Part.split('.').map(Number);
        if (octets.length !== 4 || octets.some((n) => !Number.isInteger(n) || n < 0 || n > 255)) {
            return null;
        }
        const hi = ((octets[0] << 8) | octets[1]).toString(16);
        const lo = ((octets[2] << 8) | octets[3]).toString(16);
        ip = `${ip.slice(0, lastColon)}:${hi}:${lo}`;
    }

    const halves = ip.split('::');
    if (halves.length > 2) return null;
    const left = halves[0] ? halves[0].split(':').filter(Boolean) : [];
    const right = halves.length === 2 && halves[1] ? halves[1].split(':').filter(Boolean) : [];
    const missing = halves.length === 2 ? 8 - (left.length + right.length) : 0;
    if (missing < 0 || (halves.length === 1 && left.length !== 8)) return null;

    const groups = [...left, ...Array(missing).fill('0'), ...right];
    if (groups.length !== 8) return null;
    const words = groups.map((g) => (/^[0-9a-f]{1,4}$/i.test(g) ? parseInt(g, 16) : NaN));
    if (words.some(Number.isNaN)) return null;
    return words;
}

function isPrivateIpv6(ip) {
    const words = expandIpv6(ip);
    if (!words) return true;

    const isAllZero = words.slice(0, 7).every((w) => w === 0);
    if (isAllZero && (words[7] === 0 || words[7] === 1)) return true; // :: e ::1

    const first = words[0];
    if (first >= 0xfc00 && first <= 0xfdff) return true; // fc00::/7 (ULA)
    if ((first & 0xffc0) === 0xfe80) return true; // fe80::/10 (Link-local)
    if ((first & 0xffc0) === 0xfec0) return true; // fec0::/10 (Site-local)
    if ((first & 0xff00) === 0xff00) return true; // ff00::/8 (Multicast)

    // IPv4-mapped (::ffff:0:0/96) ou IPv4-compatible (::/96)
    const isMappedV4 =
        words[0] === 0 && words[1] === 0 && words[2] === 0 &&
        words[3] === 0 && words[4] === 0 && (words[5] === 0xffff || words[5] === 0);
    if (isMappedV4) {
        const ipv4 = `${words[6] >> 8}.${words[6] & 0xff}.${words[7] >> 8}.${words[7] & 0xff}`;
        return isPrivateIpv4(ipv4);
    }

    // 6to4 (2002::/16) carrega IPv4 nos hextetos 1 e 2
    if (first === 0x2002) {
        const ipv4 = `${words[1] >> 8}.${words[1] & 0xff}.${words[2] >> 8}.${words[2] & 0xff}`;
        return isPrivateIpv4(ipv4);
    }

    // NAT64 well-known prefix (64:ff9b::/96)
    if (words[0] === 0x0064 && words[1] === 0xff9b && words[2] === 0 && words[3] === 0 && words[4] === 0 && words[5] === 0) {
        const ipv4 = `${words[6] >> 8}.${words[6] & 0xff}.${words[7] >> 8}.${words[7] & 0xff}`;
        return isPrivateIpv4(ipv4);
    }

    return false;
}

function isPrivateIp(rawIp) {
    if (!rawIp || typeof rawIp !== 'string') return true;
    const clean = rawIp.trim().replace(/^\[|\]$/g, '').split('%')[0];
    const kind = net.isIP(clean);
    if (kind === 4) return isPrivateIpv4(clean);
    if (kind === 6) return isPrivateIpv6(clean);
    return true;
}

function isSuspiciousNumericHost(host) {
    // Bloqueia formas não-canônicas de IPv4 (decimal puro, hex 0x7f..., octal 0177.0.0.1)
    if (/^\d+$/.test(host) || /^0x[0-9a-f]+$/i.test(host)) return true;
    if (/^\d+(?:\.\d+){1,3}$/.test(host)) {
        const parts = host.split('.');
        if (parts.length !== 4) return true;
        if (parts.some((p) => (p.length > 1 && p.startsWith('0')) || /^0x/i.test(p))) return true;
    }
    return false;
}

// Anti-SSRF: Block access to local / internal private networks and non-canonical numeric hosts
function isPrivateHost(hostname) {
    if (!hostname || typeof hostname !== 'string') return true;
    const host = hostname.trim().toLowerCase().replace(/^\[|\]$/g, '').replace(/\.$/, '');

    if (!host || host === 'localhost' || host.endsWith('.localhost') ||
        host.endsWith('.local') || host.endsWith('.internal') || host.endsWith('.arpa')) {
        return true;
    }

    const ipKind = net.isIP(host);
    if (ipKind !== 0) {
        return isPrivateIp(host);
    }

    if (isSuspiciousNumericHost(host)) {
        return true;
    }

    return false;
}

async function resolveAndValidateHost(hostname, lookupFn = dns.promises.lookup) {
    if (isPrivateHost(hostname)) {
        return { allowed: false, reason: 'Acesso a redes locais e privadas não é permitido.' };
    }

    const cleanHost = hostname.trim().replace(/^\[|\]$/g, '');
    const ipKind = net.isIP(cleanHost);
    if (ipKind !== 0) {
        return {
            allowed: !isPrivateIp(cleanHost),
            addresses: [{ address: cleanHost, family: ipKind }]
        };
    }

    let records;
    try {
        records = await lookupFn(cleanHost, { all: true, verbatim: true });
    } catch (_) {
        return { allowed: false, reason: 'Falha ao resolver destino.' };
    }

    if (!Array.isArray(records) || records.length === 0) {
        return { allowed: false, reason: 'Destino sem endereço IP resolvido.' };
    }

    for (const rec of records) {
        if (!rec || isPrivateIp(rec.address)) {
            return { allowed: false, reason: 'Acesso a redes locais e privadas não é permitido.' };
        }
    }

    return { allowed: true, addresses: records };
}

// In-memory rate limiting per IP (Generous limit to allow continuous HLS / MPEG-TS streaming chunks)
const requestCounts = new Map();
const MAX_REQUESTS_PER_MINUTE = 3000;

function isRateLimited(clientIp) {
    const now = Date.now();
    const windowStart = now - 60000;
    let record = requestCounts.get(clientIp);

    if (!record) {
        record = [];
        requestCounts.set(clientIp, record);
    }

    const recent = record.filter((timestamp) => timestamp > windowStart);
    recent.push(now);
    requestCounts.set(clientIp, recent);

    if (requestCounts.size > 2000) {
        for (const [ip, timestamps] of requestCounts.entries()) {
            if (timestamps.every((t) => t <= windowStart)) {
                requestCounts.delete(ip);
            }
        }
    }

    return recent.length > MAX_REQUESTS_PER_MINUTE;
}

function buildPinnedLookup(validatedAddresses) {
    const primary = validatedAddresses[0];
    return (_hostname, options, callback) => {
        if (typeof options === 'function') {
            callback = options;
            options = {};
        }
        if (options && options.all) {
            callback(null, validatedAddresses);
        } else {
            callback(null, primary.address, primary.family);
        }
    };
}

function createProxyHandler(deps = {}) {
    const lookupFn = deps.lookup || dns.promises.lookup;

    return async function handler(req, res) {
        const origin = req.headers && req.headers.origin;
        const secFetchSite = req.headers && req.headers['sec-fetch-site'];

        res.setHeader('Vary', 'Origin');
        res.setHeader('X-Content-Type-Options', 'nosniff');

        if (origin) {
            if (!isAllowedOrigin(origin, req)) {
                res.statusCode = 403;
                res.setHeader('Content-Type', 'application/json');
                res.end(JSON.stringify({ error: 'Origem não autorizada.' }));
                return;
            }
            res.setHeader('Access-Control-Allow-Origin', origin);
            res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
            res.setHeader('Access-Control-Allow-Headers', 'Range, If-Range, Content-Type');
            res.setHeader('Access-Control-Expose-Headers', 'Content-Length, Content-Range, Accept-Ranges, Content-Type');
        } else if (secFetchSite === 'cross-site') {
            res.statusCode = 403;
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify({ error: 'Requisição cross-site não autorizada.' }));
            return;
        }

        // Handle preflight OPTIONS request
        if (req.method === 'OPTIONS') {
            res.statusCode = 204;
            res.end();
            return;
        }

        if (req.method !== 'GET' && req.method !== 'HEAD') {
            res.statusCode = 405;
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify({ error: 'Método não permitido.' }));
            return;
        }

        // Rate limiting check
        const rawForwarded = req.headers && req.headers['x-forwarded-for'];
        const clientIp = (typeof rawForwarded === 'string' ? rawForwarded.split(',')[0].trim() : '') ||
            (req.socket && req.socket.remoteAddress) || 'unknown';
        if (isRateLimited(clientIp)) {
            res.statusCode = 429;
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify({ error: 'Muitas requisições. Tente novamente em instantes.' }));
            return;
        }

        // Extract target URL parameter
        let targetUrlStr = null;
        if (req.query && req.query.url) {
            targetUrlStr = req.query.url;
        } else {
            try {
                const parsedReq = new URL(req.url, 'http://localhost');
                targetUrlStr = parsedReq.searchParams.get('url');
            } catch (_) {
                targetUrlStr = null;
            }
        }

        if (!targetUrlStr) {
            res.statusCode = 400;
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify({ error: 'Falta o parâmetro url com o link de destino.' }));
            return;
        }

        let decodedUrl;
        try {
            decodedUrl = targetUrlStr;
            const parsedTarget = new URL(decodedUrl);

            if (parsedTarget.protocol !== 'http:' && parsedTarget.protocol !== 'https:') {
                res.statusCode = 400;
                res.setHeader('Content-Type', 'application/json');
                res.end(JSON.stringify({ error: 'Protocolo inválido. Apenas HTTP e HTTPS são suportados.' }));
                return;
            }

            if (isPrivateHost(parsedTarget.hostname)) {
                res.statusCode = 403;
                res.setHeader('Content-Type', 'application/json');
                res.end(JSON.stringify({ error: 'Acesso a redes locais e privadas não é permitido.' }));
                return;
            }
        } catch (_) {
            res.statusCode = 400;
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify({ error: 'URL inválida.' }));
            return;
        }

        try {
            const maxRedirects = 6;

            async function handleRequest(urlStr, redirectCount = 0) {
                if (redirectCount > maxRedirects) {
                    res.statusCode = 502;
                    res.end('Erro: Excesso de redirecionamentos.');
                    return;
                }

                let parsedUrl;
                try {
                    parsedUrl = new URL(urlStr);
                } catch (_) {
                    res.statusCode = 400;
                    res.end('URL de redirecionamento inválida.');
                    return;
                }

                if (parsedUrl.protocol !== 'http:' && parsedUrl.protocol !== 'https:') {
                    res.statusCode = 400;
                    res.end('Protocolo de redirecionamento inválido.');
                    return;
                }

                const dnsCheck = await resolveAndValidateHost(parsedUrl.hostname, lookupFn);
                if (!dnsCheck.allowed) {
                    res.statusCode = 403;
                    res.setHeader('Content-Type', 'application/json');
                    res.end(JSON.stringify({ error: dnsCheck.reason || 'Destino bloqueado pela política de rede.' }));
                    return;
                }

                const isHttps = parsedUrl.protocol === 'https:';
                const client = isHttps ? https : http;

                const forwardHeaders = {
                    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36',
                    'Accept': '*/*',
                    'Accept-Encoding': 'identity',
                    'Connection': 'keep-alive'
                };

                if (req.headers['range']) {
                    forwardHeaders['Range'] = req.headers['range'];
                }
                if (req.headers['if-range']) {
                    forwardHeaders['If-Range'] = req.headers['if-range'];
                }

                const options = {
                    hostname: parsedUrl.hostname,
                    port: parsedUrl.port || (isHttps ? 443 : 80),
                    path: parsedUrl.pathname + parsedUrl.search,
                    method: req.method === 'HEAD' ? 'HEAD' : 'GET',
                    headers: forwardHeaders,
                    lookup: buildPinnedLookup(dnsCheck.addresses)
                };
                if (isHttps) {
                    options.servername = parsedUrl.hostname;
                }

                const proxyReq = client.request(options, (proxyRes) => {
                    const statusCode = proxyRes.statusCode;

                    if (
                        (statusCode === 301 || statusCode === 302 || statusCode === 303 || statusCode === 307 || statusCode === 308) &&
                        proxyRes.headers.location
                    ) {
                        try { proxyRes.resume(); } catch (_) {}
                        let redirUrl = proxyRes.headers.location;
                        if (!redirUrl.startsWith('http://') && !redirUrl.startsWith('https://')) {
                            redirUrl = new URL(redirUrl, urlStr).toString();
                        }
                        handleRequest(redirUrl, redirectCount + 1).catch((err) => {
                            console.error('IPTV Proxy Redirect Error:', err);
                            if (!res.headersSent) {
                                res.statusCode = 502;
                                res.end('Falha ao seguir redirecionamento.');
                            }
                        });
                        return;
                    }

                    const responseHeaders = {
                        'Vary': 'Origin',
                        'X-Content-Type-Options': 'nosniff'
                    };
                    if (origin && isAllowedOrigin(origin, req)) {
                        responseHeaders['Access-Control-Allow-Origin'] = origin;
                        responseHeaders['Access-Control-Allow-Methods'] = 'GET, HEAD, OPTIONS';
                        responseHeaders['Access-Control-Allow-Headers'] = 'Range, If-Range, Content-Type';
                        responseHeaders['Access-Control-Expose-Headers'] = 'Content-Length, Content-Range, Accept-Ranges, Content-Type, Content-Encoding';
                    }

                    const isPlaylist = /get\.php|\.m3u8?|player_api\.php/i.test(decodedUrl);
                    const isStaticVod = /\.(mp4|mkv|avi|mov)(\?|$)/i.test(decodedUrl) || /\/(movie|series)\//i.test(decodedUrl);
                    const hasAccountData = /get\.php|player_api\.php|[?&](?:username|password)=|\/(?:live|movie|series)\//i.test(decodedUrl);
                    if (hasAccountData || isPlaylist) {
                        responseHeaders['Cache-Control'] = 'private, no-store, no-cache, must-revalidate';
                        responseHeaders['Pragma'] = 'no-cache';
                        responseHeaders['Expires'] = '0';
                    } else if (isStaticVod) {
                        responseHeaders['Cache-Control'] = 'public, max-age=86400, s-maxage=604800, stale-while-revalidate=86400';
                    } else {
                        responseHeaders['Cache-Control'] = 'no-cache, no-store, must-revalidate';
                        responseHeaders['Pragma'] = 'no-cache';
                        responseHeaders['Expires'] = '0';
                    }

                    if (proxyRes.headers['content-encoding']) {
                        responseHeaders['Content-Encoding'] = proxyRes.headers['content-encoding'];
                    }
                    if (proxyRes.headers['content-type']) {
                        responseHeaders['Content-Type'] = proxyRes.headers['content-type'];
                    }
                    if (proxyRes.headers['content-length']) {
                        responseHeaders['Content-Length'] = proxyRes.headers['content-length'];
                    }
                    if (proxyRes.headers['content-range']) {
                        responseHeaders['Content-Range'] = proxyRes.headers['content-range'];
                    }
                    if (proxyRes.headers['accept-ranges']) {
                        responseHeaders['Accept-Ranges'] = proxyRes.headers['accept-ranges'];
                    }

                    res.writeHead(statusCode, responseHeaders);
                    proxyRes.pipe(res);
                });

                const abortUpstream = () => {
                    if (!proxyReq.destroyed) proxyReq.destroy();
                };
                req.on('aborted', abortUpstream);
                res.on('close', () => {
                    if (!res.writableEnded) abortUpstream();
                });

                proxyReq.on('error', (err) => {
                    console.error('IPTV Proxy Request Error:', err);
                    if (!res.headersSent) {
                        res.statusCode = 502;
                        res.end('Falha ao conectar ao servidor de destino.');
                    }
                });

                proxyReq.setTimeout(25000, () => {
                    abortUpstream();
                    if (!res.headersSent) {
                        res.statusCode = 504;
                        res.end('Erro: Tempo limite de resposta esgotado no servidor IPTV.');
                    }
                });

                proxyReq.end();
            }

            await handleRequest(decodedUrl);
        } catch (error) {
            console.error('IPTV Proxy Exception:', error);
            if (!res.headersSent) {
                res.statusCode = 500;
                res.end('Erro interno ao processar proxy.');
            }
        }
    };
}

const defaultHandler = createProxyHandler();
defaultHandler.createProxyHandler = createProxyHandler;
defaultHandler.isAllowedOrigin = isAllowedOrigin;
defaultHandler.isPrivateIp = isPrivateIp;
defaultHandler.isPrivateHost = isPrivateHost;
defaultHandler.resolveAndValidateHost = resolveAndValidateHost;

module.exports = defaultHandler;
