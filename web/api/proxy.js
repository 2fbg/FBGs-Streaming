// Vercel Serverless Function to act as a secure, fast, and dedicated streaming CORS Proxy for MK21 IPTV Web.
// Since Node/Vercel acts server-side, it bypasses browser CORS and Mixed Content blocks.
// Pipes binary streams directly from the source to prevent out-of-memory errors and timeout limits.

const http = require('http');
const https = require('https');
const { URL } = require('url');

// Anti-SSRF: Block access to local / internal private networks
function isPrivateHost(hostname) {
    if (!hostname) return true;
    const host = hostname.toLowerCase().replace(/^\[|\]$/g, '');

    if (host === 'localhost' || host === '127.0.0.1' || host === '::1' || host === '0.0.0.0') {
        return true;
    }
    // Block RFC 1918 & link-local IP spaces
    if (/^10\./.test(host) || /^192\.168\./.test(host) || /^169\.254\./.test(host)) {
        return true;
    }
    if (/^172\.(1[6-9]|2[0-9]|3[0-1])\./.test(host)) {
        return true;
    }
    if (host.endsWith('.local') || host.endsWith('.internal')) {
        return true;
    }
    return false;
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

    const recent = record.filter(timestamp => timestamp > windowStart);
    recent.push(now);
    requestCounts.set(clientIp, recent);

    // Periodically prune stale IPs
    if (requestCounts.size > 2000) {
        for (const [ip, timestamps] of requestCounts.entries()) {
            if (timestamps.every(t => t <= windowStart)) {
                requestCounts.delete(ip);
            }
        }
    }

    return recent.length > MAX_REQUESTS_PER_MINUTE;
}

module.exports = function handler(req, res) {
    const origin = req.headers.origin;
    res.setHeader('Access-Control-Allow-Origin', origin || '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', '*');
    res.setHeader('Access-Control-Expose-Headers', 'Content-Length, Content-Range, Accept-Ranges, Content-Type');

    // Handle preflight OPTIONS request
    if (req.method === 'OPTIONS') {
        res.statusCode = 200;
        res.end();
        return;
    }

    // Rate limiting check
    const clientIp = (req.headers && req.headers['x-forwarded-for']) || (req.socket && req.socket.remoteAddress) || 'unknown';
    if (isRateLimited(clientIp)) {
        res.statusCode = 429;
        res.setHeader('Content-Type', 'application/json');
        res.end(JSON.stringify({ error: 'Muitas requisições. Tente novamente em instantes.' }));
        return;
    }

    // Extract target URL parameter without deprecated url.parse()
    let targetUrlStr = null;
    if (req.query && req.query.url) {
        targetUrlStr = req.query.url;
    } else {
        try {
            const parsedReq = new URL(req.url, 'http://localhost');
            targetUrlStr = parsedReq.searchParams.get('url');
        } catch (e) {
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
        decodedUrl = decodeURIComponent(targetUrlStr);
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
    } catch (e) {
        res.statusCode = 400;
        res.setHeader('Content-Type', 'application/json');
        res.end(JSON.stringify({ error: 'URL inválida.' }));
        return;
    }

    try {
        const maxRedirects = 6;

        function handleRequest(urlStr, redirectCount = 0) {
            if (redirectCount > maxRedirects) {
                res.statusCode = 502;
                res.end('Erro: Excesso de redirecionamentos (Redirect Loop)');
                return;
            }

            let parsedUrl;
            try {
                parsedUrl = new URL(urlStr);
            } catch (err) {
                res.statusCode = 400;
                res.end('URL de redirecionamento inválida.');
                return;
            }

            if (isPrivateHost(parsedUrl.hostname)) {
                res.statusCode = 403;
                res.end('Redirecionamento para rede interna bloqueado.');
                return;
            }

            const client = parsedUrl.protocol === 'https:' ? https : http;

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
                port: parsedUrl.port || (parsedUrl.protocol === 'https:' ? 443 : 80),
                path: parsedUrl.pathname + parsedUrl.search,
                method: req.method === 'HEAD' ? 'HEAD' : 'GET',
                headers: forwardHeaders
            };

            const proxyReq = client.request(options, (proxyRes) => {
                const statusCode = proxyRes.statusCode;

                // Follow HTTP Redirects internally (301, 302, 303, 307, 308)
                if ((statusCode === 301 || statusCode === 302 || statusCode === 303 || statusCode === 307 || statusCode === 308) && proxyRes.headers.location) {
                    let redirUrl = proxyRes.headers.location;
                    if (!redirUrl.startsWith('http://') && !redirUrl.startsWith('https://')) {
                        redirUrl = new URL(redirUrl, urlStr).toString();
                    }
                    handleRequest(redirUrl, redirectCount + 1);
                    return;
                }

                // Copy stream and media relevant response headers
                const responseHeaders = {
                    'Access-Control-Allow-Origin': origin || '*',
                    'Access-Control-Allow-Methods': 'GET, HEAD, OPTIONS',
                    'Access-Control-Allow-Headers': '*',
                    'Access-Control-Expose-Headers': 'Content-Length, Content-Range, Accept-Ranges, Content-Type, Content-Encoding'
                };

                // Smart Caching Headers:
                // 1. Playlists (M3U / JSON): Cache on Edge CDN so reload is instant (under 50ms)
                // 2. VOD media chunks (MP4/MKV): Cache for up to 7 days
                const isPlaylist = /get\.php|\.m3u8?|player_api\.php/i.test(decodedUrl);
                const isStaticVod = /\.(mp4|mkv|avi|mov)(\?|$)/i.test(decodedUrl) || /\/(movie|series)\//i.test(decodedUrl);
                if (isStaticVod) {
                    responseHeaders['Cache-Control'] = 'public, max-age=86400, s-maxage=604800, stale-while-revalidate=86400';
                } else if (isPlaylist) {
                    responseHeaders['Cache-Control'] = 'public, max-age=600, s-maxage=1800, stale-while-revalidate=7200';
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

                // Stream binary chunks from source to client response
                proxyRes.pipe(res);
            });

            proxyReq.on('error', (err) => {
                console.error('IPTV Proxy Request Error:', err);
                if (!res.headersSent) {
                    res.statusCode = 502;
                    res.end(`Erro de conexão do proxy: ${err.message}`);
                }
            });

            proxyReq.end();
        }

        handleRequest(decodedUrl);

    } catch (error) {
        console.error('IPTV Proxy Exception:', error);
        if (!res.headersSent) {
            res.statusCode = 500;
            res.end(`Erro ao processar proxy: ${error.message}`);
        }
    }
};
