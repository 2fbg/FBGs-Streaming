// api/proxy.js - SSRF & Open Proxy Hardened
const dns = require('dns').promises;
const ip = require('ip');

const ALLOWED_PROTOCOLS = ['http:', 'https:'];

function isPrivateIp(ipAddress) {
  return ip.isPrivate(ipAddress) || ip.isLoopback(ipAddress) || ipAddress === '169.254.169.254';
}

module.exports = async (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Range, Content-Type, Authorization');

  if (req.method === 'OPTIONS') return res.status(204).end();

  const targetUrl = req.query.url;
  if (!targetUrl) return res.status(400).json({ error: 'Parâmetro URL ausente.' });

  try {
    const parsed = new URL(targetUrl);
    if (!ALLOWED_PROTOCOLS.includes(parsed.protocol)) {
      return res.status(400).json({ error: 'Protocolo inválido.' });
    }

    const addresses = await dns.lookup(parsed.hostname, { all: true });
    for (const addr of addresses) {
      if (isPrivateIp(addr.address)) {
        return res.status(403).json({ error: 'Acesso a redes locais/privadas bloqueado por segurança.' });
      }
    }

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 12000);

    const forwardHeaders = {};
    if (req.headers.range) forwardHeaders['range'] = req.headers.range;

    const response = await fetch(targetUrl, {
      method: req.method,
      headers: forwardHeaders,
      signal: controller.signal
    });
    clearTimeout(timeout);

    res.status(response.status);
    response.headers.forEach((val, key) => {
      if (['content-type', 'content-length', 'content-range', 'accept-ranges'].includes(key.toLowerCase())) {
        res.setHeader(key, val);
      }
    });

    const buffer = await response.arrayBuffer();
    return res.send(Buffer.from(buffer));
  } catch (err) {
    if (err.name === 'AbortError') return res.status(504).json({ error: 'Gateway Timeout ao acessar stream remoto.' });
    return res.status(502).json({ error: 'Falha na comunicação com o servidor de mídia.' });
  }
};
